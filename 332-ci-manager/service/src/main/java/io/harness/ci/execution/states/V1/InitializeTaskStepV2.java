/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.FeatureName.CIE_ENABLED_RBAC;
import static io.harness.beans.FeatureName.CIE_HOSTED_VMS;
import static io.harness.beans.FeatureName.CODE_ENABLED;
import static io.harness.beans.FeatureName.QUEUE_CI_EXECUTIONS_CONCURRENCY;
import static io.harness.beans.outcomes.LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME;
import static io.harness.beans.outcomes.VmDetailsOutcome.VM_DETAILS_OUTCOME;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.INITIALIZE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.TASK_SELECTORS;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.UNIQUE_STEP_IDENTIFIERS;
import static io.harness.ci.commonconstants.CIExecutionConstants.MAXIMUM_EXPANSION_LIMIT;
import static io.harness.ci.commonconstants.CIExecutionConstants.MAXIMUM_EXPANSION_LIMIT_FREE_ACCOUNT;
import static io.harness.ci.execution.states.InitializeTaskStep.TASK_BUFFER_TIMEOUT_MILLIS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.ci.CIInitializeTaskParams.Type.DLITE_VM;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.steps.StepUtils.buildAbstractions;
import static io.harness.steps.StepUtils.generateLogAbstractions;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import io.harness.EntityType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.beans.IdentifierRef;
import io.harness.beans.dependencies.ServiceDependency;
import io.harness.beans.environment.ServiceDefinitionInfo;
import io.harness.beans.execution.CIInitTaskArgs;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.outcomes.DependencyOutcome;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome.VmDetailsOutcomeBuilder;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CILogKeyMetadata;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.InitializeExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.TaskSelectorSweepingOutput;
import io.harness.beans.sweepingoutputs.UniqueStepIdentifiersSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.callback.DelegateCallbackToken;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.executable.CiAsyncExecutable;
import io.harness.ci.execution.buildstate.BuildSetupUtils;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.execution.BackgroundTaskUtility;
import io.harness.ci.execution.execution.CIExecutionMetadata;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.integrationstage.DockerInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.IntegrationStageUtils;
import io.harness.ci.execution.integrationstage.K8InitializeServiceUtils;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.execution.utils.CIStagePlanCreationUtils;
import io.harness.ci.execution.validation.CIAccountValidationService;
import io.harness.ci.execution.validation.CIYAMLSanitizationService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.CIContainerStatus;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.VmServiceStatus;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmInitializeTaskParams;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.task.HDelegateTask;
import io.harness.encryption.Scope;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.ngexception.CILiteEngineException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.CILogKeyRepository;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.matrix.ExpandedExecutionWrapperInfo;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.steps.matrix.StrategyHelper;
import io.harness.tasks.FailureResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(CI)
public class InitializeTaskStepV2 extends CiAsyncExecutable {
  @Inject private ExceptionManager exceptionManager;
  @Inject private AccountClient accountClient;
  @Inject private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Inject CIBuildEnforcer buildEnforcer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CIFeatureFlagService ciFeatureFlagService;
  @Inject private BuildSetupUtils buildSetupUtils;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Inject private DockerInitializeTaskParamsBuilder dockerInitializeTaskParamsBuilder;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CIYAMLSanitizationService sanitizationService;
  @Inject private CIAccountValidationService validationService;
  @Inject private BackgroundTaskUtility backgroundTaskUtility;
  @Inject private CILicenseService ciLicenseService;
  @Inject private StrategyHelper strategyHelper;
  @Inject private CIAccountExecutionMetadataRepository accountExecutionMetadataRepository;

  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private HsqsClientService hsqsClientService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CIStagePlanCreationUtils ciStagePlanCreationUtils;

  @Inject SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject QueueExecutionUtils queueExecutionUtils;
  @Inject private StepExecutionParametersRepository stepExecutionParametersRepository;
  @Inject CIExecutionRepository ciExecutionRepository;

  @Inject private ExecutionMetricsService executionMetricsService;
  @Inject private CILogKeyRepository ciLogKeyRepository;

  private static final String STEP_STATUS = "ci_active_step_execution_count";
  private static final String STEP_TIME_COUNT = "ci_step_execution_time";

  private static final String DEPENDENCY_OUTCOME = "dependencies";

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    String logKey = getLogKey(ambiance);
    String taskId;
    String accountId = AmbianceUtils.getAccountId(ambiance);
    addExecutionRecord(ambiance, stepParameters, accountId);
    String runTime = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String stageRuntimeId = AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepParameters.getSpec();
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();

    stepExecutionParametersRepository.save(StepExecutionParameters.builder()
                                               .accountId(accountId)
                                               .runTimeId(runTime)
                                               .stageRunTimeId(stageRuntimeId)
                                               .stepParameters(RecastOrchestrationUtils.toJson(stepParameters))
                                               .build());
    // Check and add log key to execution ID
    CILogKeyMetadata ciLogKeyMetadata =
        CILogKeyMetadata.builder().stageExecutionId(stageRuntimeId).logKeys(List.of(logKey)).build();
    ciLogKeyRepository.save(ciLogKeyMetadata);
    boolean shouldQueue = false;
    boolean queueConcurrencyEnabled = infrastructure.getType() == Infrastructure.Type.HOSTED_VM
        && ciFeatureFlagService.isEnabled(QUEUE_CI_EXECUTIONS_CONCURRENCY, accountId);

    // only check if queue is enabled
    if (queueConcurrencyEnabled) {
      shouldQueue = buildEnforcer.shouldQueue(accountId, infrastructure);
    }

    if (shouldQueue) {
      String topic = ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic();
      log.info("start executeAsyncAfterRbac for initialize step with queue. Topic: {}", topic);
      taskId = generateUuid();
      String moduleName = topic;
      String payload = RecastOrchestrationUtils.toJson(
          CIInitTaskArgs.builder().ambiance(ambiance).callbackId(taskId).stepElementParameters(stepParameters).build());
      EnqueueRequest enqueueRequest =
          EnqueueRequest.builder().topic(topic).subTopic(accountId).producerName(moduleName).payload(payload).build();
      try {
        EnqueueResponse execute = hsqsClientService.enqueue(enqueueRequest);
        log.info("build queued. message id: {}", execute.getItemId());
        if (StringUtils.isNotBlank(execute.getItemId())) {
          ciExecutionRepository.updateQueueId(accountId, ambiance.getStageExecutionId(), execute.getItemId());
        }
      } catch (Exception e) {
        log.info("failed to queue build", e);
        throw new CIStageExecutionException(format("failed to process execution, queuing failed. runtime Id: {}",
            AmbianceUtils.getStageRuntimeIdAmbiance(ambiance)));
      }
    } else {
      CIExecutionMetadata ciExecutionMetadata = ciExecutionRepository.updateExecutionStatus(
          AmbianceUtils.getAccountId(ambiance), ambiance.getStageExecutionId(), Status.RUNNING.toString());
      if (ciExecutionMetadata == null) {
        throw new CIStageExecutionException(format(
            "Failed to process execution as ciExecutionMetadata is null for stageExecutionId Id: %s , It generally happens for aborted executions",
            ambiance.getStageExecutionId()));
      }
      taskId = executeBuild(ambiance, stepParameters);
    }

    AsyncExecutableResponse.Builder responseBuilder =
        AsyncExecutableResponse.newBuilder().addCallbackIds(taskId).addAllLogKeys(
            CollectionUtils.emptyIfNull(singletonList(logKey)));

    // Sending the status if shouldQueue is true
    if (shouldQueue) {
      return responseBuilder.setStatus(Status.QUEUED_LICENSE_LIMIT_REACHED).build();
    } else {
      InitStepV2DelegateTaskInfo initStepV2DelegateTaskInfo =
          InitStepV2DelegateTaskInfo.builder().taskID(taskId).taskName("INITIALIZATION_PHASE").build();

      sdkGraphVisualizationDataService.publishStepDetailInformation(
          ambiance, initStepV2DelegateTaskInfo, "initStepV2DelegateTaskInfo");
    }
    log.info("Submitted initialise step request for taskid {}", taskId);

    return responseBuilder.build();
  }

  private void addExecutionRecord(Ambiance ambiance, StepElementParameters stepParameters, String accountId) {
    try {
      queueExecutionUtils.addActiveExecutionBuild(
          (InitializeStepInfo) stepParameters.getSpec(), accountId, ambiance.getStageExecutionId());
    } catch (Exception ex) {
      log.error("Failed to add Execution record for {}", ambiance.getStageExecutionId(), ex);
    }
  }

  public String executeBuild(Ambiance ambiance, StepElementParameters stepParameters) {
    log.info("start executeAsyncAfterRbac for initialize step async");
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepParameters.getSpec();

    // save unique step identifiers from executionWrapperConfig, this will be used to fetch the correct artifact outcome
    // this should happen after strategy population
    consumeUniqueStepIdentifiers(initializeStepInfo, ambiance);

    String logPrefix = getLogPrefix(ambiance);
    ciStagePlanCreationUtils.validateFreeAccountStageExecutionLimit(
        AmbianceUtils.getAccountId(ambiance), initializeStepInfo.getInfrastructure());

    CIInitializeTaskParams buildSetupTaskParams =
        buildSetupUtils.getBuildSetupTaskParams(initializeStepInfo, ambiance, logPrefix);
    boolean executeOnHarnessHostedDelegates = false;
    boolean emitEvent = false;
    String stageExecutionId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();

    // Secrets are in decrypted format for DLITE_VM type
    if (buildSetupTaskParams.getType() != DLITE_VM) {
      try {
        log.info("Created params for build task: {}", EncodingUtils.convertToBase64String(buildSetupTaskParams));
      } catch (Exception e) {
        log.error("Could not serialize class CIInitializeTaskParams", e);
      }
    }
    if (buildSetupTaskParams.getType() == DLITE_VM) {
      AccountDTO accountDTO =
          CGRestUtils.getResponse(accountClient.getAccountDTO(AmbianceUtils.getAccountId(ambiance)));
      if (accountDTO == null) {
        throw new CIStageExecutionException("Account does not exist, contact Harness support team.");
      }
      String platformSelector = ((DliteVmInitializeTaskParams) buildSetupTaskParams).getSetupVmRequest().getPoolID();
      TaskSelector taskSelector = TaskSelector.newBuilder().setSelector(platformSelector).build();
      taskSelectors.add(taskSelector);
      executeOnHarnessHostedDelegates = true;

      emitEvent = true;
    } else if (initializeStepInfo.getInfrastructure().getType() == Infrastructure.Type.DOCKER) {
      if (initializeStepInfo.getDelegateSelectors().getValue() != null) {
        addExternalDelegateSelector(taskSelectors, initializeStepInfo, ambiance);
      } else {
        DockerInfraYaml dockerInfraYaml = (DockerInfraYaml) initializeStepInfo.getInfrastructure();
        String platformSelector =
            dockerInitializeTaskParamsBuilder.getHostedPoolId(dockerInfraYaml.getSpec().getPlatform());
        TaskSelector taskSelector = TaskSelector.newBuilder().setSelector(platformSelector).build();
        taskSelectors.add(taskSelector);
      }
      // TODO: start emitting & processing event for Docker as well
      // emitEvent = true;
    } else if (initializeStepInfo.getInfrastructure().getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      ConnectorConfigDTO connectorConfig =
          ((CIK8InitializeTaskParams) buildSetupTaskParams).getK8sConnector().getConnectorConfig();
      Set<String> delegateSelectors = ((KubernetesClusterConfigDTO) connectorConfig).getDelegateSelectors();

      // Delegate Selector Precedence: 1)Stage ->  2)Pipeline ->  3)Connector .If not specified use any delegate
      if (initializeStepInfo.getDelegateSelectors().getValue() != null) {
        addExternalDelegateSelector(taskSelectors, initializeStepInfo, ambiance);
      } else if (isNotEmpty(delegateSelectors)) {
        List<TaskSelector> selectorList = delegateSelectors.stream()
                                              .map(ds -> TaskSelector.newBuilder().setSelector(ds).build())
                                              .collect(Collectors.toList());
        taskSelectors.addAll(selectorList);
      } else {
        List<TaskSelector> selectorList = delegateSelectors.stream()
                                              .map(ds -> TaskSelector.newBuilder().setSelector(ds).build())
                                              .collect(Collectors.toList());
        taskSelectors.addAll(selectorList);
      }
    }

    TaskData taskData = getTaskData(stepParameters, buildSetupTaskParams);
    String accountId = AmbianceUtils.getAccountId(ambiance);

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);

    HDelegateTask task = (HDelegateTask) StepUtils.prepareDelegateTaskInput(
        accountId, taskData, abstractions, generateLogAbstractions(ambiance));

    return ciDelegateTaskExecutor.queueTask(abstractions, task,
        taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()), new ArrayList<>(),
        executeOnHarnessHostedDelegates, emitEvent, stageExecutionId, generateLogAbstractions(ambiance),
        ambiance.getExpressionFunctorToken(), true, taskSelectors);
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // If any of the responses are in serialized format, deserialize them
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    backgroundTaskUtility.queueJob(() -> saveInitialiseExecutionSweepingOutput(ambiance));

    ResponseData responseData = responseDataMap.entrySet().iterator().next().getValue();
    responseData = serializedResponseDataHelper.deserialize(responseData);
    if (responseData instanceof ErrorNotifyResponseData || responseData instanceof FailureResponseData) {
      String message;
      if (responseData instanceof ErrorNotifyResponseData) {
        if (((InitializeStepInfo) stepParameters.getSpec()).getInfrastructure().getType()
            == Infrastructure.Type.KUBERNETES_DIRECT) {
          message = emptyIfNull(ExceptionUtils.getMessage(exceptionManager.processException(
              new CILiteEngineException(((ErrorNotifyResponseData) responseData).getErrorMessage()))));
        } else {
          message = emptyIfNull(((ErrorNotifyResponseData) responseData).getErrorMessage());
        }
      } else if (responseData instanceof FailureResponseData) {
        message = emptyIfNull(ExceptionUtils.getMessage(exceptionManager.processException(
            new CIStageExecutionException(((FailureResponseData) responseData).getErrorMessage()))));
      } else {
        throw new CIStageExecutionException("Unexpected response received while process CI execution");
      }

      FailureData failureData = FailureData.newBuilder()
                                    .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                    .setLevel(Level.ERROR.name())
                                    .setCode(GENERAL_ERROR.name())
                                    .setMessage(message)
                                    .build();

      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().addFailureData(failureData).build())
          .build();
    }

    CITaskExecutionResponse ciTaskExecutionResponse = (CITaskExecutionResponse) responseData;
    CITaskExecutionResponse.Type type = ciTaskExecutionResponse.getType();
    if (type == CITaskExecutionResponse.Type.K8) {
      return handleK8TaskResponse(ambiance, stepParameters, ciTaskExecutionResponse);
    } else if (type == CITaskExecutionResponse.Type.VM || type == CITaskExecutionResponse.Type.DOCKER) {
      return handleVmTaskResponse(ciTaskExecutionResponse);
    } else {
      throw new CIStageExecutionException(format("Invalid infra type for task response: %s", type));
    }
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepElementParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepElementParameters.getSpec();
    validateFeatureFlags(initializeStepInfo, accountIdentifier);

    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();

    populateStrategyExpansion(initializeStepInfo, ambiance);
    if (EmptyPredicate.isEmpty(principal)) {
      log.info("principal info is null");
      return;
    }

    List<EntityDetail> connectorsEntityDetails =
        getConnectorIdentifiers(initializeStepInfo, accountIdentifier, projectIdentifier, orgIdentifier);

    if (isNotEmpty(connectorsEntityDetails) && ciFeatureFlagService.isEnabled(CIE_ENABLED_RBAC, accountIdentifier)) {
      log.info("validating rbac for account id: {}", accountIdentifier);
      pipelineRbacHelper.checkRuntimePermissions(ambiance, connectorsEntityDetails, true);
    }

    validateConnectors(
        initializeStepInfo, connectorsEntityDetails, accountIdentifier, orgIdentifier, projectIdentifier);
    sanitizeExecution(initializeStepInfo, accountIdentifier);
  }

  private void sanitizeExecution(InitializeStepInfo initializeStepInfo, String accountIdentifier) {
    List<ExecutionWrapperConfig> steps = initializeStepInfo.getExecutionElementConfig().getSteps();
    if (initializeStepInfo.getInfrastructure().getType() == Infrastructure.Type.HOSTED_VM) {
      sanitizationService.validate(steps);
    }
  }

  private void validateConnectors(InitializeStepInfo initializeStepInfo, List<EntityDetail> connectorEntitiesList,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (initializeStepInfo.getInfrastructure().getType() != Infrastructure.Type.HOSTED_VM) {
      return;
    }

    // For hosted VMs, we need to validate whether all the connectors connect via platform or not
    List<ConnectorDetails> connectorDetailsList =
        getConnectorDetails(connectorEntitiesList, accountIdentifier, projectIdentifier, orgIdentifier);
    Set<String> invalidIdentifiers = new HashSet<>();
    for (ConnectorDetails connectorDetails : connectorDetailsList) {
      if (connectorDetails.getExecuteOnDelegate() != null) {
        if (connectorDetails.getExecuteOnDelegate()) {
          invalidIdentifiers.add(connectorDetails.getIdentifier());
        }
      } else {
        log.warn("Connector type: {} has executeOnDelegate set as null", connectorDetails.getConnectorType());
        invalidIdentifiers.add(connectorDetails.getIdentifier());
      }
    }
    if (!isEmpty(invalidIdentifiers)) {
      throw new CIStageExecutionException(format(
          "While using hosted infrastructure, all connectors should be configured to go via the Harness platform instead of via the delegate. "
              + "Please update the connectors: %s to connect via the Harness platform instead. This can be done by "
              + "editing the connector and updating the connectivity to go via the Harness platform.",
          invalidIdentifiers));
    }
  }

  private List<ConnectorDetails> getConnectorDetails(
      List<EntityDetail> entityDetails, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    List<ConnectorDetails> connectorDetailsList = new ArrayList<>();
    BaseNGAccess ngAccess = IntegrationStageUtils.getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    for (EntityDetail entityDetail : entityDetails) {
      if (!EntityType.CONNECTORS.equals(entityDetail.getType())) {
        continue;
      }
      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetailsWithIdentifier(ngAccess, (IdentifierRef) entityDetail.getEntityRef());
      connectorDetailsList.add(connectorDetails);
    }
    return connectorDetailsList;
  }

  private void validateFeatureFlags(InitializeStepInfo initializeStepInfo, String accountIdentifier) {
    if (initializeStepInfo.getInfrastructure().getType() != Infrastructure.Type.HOSTED_VM) {
      return;
    }

    // For hosted VMs, we need to check whether the feature flag is enabled or not
    Boolean isEnabled = ciFeatureFlagService.isEnabled(CIE_HOSTED_VMS, accountIdentifier);
    if (!isEnabled) {
      throw new CIStageExecutionException(
          "Hosted builds are not enabled for this account. Please contact Harness support.");
    }
  }

  private String getLogKey(Ambiance ambiance) {
    return LogStreamingStepClientFactory.getLogBaseKey(ambiance);
  }

  public TaskData getTaskData(
      StepElementParameters stepElementParameters, CIInitializeTaskParams buildSetupTaskParams) {
    long timeout =
        Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
    SerializationFormat serializationFormat = SerializationFormat.KRYO;
    String taskType = TaskType.INITIALIZATION_PHASE.getDisplayName();
    if (buildSetupTaskParams.getType() == DLITE_VM) {
      serializationFormat = SerializationFormat.JSON;
      taskType = TaskType.DLITE_CI_VM_INITIALIZE_TASK.getDisplayName();
    }

    return TaskData.builder()
        .async(true)
        .timeout(timeout + TASK_BUFFER_TIMEOUT_MILLIS)
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .parameters(new Object[] {buildSetupTaskParams})
        .build();
  }

  private void saveInitialiseExecutionSweepingOutput(Ambiance ambiance) {
    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long currentTime = System.currentTimeMillis();

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION));
    if (!optionalSweepingOutput.isFound()) {
      try {
        InitializeExecutionSweepingOutput initializeExecutionSweepingOutput =
            InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(currentTime - startTime).build();
        executionSweepingOutputResolver.consume(
            ambiance, INITIALIZE_EXECUTION, initializeExecutionSweepingOutput, StepOutcomeGroup.STAGE.name());
      } catch (Exception e) {
        log.error("Error while consuming initialize execution sweeping output", e);
      }
    }
  }

  private StepResponse handleK8TaskResponse(
      Ambiance ambiance, StepElementParameters stepElementParameters, CITaskExecutionResponse ciTaskExecutionResponse) {
    K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) ciTaskExecutionResponse;
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepElementParameters.getSpec();

    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long currentTime = System.currentTimeMillis();
    DependencyOutcome dependencyOutcome =
        getK8DependencyOutcome(ambiance, initializeStepInfo, k8sTaskExecutionResponse.getK8sTaskResponse());
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        getPodDetailsOutcome(k8sTaskExecutionResponse.getK8sTaskResponse());
    StepResponse.StepOutcome stepOutcome =
        StepResponse.StepOutcome.builder().name(DEPENDENCY_OUTCOME).outcome(dependencyOutcome).build();

    try {
      executionMetricsService.recordStepExecutionCount(k8sTaskExecutionResponse.getCommandExecutionStatus().name(),
          STEP_STATUS, AmbianceUtils.getAccountId(ambiance), InitializeStepInfo.STEP_TYPE.getType());
      executionMetricsService.recordStepStatusExecutionTime(k8sTaskExecutionResponse.getCommandExecutionStatus().name(),
          (currentTime - startTime) / 1000, STEP_TIME_COUNT, AmbianceUtils.getAccountId(ambiance),
          InitializeStepInfo.STEP_TYPE.getType());
    } catch (Exception ex) {
      log.error(ex.getMessage());
    }
    if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      log.info(
          "LiteEngineTaskStep pod creation task executed successfully with response [{}]", k8sTaskExecutionResponse);
      if (liteEnginePodDetailsOutcome == null) {
        throw new CIStageExecutionException("Failed to get pod local ipAddress details");
      }
      log.info("ip address for pod {} is {}", k8sTaskExecutionResponse.getK8sTaskResponse().getPodName(),
          liteEnginePodDetailsOutcome.getIpAddress());
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(stepOutcome)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(POD_DETAILS_OUTCOME)
                           .group(StepOutcomeGroup.STAGE.name())
                           .outcome(liteEnginePodDetailsOutcome)
                           .build())
          .build();

    } else {
      log.error("LiteEngineTaskStep execution finished with status [{}] and response [{}]",
          k8sTaskExecutionResponse.getCommandExecutionStatus(), k8sTaskExecutionResponse);

      StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.FAILED).stepOutcome(stepOutcome);
      if (k8sTaskExecutionResponse.getErrorMessage() != null) {
        stepResponseBuilder.failureInfo(
            FailureInfo.newBuilder().setErrorMessage(k8sTaskExecutionResponse.getErrorMessage()).build());
      }
      return stepResponseBuilder.build();
    }
  }

  private void populateStrategyExpansion(InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    ExecutionElementConfig executionElement = initializeStepInfo.getExecutionElementConfig();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    List<ExecutionWrapperConfig> expandedExecutionElement = new ArrayList<>();
    Map<String, StrategyExpansionData> strategyExpansionMap = new HashMap<>();

    LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);

    if (licensesWithSummaryDTO == null) {
      throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
    }
    Optional<Integer> maxExpansionLimit = Optional.of(Integer.valueOf(MAXIMUM_EXPANSION_LIMIT));
    if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getEdition() == Edition.FREE
        && ciStagePlanCreationUtils.isHostedInfra(initializeStepInfo.getInfrastructure())) {
      maxExpansionLimit = Optional.of(Integer.valueOf(MAXIMUM_EXPANSION_LIMIT_FREE_ACCOUNT));
    }

    for (ExecutionWrapperConfig config : executionElement.getSteps()) {
      ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo;
      expandedExecutionWrapperInfo = strategyHelper.expandExecutionWrapperConfigFromClass(
          config, maxExpansionLimit, CIAbstractStepNode.class, ambiance);

      expandedExecutionElement.addAll(expandedExecutionWrapperInfo.getExpandedExecutionConfigs());
      strategyExpansionMap.putAll(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData());
    }

    initializeStepInfo.setExecutionElementConfig(
        ExecutionElementConfig.builder().steps(expandedExecutionElement).build());
    IntegrationStageConfigImpl integrationStageConfigImpl =
        (IntegrationStageConfigImpl) initializeStepInfo.getStageElementConfig();
    integrationStageConfigImpl.setExecution(ExecutionElementConfig.builder()
                                                .uuid(integrationStageConfigImpl.getExecution().getUuid())
                                                .steps(expandedExecutionElement)
                                                .build());
    initializeStepInfo.setStageElementConfig(integrationStageConfigImpl);
    initializeStepInfo.setStrategyExpansionMap(strategyExpansionMap);
  }

  private StepResponse handleVmTaskResponse(CITaskExecutionResponse ciTaskExecutionResponse) {
    VmTaskExecutionResponse vmTaskExecutionResponse = (VmTaskExecutionResponse) ciTaskExecutionResponse;
    DependencyOutcome dependencyOutcome = getVmDependencyOutcome(vmTaskExecutionResponse);
    StepResponse.StepOutcome stepOutcome =
        StepResponse.StepOutcome.builder().name(DEPENDENCY_OUTCOME).outcome(dependencyOutcome).build();

    if (vmTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(stepOutcome)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(VM_DETAILS_OUTCOME)
                           .group(StepOutcomeGroup.STAGE.name())
                           .outcome(getVmDetailsOutcome(vmTaskExecutionResponse))
                           .build())
          .build();
    } else {
      log.error("VM initialize step execution finished with status [{}] and response [{}]",
          vmTaskExecutionResponse.getCommandExecutionStatus(), vmTaskExecutionResponse);
      StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.FAILED).stepOutcome(stepOutcome);
      if (vmTaskExecutionResponse.getErrorMessage() != null) {
        stepResponseBuilder.failureInfo(
            FailureInfo.newBuilder().setErrorMessage(vmTaskExecutionResponse.getErrorMessage()).build());
      }
      return stepResponseBuilder.build();
    }
  }

  private VmDetailsOutcome getVmDetailsOutcome(VmTaskExecutionResponse vmTaskExecutionResponse) {
    VmDetailsOutcomeBuilder builder = VmDetailsOutcome.builder()
                                          .ipAddress(vmTaskExecutionResponse.getIpAddress())
                                          .poolDriverUsed(vmTaskExecutionResponse.getPoolDriverUsed());
    if (vmTaskExecutionResponse.getDelegateMetaInfo() == null
        || isEmpty(vmTaskExecutionResponse.getDelegateMetaInfo().getId())) {
      return builder.build();
    }

    return builder.delegateId(vmTaskExecutionResponse.getDelegateMetaInfo().getId()).build();
  }

  private DependencyOutcome getVmDependencyOutcome(VmTaskExecutionResponse vmTaskExecutionResponse) {
    List<ServiceDependency> serviceDependencyList = new ArrayList<>();

    List<VmServiceStatus> serviceStatuses = vmTaskExecutionResponse.getServiceStatuses();
    if (isEmpty(serviceStatuses)) {
      return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
    }

    for (VmServiceStatus serviceStatus : serviceStatuses) {
      ServiceDependency.Status status = ServiceDependency.Status.SUCCESS;
      if (serviceStatus.getStatus() == VmServiceStatus.Status.ERROR) {
        status = ServiceDependency.Status.ERROR;
      }
      serviceDependencyList.add(ServiceDependency.builder()
                                    .identifier(serviceStatus.getIdentifier())
                                    .name(serviceStatus.getName())
                                    .image(serviceStatus.getImage())
                                    .errorMessage(serviceStatus.getErrorMessage())
                                    .status(status.getDisplayName())
                                    .logKeys(Collections.singletonList(serviceStatus.getLogKey()))
                                    .build());
    }
    return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
  }

  private DependencyOutcome getK8DependencyOutcome(
      Ambiance ambiance, InitializeStepInfo stepParameters, CiK8sTaskResponse ciK8sTaskResponse) {
    List<ServiceDefinitionInfo> serviceDefinitionInfos =
        k8InitializeServiceUtils.getServiceInfos(stepParameters.getStageElementConfig());
    List<ServiceDependency> serviceDependencyList = new ArrayList<>();
    if (serviceDefinitionInfos == null) {
      return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
    }

    Map<String, CIContainerStatus> containerStatusMap = new HashMap<>();
    if (ciK8sTaskResponse != null && ciK8sTaskResponse.getPodStatus() != null
        && ciK8sTaskResponse.getPodStatus().getCiContainerStatusList() != null) {
      for (CIContainerStatus containerStatus : ciK8sTaskResponse.getPodStatus().getCiContainerStatusList()) {
        containerStatusMap.put(containerStatus.getName(), containerStatus);
      }
    }

    String logPrefix = getLogPrefix(ambiance);
    for (ServiceDefinitionInfo serviceDefinitionInfo : serviceDefinitionInfos) {
      String logKey = format("%s/serviceId:%s", logPrefix, serviceDefinitionInfo.getIdentifier());
      String containerName = serviceDefinitionInfo.getContainerName();
      if (containerStatusMap.containsKey(containerName)) {
        CIContainerStatus containerStatus = containerStatusMap.get(containerName);

        ServiceDependency.Status status = ServiceDependency.Status.SUCCESS;
        if (containerStatus.getStatus() == CIContainerStatus.Status.ERROR) {
          status = ServiceDependency.Status.ERROR;
        }
        serviceDependencyList.add(ServiceDependency.builder()
                                      .identifier(serviceDefinitionInfo.getIdentifier())
                                      .name(serviceDefinitionInfo.getName())
                                      .image(containerStatus.getImage())
                                      .startTime(containerStatus.getStartTime())
                                      .endTime(containerStatus.getEndTime())
                                      .errorMessage(containerStatus.getErrorMsg())
                                      .status(status.getDisplayName())
                                      .logKeys(Collections.singletonList(logKey))
                                      .build());
      } else {
        serviceDependencyList.add(ServiceDependency.builder()
                                      .identifier(serviceDefinitionInfo.getIdentifier())
                                      .name(serviceDefinitionInfo.getName())
                                      .image(serviceDefinitionInfo.getImage())
                                      .errorMessage("Unknown")
                                      .status(ServiceDependency.Status.ERROR.getDisplayName())
                                      .logKeys(Collections.singletonList(logKey))
                                      .build());
      }
    }
    return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
  }

  private String getLogPrefix(Ambiance ambiance) {
    return LogStreamingStepClientFactory.getLogBaseKey(ambiance, StepCategory.STAGE.name());
  }

  private LiteEnginePodDetailsOutcome getPodDetailsOutcome(CiK8sTaskResponse ciK8sTaskResponse) {
    if (ciK8sTaskResponse != null && ciK8sTaskResponse.getPodStatus() != null) {
      String ip = ciK8sTaskResponse.getPodStatus().getIp();
      String namespace = ciK8sTaskResponse.getPodNamespace();
      return LiteEnginePodDetailsOutcome.builder().ipAddress(ip).namespace(namespace).build();
    }
    return null;
  }

  private List<EntityDetail> getConnectorIdentifiers(
      InitializeStepInfo initializeStepInfo, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    List<EntityDetail> entityDetails = new ArrayList<>();
    // Add git clone connector
    if (!initializeStepInfo.isSkipGitClone()) {
      if (initializeStepInfo.getCiCodebase() == null) {
        throw new CIStageExecutionException("Codebase is mandatory with enabled cloneCodebase flag");
      }

      if (!isHarnessSCM(initializeStepInfo, accountIdentifier)) {
        if (isEmpty(initializeStepInfo.getCiCodebase().getConnectorRef().getValue())) {
          throw new CIStageExecutionException("Git connector is mandatory with enabled cloneCodebase flag");
        }
        entityDetails.add(createEntityDetails(initializeStepInfo.getCiCodebase().getConnectorRef().getValue(),
            accountIdentifier, projectIdentifier, orgIdentifier));
      }
    }

    List<String> connectorRefs =
        IntegrationStageUtils.getStageConnectorRefs(initializeStepInfo.getStageElementConfig());
    if (infrastructure.getType() == Infrastructure.Type.VM || infrastructure.getType() == Infrastructure.Type.DOCKER
        || infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
      if (!isEmpty(connectorRefs)) {
        entityDetails.addAll(
            connectorRefs.stream()
                .map(connectorIdentifier
                    -> createEntityDetails(connectorIdentifier, accountIdentifier, projectIdentifier, orgIdentifier))
                .collect(Collectors.toList()));
      }
      return entityDetails;
    }
    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
    String infraConnectorRef = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
    if (isEmpty(infraConnectorRef)) {
      throw new CIStageExecutionException("Kubernetes connector identifier cannot be empty for the stage.");
    }
    entityDetails.add(createEntityDetails(infraConnectorRef, accountIdentifier, projectIdentifier, orgIdentifier));

    entityDetails.addAll(connectorRefs.stream()
                             .map(connectorIdentifier -> {
                               return createEntityDetails(
                                   connectorIdentifier, accountIdentifier, projectIdentifier, orgIdentifier);
                             })
                             .collect(Collectors.toList()));

    return entityDetails;
  }

  private EntityDetail createEntityDetails(
      String connectorIdentifier, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    return EntityDetail.builder().entityRef(connectorRef).type(EntityType.CONNECTORS).build();
  }

  private boolean isHarnessSCM(InitializeStepInfo initializeStepInfo, String accountIdentifier) {
    return isEmpty(initializeStepInfo.getCiCodebase().getConnectorRef().getValue())
        && ciFeatureFlagService.isEnabled(CODE_ENABLED, accountIdentifier);
  }

  private void addExternalDelegateSelector(
      List<TaskSelector> taskSelectors, InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    List<TaskSelector> selectorList = TaskSelectorYaml.toTaskSelector(initializeStepInfo.getDelegateSelectors());
    if (isNotEmpty(selectorList)) {
      // Add to selectorList also add to sweeping output so that it can be used during cleanup task
      taskSelectors.addAll(selectorList);
      OptionalSweepingOutput optionalSweepingOutput =
          executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(TASK_SELECTORS));
      if (!optionalSweepingOutput.isFound()) {
        try {
          TaskSelectorSweepingOutput taskSelectorSweepingOutput =
              TaskSelectorSweepingOutput.builder().taskSelectors(selectorList).build();
          executionSweepingOutputResolver.consume(
              ambiance, TASK_SELECTORS, taskSelectorSweepingOutput, StepOutcomeGroup.STAGE.name());
        } catch (Exception e) {
          log.error("Error while consuming taskSelector sweeping output", e);
        }
      }
    }
  }

  private void consumeUniqueStepIdentifiers(InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    List<String> uniqueStepIdentifiers =
        IntegrationStageUtils.getStepIdentifiers(initializeStepInfo.getExecutionElementConfig().getSteps());
    if (isNotEmpty(uniqueStepIdentifiers)) {
      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(UNIQUE_STEP_IDENTIFIERS));
      if (!optionalSweepingOutput.isFound()) {
        try {
          UniqueStepIdentifiersSweepingOutput uniqueStepIdentifiersSweepingOutput =
              UniqueStepIdentifiersSweepingOutput.builder().uniqueStepIdentifiers(uniqueStepIdentifiers).build();
          executionSweepingOutputResolver.consume(
              ambiance, UNIQUE_STEP_IDENTIFIERS, uniqueStepIdentifiersSweepingOutput, StepOutcomeGroup.STAGE.name());
        } catch (Exception e) {
          log.error("Error while consuming uniqueIdentifiers sweeping output", e);
        }
      }
    }
  }
}
