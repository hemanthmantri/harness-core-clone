/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.advisers.manualIntervention.ManualInterventionAdviserRollbackParameters;
import io.harness.advisers.manualIntervention.ManualInterventionAdviserWithRollback;
import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.pipelinerollback.OnFailPipelineRollbackAdviser;
import io.harness.advisers.pipelinerollback.OnFailPipelineRollbackParameters;
import io.harness.advisers.retry.ManualInterventionActionConfigPostRetry;
import io.harness.advisers.retry.RetryAdviserRollbackParameters;
import io.harness.advisers.retry.RetryAdviserWithRollback;
import io.harness.advisers.retry.RetryStepGroupAdvisor;
import io.harness.advisers.rollback.OnFailRollbackAdviser;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.govern.Switch;
import io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.v1.FailureStrategiesUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.abort.OnAbortAdviser;
import io.harness.pms.sdk.core.adviser.abort.OnAbortAdviserParameters;
import io.harness.pms.sdk.core.adviser.ignore.IgnoreAdviser;
import io.harness.pms.sdk.core.adviser.ignore.IgnoreAdviserParameters;
import io.harness.pms.sdk.core.adviser.markFailure.OnMarkFailureAdviser;
import io.harness.pms.sdk.core.adviser.markFailure.OnMarkFailureAdviserParameters;
import io.harness.pms.sdk.core.adviser.marksuccess.OnMarkSuccessAdviser;
import io.harness.pms.sdk.core.adviser.marksuccess.OnMarkSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.failurestrategy.manualintervention.v1.ManualInterventionFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetrySGFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.failurestrategy.v1.FailureStrategyActionConfigV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureActionTypeV1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PlanCreatorUtilsV1 {
  @Inject KryoSerializer kryoSerializer;
  public List<AdviserObtainment> getAdviserObtainmentsForStage(KryoSerializer kryoSerializer, Dependency dependency) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    AdviserObtainment nextStepAdviser = getNextStepAdviser(kryoSerializer, dependency);
    // The case of parallel doesn't need to be handled explicitly as nextId will not be present in dependency in that
    // case.
    // TODO(shalini): handle the case of strategy and rollback
    if (nextStepAdviser != null) {
      adviserObtainments.add(nextStepAdviser);
    }
    return adviserObtainments;
  }

  // TODO(shalini): stage rollback, pipeline rollback, and proceedWithDefaultValues not handled yet
  public List<AdviserObtainment> getAdviserObtainmentsForStep(
      KryoSerializer kryoSerializer, Dependency dependency, YamlNode node) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    List<AdviserObtainment> failureStrategyAdvisers = getFailureStrategiesAdvisers(
        kryoSerializer, dependency, node, getNextNodeUuid(kryoSerializer, dependency), false);
    adviserObtainments.addAll(failureStrategyAdvisers);
    AdviserObtainment nextStepAdviser = getNextStepAdviser(kryoSerializer, dependency);
    if (nextStepAdviser != null) {
      adviserObtainments.add(nextStepAdviser);
    }
    return adviserObtainments;
  }

  public List<AdviserObtainment> getAdviserObtainmentsForStep(
      KryoSerializer kryoSerializer, Dependency dependency, List<FailureConfigV1> stepFailureStrategies) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    List<AdviserObtainment> failureStrategyAdvisers = getFailureStrategiesAdvisers(
        kryoSerializer, dependency, stepFailureStrategies, getNextNodeUuid(kryoSerializer, dependency), false);
    adviserObtainments.addAll(failureStrategyAdvisers);
    AdviserObtainment nextStepAdviser = getNextStepAdviser(kryoSerializer, dependency);
    if (nextStepAdviser != null) {
      adviserObtainments.add(nextStepAdviser);
    }
    return adviserObtainments;
  }

  public SdkTimeoutObtainment getTimeoutObtainmentForStage(AbstractStageNodeV1 stageNode) {
    if (ParameterField.isNotNull(stageNode.getTimeout())) {
      return SdkTimeoutObtainment.builder()
          .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
          .parameters(AbsoluteSdkTimeoutTrackerParameters.builder().timeout(stageNode.getTimeout()).build())
          .build();
    }

    return null;
  }

  public String getNextNodeUuid(KryoSerializer kryoSerializer, Dependency dependency) {
    Optional<Object> nextNodeIdOptional =
        getDeserializedObjectFromDependency(dependency, kryoSerializer, PlanCreatorConstants.NEXT_ID, false);
    if (nextNodeIdOptional.isPresent() && nextNodeIdOptional.get() instanceof String) {
      return (String) nextNodeIdOptional.get();
    }
    return null;
  }

  public AdviserObtainment getNextStepAdviser(KryoSerializer kryoSerializer, Dependency dependency) {
    if (dependency == null) {
      return null;
    }
    String nextId = getNextNodeUuid(kryoSerializer, dependency);
    if (nextId != null) {
      return AdviserObtainment.newBuilder()
          .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
          .setParameters(ByteString.copyFrom(
              kryoSerializer.asBytes(NextStepAdviserParameters.builder().nextNodeId(nextId).build())))
          .build();
    }
    return null;
  }

  public HarnessValue getNodeMetadataValueFromDependency(Dependency dependency, String key) {
    if (isNotEmpty(dependency.getNodeMetadata().getDataMap())
        && dependency.getNodeMetadata().getDataMap().containsKey(key)) {
      return dependency.getNodeMetadata().getDataMap().get(key);
    }
    return null;
  }

  public Optional<Object> getDeserializedObjectFromDependency(
      Dependency dependency, KryoSerializer kryoSerializer, String key, boolean asInflatedObject) {
    if (dependency == null) {
      return Optional.empty();
    }
    HarnessValue harnessValue = getNodeMetadataValueFromDependency(dependency, key);
    if (harnessValue != null) {
      if (harnessValue.hasStringValue()) {
        return Optional.of(harnessValue.getStringValue());
      }
      if (harnessValue.hasBytesValue()) {
        ByteString bytes = harnessValue.getBytesValue();
        Optional<Object> objectOptional = getObjectFromBytes(bytes, kryoSerializer, asInflatedObject);
        if (objectOptional.isPresent()) {
          return objectOptional;
        }
      }
      if (harnessValue.hasBoolValue()) {
        return Optional.of(harnessValue.getBoolValue());
      }
    }
    return Optional.empty();
  }

  public RepairActionCode toRepairAction(FailureStrategyActionConfigV1 action) {
    switch (action.getType()) {
      case IGNORE:
        return RepairActionCode.IGNORE;
      case MARK_AS_SUCCESS:
        return RepairActionCode.MARK_AS_SUCCESS;
      case ABORT:
        return RepairActionCode.END_EXECUTION;
      case STAGE_ROLLBACK:
        return RepairActionCode.STAGE_ROLLBACK;
      case MANUAL_INTERVENTION:
        return RepairActionCode.MANUAL_INTERVENTION;
      case RETRY:
        return RepairActionCode.RETRY;
      case MARK_AS_FAILURE:
        return RepairActionCode.MARK_AS_FAILURE;
      case PIPELINE_ROLLBACK:
        return RepairActionCode.PIPELINE_ROLLBACK;
      default:
        throw new InvalidRequestException(
            action.toString() + " Failure action doesn't have corresponding RepairAction Code.");
    }
  }

  private AdviserObtainment getManualInterventionAdviserObtainment(KryoSerializer kryoSerializer,
      Set<FailureType> failureTypes, AdviserObtainment.Builder adviserObtainmentBuilder,
      ManualInterventionFailureActionConfigV1 actionConfig,
      FailureStrategyActionConfigV1 actionUnderManualIntervention) {
    return adviserObtainmentBuilder.setType(ManualInterventionAdviserWithRollback.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
            ManualInterventionAdviserRollbackParameters.builder()
                .applicableFailureTypes(failureTypes)
                .timeoutAction(toRepairAction(actionUnderManualIntervention))
                .timeout((int) TimeoutUtils.getTimeoutInSeconds(actionConfig.getSpec().getTimeout(), 0))
                .build())))
        .build();
  }

  // TODO: (shalini): set strategyToUuid map in RetryAdviserRollbackParameters used in stage rollback
  private AdviserObtainment getRetryAdviserObtainment(KryoSerializer kryoSerializer, Set<FailureType> failureTypes,
      String nextNodeUuid, AdviserObtainment.Builder adviserObtainmentBuilder, RetryFailureActionConfigV1 retryAction,
      ParameterField<Integer> retryCount, FailureStrategyActionConfigV1 actionUnderRetry) {
    return adviserObtainmentBuilder.setType(RetryAdviserWithRollback.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
            RetryAdviserRollbackParameters.builder()
                .applicableFailureTypes(failureTypes)
                .nextNodeId(nextNodeUuid)
                .repairActionCodeAfterRetry(toRepairAction(actionUnderRetry))
                .actionConfigPostRetry(getManualInterventionActionConfigPostRetry(actionUnderRetry))
                .retryCount(retryCount.getValue())
                .waitIntervalList(retryAction.getSpec()
                                      .getInterval()
                                      .getValue()
                                      .stream()
                                      .map(s -> (int) TimeoutUtils.getTimeoutInSeconds(s, 0))
                                      .collect(Collectors.toList()))
                .build())))
        .build();
  }

  private ManualInterventionActionConfigPostRetry getManualInterventionActionConfigPostRetry(
      FailureStrategyActionConfigV1 actionUnderRetry) {
    if (actionUnderRetry instanceof ManualInterventionFailureActionConfigV1) {
      ManualInterventionFailureActionConfigV1 manualInterventionFailureConfigV1 =
          (ManualInterventionFailureActionConfigV1) actionUnderRetry;
      return ManualInterventionActionConfigPostRetry.builder()
          .actionAfterManualInterventionTimeout(
              toRepairAction(manualInterventionFailureConfigV1.getSpec().getTimeout_action()))
          .timeoutInSeconds(TimeoutUtils.getTimeoutInSeconds(manualInterventionFailureConfigV1.getSpec().getTimeout(),
              Duration.newBuilder().setSeconds(java.time.Duration.ofDays(1).toMinutes() * 60).getSeconds()))
          .build();
    }
    return null;
  }

  private List<AdviserObtainment> getFailureStrategiesAdvisers(KryoSerializer kryoSerializer, Dependency dependency,
      YamlNode yamlNode, String nextNodeUuid, boolean isStepInsideRollback) {
    List<FailureConfigV1> stepFailureStrategies = getFailureStrategies(yamlNode);
    return getFailureStrategiesAdvisers(
        kryoSerializer, dependency, stepFailureStrategies, nextNodeUuid, isStepInsideRollback);
  }

  @VisibleForTesting
  List<AdviserObtainment> getFailureStrategiesAdvisers(KryoSerializer kryoSerializer, Dependency dependency,
      List<FailureConfigV1> stepFailureStrategies, String nextNodeUuid, boolean isStepInsideRollback) {
    List<FailureConfigV1> stageFailureStrategies = getStageFailureStrategies(kryoSerializer, dependency);
    List<FailureConfigV1> stepGroupFailureStrategies = getStepGroupFailureStrategies(kryoSerializer, dependency);
    Map<FailureStrategyActionConfigV1, Collection<FailureType>> actionMap =
        FailureStrategiesUtilsV1.priorityMergeFailureStrategies(
            stepFailureStrategies, stepGroupFailureStrategies, stageFailureStrategies);
    return getFailureStrategiesAdvisers(
        kryoSerializer, actionMap, isStepInsideRollback, nextNodeUuid, PlanCreatorUtilsV1::getAdviserObtainmentForStep);
  }
  public Optional<Object> getDeserializedObjectFromParentInfo(
      KryoSerializer kryoSerializer, Dependency dependency, String key, boolean asInflatedObject) {
    if (dependency == null) {
      return Optional.empty();
    }
    HarnessValue harnessValue = getHarnessValueParentInfoFromDependency(dependency, key);
    if (harnessValue != null) {
      if (harnessValue.hasStringValue()) {
        return Optional.of(harnessValue.getStringValue());
      }
      if (harnessValue.hasBytesValue()) {
        ByteString bytes = harnessValue.getBytesValue();
        Optional<Object> objectOptional = getObjectFromBytes(bytes, kryoSerializer, asInflatedObject);
        if (objectOptional.isPresent()) {
          return objectOptional;
        }
      }
      if (harnessValue.hasBoolValue()) {
        return Optional.of(harnessValue.getBoolValue());
      }
    }
    return Optional.empty();
  }

  private HarnessValue getHarnessValueParentInfoFromDependency(Dependency dependency, String key) {
    if (dependency != null && dependency.getParentInfo().getDataMap().containsKey(key)) {
      return dependency.getParentInfo().getDataMap().get(key);
    }
    return null;
  }

  Optional<Object> getObjectFromBytes(ByteString bytes, KryoSerializer kryoSerializer, boolean asInflatedObject) {
    if (isNotEmpty(bytes)) {
      if (asInflatedObject) {
        return Optional.of(kryoSerializer.asInflatedObject(bytes.toByteArray()));
      }
      return Optional.of(kryoSerializer.asObject(bytes.toByteArray()));
    }
    return Optional.empty();
  }

  public List<FailureConfigV1> getStageFailureStrategies(KryoSerializer kryoSerializer, Dependency dependency) {
    Optional<Object> stageFailureStrategiesOptional = getDeserializedObjectFromParentInfo(
        kryoSerializer, dependency, PlanCreatorConstants.STAGE_FAILURE_STRATEGIES, true);
    List<FailureConfigV1> stageFailureStrategies = null;
    if (stageFailureStrategiesOptional.isPresent()) {
      stageFailureStrategies = (List<FailureConfigV1>) stageFailureStrategiesOptional.get();
    }
    return stageFailureStrategies;
  }

  List<FailureConfigV1> getStepGroupFailureStrategies(KryoSerializer kryoSerializer, Dependency dependency) {
    Optional<Object> stepGroupFailureStrategiesOptional = getDeserializedObjectFromParentInfo(
        kryoSerializer, dependency, PlanCreatorConstants.STEP_GROUP_FAILURE_STRATEGIES, true);
    List<FailureConfigV1> stepGroupFailureStrategies = null;
    if (stepGroupFailureStrategiesOptional.isPresent()) {
      stepGroupFailureStrategies = (List<FailureConfigV1>) stepGroupFailureStrategiesOptional.get();
    }
    return stepGroupFailureStrategies;
  }

  public List<AdviserObtainment> getFailureStrategiesAdvisers(KryoSerializer kryoSerializer,
      Map<FailureStrategyActionConfigV1, Collection<FailureType>> actionMap, boolean isStepInsideRollback,
      String nextNodeUuid, GetAdviserForActionType function) {
    List<AdviserObtainment> adviserObtainmentList = new ArrayList<>();
    for (Map.Entry<FailureStrategyActionConfigV1, Collection<FailureType>> entry : actionMap.entrySet()) {
      FailureStrategyActionConfigV1 action = entry.getKey();
      Set<FailureType> failureTypes = new HashSet<>(entry.getValue());
      NGFailureActionTypeV1 actionType = action.getType();

      if (isStepInsideRollback) {
        if (actionType == io.harness.yaml.core.failurestrategy.v1.NGFailureActionTypeV1.STAGE_ROLLBACK) {
          throw new InvalidRequestException("Step inside rollback section cannot have Rollback as failure strategy.");
        }
      }
      AdviserObtainment adviserObtainment =
          function.getAdviserForActionType(kryoSerializer, action, failureTypes, actionType, nextNodeUuid);
      if (adviserObtainment != null) {
        adviserObtainmentList.add(adviserObtainment);
      }
    }
    return adviserObtainmentList;
  }

  public AdviserObtainment getAdviserObtainmentForStepGroup(KryoSerializer kryoSerializer,
      FailureStrategyActionConfigV1 action, Set<FailureType> failureTypes, NGFailureActionTypeV1 actionType,
      String nextNodeUuid) {
    AdviserObtainment.Builder adviserObtainmentBuilder = AdviserObtainment.newBuilder();
    switch (actionType) {
      case RETRY_STEP_GROUP:
        RetrySGFailureActionConfigV1 retrySGAction = (RetrySGFailureActionConfigV1) action;
        FailureStrategiesUtilsV1.validateRetrySGFailureAction(retrySGAction);
        ParameterField<Integer> retrySGCount = retrySGAction.getSpec().getAttempts();
        return getRetryStepGroupAdviserObtainment(
            kryoSerializer, failureTypes, nextNodeUuid, adviserObtainmentBuilder, retrySGAction, retrySGCount);
      default:
        // do nothing
    }
    return null;
  }

  // TODO: (shalini): set strategyToUuid map in RetryAdviserRollbackParameters used in stage rollback
  @VisibleForTesting
  AdviserObtainment getRetryStepGroupAdviserObtainment(KryoSerializer kryoSerializer, Set<FailureType> failureTypes,
      String nextNodeUuid, AdviserObtainment.Builder adviserObtainmentBuilder, RetrySGFailureActionConfigV1 retryAction,
      ParameterField<Integer> retryCount) {
    return adviserObtainmentBuilder.setType(RetryStepGroupAdvisor.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(
            kryoSerializer.asBytes(RetryAdviserRollbackParameters.builder()
                                       .applicableFailureTypes(failureTypes)
                                       .nextNodeId(nextNodeUuid)
                                       .retryCount(retryCount.getValue())
                                       .waitIntervalList(retryAction.getSpec()
                                                             .getInterval()
                                                             .getValue()
                                                             .stream()
                                                             .map(s -> (int) TimeoutUtils.getTimeoutInSeconds(s, 0))
                                                             .collect(Collectors.toList()))
                                       .build())))
        .build();
  }

  AdviserObtainment getAdviserObtainmentForStep(KryoSerializer kryoSerializer, FailureStrategyActionConfigV1 action,
      Set<FailureType> failureTypes, NGFailureActionTypeV1 actionType, String nextNodeUuid) {
    AdviserObtainment.Builder adviserObtainmentBuilder = AdviserObtainment.newBuilder();
    switch (actionType) {
      case IGNORE:
        return adviserObtainmentBuilder.setType(IgnoreAdviser.ADVISER_TYPE)
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(IgnoreAdviserParameters.builder()
                                                                          .applicableFailureTypes(failureTypes)
                                                                          .nextNodeId(nextNodeUuid)
                                                                          .build())))
            .build();
      case RETRY:
        RetryFailureActionConfigV1 retryAction = (RetryFailureActionConfigV1) action;
        FailureStrategiesUtilsV1.validateRetryFailureAction(retryAction);
        ParameterField<Integer> retryCount = retryAction.getSpec().getAttempts();
        FailureStrategyActionConfigV1 actionUnderRetry = retryAction.getSpec().getFailure().getAction();
        return getRetryAdviserObtainment(kryoSerializer, failureTypes, nextNodeUuid, adviserObtainmentBuilder,
            retryAction, retryCount, actionUnderRetry);
      case MARK_AS_SUCCESS:
        return adviserObtainmentBuilder.setType(OnMarkSuccessAdviser.ADVISER_TYPE)
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(OnMarkSuccessAdviserParameters.builder()
                                                                          .applicableFailureTypes(failureTypes)
                                                                          .nextNodeId(nextNodeUuid)
                                                                          .build())))
            .build();
      case ABORT:
        return adviserObtainmentBuilder.setType(OnAbortAdviser.ADVISER_TYPE)
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                OnAbortAdviserParameters.builder().applicableFailureTypes(failureTypes).build())))
            .build();
      case STAGE_ROLLBACK:
        // TODO(Shalini): Add methd to get rollback parameters and set it below in parameters
        return adviserObtainmentBuilder.setType(OnFailRollbackAdviser.ADVISER_TYPE)
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(null)))
            .build();
      case MANUAL_INTERVENTION:
        ManualInterventionFailureActionConfigV1 actionConfig = (ManualInterventionFailureActionConfigV1) action;
        FailureStrategiesUtilsV1.validateManualInterventionFailureAction(actionConfig);
        FailureStrategyActionConfigV1 actionUnderManualIntervention = actionConfig.getSpec().getTimeout_action();
        return getManualInterventionAdviserObtainment(
            kryoSerializer, failureTypes, adviserObtainmentBuilder, actionConfig, actionUnderManualIntervention);
      case PIPELINE_ROLLBACK:
        OnFailPipelineRollbackParameters onFailPipelineRollbackParameters =
            FailureStrategiesUtilsV1.buildOnFailPipelineRollbackParameters(failureTypes);
        return adviserObtainmentBuilder.setType(OnFailPipelineRollbackAdviser.ADVISER_TYPE)
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(onFailPipelineRollbackParameters)))
            .build();
      case MARK_AS_FAILURE:
        return adviserObtainmentBuilder.setType(OnMarkFailureAdviser.ADVISER_TYPE)
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(OnMarkFailureAdviserParameters.builder()
                                                                          .applicableFailureTypes(failureTypes)
                                                                          .nextNodeId(nextNodeUuid)
                                                                          .build())))
            .build();
      default:
        Switch.unhandled(actionType);
    }
    return null;
  }

  public ParameterField<List<TaskSelectorYaml>> getDelegates(YamlNode node) {
    YamlField delegatesField = node.getField(YAMLFieldNameConstants.DELEGATES);
    try {
      if (delegatesField != null) {
        return getDelegatesParameterField(delegatesField.getNode());
      }
      return null;
    } catch (Exception ex) {
      throw new InvalidRequestException("Invalid Yaml for Delegates", ex);
    }
  }

  ParameterField<List<TaskSelectorYaml>> getDelegatesParameterField(YamlNode delegateYamlNode) throws Exception {
    ParameterField<List<TaskSelectorYaml>> delegateListV1ParameterField = null;
    if (delegateYamlNode.isArray() || JsonUtils.isJsonList(delegateYamlNode.asText())) {
      delegateListV1ParameterField =
          YamlUtils.read(delegateYamlNode.toString(), new TypeReference<ParameterField<List<TaskSelectorYaml>>>() {});
    } else {
      ParameterField<TaskSelectorYaml> delegateV1ParameterField =
          YamlUtils.read(delegateYamlNode.toString(), new TypeReference<ParameterField<TaskSelectorYaml>>() {});
      if (ParameterField.isNotNull(delegateV1ParameterField)) {
        if (delegateV1ParameterField.isExpression()) {
          delegateListV1ParameterField =
              ParameterField.createExpressionField(true, delegateV1ParameterField.getExpressionValue(), null, false);
        } else {
          delegateListV1ParameterField =
              ParameterField.createValueField(new ArrayList<>(List.of(delegateV1ParameterField.getValue())));
        }
      }
    }
    return delegateListV1ParameterField;
  }

  public List<FailureConfigV1> getFailureStrategies(YamlNode node) {
    YamlField failureConfigV1 = node.getField("failure");
    ParameterField<List<FailureConfigV1>> failureConfigListV1ParameterField = null;

    try {
      if (failureConfigV1 != null) {
        failureConfigListV1ParameterField = getFailureStrategiesListParameterField(failureConfigV1.getNode());
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Invalid yaml for failure strategies", e);
    }
    // If failureStrategies configured as <+input> and no value is given, failureStrategyConfigs.getValue() will still
    // be null and handled as empty list
    if (ParameterField.isNotNull(failureConfigListV1ParameterField)) {
      return failureConfigListV1ParameterField.getValue();
    } else {
      return null;
    }
  }

  ParameterField<List<FailureConfigV1>> getFailureStrategiesListParameterField(YamlNode failureConfigNode)
      throws Exception {
    ParameterField<List<FailureConfigV1>> failureConfigListV1ParameterField = null;
    if (failureConfigNode.isArray()) {
      failureConfigListV1ParameterField =
          YamlUtils.read(failureConfigNode.toString(), new TypeReference<ParameterField<List<FailureConfigV1>>>() {});
    } else {
      ParameterField<FailureConfigV1> failureConfigV1ParameterField =
          YamlUtils.read(failureConfigNode.toString(), new TypeReference<ParameterField<FailureConfigV1>>() {});
      if (ParameterField.isNotNull(failureConfigV1ParameterField)) {
        failureConfigListV1ParameterField =
            ParameterField.createValueField(new ArrayList<>(List.of(failureConfigV1ParameterField.getValue())));
      }
    }
    return failureConfigListV1ParameterField;
  }

  // TODO: Get isStepInsideRollback from dependency metadata map
  public boolean isStepInsideRollback(Dependency dependency) {
    return false;
  }

  @FunctionalInterface
  public interface GetAdviserForActionType {
    AdviserObtainment getAdviserForActionType(KryoSerializer kryoSerializer, FailureStrategyActionConfigV1 action,
        Set<FailureType> failureTypes, NGFailureActionTypeV1 actionType, String nextNodeUuid);
  }

  public YamlField getStageConfig(YamlField yamlField, String stageIdentifier) {
    if (EmptyPredicate.isEmpty(stageIdentifier)) {
      return null;
    }

    if (yamlField.getName().equals(YAMLFieldNameConstants.PIPELINE)
        || yamlField.getName().equals(YAMLFieldNameConstants.STAGES)) {
      return null;
    }

    // recursively traversing up the pipeline yaml tree (starting from given yamlField) until we find 'stages' element
    // or pipeline root element
    YamlNode stages = YamlUtils.getGivenYamlNodeFromParentPath(yamlField.getNode(), YAMLFieldNameConstants.STAGES);
    // we get all 'stage' elements from 'stages' previously found
    List<YamlField> stageYamlFields = getStageYamlFields(stages);
    for (YamlField stageYamlField : stageYamlFields) {
      if (stageIdentifier.equals(stageYamlField.getNode().getField(YAMLFieldNameConstants.ID).getNode().asText())) {
        return stageYamlField;
      }
    }

    // in case of nested parallel stage we need to traverse other siblings of parallel stage
    // max number of nested parallel stages is 1 level
    // example:
    //
    // spec:
    //  stages:
    //    - stage1 (deployment type)
    //    - stage2 (parallel type)
    //        spec:
    //          stages:
    //            - substage1
    //            - substage2 (svc useFromStage stage1)
    //
    // with this logic we are moving up the pipeline hierarchy
    // so in worst case there will be one level of parallel stages which utilizes useFromStage from a stage at root
    if (stages != null && stages.getParentNode() != null && stages.getParentNode().getParentNode() != null) {
      return getStageConfig(new YamlField(stages.getParentNode()), stageIdentifier);
    }

    return null;
  }

  private List<YamlField> getStageYamlFields(YamlNode stagesYamlNode) {
    List<YamlField> stageFields = new LinkedList<>();

    if (stagesYamlNode != null) {
      List<YamlNode> yamlNodes = Optional.of(stagesYamlNode.asArray()).orElse(Collections.emptyList());

      yamlNodes.forEach(yamlNode -> {
        String stageFieldType = yamlNode.getStringValue(YAMLFieldNameConstants.TYPE);
        if (YAMLFieldNameConstants.PARALLEL.equalsIgnoreCase(stageFieldType)) {
          // max number of allowed nested parallel stages is 1 level hence the for loop instead of recursion
          YamlNode childStages = yamlNode.getField(YAMLFieldNameConstants.SPEC)
                                     .getNode()
                                     .getField(YAMLFieldNameConstants.STAGES)
                                     .getNode();
          if (childStages.isArray()) {
            childStages.asArray().forEach(childStage -> { stageFields.add(new YamlField(childStage)); });
          }
        } else {
          stageFields.add(new YamlField(yamlNode));
        }
      });
    }

    return stageFields;
  }

  public List<YamlField> getStepYamlFields(List<YamlNode> stepYamlNodes) {
    List<YamlField> stepFields = new LinkedList<>();

    stepYamlNodes.forEach(yamlNode -> {
      YamlField stepField = yamlNode.getField(YAMLFieldNameConstants.STEP);
      YamlField stepGroupField = yamlNode.getField(YAMLFieldNameConstants.STEP_GROUP);
      YamlField parallelStepField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (stepField != null) {
        stepFields.add(stepField);
      } else if (stepGroupField != null) {
        stepFields.add(stepGroupField);
      } else if (parallelStepField != null) {
        stepFields.add(parallelStepField);
      }
    });
    return stepFields;
  }

  public boolean isInsideParallelNode(PlanCreationContext ctx) {
    Optional<Object> value = getDeserializedObjectFromDependency(
        ctx.getDependency(), kryoSerializer, PlanCreatorConstants.IS_INSIDE_PARALLEL_NODE, false);
    return value.isPresent() && (boolean) value.get();
  }
}
