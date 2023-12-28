/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s.asyncsteps;

import static io.harness.delegate.task.k8s.K8sTrafficRoutingRequest.K8sTrafficRoutingRequestBuilder;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ReleaseMetadataFactory;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.executables.AsyncExecutableTaskHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.k8s.K8sTrafficRoutingStepParameters;
import io.harness.cdng.k8s.trafficrouting.ConfigK8sTrafficRouting;
import io.harness.cdng.k8s.trafficrouting.InheritK8sTrafficRouting;
import io.harness.cdng.k8s.trafficrouting.K8sTrafficRoutingHelper;
import io.harness.delegate.AccountId;
import io.harness.delegate.TaskId;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.k8s.K8sTrafficRoutingRequest;
import io.harness.delegate.task.k8s.K8sTrafficRoutingResponse;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfigType;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.k8s.trafficrouting.TrafficRoutingInfoDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.executable.AsyncExecutableWithCapabilities;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class K8sTrafficRoutingStep
    extends AsyncExecutableWithCapabilities implements AsyncExecutable<StepBaseParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_TRAFFIC_ROUTING.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private final String K8S_TRAFFIC_ROUTING_COMMAND_NAME = "K8s Traffic Routing";

  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject K8sTrafficRoutingHelper k8sTrafficRoutingHelper;
  @Inject private AsyncExecutableTaskHelper asyncExecutableTaskHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private ReleaseMetadataFactory releaseMetadataFactory;

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    // Noop
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    if (!cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_K8S_TRAFFIC_ROUTING_NG)) {
      throw new UnsupportedOperationException(
          format("Feature Flag %s is not enabled. It is not possible to execute Traffic Routing step.",
              FeatureName.CDS_K8S_TRAFFIC_ROUTING_NG.name()));
    }
    InfrastructureOutcome infrastructure = cdStepHelper.getInfrastructureOutcome(ambiance);
    String releaseName = cdStepHelper.getReleaseName(ambiance, infrastructure);
    K8sTrafficRoutingStepParameters k8sTrafficRoutingStepParameters =
        (K8sTrafficRoutingStepParameters) stepParameters.getSpec();

    // TODO: placeholder until we implement the retrieval of this object from collection or sweeping output
    TrafficRoutingInfoDTO trafficRoutingInfoDTO = null;

    K8sTrafficRoutingConfig k8sTrafficRoutingConfig = fetchTrafficRoutingConfig(k8sTrafficRoutingStepParameters);

    K8sTrafficRoutingRequestBuilder k8sTrafficRoutingRequestBuilder =
        K8sTrafficRoutingRequest.builder()
            .releaseName(releaseName)
            .commandName(K8S_TRAFFIC_ROUTING_COMMAND_NAME)
            .taskType(K8sTaskType.TRAFFIC_ROUTING)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .accountId(accountId)
            .trafficRoutingConfig(k8sTrafficRoutingConfig)
            .trafficRoutingInfo(trafficRoutingInfoDTO)
            .useDeclarativeRollback(k8sStepHelper.isDeclarativeRollbackEnabled(ambiance));

    k8sStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);

    K8sTrafficRoutingRequest k8sTrafficRoutingRequest = k8sTrafficRoutingRequestBuilder.build();
    TaskType taskType = k8sStepHelper.getK8sTaskType(k8sTrafficRoutingRequest, ambiance);
    TaskRequest taskRequest =
        k8sStepHelper.createTaskRequest(stepParameters, k8sTrafficRoutingRequest, taskType, ambiance);
    return asyncExecutableTaskHelper.getAsyncExecutableResponse(ambiance, taskRequest);
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info(format("Finalizing %s execution", K8S_TRAFFIC_ROUTING_COMMAND_NAME));

    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    K8sDeployResponse k8sTaskExecutionResponse;
    try {
      k8sTaskExecutionResponse = (K8sDeployResponse) StrategyHelper.buildResponseDataSupplier(responseDataMap).get();
    } catch (Exception e) {
      String errorMessage = e.getMessage();

      if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
        errorMessage = e.getCause().getMessage();
      }

      log.error("Error while processing K8s Task response: {}", errorMessage, e);

      return stepResponseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().setErrorMessage(errorMessage).build())
          .build();
    }

    stepResponseBuilder.unitProgressList(k8sTaskExecutionResponse.getCommandUnitsProgress().getUnitProgresses());

    if (k8sTaskExecutionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return K8sStepHelper.getFailureResponseBuilder(k8sTaskExecutionResponse, stepResponseBuilder).build();
    }

    if (k8sTaskExecutionResponse.getK8sNGTaskResponse() instanceof K8sTrafficRoutingResponse) {
      K8sTrafficRoutingResponse k8sTrafficRoutingResponse =
          (K8sTrafficRoutingResponse) k8sTaskExecutionResponse.getK8sNGTaskResponse();
      TrafficRoutingInfoDTO trafficRoutingInfoDTO = k8sTrafficRoutingResponse.getInfo();
      // TODO: placeholder for action of saving traffic routing info into collection or sweeping output
    }

    return stepResponseBuilder.status(Status.SUCCEEDED).build();
  }

  private K8sTrafficRoutingConfig fetchTrafficRoutingConfig(
      K8sTrafficRoutingStepParameters k8sTrafficRoutingStepParameters) {
    Optional<K8sTrafficRoutingConfig> optionalK8sTrafficRoutingConfig = Optional.empty();
    if (K8sTrafficRoutingConfigType.CONFIG.equals(k8sTrafficRoutingStepParameters.getType())) {
      optionalK8sTrafficRoutingConfig = k8sTrafficRoutingHelper.validateAndGetTrafficRoutingConfig(
          (ConfigK8sTrafficRouting) k8sTrafficRoutingStepParameters.getTrafficRouting());
    } else if (K8sTrafficRoutingConfigType.INHERIT.equals(k8sTrafficRoutingStepParameters.getType())) {
      optionalK8sTrafficRoutingConfig = k8sTrafficRoutingHelper.validateAndGetInheritedTrafficRoutingConfig(
          (InheritK8sTrafficRouting) k8sTrafficRoutingStepParameters.getTrafficRouting());
    }

    if (optionalK8sTrafficRoutingConfig.isPresent()) {
      K8sTrafficRoutingConfig k8sTrafficRoutingConfig = optionalK8sTrafficRoutingConfig.get();
      k8sTrafficRoutingConfig.setType(k8sTrafficRoutingStepParameters.getType());
      return k8sTrafficRoutingConfig;
    }

    return null;
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepBaseParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    String taskId = executableResponse.getCallbackIdsList().iterator().next();
    String accountId = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId);
    delegateGrpcClientWrapper.cancelV2Task(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
  }
}
