/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ssca.services.remediation_tracker;

import static io.harness.authorization.AuthorizationServiceHeader.SSCA_SERVICE;
import static io.harness.spec.server.ssca.v1.model.Operator.EQUALS;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.user.UserInfo;
import io.harness.persistence.UserProvider;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.remediation_tracker.RemediationTrackerRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.ssca.v1.model.ComponentFilter;
import io.harness.spec.server.ssca.v1.model.CreateTicketRequest;
import io.harness.spec.server.ssca.v1.model.ExcludeArtifactRequest;
import io.harness.spec.server.ssca.v1.model.NameOperator;
import io.harness.spec.server.ssca.v1.model.Operator;
import io.harness.spec.server.ssca.v1.model.PipelineInfo;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactDeploymentsListingRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactDeploymentsListingResponse;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactDetailsResponse;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactListingRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactListingResponse;
import io.harness.spec.server.ssca.v1.model.RemediationCount;
import io.harness.spec.server.ssca.v1.model.RemediationDetailsResponse;
import io.harness.spec.server.ssca.v1.model.RemediationListingRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationListingResponse;
import io.harness.spec.server.ssca.v1.model.RemediationTrackerCreateRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationTrackerUpdateRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationTrackersOverallSummaryResponseBody;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.remediation_tracker.PatchedPendingArtifactEntitiesResult;
import io.harness.ssca.beans.ticket.TicketRequestDto;
import io.harness.ssca.beans.ticket.TicketResponseDto;
import io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields.VersionField;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.entities.remediation_tracker.ArtifactInfo;
import io.harness.ssca.entities.remediation_tracker.CVEVulnerability.CVEVulnerabilityInfoKeys;
import io.harness.ssca.entities.remediation_tracker.DeploymentsCount;
import io.harness.ssca.entities.remediation_tracker.EnvironmentInfo;
import io.harness.ssca.entities.remediation_tracker.Pipeline;
import io.harness.ssca.entities.remediation_tracker.RemediationCondition;
import io.harness.ssca.entities.remediation_tracker.RemediationStatus;
import io.harness.ssca.entities.remediation_tracker.RemediationTrackerEntity;
import io.harness.ssca.entities.remediation_tracker.RemediationTrackerEntity.RemediationTrackerEntityKeys;
import io.harness.ssca.entities.remediation_tracker.VulnerabilityInfo;
import io.harness.ssca.entities.remediation_tracker.VulnerabilityInfo.VulnerabilityInfoKeys;
import io.harness.ssca.mapper.RemediationTrackerMapper;
import io.harness.ssca.services.ArtifactService;
import io.harness.ssca.services.CdInstanceSummaryService;
import io.harness.ssca.services.NormalisedSbomComponentService;
import io.harness.ssca.ticket.TicketServiceRestClientService;
import io.harness.ssca.utils.PageResponseUtils;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class RemediationTrackerServiceImpl implements RemediationTrackerService {
  @Inject RemediationTrackerRepository repository;

  @Inject ArtifactService artifactService;

  @Inject CdInstanceSummaryService cdInstanceSummaryService;

  @Inject NormalisedSbomComponentService normalisedSbomComponentService;

  @Inject MongoTemplate mongoTemplate;

  @Inject UserProvider userProvider;

  @Inject private UserClient userClient;

  @Inject TicketServiceRestClientService ticketServiceRestClientService;

  private String sscaManagerServiceSecret;

  private ServiceTokenGenerator tokenGenerator;

  @Inject
  public RemediationTrackerServiceImpl(
      @Named("sscaManagerServiceSecret") String sscaManagerServiceSecret, ServiceTokenGenerator tokenGenerator) {
    this.sscaManagerServiceSecret = sscaManagerServiceSecret;
    this.tokenGenerator = tokenGenerator;
  }

  @Override
  public String createRemediationTracker(
      String accountId, String orgId, String projectId, RemediationTrackerCreateRequestBody body) {
    validateRemediationCreateRequest(body);
    RemediationTrackerEntity remediationTracker =
        RemediationTrackerEntity.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .comments(body.getComments())
            .contactInfo(RemediationTrackerMapper.mapContactInfo(body.getContact()))
            .condition(RemediationTrackerMapper.mapRemediationCondition(body.getRemediationCondition()))
            .vulnerabilityInfo(RemediationTrackerMapper.mapVulnerabilityInfo(body.getVulnerabilityInfo()))
            .status(RemediationStatus.ON_GOING)
            .startTimeMilli(System.currentTimeMillis())
            .targetEndDateEpochDay(body.getTargetEndDate() != null ? body.getTargetEndDate().toEpochDay() : null)
            .createdBy(userProvider.activeUser().getUuid())
            .lastUpdatedBy(userProvider.activeUser().getUuid())
            .build();
    remediationTracker = repository.save(remediationTracker);
    // If this increases API latency, we can move this to a separate thread or a job.
    updateArtifactsAndEnvironments(remediationTracker);
    return remediationTracker.getUuid();
  }

  @Override
  public String updateRemediationTracker(String accountId, String orgId, String projectId, String remediationTrackerId,
      RemediationTrackerUpdateRequestBody body) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    if (remediationTracker.getStatus() == RemediationStatus.COMPLETED) {
      throw new InvalidArgumentsException(
          String.format("Remediation Tracker: %s is already closed.", remediationTrackerId));
    }
    Criteria criteria = Criteria.where(RemediationTrackerEntityKeys.uuid).is(remediationTracker.getUuid());
    Update update = new Update();
    update.set(RemediationTrackerEntityKeys.contactInfo, RemediationTrackerMapper.mapContactInfo(body.getContact()));
    update.set(RemediationTrackerEntityKeys.targetEndDateEpochDay, body.getTargetEndDate().toEpochDay());
    update.set(RemediationTrackerEntityKeys.comments, body.getComments());
    update.set(RemediationTrackerEntityKeys.lastUpdatedBy, userProvider.activeUser());
    VulnerabilityInfo vulnerabilityInfo = remediationTracker.getVulnerabilityInfo();
    vulnerabilityInfo.setVulnerabilityDescription(body.getVulnerabilityDescription());
    vulnerabilityInfo.setSeverity(RemediationTrackerMapper.mapSeverityToVulnerabilitySeverity(body.getSeverity()));
    update.set(RemediationTrackerEntityKeys.vulnerabilityInfo, vulnerabilityInfo);

    repository.update(new Query(criteria), update);
    updateArtifactsAndEnvironments(remediationTracker);
    return remediationTracker.getUuid();
  }

  @Override
  public RemediationDetailsResponse getRemediationDetails(
      String accountId, String orgId, String projectId, String remediationTrackerId) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    updateArtifactsAndEnvironments(remediationTracker);
    RemediationDetailsResponse response = RemediationTrackerMapper.mapRemediationDetailsResponse(remediationTracker);
    Optional<UserInfo> userInfoOptional =
        CGRestUtils.getResponse(userClient.getUserById(remediationTracker.getCreatedBy()));
    if (userInfoOptional.isPresent()) {
      response.setCreatedByName(userInfoOptional.get().getName());
      response.setCreatedByEmail(userInfoOptional.get().getEmail());
    }
    // TODO fetch info for ticketId
    return response;
  }

  @Override
  public RemediationArtifactDetailsResponse getRemediationArtifactDetails(
      String accountId, String orgId, String projectId, String remediationTrackerId, String artifactId) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    updateArtifactsAndEnvironments(remediationTracker);

    ArtifactInfo artifactInfo = remediationTracker.getArtifactInfos().get(artifactId);
    if (artifactInfo == null) {
      throw new InvalidArgumentsException(String.format("ArtifactId: %s not present.", artifactId));
    }
    RemediationArtifactDetailsResponse response =
        RemediationTrackerMapper.mapArtifactInfoToArtifactDetailsResponse(remediationTracker, artifactInfo);
    ArtifactEntity latestArtifact = artifactService.getLatestArtifact(accountId, orgId, projectId, artifactId);
    if (latestArtifact != null) {
      PipelineInfo pipeline = artifactService.getPipelineInfo(accountId, orgId, projectId, latestArtifact);
      response.setBuildPipeline(pipeline);
      response.setLatestBuildTag(latestArtifact.getTag());
    }
    // TODO add ticket info
    return response;
  }

  @Override
  public boolean close(String accountId, String orgId, String projectId, String remediationTrackerId) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    if (remediationTracker.getStatus() == RemediationStatus.COMPLETED) {
      return false;
    }
    closeTracker(remediationTracker);
    remediationTracker.setClosedManually(true);
    remediationTracker.setClosedBy(userProvider.activeUser().getUuid());
    repository.save(remediationTracker);
    return true;
  }

  @Override
  public boolean excludeArtifact(
      String accountId, String orgId, String projectId, String remediationTrackerId, ExcludeArtifactRequest body) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    if (remediationTracker.getStatus() == RemediationStatus.COMPLETED) {
      throw new InvalidArgumentsException(
          String.format("Remediation Tracker: %s is already closed.", remediationTrackerId));
    }
    ArtifactInfo artifactInfo = remediationTracker.getArtifactInfos().get(body.getArtifactId());
    if (artifactInfo == null) {
      throw new InvalidArgumentsException(String.format("ArtifactId: %s not present.", body.getArtifactId()));
    }
    artifactInfo.setExcluded(true);
    repository.save(remediationTracker);
    updateArtifactsAndEnvironments(remediationTracker);
    return true;
  }

  @Override
  public void updateArtifactsAndEnvironments(RemediationTrackerEntity remediationTracker) {
    List<ComponentFilter> componentFilter = getComponentFilters(remediationTracker);
    List<String> orchestrationIdsMatchingTrackerFilter = getOrchestrationIds(remediationTracker, componentFilter);

    // Auto closing if no orchestrations matching the filter.
    closeTrackerIfNoOrchestrations(remediationTracker, orchestrationIdsMatchingTrackerFilter);

    List<ArtifactEntity> patchedArtifactEntities = new ArrayList<>();
    List<ArtifactEntity> pendingArtifactEntities = new ArrayList<>();
    processPatchedPendingArtifactEntities(
        remediationTracker, orchestrationIdsMatchingTrackerFilter, patchedArtifactEntities, pendingArtifactEntities);

    // Auto closing if no pending entities.
    closeTrackerIfNoPendingEntities(pendingArtifactEntities, remediationTracker);

    List<ArtifactInfo> artifactInfos =
        getArtifactInfo(remediationTracker, patchedArtifactEntities, pendingArtifactEntities);

    updateRemediationTrackerWithDetails(remediationTracker, artifactInfos);
    Criteria criteria = Criteria.where(RemediationTrackerEntityKeys.uuid).is(remediationTracker.getUuid());
    Update update = new Update();
    update.set(RemediationTrackerEntityKeys.artifactInfos, remediationTracker.getArtifactInfos());
    update.set(RemediationTrackerEntityKeys.deploymentsCount, remediationTracker.getDeploymentsCount());
    update.set(RemediationTrackerEntityKeys.status, remediationTracker.getStatus());
    update.set(RemediationTrackerEntityKeys.endTimeMilli, remediationTracker.getEndTimeMilli());
    repository.update(new Query(criteria), update);
  }

  @Override
  public RemediationTrackerEntity getRemediationTracker(String remediationTrackerId) {
    return repository.findById(remediationTrackerId)
        .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
  }

  @Override
  public RemediationTrackersOverallSummaryResponseBody getOverallSummaryForRemediationTrackers(
      String accountId, String orgId, String projectId) {
    Aggregation aggregationForMeanTime =
        Aggregation.newAggregation(Aggregation.match(Criteria.where(RemediationTrackerEntityKeys.accountIdentifier)
                                                         .is(accountId)
                                                         .and(RemediationTrackerEntityKeys.orgIdentifier)
                                                         .is(orgId)
                                                         .and(RemediationTrackerEntityKeys.projectIdentifier)
                                                         .is(projectId)
                                                         .and(RemediationTrackerEntityKeys.endTimeMilli)
                                                         .exists(true)
                                                         .and(RemediationTrackerEntityKeys.status)
                                                         .is(RemediationStatus.COMPLETED)),
            Aggregation.project().andExpression("$endTimeMilli - $startTimeMilli").as("remediationTimeInMilliseconds"),
            Aggregation.group().avg("remediationTimeInMilliseconds").as("meanTimeToRemediateInMilliseconds"),
            Aggregation.project("meanTimeToRemediateInMilliseconds")
                .andExpression("meanTimeToRemediateInMilliseconds / 3600000")
                .as("meanTimeToRemediateInHours"));

    RemediationTrackersOverallSummaryResponseBody overallSummary =
        new RemediationTrackersOverallSummaryResponseBody().meanTimeToRemediateInHours(null).remediationCounts(
            new ArrayList<>());
    List<RemediationTrackersOverallSummaryResponseBody> remediationTrackersOverallSummaryResponseBodies =
        mongoTemplate
            .aggregate(aggregationForMeanTime, RemediationTrackerEntity.class,
                RemediationTrackersOverallSummaryResponseBody.class)
            .getMappedResults();
    if (EmptyPredicate.isNotEmpty(remediationTrackersOverallSummaryResponseBodies)) {
      overallSummary.setMeanTimeToRemediateInHours(roundOffTwoDecimalPlace(
          remediationTrackersOverallSummaryResponseBodies.get(0).getMeanTimeToRemediateInHours()));
    }

    Aggregation aggregationForRemediationCount =
        Aggregation.newAggregation(Aggregation.match(Criteria.where(RemediationTrackerEntityKeys.accountIdentifier)
                                                         .is(accountId)
                                                         .and(RemediationTrackerEntityKeys.orgIdentifier)
                                                         .is(orgId)
                                                         .and(RemediationTrackerEntityKeys.projectIdentifier)
                                                         .is(projectId)),
            Aggregation.group(RemediationTrackerEntityKeys.status).count().as("count"),
            Aggregation.project("status", "count").and("status").previousOperation());
    overallSummary.setRemediationCounts(
        mongoTemplate.aggregate(aggregationForRemediationCount, RemediationTrackerEntity.class, RemediationCount.class)
            .getMappedResults());

    Criteria matchCriteria = Criteria.where("accountIdentifier")
                                 .is(accountId)
                                 .and("orgIdentifier")
                                 .is(orgId)
                                 .and("projectIdentifier")
                                 .is(projectId);

    TypedAggregation<RemediationTrackerEntity> aggregation =
        Aggregation.newAggregation(RemediationTrackerEntity.class, Aggregation.match(matchCriteria),
            Aggregation.group()
                .sum("deploymentsCount.pendingProdCount")
                .as("pendingProdCount")
                .sum("deploymentsCount.patchedProdCount")
                .as("patchedProdCount")
                .sum("deploymentsCount.pendingNonProdCount")
                .as("pendingNonProdCount")
                .sum("deploymentsCount.patchedNonProdCount")
                .as("patchedNonProdCount"));

    AggregationResults<io.harness.spec.server.ssca.v1.model.DeploymentsCount> aggregationResults =
        mongoTemplate.aggregate(aggregation, io.harness.spec.server.ssca.v1.model.DeploymentsCount.class);

    if (!aggregationResults.getMappedResults().isEmpty()) {
      overallSummary.setDeploymentsCount(aggregationResults.getMappedResults().get(0));
    } else {
      // Handle the case where there are no matching documents
      overallSummary.setDeploymentsCount(new io.harness.spec.server.ssca.v1.model.DeploymentsCount());
    }
    return overallSummary;
  }

  @Override
  public String createTicket(
      String projectId, String remediationTrackerId, String orgId, CreateTicketRequest body, String accountId) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    if (remediationTracker.getStatus() == RemediationStatus.COMPLETED) {
      throw new InvalidArgumentsException(
          String.format("Remediation Tracker: %s is already closed.", remediationTrackerId));
    }

    SecurityContextBuilder.setContext(new ServicePrincipal(SSCA_SERVICE.getServiceId()));
    String authToken = tokenGenerator.getServiceTokenWithDuration(
        sscaManagerServiceSecret, Duration.ofHours(4), SecurityContextBuilder.getPrincipal());

    if (!StringUtils.isEmpty(body.getArtifactId())) {
      ArtifactInfo artifactInfo = remediationTracker.getArtifactInfos().get(body.getArtifactId());
      if (artifactInfo == null) {
        throw new InvalidArgumentsException(String.format("ArtifactId: %s not present.", body.getArtifactId()));
      }
      if (artifactInfo.getTicketId() != null) {
        throw new InvalidArgumentsException(
            String.format("Ticket already exists for artifactId: %s.", body.getArtifactId()));
      }
      TicketRequestDto ticketRequestDto = RemediationTrackerMapper.mapToTicketRequestDto(remediationTrackerId, body);
      TicketResponseDto ticketResponseDto =
          ticketServiceRestClientService.createTicket(authToken, accountId, orgId, projectId, ticketRequestDto);
      String ticketId = ticketResponseDto.getExternalId();
      artifactInfo.setTicketId(ticketId);
      return ticketId;
    }

    else {
      TicketRequestDto ticketRequestDto = RemediationTrackerMapper.mapToTicketRequestDto(remediationTrackerId, body);
      TicketResponseDto ticketResponseDto =
          ticketServiceRestClientService.createTicket(authToken, accountId, orgId, projectId, ticketRequestDto);
      String ticketId = ticketResponseDto.getExternalId();

      remediationTracker.setTicketId(ticketId);
      return ticketId;
    }
  }

  @Override
  public Page<RemediationListingResponse> listRemediations(
      String accountId, String orgId, String projectId, RemediationListingRequestBody body, Pageable pageable) {
    Criteria criteria = Criteria.where(RemediationTrackerEntityKeys.accountIdentifier)
                            .is(accountId)
                            .and(RemediationTrackerEntityKeys.orgIdentifier)
                            .is(orgId)
                            .and(RemediationTrackerEntityKeys.projectIdentifier)
                            .is(projectId);

    List<Criteria> filterCriteria = new ArrayList<>();
    if (body.getCveFilter() != null) {
      filterCriteria.add(
          getFilterCriteria(RemediationTrackerEntityKeys.vulnerabilityInfo + "." + CVEVulnerabilityInfoKeys.cve,
              body.getCveFilter().getValue(), body.getCveFilter().getOperator()));
    }

    if (body.getComponentNameFilter() != null) {
      filterCriteria.add(
          getFilterCriteria(RemediationTrackerEntityKeys.vulnerabilityInfo + "." + VulnerabilityInfoKeys.component,
              body.getComponentNameFilter().getValue(), body.getComponentNameFilter().getOperator()));
    }
    if (!filterCriteria.isEmpty()) {
      criteria = criteria.andOperator(filterCriteria.toArray(Criteria[] ::new));
    }

    Page<RemediationTrackerEntity> remediationTrackerEntities = repository.findAll(criteria, pageable);

    List<RemediationListingResponse> remediationListingResponses =
        getRemediationListingResponses(remediationTrackerEntities.toList());

    return new PageImpl<>(remediationListingResponses, pageable, remediationTrackerEntities.getTotalElements());
  }

  @Override
  public Page<RemediationArtifactListingResponse> listRemediationArtifacts(String accountId, String orgId,
      String projectId, String remediationTrackerId, RemediationArtifactListingRequestBody body, Pageable pageable) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    updateArtifactsAndEnvironments(remediationTracker);

    List<ArtifactInfo> artifactInfos = new ArrayList<>(remediationTracker.getArtifactInfos().values());
    List<RemediationArtifactListingResponse> artifactListingResponses =
        artifactInfos.stream()
            .map(RemediationTrackerMapper::mapArtifactInfoToArtifactListingResponse)
            .collect(Collectors.toList());
    // TODO add ticket details
    artifactListingResponses = artifactListingResponses.stream()
                                   .sorted(Comparator.comparing(RemediationArtifactListingResponse::getName))
                                   .collect(Collectors.toList());
    // We have implemented in-memory sort here to support pagination.
    // Since we don't expect a lot of artifacts in a remediation tracker, this should be fine.
    return new PageImpl<>(
        PageResponseUtils.getPaginatedList(artifactListingResponses, pageable.getPageNumber(), pageable.getPageSize()),
        pageable, artifactListingResponses.size());
  }

  @Override
  public Page<RemediationArtifactDeploymentsListingResponse> listRemediationArtifactDeployments(String accountId,
      String orgId, String projectId, String remediationTrackerId, String artifactId,
      RemediationArtifactDeploymentsListingRequestBody body, Pageable pageable) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    updateArtifactsAndEnvironments(remediationTracker);

    ArtifactInfo artifactInfo = remediationTracker.getArtifactInfos().get(artifactId);
    if (artifactInfo == null) {
      throw new InvalidArgumentsException(String.format("ArtifactId: %s not present.", artifactId));
    }
    List<EnvironmentInfo> environments = artifactInfo.getEnvironments();
    List<RemediationArtifactDeploymentsListingResponse> responses =
        environments.stream()
            .map(RemediationTrackerMapper::mapEnvironmentInfoToArtifactDeploymentsListingResponse)
            .collect(Collectors.toList());
    responses = responses.stream()
                    .sorted(Comparator.comparing(RemediationArtifactDeploymentsListingResponse::getName))
                    .collect(Collectors.toList());
    // We have implemented in-memory sort here to support pagination.
    // Since we don't expect a lot of artifacts in a remediation tracker, this should be fine.
    return new PageImpl<>(
        PageResponseUtils.getPaginatedList(responses, pageable.getPageNumber(), pageable.getPageSize()), pageable,
        responses.size());
  }

  @Override
  public List<io.harness.spec.server.ssca.v1.model.EnvironmentInfo> getAllEnvironmentsInArtifact(String accountId,
      String orgId, String projectId, String remediationTrackerId, String artifactId, EnvType environmentType) {
    RemediationTrackerEntity remediationTracker =
        repository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUuid(
                accountId, orgId, projectId, new ObjectId(remediationTrackerId))
            .orElseThrow(() -> new InvalidArgumentsException("Remediation Tracker not found"));
    updateArtifactsAndEnvironments(remediationTracker);

    ArtifactInfo artifactInfo = remediationTracker.getArtifactInfos().get(artifactId);
    if (artifactInfo == null) {
      throw new InvalidArgumentsException(String.format("ArtifactId: %s not present.", artifactId));
    }
    List<EnvironmentInfo> environments = artifactInfo.getEnvironments();
    return buildEnvironmentInfos(environments, environmentType);
  }

  private void validateRemediationCreateRequest(RemediationTrackerCreateRequestBody body) {
    if ((body.getRemediationCondition().getOperator()
            != io.harness.spec.server.ssca.v1.model.RemediationCondition.OperatorEnum.ALL)
        && (body.getRemediationCondition().getOperator()
            != io.harness.spec.server.ssca.v1.model.RemediationCondition.OperatorEnum.MATCHES)) {
      List<Integer> versions = VersionField.getVersion(body.getRemediationCondition().getVersion());
      if (versions.size() != 3 || versions.get(0) == -1) {
        throw new InvalidArgumentsException(
            "Unsupported Version Format. Semantic Versioning is required for LessThan and LessThanEquals operator.");
      }
    }
  }

  public double roundOffTwoDecimalPlace(final double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private void closeTrackerIfNoOrchestrations(
      RemediationTrackerEntity remediationTracker, List<String> orchestrationIdsMatchingTrackerFilter) {
    if (orchestrationIdsMatchingTrackerFilter.isEmpty()) {
      closeTracker(remediationTracker);
    }
  }

  private void closeTrackerIfNoPendingEntities(
      List<ArtifactEntity> pendingArtifactEntities, RemediationTrackerEntity remediationTracker) {
    if (pendingArtifactEntities.isEmpty()) {
      closeTracker(remediationTracker);
    }
  }

  private void closeTracker(RemediationTrackerEntity remediationTracker) {
    remediationTracker.setStatus(RemediationStatus.COMPLETED);
    remediationTracker.setEndTimeMilli(System.currentTimeMillis());
  }

  private void updateRemediationTrackerWithDetails(
      RemediationTrackerEntity remediationTracker, List<ArtifactInfo> artifactInfos) {
    DeploymentsCount deploymentsCount = DeploymentsCount.builder().build();
    Map<String, ArtifactInfo> artifactInfoMap =
        artifactInfos.stream().collect(Collectors.toMap(ArtifactInfo::getArtifactId, artifactInfo -> {
          if (remediationTracker.getArtifactInfos() != null
              && remediationTracker.getArtifactInfos().containsKey(artifactInfo.getArtifactId())) {
            artifactInfo.setTicketId(
                remediationTracker.getArtifactInfos().get(artifactInfo.getArtifactId()).getTicketId());
            artifactInfo.setExcluded(
                remediationTracker.getArtifactInfos().get(artifactInfo.getArtifactId()).isExcluded());
          }
          if (!artifactInfo.isExcluded()) {
            deploymentsCount.add(artifactInfo.getDeploymentsCount());
          }
          return artifactInfo;
        }));
    remediationTracker.setDeploymentsCount(deploymentsCount);
    remediationTracker.setArtifactInfos(artifactInfoMap);
  }

  private List<PatchedPendingArtifactEntitiesResult> getPatchedAndPendingArtifacts(
      RemediationTrackerEntity remediationTracker, List<String> orchestrationIdsMatchingTrackerFilter) {
    Set<String> artifactIdsFromTrackerEntity = remediationTracker.getArtifactInfos() != null
        ? remediationTracker.getArtifactInfos().keySet()
        : new HashSet<>();
    Set<String> artifactIdsMatchingTrackerFilter =
        artifactService.getDistinctArtifactIds(remediationTracker.getAccountId(), remediationTracker.getOrgIdentifier(),
            remediationTracker.getProjectIdentifier(), orchestrationIdsMatchingTrackerFilter);
    Set<String> artifactIds = new HashSet<>(artifactIdsFromTrackerEntity);
    artifactIds.addAll(artifactIdsMatchingTrackerFilter);
    return artifactService.listDeployedArtifactsFromIdsWithCriteria(remediationTracker.getAccountId(),
        remediationTracker.getOrgIdentifier(), remediationTracker.getProjectIdentifier(), artifactIds,
        orchestrationIdsMatchingTrackerFilter);
  }

  private void processPatchedPendingArtifactEntities(RemediationTrackerEntity remediationTracker,
      List<String> orchestrationIdsMatchingTrackerFilter, List<ArtifactEntity> patchedArtifactEntities,
      List<ArtifactEntity> pendingArtifactEntities) {
    List<PatchedPendingArtifactEntitiesResult> results =
        getPatchedAndPendingArtifacts(remediationTracker, orchestrationIdsMatchingTrackerFilter);
    if (EmptyPredicate.isNotEmpty(results)) {
      PatchedPendingArtifactEntitiesResult result = results.get(0);
      patchedArtifactEntities.addAll(result.getPatchedArtifacts());
      pendingArtifactEntities.addAll(result.getPendingArtifacts());
    }
  }

  private List<String> getOrchestrationIds(
      RemediationTrackerEntity remediationTracker, List<ComponentFilter> componentFilter) {
    return (CollectionUtils.isNotEmpty(componentFilter))
        ? normalisedSbomComponentService.getOrchestrationIds(remediationTracker.getAccountId(),
            remediationTracker.getOrgIdentifier(), remediationTracker.getProjectIdentifier(), null, componentFilter)
        : Collections.emptyList();
  }

  private List<RemediationListingResponse> getRemediationListingResponses(
      List<RemediationTrackerEntity> remediationTrackerEntities) {
    return remediationTrackerEntities.stream()
        .map(RemediationTrackerMapper::mapRemediationListResponse)
        .collect(Collectors.toList());
  }

  private Criteria getFilterCriteria(String fieldName, String value, NameOperator operator) {
    switch (operator) {
      case EQUALS:
        return Criteria.where(fieldName).is(value);
      case CONTAINS:
        return Criteria.where(fieldName).regex(value);
      case STARTSWITH:
        return Criteria.where(fieldName).regex(Pattern.compile("^".concat(value)));
      default:
        throw new InvalidRequestException(String.format("Filter does not support %s operator", operator));
    }
  }
  private List<ComponentFilter> getComponentFilters(RemediationTrackerEntity entity) {
    List<ComponentFilter> componentFilter = new ArrayList<>();
    componentFilter.add(new ComponentFilter()
                            .fieldName(ComponentFilter.FieldNameEnum.COMPONENTNAME)
                            .operator(EQUALS)
                            .value(entity.getVulnerabilityInfo().getComponent()));

    Operator mappedOperator = mapOperator(entity.getCondition().getOperator());
    if (mappedOperator != null) {
      componentFilter.add(new ComponentFilter()
                              .fieldName(ComponentFilter.FieldNameEnum.COMPONENTVERSION)
                              .operator(mappedOperator)
                              .value(entity.getCondition().getVersion()));
    }

    return componentFilter;
  }

  private List<ArtifactInfo> getArtifactInfo(RemediationTrackerEntity remediationTracker,
      List<ArtifactEntity> patchedArtifactEntities, List<ArtifactEntity> pendingArtifactEntities) {
    // Steps: 1. Get cd instance summaries for all the artifact correlation ids.
    // 2. Build artifact details from the patched and pending artifact entities.
    // 3. Build artifact info from the cd instance summaries. If the tag is patched, we update the
    // latest tag with fix details build in the prev step.
    // 4. If the artifact is not excluded, we update the deployments count.
    List<CdInstanceSummary> cdInstanceSummaries =
        getCdInstanceSummaries(remediationTracker, patchedArtifactEntities, pendingArtifactEntities);
    Map<String, ArtifactDetails> artifactCorelationIdToDetailMap =
        getArtifactCorelationIdToDetailMap(patchedArtifactEntities, pendingArtifactEntities);
    Map<String, ArtifactInfo> artifactIdtoInfoMap = new HashMap<>();
    DeploymentsCount deploymentsCount = DeploymentsCount.builder().build();
    for (CdInstanceSummary summary : cdInstanceSummaries) {
      ArtifactDetails details = artifactCorelationIdToDetailMap.get(summary.getArtifactCorrelationId());
      ArtifactInfo info = artifactIdtoInfoMap.computeIfAbsent(details.getArtifactId(),
          id
          -> ArtifactInfo.builder()
                 .artifactId(details.getArtifactId())
                 .artifactName(details.getArtifactName())
                 .environments(new ArrayList<>())
                 .deploymentsCount(DeploymentsCount.builder().build())
                 .build());
      // Build details for the tag are fetched from the artifact entity.
      // We generated those details from the artifact entity. So, we can use the same details.
      if (details.isPatched() && info.getLatestTagWithFixPipelineTriggeredAt() < details.getCreatedOn()) {
        info.setLatestTagWithFixPipelineTriggeredAt(details.getCreatedOn());
        info.setLatestTagWithFix(details.getArtifactTag());
        info.setLatestTagWithFixPipelineId(details.getBuildPipelineId());
        info.setLatestTagWithFixPipelineExecutionId(details.getBuildPipelineExecutionId());
      }

      info.getDeploymentsCount().update(summary.getEnvType(), details.isPatched());
      EnvironmentInfo environmentInfo = buildEnvironmentInfo(summary, details);
      info.getEnvironments().add(environmentInfo);
      artifactIdtoInfoMap.put(details.getArtifactId(), info);
      // if artifact is excluded, we don't count it in deployments count.
      if (!info.isExcluded()) {
        deploymentsCount.update(summary.getEnvType(), details.isPatched());
      }
    }

    return new ArrayList<>(artifactIdtoInfoMap.values());
  }

  private Map<String, ArtifactDetails> getArtifactCorelationIdToDetailMap(
      List<ArtifactEntity> patchedArtifactEntities, List<ArtifactEntity> pendingArtifactEntities) {
    Map<String, ArtifactDetails> artifactCorelationIdToDetailMap = new HashMap<>();
    for (ArtifactEntity entity : patchedArtifactEntities) {
      artifactCorelationIdToDetailMap.put(entity.getArtifactCorrelationId(),
          ArtifactDetails.builder()
              .artifactId(entity.getArtifactId())
              .artifactName(entity.getName())
              .artifactTag(entity.getTag())
              .patched(true)
              .createdOn(entity.getCreatedOn().toEpochMilli())
              .buildPipelineId(entity.getPipelineId())
              .buildPipelineExecutionId(entity.getPipelineExecutionId())
              .build());
    }
    for (ArtifactEntity entity : pendingArtifactEntities) {
      artifactCorelationIdToDetailMap.put(entity.getArtifactCorrelationId(),
          ArtifactDetails.builder()
              .artifactId(entity.getArtifactId())
              .artifactName(entity.getName())
              .artifactTag(entity.getTag())
              .patched(false)
              .createdOn(entity.getCreatedOn().toEpochMilli())
              .buildPipelineId(entity.getPipelineId())
              .buildPipelineExecutionId(entity.getPipelineExecutionId())
              .build());
    }
    return artifactCorelationIdToDetailMap;
  }

  private List<CdInstanceSummary> getCdInstanceSummaries(RemediationTrackerEntity remediationTracker,
      List<ArtifactEntity> patchedArtifactEntities, List<ArtifactEntity> pendingArtifactEntities) {
    List<String> artifactCorelationIds =
        Stream
            .concat(patchedArtifactEntities.stream().map(ArtifactEntity::getArtifactCorrelationId),
                pendingArtifactEntities.stream().map(ArtifactEntity::getArtifactCorrelationId))
            .collect(Collectors.toList());
    return cdInstanceSummaryService.getCdInstanceSummaries(remediationTracker.getAccountId(),
        remediationTracker.getOrgIdentifier(), remediationTracker.getProjectIdentifier(), artifactCorelationIds);
  }

  private Operator mapOperator(RemediationCondition.Operator operator) {
    switch (operator) {
      case LESS_THAN:
        return Operator.LESSTHAN;
      case LESS_THAN_EQUALS:
        return Operator.LESSTHANEQUALS;
      case EQUALS:
        return EQUALS;
      case ALL:
        return null;
      default:
        throw new InvalidArgumentsException("Unsupported Operator " + operator);
    }
  }

  private List<io.harness.spec.server.ssca.v1.model.EnvironmentInfo> buildEnvironmentInfos(
      List<EnvironmentInfo> infos, EnvType environmentType) {
    return infos.stream()
        .filter(info -> environmentType == null || environmentType == info.getEnvType())
        .map(info
            -> new io.harness.spec.server.ssca.v1.model.EnvironmentInfo()
                   .identifier(info.getEnvIdentifier())
                   .name(info.getEnvName())
                   .type(RemediationTrackerMapper.mapEnvType(info.getEnvType())))
        .distinct()
        .collect(Collectors.toList());
  }

  private EnvironmentInfo buildEnvironmentInfo(CdInstanceSummary summary, ArtifactDetails details) {
    return EnvironmentInfo.builder()
        .envIdentifier(summary.getEnvIdentifier())
        .envName(summary.getEnvName())
        .tag(details.getArtifactTag())
        .envType(summary.getEnvType())
        .deploymentPipeline(buildDeploymentPipeline(summary))
        .isPatched(details.isPatched())
        .build();
  }

  private Pipeline buildDeploymentPipeline(CdInstanceSummary summary) {
    return Pipeline.builder()
        .pipelineName(summary.getLastPipelineName())
        .pipelineId(summary.getLastPipelineExecutionName())
        .pipelineExecutionId(summary.getLastPipelineExecutionId())
        .triggeredById(summary.getLastDeployedById())
        .triggeredBy(summary.getLastDeployedByName())
        .triggeredAt(summary.getLastDeployedAt())
        .build();
  }

  @Data
  @Builder
  static class ArtifactDetails {
    String artifactId;
    String artifactName;
    String artifactTag;
    boolean patched;
    long createdOn;
    String buildPipelineId;
    String buildPipelineExecutionId;
  }
}
