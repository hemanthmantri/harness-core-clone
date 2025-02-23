/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ORG_IDENTIFIER;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PIPELINE_ID;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PROJECT_IDENTIFIER;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineExecutor {
  private final String START_PIPELINE_EXECUTION_EVENT = "ng_start_pipeline_execution";
  ExecutionHelper executionHelper;
  ValidateAndMergeHelper validateAndMergeHelper;
  PlanExecutionMetadataService planExecutionMetadataService;
  RetryExecutionHelper retryExecutionHelper;
  PMSPipelineTemplateHelper pipelineTemplateHelper;
  PipelineTelemetryHelper pipelineTelemetryHelper;
  PlanExecutionService planExecutionService;
  RollbackModeExecutionHelper rollbackModeExecutionHelper;
  PMSExecutionService pmsExecutionService;
  NodeExecutionService nodeExecutionService;

  public PlanExecutionResponseDto runPipelineWithInputSetPipelineYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String runtimeInputYaml, boolean useV2, boolean notifyOnlyUser, String notes) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        runtimeInputYaml, Collections.emptyList(), Collections.emptyMap(), useV2, notifyOnlyUser, notes);
  }

  public PlanExecutionResponseDto runPipelineWithInputSetReferencesList(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType,
      MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS, String pipelineBranch, String pipelineRepoID,
      String notes) {
    JsonNode lastJsonNodeToMerge = null;
    if (isNotEmpty(mergeInputSetRequestDTOPMS.getLastYamlToMerge())) {
      lastJsonNodeToMerge = YamlUtils.readAsJsonNode(mergeInputSetRequestDTOPMS.getLastYamlToMerge());
    }
    JsonNode mergedRuntimeInputJsonNode =
        validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, mergeInputSetRequestDTOPMS.getInputSetReferences(), pipelineBranch,
            pipelineRepoID, null, lastJsonNodeToMerge, false, false);
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        mergedRuntimeInputJsonNode, Collections.emptyList(), Collections.emptyMap(), false, false, false, notes);
  }

  public PlanExecutionResponseDto runStagesWithRuntimeInputYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, RunStageRequestDTO runStageRequestDTO, boolean useV2, String notes) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        runStageRequestDTO.getRuntimeInputYaml(), runStageRequestDTO.getStageIdentifiers(),
        runStageRequestDTO.getExpressionValues(), useV2, false, notes);
  }

  public PlanExecutionResponseDto rerunStagesWithRuntimeInputYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String originalExecutionId, RunStageRequestDTO runStageRequestDTO, boolean useV2,
      boolean isDebug, String notes) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, runStageRequestDTO.getRuntimeInputYaml(), runStageRequestDTO.getStageIdentifiers(),
        runStageRequestDTO.getExpressionValues(), useV2, false, notes);
  }

  public PlanExecutionResponseDto rerunPipelineWithInputSetPipelineYaml(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, String originalExecutionId,
      String runtimeInputYaml, boolean useV2, boolean isDebug, String notes) {
    JsonNode runtimeInputJsonNode = null;
    if (isNotEmpty(runtimeInputYaml)) {
      runtimeInputJsonNode = YamlUtils.readAsJsonNode(runtimeInputYaml);
    }
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, runtimeInputJsonNode, Collections.emptyList(), Collections.emptyMap(), useV2, false, isDebug,
        notes);
  }

  public PlanExecutionResponseDto rerunPipelineWithInputSetReferencesList(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, String originalExecutionId,
      MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS, String pipelineBranch, String pipelineRepoID,
      boolean isDebug, String notes) {
    JsonNode lastJsonNodeToMerge = null;
    if (isNotEmpty(mergeInputSetRequestDTOPMS.getLastYamlToMerge())) {
      lastJsonNodeToMerge = YamlUtils.readAsJsonNode(mergeInputSetRequestDTOPMS.getLastYamlToMerge());
    }
    JsonNode mergedRuntimeInputJsonNode =
        validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, mergeInputSetRequestDTOPMS.getInputSetReferences(), pipelineBranch,
            pipelineRepoID, null, lastJsonNodeToMerge, false, false);
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, mergedRuntimeInputJsonNode, Collections.emptyList(), Collections.emptyMap(), false, false, false,
        notes);
  }

  private PlanExecutionResponseDto startPlanExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String originalExecutionId, String moduleType, String runtimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser,
      String notes) {
    JsonNode runtimeInputJsonNode = null;
    if (isNotEmpty(runtimeInputYaml)) {
      runtimeInputJsonNode = YamlUtils.readAsJsonNode(runtimeInputYaml);
    }
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, runtimeInputJsonNode, stagesToRun, expressionValues, useV2, notifyOnlyUser, false, notes);
  }

  private PlanExecutionResponseDto startPlanExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String originalExecutionId, String moduleType, JsonNode runtimeInputJsonNode,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser,
      boolean isDebug, String notes) {
    sendExecutionStartTelemetryEvent(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
      throw new InvalidRequestException(String.format(
          "Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s", pipelineIdentifier, projectIdentifier));
    }
    ExecArgs execArgs = getExecArgsWithJsonNode(originalExecutionId, moduleType, runtimeInputJsonNode, stagesToRun,
        expressionValues, notifyOnlyUser, pipelineEntity, isDebug, notes);

    return getPlanExecutionResponseDto(accountId, orgIdentifier, projectIdentifier, useV2, pipelineEntity, execArgs);
  }

  // todo: check if we need to take notifyOnlyUser and isDebug
  public PlanExecution startPostExecutionRollback(String accountId, String orgIdentifier, String projectIdentifier,
      String originalExecutionId, List<String> stageNodeExecutionIds, String notes) {
    // because post execution rollback will not be linked within any other execution via some stage, it does not have
    // any parent stage info
    return startRollbackModeExecution(accountId, orgIdentifier, projectIdentifier, originalExecutionId,
        stageNodeExecutionIds, ExecutionMode.POST_EXECUTION_ROLLBACK, null, notes);
  }

  public PlanExecution startPipelineRollback(String accountId, String orgIdentifier, String projectIdentifier,
      String originalExecutionId, PipelineStageInfo parentStageInfo) {
    List<String> stageExecutionIds =
        nodeExecutionService.fetchStageExecutions(originalExecutionId)
            .stream()
            .filter(n -> !n.getGroup().equals("STRATEGY"))
            .filter(n -> !n.getStepType().getType().equals(OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE))
            .map(NodeExecution::getUuid)
            .collect(Collectors.toList());
    return startRollbackModeExecution(accountId, orgIdentifier, projectIdentifier, originalExecutionId,
        stageExecutionIds, ExecutionMode.PIPELINE_ROLLBACK, parentStageInfo, null);
  }

  PlanExecution startRollbackModeExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String originalExecutionId, List<String> stageNodeExecutionIds, ExecutionMode executionMode,
      PipelineStageInfo parentStageInfo, String notes) {
    String executionId = generateUuid();
    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(null);
    PlanExecution originalPlanExecution = planExecutionService.getWithFieldsIncluded(
        originalExecutionId, Set.of(PlanExecutionKeys.createdAt, PlanExecutionKeys.metadata));
    ExecutionMetadata originalExecutionMetadata = originalPlanExecution.getMetadata();
    rollbackModeExecutionHelper.checkAndThrowExceptionIfExecutionOlderThanOneMonthForPostProdRollback(
        originalPlanExecution.getCreatedAt(), executionMode);
    ExecutionMetadata executionMetadata = rollbackModeExecutionHelper.transformExecutionMetadata(
        originalExecutionMetadata, executionId, triggerInfo, executionMode, parentStageInfo, stageNodeExecutionIds);

    Optional<PlanExecutionMetadata> optPlanExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(originalExecutionId);
    if (optPlanExecutionMetadata.isEmpty()) {
      return null;
    }
    PlanExecutionMetadata originalPlanExecutionMetadata = optPlanExecutionMetadata.get();
    PlanExecutionMetadata planExecutionMetadata = rollbackModeExecutionHelper.transformPlanExecutionMetadata(
        originalPlanExecutionMetadata, executionId, executionMode, stageNodeExecutionIds, notes);
    return executionHelper.startExecution(accountId, orgIdentifier, projectIdentifier, executionMetadata,
        planExecutionMetadata, false, null, originalExecutionId, null, true);
  }

  private PlanExecutionResponseDto getPlanExecutionResponseDto(String accountId, String orgIdentifier,
      String projectIdentifier, boolean useV2, PipelineEntity pipelineEntity, ExecArgs execArgs) {
    PlanExecution planExecution;
    if (useV2) {
      planExecution = executionHelper.startExecutionV2(accountId, orgIdentifier, projectIdentifier,
          execArgs.getMetadata(), execArgs.getPlanExecutionMetadata(), false, null, null, null, true);
    } else {
      planExecution = executionHelper.startExecution(accountId, orgIdentifier, projectIdentifier,
          execArgs.getMetadata(), execArgs.getPlanExecutionMetadata(), false, null, null, null, true);
    }
    return PlanExecutionResponseDto.builder()
        .planExecution(planExecution)
        .gitDetails(PMSPipelineDtoMapper.getEntityGitDetails(pipelineEntity))
        .build();
  }

  private ExecArgs getExecArgsWithJsonNode(String originalExecutionId, String moduleType, JsonNode runtimeInputJsonNode,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean notifyOnlyUser,
      PipelineEntity pipelineEntity, boolean isDebug, String notes) {
    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(originalExecutionId);

    // RetryExecutionParameters
    RetryExecutionParameters retryExecutionParameters = buildRetryExecutionParameters(false, null, null, null);

    return executionHelper.buildExecutionArgs(pipelineEntity, moduleType, stagesToRun, expressionValues, triggerInfo,
        originalExecutionId, retryExecutionParameters, notifyOnlyUser, isDebug, notes, runtimeInputJsonNode);
  }

  public PlanExecutionResponseDto retryPipelineWithInputSetPipelineYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String inputSetPipelineYaml, String previousExecutionId, List<String> retryStagesIdentifier,
      boolean runAllStages, boolean useV2, boolean isDebug, String notes) {
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);

    if (!runAllStages && retryStagesIdentifier.size() > 1) {
      // run only failed stage
      retryStagesIdentifier = retryExecutionHelper.fetchOnlyFailedStages(previousExecutionId, retryStagesIdentifier);
    }

    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(null);
    Optional<PlanExecutionMetadata> optionalPlanExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(previousExecutionId);

    if (!optionalPlanExecutionMetadata.isPresent()) {
      throw new InvalidRequestException(String.format("No plan exist for %s planExecutionId", previousExecutionId));
    }
    PlanExecutionMetadata planExecutionMetadata = optionalPlanExecutionMetadata.get();
    String previousProcessedYaml = planExecutionMetadata.getProcessedYaml();
    List<String> identifierOfSkipStages = new ArrayList<>();

    // RetryExecutionParameters
    // TODO(BRIJESH): Stage identifiers should be same as YAML and not with the matrix prefix. Its temp. Do something
    // here.
    RetryExecutionParameters retryExecutionParameters =
        buildRetryExecutionParameters(true, previousProcessedYaml, retryStagesIdentifier, identifierOfSkipStages);

    StagesExecutionMetadata stagesExecutionMetadata = planExecutionMetadata.getStagesExecutionMetadata();
    JsonNode inputSetPipelineJsonNode = null;
    if (!isEmpty(inputSetPipelineYaml)) {
      inputSetPipelineJsonNode = YamlUtils.readAsJsonNode(inputSetPipelineYaml);
    }
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType,
        stagesExecutionMetadata == null ? null : stagesExecutionMetadata.getStageIdentifiers(),
        stagesExecutionMetadata == null ? null : stagesExecutionMetadata.getExpressionValues(), triggerInfo,
        previousExecutionId, retryExecutionParameters, false, isDebug, notes, inputSetPipelineJsonNode);
    PlanExecution planExecution;
    if (useV2) {
      planExecution = executionHelper.startExecutionV2(accountId, orgIdentifier, projectIdentifier,
          execArgs.getMetadata(), execArgs.getPlanExecutionMetadata(), true, identifierOfSkipStages,
          previousExecutionId, retryStagesIdentifier, runAllStages);
    } else {
      planExecution = executionHelper.startExecution(accountId, orgIdentifier, projectIdentifier,
          execArgs.getMetadata(), execArgs.getPlanExecutionMetadata(), true, identifierOfSkipStages,
          previousExecutionId, retryStagesIdentifier, runAllStages);
    }
    return PlanExecutionResponseDto.builder()
        .planExecution(planExecution)
        .gitDetails(EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity))
        .build();
  }

  public RetryExecutionParameters buildRetryExecutionParameters(
      boolean isRetry, String processedYaml, List<String> stagesIdentifier, List<String> identifierOfSkipStages) {
    if (!isRetry) {
      return RetryExecutionParameters.builder().isRetry(false).build();
    }

    return RetryExecutionParameters.builder()
        .isRetry(true)
        .previousProcessedYaml(processedYaml)
        .retryStagesIdentifier(stagesIdentifier)
        .identifierOfSkipStages(identifierOfSkipStages)
        .build();
  }

  private void sendExecutionStartTelemetryEvent(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(PROJECT_IDENTIFIER, projectId);
    propertiesMap.put(ORG_IDENTIFIER, orgId);
    propertiesMap.put(PIPELINE_ID, pipelineIdentifier);
    pipelineTelemetryHelper.sendTelemetryEventWithAccountName(START_PIPELINE_EXECUTION_EVENT, accountId, propertiesMap);
  }

  public PlanExecutionResponseDto runPipelineAsChildPipelineWithJsonNode(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, JsonNode runtimeJsonNode, boolean useV2,
      boolean notifyOnlyUser, List<String> inputSetReferences, PipelineStageInfo info, boolean isDebug) {
    JsonNode inputSetJsonNode = runtimeJsonNode;
    if (!isEmpty(inputSetReferences)) {
      GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      inputSetJsonNode = validateAndMergeHelper.getMergeInputSetFromPipelineTemplateWithJsonNode(accountId,
          orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetReferences, gitEntityInfo.getBranch(),
          gitEntityInfo.getRepoName(), null);
    }
    return startPlanExecutionForChildPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null,
        moduleType, Collections.emptyList(), Collections.emptyMap(), useV2, notifyOnlyUser, info, isDebug,
        inputSetJsonNode);
  }

  private PlanExecutionResponseDto startPlanExecutionForChildPipeline(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String originalExecutionId, String moduleType,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser,
      PipelineStageInfo info, boolean isDebug, JsonNode mergedRuntimeInputJsonNode) {
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
      throw new InvalidRequestException(String.format(
          "Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s", pipelineIdentifier, projectIdentifier));
    }
    ExecArgs execArgs;
    execArgs = getExecArgsWithJsonNode(originalExecutionId, moduleType, mergedRuntimeInputJsonNode, stagesToRun,
        expressionValues, notifyOnlyUser, pipelineEntity, isDebug, null);

    if (info != null) {
      execArgs.setMetadata(execArgs.getMetadata().toBuilder().setPipelineStageInfo(info).build());

      // Setting payload, trigger info to support trigger expression in child pipeline
      setTriggerInfo(info, execArgs, accountId);
    }
    return getPlanExecutionResponseDto(accountId, orgIdentifier, projectIdentifier, useV2, pipelineEntity, execArgs);
  }

  public void setTriggerInfo(PipelineStageInfo info, ExecArgs execArgs, String accountId) {
    // Need to set triggerJsonPayload from parent to child to resolve trigger expression in child
    PlanExecutionMetadata planExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(info.getExecutionId())
            .orElseThrow(()
                             -> new InternalServerErrorException(
                                 "PlanExecution metadata null for planExecutionId " + info.getExecutionId(), null));

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            accountId, info.getOrgId(), info.getProjectId(), info.getExecutionId());

    String triggerJsonPayload = "";
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();

    if (planExecutionMetadata.getTriggerPayload() != null) {
      triggerPayload = planExecutionMetadata.getTriggerPayload();
    }

    if (planExecutionMetadata.getTriggerJsonPayload() != null) {
      triggerJsonPayload = planExecutionMetadata.getTriggerJsonPayload();
    }

    execArgs.setPlanExecutionMetadata(execArgs.getPlanExecutionMetadata().withTriggerJsonPayload(triggerJsonPayload));

    // To support expression related to PR_NUMBER, branch name etc
    execArgs.setPlanExecutionMetadata(execArgs.getPlanExecutionMetadata().withTriggerPayload(triggerPayload));

    // To support expression like - <+pipeline.triggeredBy.name>
    execArgs.setMetadata(execArgs.getMetadata()
                             .toBuilder()
                             .setTriggerInfo(pipelineExecutionSummaryEntity.getExecutionTriggerInfo())
                             .build());
  }
}
