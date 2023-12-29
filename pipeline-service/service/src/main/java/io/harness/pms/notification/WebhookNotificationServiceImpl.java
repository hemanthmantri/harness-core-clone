/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.beans.FeatureName;
import io.harness.cdstage.remote.CDNGStageSummaryResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.notification.PipelineEventType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebhookNotificationServiceImpl implements WebhookNotificationService {
  private final CDNGStageSummaryResourceClient cdngStageSummaryResourceClient;
  private final PlanExecutionMetadataService planExecutionMetadataService;

  @Inject
  public WebhookNotificationServiceImpl(CDNGStageSummaryResourceClient cdngStageSummaryResourceClient,
      PlanExecutionMetadataService planExecutionMetadataService) {
    this.cdngStageSummaryResourceClient = cdngStageSummaryResourceClient;
    this.planExecutionMetadataService = planExecutionMetadataService;
  }
  @Override
  public ModuleInfo getModuleInfo(
      Ambiance ambiance, PipelineExecutionSummaryEntity executionSummaryEntity, PipelineEventType eventType) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    boolean shouldAddInputYaml =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_INPUT_YAML_IN_WEBHOOK_NOTIFICATION.name());
    if (currentLevel == null || currentLevel.getStepType().getStepCategory() == StepCategory.PIPELINE) {
      return getModuleInfoForPipelineLevel(executionSummaryEntity, eventType, shouldAddInputYaml);
    }
    if (currentLevel.getStepType().getStepCategory() == StepCategory.STAGE) {
      return getModuleInfoForStage(executionSummaryEntity, ambiance, eventType, shouldAddInputYaml);
    }
    return null;
  }

  private ModuleInfo getModuleInfoForPipelineLevel(
      PipelineExecutionSummaryEntity executionSummaryEntity, PipelineEventType eventType, boolean shouldAddInputYaml) {
    ModuleInfo.ModuleInfoBuilder moduleInfo = ModuleInfo.builder();
    Map<String, Object> moduleInfoMap = executionSummaryEntity.getModuleInfo().get("cd");
    if (shouldAddInputYaml && eventType == PipelineEventType.PIPELINE_START) {
      PlanExecutionMetadata planExecutionMetadata =
          planExecutionMetadataService.getWithFieldsIncludedFromSecondary(executionSummaryEntity.getPlanExecutionId(),
              Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.inputSetYaml));
      if (planExecutionMetadata != null) {
        moduleInfo.inputYaml(planExecutionMetadata.getInputSetYaml());
      }
    }
    if (EmptyPredicate.isEmpty(moduleInfoMap)) {
      return moduleInfo.build();
    }
    if (moduleInfoMap.containsKey("infrastructureIdentifiers")) {
      moduleInfo.infrastructures((List<String>) moduleInfoMap.get("infrastructureIdentifiers"));
    }
    if (moduleInfoMap.containsKey("envIdentifiers")) {
      moduleInfo.environments((List<String>) moduleInfoMap.get("envIdentifiers"));
    }
    if (moduleInfoMap.containsKey("serviceIdentifiers")) {
      moduleInfo.services((List<String>) moduleInfoMap.get("serviceIdentifiers"));
    }
    if (moduleInfoMap.containsKey("envGroupIdentifiers")) {
      moduleInfo.envGroups((List<String>) moduleInfoMap.get("envGroupIdentifiers"));
    }

    return moduleInfo.build();
  }

  // TODO: Make this generic
  private ModuleInfo getModuleInfoForStage(PipelineExecutionSummaryEntity executionSummaryEntity, Ambiance ambiance,
      PipelineEventType pipelineEventType, boolean shouldAddInputYaml) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    Map<String, CDStageSummaryResponseDTO> stageSummaryResponseDTOMap = null;
    Optional<Level> strategyLevel = AmbianceUtils.getStrategyLevelFromAmbiance(ambiance);
    String stageIdentifier =
        strategyLevel.isEmpty() ? currentLevel.getIdentifier() : strategyLevel.get().getIdentifier();
    ModuleInfo.ModuleInfoBuilder moduleInfoBuilder = ModuleInfo.builder();
    if (shouldAddInputYaml && pipelineEventType == PipelineEventType.STAGE_START) {
      PlanExecutionMetadata planExecutionMetadata =
          planExecutionMetadataService.getWithFieldsIncludedFromSecondary(executionSummaryEntity.getPlanExecutionId(),
              Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.inputSetYaml));
      if (planExecutionMetadata != null) {
        moduleInfoBuilder.inputYaml(planExecutionMetadata.getInputSetYaml());
      }
    }
    try {
      if (pipelineEventType != PipelineEventType.STAGE_START) {
        stageSummaryResponseDTOMap = getResponse(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
            executionSummaryEntity.getAccountId(), executionSummaryEntity.getOrgIdentifier(),
            executionSummaryEntity.getProjectIdentifier(), Lists.newArrayList(currentLevel.getRuntimeId())));
      } else {
        stageSummaryResponseDTOMap = getResponse(
            cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(executionSummaryEntity.getAccountId(),
                executionSummaryEntity.getOrgIdentifier(), executionSummaryEntity.getProjectIdentifier(),
                executionSummaryEntity.getPlanExecutionId(), Lists.newArrayList(stageIdentifier)));
      }
    } catch (Exception ex) {
      log.error("Exception occurred while updating module info during webhook notification", ex);
      return moduleInfoBuilder.build();
    }
    if (stageSummaryResponseDTOMap == null) {
      return moduleInfoBuilder.build();
    }
    CDStageSummaryResponseDTO stageSummaryResponseDTO = stageSummaryResponseDTOMap.get(currentLevel.getRuntimeId());
    if (stageSummaryResponseDTO == null) {
      stageSummaryResponseDTO = stageSummaryResponseDTOMap.get(stageIdentifier);
    }
    return moduleInfoBuilder
        .services(EmptyPredicate.isEmpty(stageSummaryResponseDTO.getServices())
                ? Lists.newArrayList(stageSummaryResponseDTO.getService())
                : Lists.newArrayList(stageSummaryResponseDTO.getServices()))
        .artifactInfo(Lists.newArrayList(stageSummaryResponseDTO.getArtifactDisplayName()))
        .environments(EmptyPredicate.isEmpty(stageSummaryResponseDTO.getEnvironments())
                ? Lists.newArrayList(stageSummaryResponseDTO.getEnvironment())
                : Lists.newArrayList(stageSummaryResponseDTO.getEnvironments()))
        .infrastructures(EmptyPredicate.isEmpty(stageSummaryResponseDTO.getInfras())
                ? Lists.newArrayList(stageSummaryResponseDTO.getInfra())
                : Lists.newArrayList(stageSummaryResponseDTO.getInfras()))
        .build();
  }
}
