/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.count;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.skip;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

import io.harness.beans.FeatureName;
import io.harness.network.Http;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ArtifactRepository;
import io.harness.repositories.BaselineRepository;
import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse.EnvTypeEnum;
import io.harness.spec.server.ssca.v1.model.ArtifactDetailResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse.ActivityEnum;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponseScorecard;
import io.harness.spec.server.ssca.v1.model.ComponentFilter;
import io.harness.spec.server.ssca.v1.model.LicenseFilter;
import io.harness.spec.server.ssca.v1.model.PipelineInfo;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.spec.server.ssca.v1.model.Slsa;
import io.harness.ssca.beans.EnforcementSummaryDBO.EnforcementSummaryDBOKeys;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.SbomDTO;
import io.harness.ssca.beans.remediation_tracker.PatchedPendingArtifactEntitiesResult;
import io.harness.ssca.entities.BaselineEntity;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.EnforcementSummaryEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity.EnforcementSummaryEntityKeys;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.events.SSCAArtifactUpdatedEvent;
import io.harness.ssca.search.SearchService;
import io.harness.ssca.search.beans.ArtifactFilter;
import io.harness.ssca.utils.PipelineUtils;
import io.harness.ssca.utils.SBOMUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.CountOperation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SkipOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class ArtifactServiceImpl implements ArtifactService {
  @Inject ArtifactRepository artifactRepository;
  @Inject EnforcementSummaryRepo enforcementSummaryRepo;
  @Inject EnforcementSummaryService enforcementSummaryService;
  @Inject NormalisedSbomComponentService normalisedSbomComponentService;
  @Inject CdInstanceSummaryService cdInstanceSummaryService;
  @Inject PipelineUtils pipelineUtils;

  @Inject FeatureFlagService featureFlagService;

  @Inject SearchService searchService;

  @Inject BaselineRepository baselineRepository;

  @Inject MongoTemplate mongoTemplate;

  @Inject TransactionTemplate transactionTemplate;

  @Inject OutboxService outboxService;

  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject @Named("isElasticSearchEnabled") boolean isElasticSearchEnabled;

  private final String GCP_REGISTRY_HOST = "gcr.io";

  @Override
  public ArtifactEntity getArtifactFromSbomPayload(
      String accountId, String orgIdentifier, String projectIdentifier, SbomProcessRequestBody body, SbomDTO sbomDTO) {
    String artifactId = generateArtifactId(body.getArtifact().getRegistryUrl(), body.getArtifact().getName());
    return ArtifactEntity.builder()
        .id(UUID.randomUUID().toString())
        .artifactId(artifactId)
        .orchestrationId(body.getSbomMetadata().getStepExecutionId())
        .pipelineExecutionId(body.getSbomMetadata().getPipelineExecutionId())
        .artifactCorrelationId(getCDImagePath(
            body.getArtifact().getRegistryUrl(), body.getArtifact().getName(), body.getArtifact().getTag()))
        .name(body.getArtifact().getName())
        .orgId(orgIdentifier)
        .projectId(projectIdentifier)
        .accountId(accountId)
        .sbomName(body.getSbomProcess().getName())
        .type(body.getArtifact().getType())
        .url(body.getArtifact().getRegistryUrl())
        .pipelineId(body.getSbomMetadata().getPipelineIdentifier())
        .stageId(body.getSbomMetadata().getStageIdentifier())
        .tag(body.getArtifact().getTag())
        .isAttested(body.getAttestation().isIsAttested())
        .attestedFileUrl(body.getAttestation().getUrl())
        .stepId(body.getSbomMetadata().getStepIdentifier())
        .sequenceId(body.getSbomMetadata().getSequenceId())
        .createdOn(Instant.now())
        .sbom(ArtifactEntity.Sbom.builder()
                  .tool(body.getSbomMetadata().getTool())
                  .toolVersion("2.0")
                  .sbomFormat(body.getSbomProcess().getFormat())
                  .sbomVersion(SBOMUtils.getSbomVersion(sbomDTO))
                  .build())
        .prodEnvCount(0l)
        .nonProdEnvCount(0l)
        .invalid(false)
        .build();
  }

  @Override
  public Optional<ArtifactEntity> getArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String orchestrationId) {
    return artifactRepository.findByAccountIdAndOrgIdAndProjectIdAndOrchestrationId(
        accountId, orgIdentifier, projectIdentifier, orchestrationId);
  }

  @Override
  public Optional<ArtifactEntity> getArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, Sort sort) {
    return artifactRepository.findFirstByAccountIdAndOrgIdAndProjectIdAndArtifactIdLike(
        accountId, orgIdentifier, projectIdentifier, artifactId, sort);
  }

  @Override
  public String getArtifactName(String accountId, String orgIdentifier, String projectIdentifier, String artifactId) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.artifactId)
                            .is(artifactId)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    ArtifactEntity artifactEntity =
        artifactRepository.findOne(criteria, Sort.by(Direction.DESC, ArtifactEntityKeys.createdOn.toLowerCase()),
            List.of(ArtifactEntityKeys.name, ArtifactEntityKeys.isAttested.toLowerCase()));
    if (artifactEntity == null) {
      return null;
    }
    return artifactEntity.getName();
  }

  @Override
  public ArtifactEntity getArtifactByCorrelationId(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactCorrelationId) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.artifactCorrelationId)
                            .is(artifactCorrelationId)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    return artifactRepository.findOne(criteria);
  }

  @Override
  public ArtifactEntity getLatestArtifactByImageNameAndTag(
      String accountId, String orgIdentifier, String projectIdentifier, String imageName, String tag) {
    // regex for image name ^(.*\/)?<IMAGE NAME>$
    String nameEndingWithImageNameRegex = "^(.*\\/)?" + imageName + "$";
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.name)
                            .regex(nameEndingWithImageNameRegex)
                            .and(ArtifactEntityKeys.tag)
                            .is(tag)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    Sort sort = Sort.by(Direction.DESC, ArtifactEntityKeys.createdOn);
    return artifactRepository.findOne(criteria, sort, new ArrayList<>());
  }

  @Override
  public ArtifactEntity getLatestArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String tag) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.artifactId)
                            .is(artifactId)
                            .and(ArtifactEntityKeys.tag)
                            .is(tag)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    return artifactRepository.findOne(criteria);
  }

  @Override
  public ArtifactEntity getLatestArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.artifactId)
                            .is(artifactId)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    return artifactRepository.findOne(
        criteria, Sort.by(Direction.DESC, ArtifactEntityKeys.createdOn.toLowerCase()), new ArrayList<>());
  }

  @Override
  public ArtifactDetailResponse getArtifactDetails(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String tag) {
    ArtifactEntity artifact = getLatestArtifact(accountId, orgIdentifier, projectIdentifier, artifactId, tag);
    if (Objects.isNull(artifact)) {
      throw new NotFoundException(
          String.format("Artifact with artifactId [%s] and tag [%s] is not found", artifactId, tag));
    }
    PipelineInfo pipelineInfo = getPipelineInfo(accountId, orgIdentifier, projectIdentifier, artifact);
    return new ArtifactDetailResponse()
        .id(artifact.getArtifactId())
        .name(artifact.getName())
        .tag(artifact.getTag())
        .url(artifact.getUrl())
        .componentsCount(artifact.getComponentsCount().intValue())
        .updated(String.format("%d", artifact.getLastUpdatedAt()))
        .prodEnvCount(artifact.getProdEnvCount().intValue())
        .nonProdEnvCount(artifact.getNonProdEnvCount().intValue())
        .buildPipelineId(pipelineInfo.getId())
        .buildPipelineName(pipelineInfo.getName())
        .buildPipelineExecutionId(pipelineInfo.getExecutionId())
        .orchestrationId(artifact.getOrchestrationId());
  }

  @Override
  public PipelineInfo getPipelineInfo(
      String accountId, String orgIdentifier, String projectIdentifier, ArtifactEntity artifact) {
    JsonNode node = pipelineUtils.getPipelineExecutionSummaryResponse(
        artifact.getPipelineExecutionId(), accountId, orgIdentifier, projectIdentifier);
    return new PipelineInfo()
        .id(artifact.getPipelineId())
        .name(pipelineUtils.parsePipelineName(node))
        .executionId(artifact.getPipelineExecutionId());
  }

  @Override
  public String generateArtifactId(String registryUrl, String name) {
    return UUID.nameUUIDFromBytes((registryUrl + ":" + name).getBytes()).toString();
  }

  @Override
  @Transactional
  public void saveArtifactAndInvalidateOldArtifact(ArtifactEntity artifact) {
    ArtifactEntity lastArtifact = getLatestArtifact(artifact.getAccountId(), artifact.getOrgId(),
        artifact.getProjectId(), artifact.getArtifactId(), artifact.getTag());
    artifactRepository.invalidateOldArtifact(artifact);
    artifact.setLastUpdatedAt(artifact.getCreatedOn().toEpochMilli());
    if (Objects.nonNull(lastArtifact)) {
      artifact.setProdEnvCount(lastArtifact.getProdEnvCount());
      artifact.setNonProdEnvCount(lastArtifact.getNonProdEnvCount());
    }
    artifactRepository.save(artifact);
  }

  @Override
  public void saveArtifact(ArtifactEntity artifact) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      artifactRepository.save(artifact);
      if (isElasticSearchEnabled) {
        outboxService.save(new SSCAArtifactUpdatedEvent(
            artifact.getAccountId(), artifact.getOrgId(), artifact.getProjectId(), artifact));
      }
      return artifact.getArtifactId();
    }));
  }

  @Override
  public Page<ArtifactListingResponse> listLatestArtifacts(
      String accountId, String orgIdentifier, String projectIdentifier, Pageable pageable) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);

    MatchOperation matchOperation = match(criteria);
    SortOperation sortOperation = sort(Sort.by(Direction.DESC, ArtifactEntityKeys.createdOn));
    GroupOperation groupByArtifactId = group(ArtifactEntityKeys.artifactId).first("$$ROOT").as("document");
    Sort.Order customSort = pageable.getSort().get().collect(Collectors.toList()).get(0);
    SortOperation customSortOperation =
        sort(Sort.by(customSort.getDirection(), "document." + customSort.getProperty()));
    SkipOperation skipOperation = skip(pageable.getOffset());
    LimitOperation limitOperation = limit(pageable.getPageSize());
    ProjectionOperation projectionOperation = new ProjectionOperation().andExclude("_id");

    Aggregation aggregation = Aggregation.newAggregation(matchOperation, sortOperation, groupByArtifactId,
        customSortOperation, projectionOperation, skipOperation, limitOperation);
    List<ArtifactEntity> artifactEntities = artifactRepository.findAll(aggregation);
    // Aggregation Query: { "aggregate" : "__collection__", "pipeline" : [{ "$match" : { "accountId" :
    // "kmpySmUISimoRrJL6NL73w", "orgId" : "default", "projectId" : "LocalPipeline", "invalid" : false}}, { "$sort" : {
    // "lastUpdatedAt" : -1, "sort" : 1}}, { "$group" : { "_id" : "$artifactId", "document" : { "$first" : "$$ROOT"}}},
    // { "$project" : { "_id" : 0}}, { "$skip" : skip}, { "$limit" : limit}]}
    List<ArtifactListingResponse> artifactListingResponses =
        getArtifactListingResponses(accountId, orgIdentifier, projectIdentifier, artifactEntities);

    CountOperation countOperation = count().as("count");
    aggregation = Aggregation.newAggregation(matchOperation, groupByArtifactId, countOperation);
    long total = artifactRepository.getCount(aggregation);

    return new PageImpl<>(artifactListingResponses, pageable, total);
  }

  @Override
  public Page<ArtifactListingResponse> listArtifacts(String accountId, String orgIdentifier, String projectIdentifier,
      ArtifactListingRequestBody body, Pageable pageable) {
    if (featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_USE_ELK.name())) {
      List<String> orchestrationIds = searchService.getOrchestrationIds(accountId, orgIdentifier, projectIdentifier,
          ArtifactFilter.builder()
              .searchTerm(body.getSearchTerm())
              .componentFilter(body.getComponentFilter())
              .licenseFilter(body.getLicenseFilter())
              .build());

      if (orchestrationIds.isEmpty()) {
        return Page.empty();
      }

      Criteria criteria = Criteria.where(ArtifactEntityKeys.orchestrationId)
                              .in(orchestrationIds)
                              .andOperator(getPolicyFilterCriteria(body), getDeploymentFilterCriteria(body));

      Page<ArtifactEntity> artifactEntities = artifactRepository.findAll(criteria, pageable);

      List<ArtifactListingResponse> artifactListingResponses =
          getArtifactListingResponses(accountId, orgIdentifier, projectIdentifier, artifactEntities.toList());

      return new PageImpl<>(artifactListingResponses, pageable, artifactEntities.getTotalElements());
    }

    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);

    if (!StringUtils.isEmpty(body.getSearchTerm())) {
      criteria.and(ArtifactEntityKeys.name).regex(body.getSearchTerm(), "i");
    }

    LicenseFilter licenseFilter = body.getLicenseFilter();
    List<ComponentFilter> componentFilter = body.getComponentFilter();
    Criteria filterCriteria = new Criteria();
    if (Objects.nonNull(licenseFilter) || isNotEmpty(componentFilter)) {
      List<String> orchestrationIds = normalisedSbomComponentService.getOrchestrationIds(
          accountId, orgIdentifier, projectIdentifier, licenseFilter, componentFilter);
      if (isNotEmpty(orchestrationIds)) {
        filterCriteria = Criteria.where(ArtifactEntityKeys.orchestrationId).in(orchestrationIds);
      } else {
        return Page.empty();
      }
    }

    criteria.andOperator(getPolicyFilterCriteria(body), getDeploymentFilterCriteria(body), filterCriteria);

    Page<ArtifactEntity> artifactEntities = artifactRepository.findAll(criteria, pageable);

    List<ArtifactListingResponse> artifactListingResponses =
        getArtifactListingResponses(accountId, orgIdentifier, projectIdentifier, artifactEntities.toList());

    return new PageImpl<>(artifactListingResponses, pageable, artifactEntities.getTotalElements());
  }

  // make this method accept orchestrationIds and return two separate lists of artifactENtities,
  // one with criteria orchestrationId in orchestrationIds and
  // the other with orchestrationId nin orchestrationIds using one facet query.
  // Currently i had to call this method twice for both the usecases. It is not efficient.
  @Override
  public List<PatchedPendingArtifactEntitiesResult> listDeployedArtifactsFromIdsWithCriteria(String accountId,
      String orgIdentifier, String projectIdentifier, Set<String> artifactIds, List<String> orchestrationIds) {
    Criteria criteria =
        Criteria.where(ArtifactEntityKeys.accountId)
            .is(accountId)
            .and(ArtifactEntityKeys.orgId)
            .is(orgIdentifier)
            .and(ArtifactEntityKeys.projectId)
            .is(projectIdentifier)
            .and(ArtifactEntityKeys.invalid)
            .is(false)
            .and(ArtifactEntityKeys.artifactId)
            .in(artifactIds)
            .andOperator(new Criteria().orOperator(Criteria.where(ArtifactEntityKeys.prodEnvCount).gt(0),
                Criteria.where(ArtifactEntityKeys.nonProdEnvCount).gt(0)));
    Aggregation aggregation =
        Aggregation.newAggregation(Aggregation.facet(getPatchedDeploymentCriteria(criteria, orchestrationIds))
                                       .as("patchedArtifacts")
                                       .and(getPendingDeploymentCriteria(criteria, orchestrationIds))
                                       .as("pendingArtifacts"));
    return mongoTemplate.aggregate(aggregation, ArtifactEntity.class, PatchedPendingArtifactEntitiesResult.class)
        .getMappedResults();
  }

  private AggregationOperation getPatchedDeploymentCriteria(Criteria criteria, List<String> orchestrationIds) {
    Criteria patchedCriteria =
        new Criteria().andOperator(criteria, Criteria.where(ArtifactEntityKeys.orchestrationId).nin(orchestrationIds));
    return Aggregation.match(patchedCriteria);
  }

  private AggregationOperation getPendingDeploymentCriteria(Criteria criteria, List<String> orchestrationIds) {
    Criteria pendingCriteria =
        new Criteria().andOperator(criteria, Criteria.where(ArtifactEntityKeys.orchestrationId).in(orchestrationIds));
    return Aggregation.match(pendingCriteria);
  }

  @Override
  public Page<ArtifactComponentViewResponse> getArtifactComponentView(String accountId, String orgIdentifier,
      String projectIdentifier, String artifactId, String tag, ArtifactComponentViewRequestBody filterBody,
      Pageable pageable) {
    ArtifactEntity artifact = getLatestArtifact(accountId, orgIdentifier, projectIdentifier, artifactId, tag);
    if (Objects.isNull(artifact)) {
      throw new NotFoundException(String.format("No Artifact Found with {id: %s}", artifactId));
    }

    return normalisedSbomComponentService
        .getNormalizedSbomComponents(accountId, orgIdentifier, projectIdentifier, artifact, filterBody, pageable)
        .map(entity
            -> new ArtifactComponentViewResponse()
                   .packageName(entity.getPackageName())
                   .packageLicense(String.join(", ", entity.getPackageLicense()))
                   .packageManager(entity.getPackageManager())
                   .packageSupplier(entity.getPackageOriginatorName())
                   .purl(entity.getPurl())
                   .packageVersion(entity.getPackageVersion()));
  }

  @Override
  public Page<ArtifactDeploymentViewResponse> getArtifactDeploymentView(String accountId, String orgIdentifier,
      String projectIdentifier, String artifactId, String tag, ArtifactDeploymentViewRequestBody filterBody,
      Pageable pageable) {
    ArtifactEntity artifact = getLatestArtifact(accountId, orgIdentifier, projectIdentifier, artifactId, tag);
    if (Objects.isNull(artifact)) {
      throw new NotFoundException(String.format("No Artifact Found with {id: %s}", artifactId));
    }

    Page<CdInstanceSummary> cdInstanceSummaries = cdInstanceSummaryService.getCdInstanceSummaries(
        accountId, orgIdentifier, projectIdentifier, artifact, filterBody, pageable);
    Criteria enforcementSummaryCriteria = Criteria.where(EnforcementSummaryEntityKeys.accountId)
                                              .is(accountId)
                                              .and(EnforcementSummaryEntityKeys.orgIdentifier)
                                              .is(orgIdentifier)
                                              .and(EnforcementSummaryEntityKeys.projectIdentifier)
                                              .is(projectIdentifier)
                                              .and(EnforcementSummaryEntityKeys.pipelineExecutionId)
                                              .in(cdInstanceSummaries.map(entity -> entity.getLastPipelineExecutionId())
                                                      .get()
                                                      .collect(Collectors.toList()));

    Map<String, EnforcementSummaryEntity> enforcementSummaryEntityMap =
        enforcementSummaryRepo.findAll(enforcementSummaryCriteria)
            .stream()
            .collect(Collectors.toMap(EnforcementSummaryEntity::getPipelineExecutionId, Function.identity()));

    return cdInstanceSummaries.map(entity -> {
      EnforcementSummaryEntity enforcementSummaryEntity =
          enforcementSummaryEntityMap.get(entity.getLastPipelineExecutionId());

      ArtifactDeploymentViewResponse response =
          new ArtifactDeploymentViewResponse()
              .envId(entity.getEnvIdentifier())
              .envName(entity.getEnvName())
              .envType(entity.getEnvType() == EnvType.Production ? EnvTypeEnum.PROD : EnvTypeEnum.NONPROD)
              .pipelineName(entity.getLastPipelineName())
              .pipelineId(entity.getLastPipelineExecutionName())
              .pipelineExecutionId(entity.getLastPipelineExecutionId())
              .pipelineSequenceId(entity.getSequenceId())
              .triggeredById(entity.getLastDeployedById())
              .triggeredBy(entity.getLastDeployedByName())
              .triggeredAt(entity.getLastDeployedAt().toString())
              .triggeredType(entity.getTriggerType());

      if (Objects.nonNull(enforcementSummaryEntity)) {
        response.allowListViolationCount(String.valueOf(enforcementSummaryEntity.getAllowListViolationCount()))
            .denyListViolationCount(String.valueOf(enforcementSummaryEntity.getDenyListViolationCount()))
            .enforcementId(enforcementSummaryEntity.getEnforcementId());
      }

      if (Objects.nonNull(entity.getSlsaVerificationSummary())) {
        response.slsaVerification(
            new Slsa()
                .provenance(entity.getSlsaVerificationSummary().getProvenanceArtifact())
                .policyOutcomeStatus(entity.getSlsaVerificationSummary().getSlsaPolicyOutcomeStatus()));
      }

      return response;
    });
  }

  @Override
  public void updateArtifactEnvCount(ArtifactEntity artifact, EnvType envType, long count) {
    if (envType == EnvType.Production) {
      long envCount = Long.max(artifact.getProdEnvCount() + count, 0);
      artifact.setProdEnvCount(envCount);
    } else {
      long envCount = Long.max(artifact.getNonProdEnvCount() + count, 0);
      artifact.setNonProdEnvCount(envCount);
    }
    artifact.setLastUpdatedAt(Instant.now().toEpochMilli());
    saveArtifact(artifact);
  }

  @Override
  public ArtifactEntity getLastGeneratedArtifactFromTime(
      String accountId, String orgId, String projectId, String artifactId, Instant time) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgId)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectId)
                            .and(ArtifactEntityKeys.artifactId)
                            .is(artifactId)
                            .and(ArtifactEntityKeys.createdOn)
                            .lt(time);
    return artifactRepository.findOne(
        criteria, Sort.by(Direction.DESC, ArtifactEntityKeys.createdOn), new ArrayList<>());
  }

  private List<ArtifactListingResponse> getArtifactListingResponses(
      String accountId, String orgIdentifier, String projectIdentifier, List<ArtifactEntity> artifactEntities) {
    List<String> orchestrationIds =
        artifactEntities.stream().map(ArtifactEntity::getOrchestrationId).collect(Collectors.toList());

    Criteria criteria = Criteria.where(EnforcementSummaryEntityKeys.orchestrationId).in(orchestrationIds);
    MatchOperation matchOperation = match(criteria);
    SortOperation sortOperation = sort(Sort.by(Direction.DESC, EnforcementSummaryEntityKeys.createdAt));
    GroupOperation groupByOrchestrationId =
        group(EnforcementSummaryEntityKeys.orchestrationId).first("$$ROOT").as("document");
    Aggregation aggregation = newAggregation(matchOperation, sortOperation, groupByOrchestrationId);

    // Aggregate Query: { "aggregate" : "__collection__", "pipeline" : [{ "$match" : { "orchestrationId" : { "$in" :
    // ["unique123", "unique12", "unique12"]}}}, { "$sort" : { "createdAt" : -1}}, { "$group" : { "_id" :
    // "$orchestrationId", "document" : { "$first" : "$$ROOT"}}}]}
    List<EnforcementSummaryEntity> enforcementSummaryEntities = enforcementSummaryRepo.findAll(aggregation);
    Map<String, EnforcementSummaryEntity> enforcementSummaryEntityMap = enforcementSummaryEntities.stream().collect(
        Collectors.toMap(entity -> entity.getOrchestrationId(), Function.identity()));

    List<BaselineEntity> baselineEntities =
        baselineRepository.findAll(accountId, orgIdentifier, projectIdentifier, orchestrationIds);

    Set<String> baselineEntityOrchestrationIds =
        baselineEntities.stream().map(entity -> entity.getOrchestrationId()).collect(Collectors.toSet());

    List<ArtifactListingResponse> responses = new ArrayList<>();
    for (ArtifactEntity artifact : artifactEntities) {
      EnforcementSummaryEntity enforcementSummary = EnforcementSummaryEntity.builder().build();

      if (enforcementSummaryEntityMap.containsKey(artifact.getOrchestrationId())) {
        enforcementSummary = enforcementSummaryEntityMap.get(artifact.getOrchestrationId());
      }

      Boolean baseline = false;
      if (baselineEntityOrchestrationIds.contains(artifact.getOrchestrationId())) {
        baseline = true;
      }
      ArtifactListingResponseScorecard scorecard = new ArtifactListingResponseScorecard();
      if (artifact.getScorecard() != null) {
        scorecard.setAvgScore(artifact.getScorecard().getAvgScore());
        scorecard.setMaxScore(artifact.getScorecard().getMaxScore());
      }

      responses.add(
          new ArtifactListingResponse()
              .id(artifact.getArtifactId())
              .name(artifact.getName())
              .tag(artifact.getTag())
              .url(artifact.getUrl())
              .componentsCount(artifact.getComponentsCount().intValue())
              .allowListViolationCount(String.valueOf(enforcementSummary.getAllowListViolationCount()))
              .denyListViolationCount(String.valueOf(enforcementSummary.getDenyListViolationCount()))
              .enforcementId(enforcementSummary.getEnforcementId())
              .activity(artifact.getProdEnvCount() + artifact.getNonProdEnvCount() == 0 ? ActivityEnum.GENERATED
                                                                                        : ActivityEnum.DEPLOYED)
              .updated(String.format("%d", artifact.getLastUpdatedAt()))
              .prodEnvCount(artifact.getProdEnvCount().intValue())
              .nonProdEnvCount(artifact.getNonProdEnvCount().intValue())
              .orchestrationId(artifact.getOrchestrationId())
              .buildPipelineId(artifact.getPipelineId())
              .buildPipelineExecutionId(artifact.getPipelineExecutionId())
              .baseline(baseline)
              .scorecard(scorecard));
    }
    return responses;
  }

  @VisibleForTesting
  String getCDImagePath(String url, String image, String tag) {
    URI uri = UriBuilder.fromUri(url).build();
    String registryUrl = UriBuilder.fromUri(url).path(uri.getPath().endsWith("/") ? "" : "/").build().toString();
    String domainName = Http.getDomainWithPort(registryUrl);
    if (domainName.contains(GCP_REGISTRY_HOST)) {
      return image + ":" + tag;
    }
    return domainName + "/" + image + ":" + tag;
  }

  private Criteria getPolicyFilterCriteria(ArtifactListingRequestBody body) {
    Criteria criteria = new Criteria();
    if (Objects.isNull(body) || Objects.isNull(body.getPolicyViolation())) {
      return criteria;
    }
    Aggregation aggregation = getPolicyViolationEnforcementAggregation(body);

    // { "aggregate" : "__collection__", "pipeline" : [{ "$sort" : { "createdAt" : -1}}, { "$group" : { "_id" :
    // "$orchestrationId", "document" : { "$first" : "$$ROOT"}}}, { "$unwind" : "$document"}, { "$match" : {
    // "document.denylistviolationcount" : { "$ne" : 0}}}]}
    Set<String> orchestrationIds = enforcementSummaryRepo.findAllOrchestrationId(aggregation);
    criteria.and(ArtifactEntityKeys.orchestrationId).in(orchestrationIds);
    return criteria;
  }

  @VisibleForTesting
  Aggregation getPolicyViolationEnforcementAggregation(ArtifactListingRequestBody body) {
    Criteria enforcementCriteria = new Criteria();
    switch (body.getPolicyViolation()) {
      case DENY:
        enforcementCriteria = Criteria
                                  .where(EnforcementSummaryDBOKeys.document + "."
                                      + EnforcementSummaryEntityKeys.denyListViolationCount.toLowerCase())
                                  .ne(0);
        break;
      case ALLOW:
        enforcementCriteria = Criteria
                                  .where(EnforcementSummaryDBOKeys.document + "."
                                      + EnforcementSummaryEntityKeys.allowListViolationCount.toLowerCase())
                                  .ne(0);
        break;
      case ANY:
        Criteria allowCriteria = Criteria
                                     .where(EnforcementSummaryDBOKeys.document + "."
                                         + EnforcementSummaryEntityKeys.allowListViolationCount.toLowerCase())
                                     .ne(0);
        Criteria denyCriteria = Criteria
                                    .where(EnforcementSummaryDBOKeys.document + "."
                                        + EnforcementSummaryEntityKeys.denyListViolationCount.toLowerCase())
                                    .ne(0);
        enforcementCriteria = new Criteria().orOperator(allowCriteria, denyCriteria);
        break;
      case NONE:
        enforcementCriteria = Criteria
                                  .where(EnforcementSummaryDBOKeys.document + "."
                                      + EnforcementSummaryEntityKeys.allowListViolationCount.toLowerCase())
                                  .is(0)
                                  .and(EnforcementSummaryDBOKeys.document + "."
                                      + EnforcementSummaryEntityKeys.denyListViolationCount.toLowerCase())
                                  .is(0);
        break;
      default:
        log.error("Unknown Policy Violation Type");
    }

    MatchOperation matchOperation = match(enforcementCriteria);
    SortOperation sortOperation = sort(Sort.by(Direction.DESC, EnforcementSummaryEntityKeys.createdAt));
    GroupOperation groupByOrchestrationId =
        group(EnforcementSummaryEntityKeys.orchestrationId).first("$$ROOT").as(EnforcementSummaryDBOKeys.document);
    UnwindOperation unwindOperation = unwind(EnforcementSummaryDBOKeys.document);
    ProjectionOperation projectionOperation = Aggregation.project(
        EnforcementSummaryDBOKeys.document + "." + EnforcementSummaryEntityKeys.orchestrationId.toLowerCase());
    return newAggregation(sortOperation, groupByOrchestrationId, unwindOperation, matchOperation, projectionOperation);
  }

  private Criteria getDeploymentFilterCriteria(ArtifactListingRequestBody body) {
    if (Objects.isNull(body) || Objects.isNull(body.getEnvironmentType())) {
      return new Criteria();
    }
    switch (body.getEnvironmentType()) {
      case NONPROD:
        return Criteria.where(ArtifactEntityKeys.nonProdEnvCount).gt(0);
      case PROD:
        return Criteria.where(ArtifactEntityKeys.prodEnvCount).gt(0);
      case ALL:
        return new Criteria().orOperator(Criteria.where(ArtifactEntityKeys.nonProdEnvCount).gt(0),
            Criteria.where(ArtifactEntityKeys.prodEnvCount).gt(0));
      case NONE:
        return Criteria.where(ArtifactEntityKeys.nonProdEnvCount).is(0).and(ArtifactEntityKeys.prodEnvCount).is(0);
      default:
        log.error("Unknown Policy Environment Type");
    }
    return new Criteria();
  }

  @Override
  public Set<String> getDistinctArtifactIds(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> orchestrationIds) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    Criteria filterCriteria;
    if (isNotEmpty(orchestrationIds)) {
      filterCriteria = Criteria.where(ArtifactEntityKeys.orchestrationId).in(orchestrationIds);
    } else {
      return Collections.emptySet();
    }
    criteria.andOperator(filterCriteria);
    return new HashSet<>(artifactRepository.findDistinctArtifactIds(criteria));
  }
}
