/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.SLSAVerificationSummary;
import io.harness.ssca.beans.instance.ArtifactCorrelationDetailsDTO;
import io.harness.ssca.beans.instance.ArtifactDetailsDTO;
import io.harness.ssca.beans.instance.InstanceDTO;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryKeys;
import io.harness.ssca.entities.EnforcementSummaryEntity.EnforcementSummaryEntityKeys;
import io.harness.ssca.helpers.CdInstanceSummaryServiceHelper;
import io.harness.ssca.utils.PipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class CdInstanceSummaryServiceImpl implements CdInstanceSummaryService {
  @Inject CdInstanceSummaryRepo cdInstanceSummaryRepo;
  @Inject ArtifactService artifactService;
  @Inject EnforcementSummaryRepo enforcementSummaryRepo;
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject PipelineUtils pipelineUtils;
  @Inject FeatureFlagService featureFlagService;
  @Inject CdInstanceSummaryServiceHelper cdInstanceSummaryServiceHelper;

  private static String IDENTIFIER = "identifier";
  private static String STEP_TYPE = "stepType";

  private static String EXECUTION_GRAPH = "executionGraph";
  private static String SLSA_VERIFICATION_STEP_ID = "SlsaVerification";
  private static String NODE_MAP = "nodeMap";
  private static String OUTCOMES = "outcomes";

  private static String PIPELINE_EXECUTION_SUMMARY = "pipelineExecutionSummary";
  private static String RUN_SEQUENCE = "runSequence";
  private static String EXECUTION_TRIGGER_INFO = "executionTriggerInfo";
  private static String TRIGGER_TYPE = "triggerType";

  private static String STEP_ARTIFACTS = "stepArtifacts";
  private static String PROVENANCE_ARTIFACTS = "provenanceArtifacts";
  private static String POLICY_OUTPUT = "policyOutput";
  private static String STATUS = "status";

  private static String PUBLISHED_IMAGES = "publishedImageArtifacts";
  private static String IMAGE_NAME = "imageName";
  private static String TAG = "tag";

  @Override
  public boolean upsertInstance(InstanceDTO instance) {
    if (isCDArtifactNull(instance)) {
      if (featureFlagService.isFeatureFlagEnabled(
              instance.getAccountIdentifier(), FeatureName.SSCA_MATCH_INSTANCE_IMAGE_NAME.name())) {
        if (cdInstanceSummaryServiceHelper.isK8sInstanceInfo(instance)) {
          Set<ArtifactEntity> artifactEntities =
              cdInstanceSummaryServiceHelper.findCorrelatedArtifactsForK8sInstance(instance);
          for (ArtifactEntity artifact : artifactEntities) {
            setArtifactCorrelationIdInInstance(instance, artifact.getArtifactCorrelationId());
            saveInstanceSummaryAndUpdateCorrelatedArtifact(instance, artifact);
          }
        } else {
          log.info("Either instance info not populated or not an instance of K8s for instance ID " + instance.getId());
        }
      } else {
        log.info(
            String.format("Instance skipped because of missing artifact identity, {InstanceId: %s}", instance.getId()));
      }
      return true;
    }

    ArtifactEntity artifact =
        artifactService.getArtifactByCorrelationId(instance.getAccountIdentifier(), instance.getOrgIdentifier(),
            instance.getProjectIdentifier(), instance.getPrimaryArtifact().getArtifactIdentity().getImage());
    if (Objects.isNull(artifact)) {
      log.info(String.format(
          "Instance skipped because of missing correlated artifactEntity, {InstanceId: %s}", instance.getId()));
      return true;
    }

    saveInstanceSummaryAndUpdateCorrelatedArtifact(instance, artifact);
    return true;
  }

  private void saveInstanceSummaryAndUpdateCorrelatedArtifact(InstanceDTO instance, ArtifactEntity artifact) {
    log.info(String.format(
        "Updating artifact with correlation id: %s, uuid: %s", artifact.getArtifactCorrelationId(), artifact.getId()));
    CdInstanceSummary cdInstanceSummary = getCdInstanceSummary(instance.getAccountIdentifier(),
        instance.getOrgIdentifier(), instance.getProjectIdentifier(),
        instance.getPrimaryArtifact().getArtifactIdentity().getImage(), instance.getEnvIdentifier());

    if (Objects.nonNull(cdInstanceSummary)) {
      JsonNode rootNode = pipelineUtils.getPipelineExecutionSummaryResponse(instance.getLastPipelineExecutionId(),
          instance.getAccountIdentifier(), instance.getOrgIdentifier(), instance.getProjectIdentifier(),
          instance.getStageSetupId());
      cdInstanceSummary.getInstanceIds().add(instance.getId());
      cdInstanceSummary.setSlsaVerificationSummary(getSlsaVerificationSummary(rootNode, instance, artifact));
      cdInstanceSummary = setPipelineDetails(cdInstanceSummary, rootNode, instance);
      artifactService.updateArtifactEnvCount(artifact, cdInstanceSummary.getEnvType(), 0);
      cdInstanceSummaryRepo.save(cdInstanceSummary);
    } else {
      CdInstanceSummary newCdInstanceSummary = createInstanceSummary(instance, artifact);
      artifactService.updateArtifactEnvCount(artifact, newCdInstanceSummary.getEnvType(), 1);
      cdInstanceSummaryRepo.save(newCdInstanceSummary);
    }
  }

  private boolean isCDArtifactNull(InstanceDTO instance) {
    return Objects.isNull(instance.getPrimaryArtifact())
        || Objects.isNull(instance.getPrimaryArtifact().getArtifactIdentity())
        || EmptyPredicate.isEmpty(instance.getPrimaryArtifact().getArtifactIdentity().getImage());
  }

  @Override
  public boolean removeInstance(InstanceDTO instance) {
    if (isCDArtifactNull(instance)) {
      if (featureFlagService.isFeatureFlagEnabled(
              instance.getAccountIdentifier(), FeatureName.SSCA_MATCH_INSTANCE_IMAGE_NAME.name())) {
        if (cdInstanceSummaryServiceHelper.isK8sInstanceInfo(instance)) {
          Set<ArtifactEntity> artifactEntities =
              cdInstanceSummaryServiceHelper.findCorrelatedArtifactsForK8sInstance(instance);
          for (ArtifactEntity artifact : artifactEntities) {
            setArtifactCorrelationIdInInstance(instance, artifact.getArtifactCorrelationId());
            deleteInstanceSummaryAndUpdateCorrelatedArtifact(instance);
          }
        } else {
          log.info("For instance removal, either instance info not populated or not an instance of K8s for instance ID "
              + instance.getId());
        }
      } else {
        log.info(
            String.format("Instance skipped because of missing artifact identity, {InstanceId: %s}", instance.getId()));
      }
      return true;
    }
    deleteInstanceSummaryAndUpdateCorrelatedArtifact(instance);
    return true;
  }

  private void deleteInstanceSummaryAndUpdateCorrelatedArtifact(InstanceDTO instance) {
    CdInstanceSummary cdInstanceSummary = getCdInstanceSummary(instance.getAccountIdentifier(),
        instance.getOrgIdentifier(), instance.getProjectIdentifier(),
        instance.getPrimaryArtifact().getArtifactIdentity().getImage(), instance.getEnvIdentifier());

    if (Objects.nonNull(cdInstanceSummary)) {
      cdInstanceSummary.getInstanceIds().remove(instance.getId());
      ArtifactEntity artifact =
          artifactService.getArtifactByCorrelationId(instance.getAccountIdentifier(), instance.getOrgIdentifier(),
              instance.getProjectIdentifier(), instance.getPrimaryArtifact().getArtifactIdentity().getImage());
      if (cdInstanceSummary.getInstanceIds().isEmpty()) {
        artifactService.updateArtifactEnvCount(artifact, cdInstanceSummary.getEnvType(), -1);
        cdInstanceSummaryRepo.delete(cdInstanceSummary);
      } else {
        artifactService.updateArtifactEnvCount(artifact, cdInstanceSummary.getEnvType(), 0);
        cdInstanceSummaryRepo.save(cdInstanceSummary);
      }
    }
  }

  @Override
  public Page<CdInstanceSummary> getCdInstanceSummaries(String accountId, String orgIdentifier,
      String projectIdentifier, ArtifactEntity artifact, ArtifactDeploymentViewRequestBody filterBody,
      Pageable pageable) {
    Criteria criteria = Criteria.where(CdInstanceSummaryKeys.artifactCorrelationId)
                            .is(artifact.getArtifactCorrelationId())
                            .and(CdInstanceSummaryKeys.accountIdentifier)
                            .is(accountId)
                            .and(CdInstanceSummaryKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(CdInstanceSummaryKeys.projectIdentifier)
                            .is(projectIdentifier);

    criteria.andOperator(getEnvironmentFilterCriteria(filterBody), getEnvironmentTypeFilterCriteria(filterBody),
        getPolicyViolationTypeFilterCriteria(accountId, orgIdentifier, projectIdentifier, filterBody));

    return cdInstanceSummaryRepo.findAll(criteria, pageable);
  }

  @Override
  public List<CdInstanceSummary> getCdInstanceSummaries(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> artifactCorelationIds) {
    Criteria criteria = Criteria.where(CdInstanceSummaryKeys.accountIdentifier)
                            .is(accountId)
                            .and(CdInstanceSummaryKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(CdInstanceSummaryKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(CdInstanceSummaryKeys.artifactCorrelationId)
                            .in(artifactCorelationIds);

    return cdInstanceSummaryRepo.findAll(criteria);
  }

  private Criteria getEnvironmentFilterCriteria(ArtifactDeploymentViewRequestBody filterBody) {
    if (Objects.nonNull(filterBody) && Objects.nonNull(filterBody.getEnvironment())) {
      Pattern pattern = Pattern.compile("[.]*" + filterBody.getEnvironment() + "[.]*");
      return Criteria.where(CdInstanceSummaryKeys.envName).regex(pattern);
    }
    return new Criteria();
  }

  private Criteria getEnvironmentTypeFilterCriteria(ArtifactDeploymentViewRequestBody filterBody) {
    if (Objects.nonNull(filterBody) && Objects.nonNull(filterBody.getEnvironmentType())) {
      switch (filterBody.getEnvironmentType()) {
        case PROD:
          return Criteria.where(CdInstanceSummaryKeys.envType).is(EnvType.Production);
        case NONPROD:
          return Criteria.where(CdInstanceSummaryKeys.envType).is(EnvType.PreProduction);
        default:
          throw new InvalidArgumentsException(
              String.format("Unknown environment type filter: %s", filterBody.getEnvironmentType()));
      }
    }
    return new Criteria();
  }

  private Criteria getPolicyViolationTypeFilterCriteria(
      String accountId, String orgIdentifier, String projectIdentifier, ArtifactDeploymentViewRequestBody filterBody) {
    if (Objects.isNull(filterBody) || Objects.isNull(filterBody.getPolicyViolation())) {
      return new Criteria();
    }
    Criteria enforcementSummaryCriteria =
        getPolicyViolationEnforcementCriteria(accountId, orgIdentifier, projectIdentifier, filterBody);

    List<String> pipelineExecutionIds = enforcementSummaryRepo.findAll(enforcementSummaryCriteria)
                                            .stream()
                                            .map(entity -> entity.getPipelineExecutionId())
                                            .collect(Collectors.toList());
    return Criteria.where(CdInstanceSummaryKeys.lastPipelineExecutionId).in(pipelineExecutionIds);
  }

  @VisibleForTesting
  Criteria getPolicyViolationEnforcementCriteria(
      String accountId, String orgIdentifier, String projectIdentifier, ArtifactDeploymentViewRequestBody filterBody) {
    Criteria enforcementSummaryCriteria = Criteria.where(EnforcementSummaryEntityKeys.accountId)
                                              .is(accountId)
                                              .and(EnforcementSummaryEntityKeys.orgIdentifier)
                                              .is(orgIdentifier)
                                              .and(EnforcementSummaryEntityKeys.projectIdentifier)
                                              .is(projectIdentifier);

    switch (filterBody.getPolicyViolation()) {
      case ALLOW:
        return enforcementSummaryCriteria.and(EnforcementSummaryEntityKeys.allowListViolationCount).gt(0);
      case DENY:
        return enforcementSummaryCriteria.and(EnforcementSummaryEntityKeys.denyListViolationCount).gt(0);
      case ANY:
        Criteria allowCriteria = Criteria.where(EnforcementSummaryEntityKeys.allowListViolationCount).gt(0);
        Criteria denyCriteria = Criteria.where(EnforcementSummaryEntityKeys.denyListViolationCount).gt(0);
        return new Criteria().andOperator(
            enforcementSummaryCriteria, new Criteria().orOperator(allowCriteria, denyCriteria));
      case NONE:
        return enforcementSummaryCriteria.and(EnforcementSummaryEntityKeys.denyListViolationCount)
            .is(0)
            .and(EnforcementSummaryEntityKeys.allowListViolationCount)
            .is(0);
      default:
        throw new InvalidArgumentsException(
            String.format("Unknown policy type filter: %s", filterBody.getPolicyViolation()));
    }
  }

  @Override
  public CdInstanceSummary getCdInstanceSummary(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactCorrelationId, String envIdentifier) {
    Criteria criteria = Criteria.where(CdInstanceSummaryKeys.artifactCorrelationId)
                            .is(artifactCorrelationId)
                            .and(CdInstanceSummaryKeys.accountIdentifier)
                            .is(accountId)
                            .and(CdInstanceSummaryKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(CdInstanceSummaryKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(CdInstanceSummaryKeys.envIdentifier)
                            .is(envIdentifier);

    return cdInstanceSummaryRepo.findOne(criteria);
  }

  @VisibleForTesting
  public CdInstanceSummary createInstanceSummary(InstanceDTO instance, ArtifactEntity artifact) {
    JsonNode rootNode = pipelineUtils.getPipelineExecutionSummaryResponse(instance.getLastPipelineExecutionId(),
        instance.getAccountIdentifier(), instance.getOrgIdentifier(), instance.getProjectIdentifier(),
        instance.getStageSetupId());

    CdInstanceSummary cdInstanceSummary =
        CdInstanceSummary.builder()
            .artifactCorrelationId(instance.getPrimaryArtifact().getArtifactIdentity().getImage())
            .accountIdentifier(instance.getAccountIdentifier())
            .orgIdentifier(instance.getOrgIdentifier())
            .projectIdentifier(instance.getProjectIdentifier())
            .slsaVerificationSummary(getSlsaVerificationSummary(rootNode, instance, artifact))
            .envIdentifier(instance.getEnvIdentifier())
            .envName(instance.getEnvName())
            .envType(EnvType.valueOf(instance.getEnvType()))
            .createdAt(Instant.now().toEpochMilli())
            .instanceIds(Collections.singleton(instance.getId()))
            .build();

    return setPipelineDetails(cdInstanceSummary, rootNode, instance);
  }

  private CdInstanceSummary setPipelineDetails(
      CdInstanceSummary cdInstanceSummary, JsonNode rootNode, InstanceDTO instance) {
    cdInstanceSummary.setLastPipelineName(pipelineUtils.parsePipelineName(rootNode));
    cdInstanceSummary.setLastPipelineExecutionName(instance.getLastPipelineExecutionName());
    cdInstanceSummary.setLastPipelineExecutionId(instance.getLastPipelineExecutionId());
    cdInstanceSummary.setSequenceId(getNodeValue(parseField(rootNode, PIPELINE_EXECUTION_SUMMARY, RUN_SEQUENCE)));
    cdInstanceSummary.setLastDeployedAt(instance.getLastDeployedAt());
    cdInstanceSummary.setLastDeployedById(instance.getLastDeployedById());
    cdInstanceSummary.setLastDeployedByName(instance.getLastDeployedByName());
    cdInstanceSummary.setTriggerType(
        getNodeValue(parseField(rootNode, PIPELINE_EXECUTION_SUMMARY, EXECUTION_TRIGGER_INFO, TRIGGER_TYPE)));
    return cdInstanceSummary;
  }

  private SLSAVerificationSummary getSlsaVerificationSummary(
      JsonNode rootNode, InstanceDTO instance, ArtifactEntity artifact) {
    if (rootNode == null) {
      return null;
    }
    try {
      JsonNode executionNodeList = parseField(rootNode, EXECUTION_GRAPH, NODE_MAP);
      if (Objects.isNull(executionNodeList)) {
        return null;
      }
      for (JsonNode node : executionNodeList) {
        if (SLSA_VERIFICATION_STEP_ID.equals(getNodeValue(node.get(STEP_TYPE)))
            && getNodeValue(node.get(IDENTIFIER)) != null) {
          String fqnSlsaStepIdentifier = getFqnSlsaStepIdentifier(node);
          if (fqnSlsaStepIdentifier != null && isCorrelated(fqnSlsaStepIdentifier, node, artifact)) {
            JsonNode provenanceArtifactList =
                parseField(node, OUTCOMES, fqnSlsaStepIdentifier, STEP_ARTIFACTS, PROVENANCE_ARTIFACTS);
            String provenanceArtifactData = Objects.nonNull(provenanceArtifactList) && provenanceArtifactList.size() > 0
                ? provenanceArtifactList.get(0).toString()
                : null;
            return SLSAVerificationSummary.builder()
                .slsaPolicyOutcomeStatus(getNodeValue(parseField(node, OUTCOMES, POLICY_OUTPUT, STATUS)))
                .provenanceArtifact(provenanceArtifactData)
                .build();
          }
        }
      }
    } catch (Exception e) {
      log.error(String.format("Failed to extract SLSA Verification Data. Exception: %s", e));
    }
    return null;
  }

  private String getFqnSlsaStepIdentifier(JsonNode node) {
    JsonNode outcomes = parseField(node, OUTCOMES);
    if (outcomes == null) {
      return null;
    }
    Iterator<String> fields = outcomes.fieldNames();
    String identifier = getNodeValue(parseField(node, IDENTIFIER));
    while (fields.hasNext()) {
      String field = fields.next();
      if (field.startsWith("artifact") && field.endsWith(identifier)) {
        return field;
      }
    }
    return null;
  }

  private boolean isCorrelated(String fqnIdentifier, JsonNode node, ArtifactEntity artifact) {
    JsonNode publishedImageArtifacts = parseField(node, OUTCOMES, fqnIdentifier, STEP_ARTIFACTS, PUBLISHED_IMAGES);
    if (publishedImageArtifacts == null) {
      return false;
    }
    for (JsonNode artifactNode : publishedImageArtifacts) {
      if (artifact.getName().equals(getNodeValue(parseField(artifactNode, IMAGE_NAME)))
          && artifact.getTag().equals(getNodeValue(parseField(artifactNode, TAG)))) {
        return true;
      }
    }
    return false;
  }

  private JsonNode parseField(JsonNode rootNode, String... path) {
    for (String field : path) {
      if (rootNode == null) {
        return null;
      } else {
        rootNode = rootNode.get(field);
      }
    }
    return rootNode;
  }

  private String getNodeValue(JsonNode node) {
    if (Objects.isNull(node)) {
      return null;
    }
    return node.asText();
  }

  private void setArtifactCorrelationIdInInstance(InstanceDTO instance, String artifactCorrelationId) {
    instance.setPrimaryArtifact(
        ArtifactDetailsDTO.builder()
            .artifactIdentity(ArtifactCorrelationDetailsDTO.builder().image(artifactCorrelationId).build())
            .build());
  }
}
