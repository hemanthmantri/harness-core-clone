/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.service.core.litek8s.ContainerFactory.RESERVED_ADDON_PORT;
import static io.harness.delegate.service.core.litek8s.ContainerFactory.RESERVED_LE_PORT;
import static io.harness.delegate.service.core.util.K8SConstants.DELEGATE_FIELD_MANAGER;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.core.beans.InputData;
import io.harness.delegate.core.beans.K8SInfra;
import io.harness.delegate.core.beans.K8SStep;
import io.harness.delegate.core.beans.K8sExecution;
import io.harness.delegate.service.core.k8s.K8SEnvVar;
import io.harness.delegate.service.core.k8s.K8SSecret;
import io.harness.delegate.service.core.k8s.K8SService;
import io.harness.delegate.service.core.litek8s.mappers.LEShellTypeMapper;
import io.harness.delegate.service.core.util.ApiExceptionLogger;
import io.harness.delegate.service.core.util.K8SResourceHelper;
import io.harness.delegate.service.core.util.PodCreationFailedException;
import io.harness.delegate.service.handlermapping.context.Context;
import io.harness.delegate.service.runners.itfc.Runner;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingClient;
import io.harness.logstreaming.LogStreamingTaskClient;
import io.harness.product.ci.engine.proto.ExecuteStep;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.LiteEngineGrpc;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.utils.TokenUtils;

import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Yaml;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class K8SLiteRunner implements Runner {
  private static final String LOG_SERVICE_TOKEN_VARIABLE = "HARNESS_LOG_SERVICE_TOKEN";
  private static final String LOG_SERVICE_ENDPOINT_VARIABLE = "HARNESS_LOG_SERVICE_ENDPOINT";
  private static final String HARNESS_LOG_PREFIX_VARIABLE = "HARNESS_LOG_PREFIX";

  private static final boolean IS_LOCAL_BIJOU_RUNNER =
      TRUE.toString().equals(System.getenv().get("BIJOU_LOCAL_TEST_MODE"));
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);

  private final int POD_INIT_MAX_WAIT_TIME_IN_SEC = 240;

  private final int MAX_ATTEMPTS = 3;
  private final DelegateConfiguration delegateConfiguration;

  private final CoreV1Api coreApi;
  private final ContainerFactory containerFactory;
  private final SecretsBuilder secretsBuilder;
  private final K8SRunnerConfig config;
  private final InfraCleaner infraCleaner;
  private final LogStreamingClient logStreamingClient;
  private final DelegateLogService delegateLogService;
  private final RunnerK8EventHandler k8EventHandler;

  @Override
  public void init(final String infraId, final InputData infra, final Map<String, char[]> decrypted,
      final Context context) throws Exception {
    log.info("Setting up pod spec");
    ILogStreamingTaskClient logStreamingTaskClient = null;
    try {
      // Step 0 - unpack infra definition. Each runner knows the infra spec it expects
      final var k8sInfra = K8SInfra.parseFrom(infra.getBinaryData());

      // TODO: add image pull secrets support
      // Step 1 - decrypt image pull secrets and create secret resources.
      // pullSecrets need to be decrypted by component which is configured during startup (e.g. runner or core),
      // otherwise we will have chicken & egg problem. E.g. delegate creates pod/container to decrypt secret, but image
      // for it needs secret itself.
      // I think other task secrets are known in advance for entire stage for both CI & CD (I think no real secret
      // expressions or dynamic secrets), this means we can do them during init here or execute step later
      // final var imageSecrets =
      //     Streams
      //         .mapWithIndex(k8sInfra.getInfraSecretsList().stream(),
      //             (secret, index) -> secretsBuilder.createImagePullSecrets(infraId, secret, index))
      //         .collect(toList());

      // Step 1a - Should we decrypt other step secrets here and create resources?
      final var taskSecrets = k8sInfra.getStepsList().stream().collect(groupingBy(
          K8SStep::getId, flatMapping(task -> createTaskSecrets(infraId, task, decrypted, context), toList())));

      final var loggingToken = k8sInfra.getLogToken();

      logStreamingTaskClient = LogStreamingTaskClient.getInstance(
          loggingToken, k8sInfra.getLogPrefix(), logStreamingClient, config.getAccountId(), delegateLogService);
      logStreamingTaskClient.openStream(null);

      final V1Secret loggingSecret = createLoggingSecret(infraId,
          RunnerSetupHelper.fetchLogServiceUrl(config.getLogServiceUrl(), IS_LOCAL_BIJOU_RUNNER), loggingToken,
          k8sInfra.getLogPrefix());

      // Step 1c - TODO: Support certs (i.e. secret files that get mounted as secret volume).
      // Right now these are copied from delegate using special syntax and env vars (complicated)

      // Step 2 - create any other resources like volumes, config maps etc...
      final var protoVolumes = VolumeBuilder.unpackVolumes(k8sInfra.getResourcesList());
      final var volumes = VolumeBuilder.createVolumes(protoVolumes);
      final var volumeMounts = VolumeBuilder.createVolumeMounts(protoVolumes);

      // Step 3 - create service endpoint for LE communication
      final var namespace = config.getNamespace();

      logStreamingTaskClient.log(LogLevel.INFO,
          format("Starting job to create pod %s on %s namespace", K8SResourceHelper.getPodName(infraId), namespace));

      V1Service v1Service =
          K8SService.clusterIp(infraId, namespace, K8SResourceHelper.getPodName(infraId), RESERVED_LE_PORT)
              .create(coreApi);

      // Step 4 - create pod - we don't need to busy wait - maybe LE should send task response as first thing when
      // created?
      final var portMap = new PortMap(RESERVED_ADDON_PORT);
      final V1Pod pod = PodBuilder.createSpec(containerFactory, config, infraId)
                            .withTasks(createContainers(k8sInfra, taskSecrets, loggingSecret, volumeMounts, portMap))
                            .buildPod(k8sInfra, volumes, loggingSecret, portMap);

      log.info("Creating Task Pod with YAML:\n{}", Yaml.dump(pod));

      coreApi.createNamespacedPod(namespace, pod, null, null, DELEGATE_FIELD_MANAGER, "Warn");

      Watch<CoreV1Event> watch =
          k8EventHandler.startAsyncPodEventWatch(namespace, pod.getMetadata().getName(), logStreamingTaskClient);

      K8SResourceHelper.waitUntilPodIsReady(
          coreApi, pod.getMetadata().getName(), namespace, POD_INIT_MAX_WAIT_TIME_IN_SEC);

      // It is important to port forward for local only after service is created
      if (IS_LOCAL_BIJOU_RUNNER) {
        RunnerSetupHelper.portForward(v1Service.getMetadata().getName(), namespace);
      }

      logStreamingTaskClient.log(
          LogLevel.INFO, format("Done creating the task pod %s for %s!!", pod.getMetadata().getName(), infraId));

      if (watch != null) {
        k8EventHandler.stopEventWatch(watch);
      }

      // Step 6 - send response to SaaS
    } catch (ApiException e) {
      log.error("Failed to create the task {}. {}", infraId, ApiExceptionLogger.format(e), e);
    } catch (InvalidProtocolBufferException e) {
      log.error("Failed to parse protobuf data {}", infraId, e);
    } catch (PodCreationFailedException ex) {
      log.error("Failed to create the pod for task {} with error {}", infraId, ex.getMessage());
      throw ex;
    } catch (Exception e) {
      log.error("Failed to create the task {}", infraId, e);
      throw e;
    } finally {
      if (logStreamingTaskClient != null) {
        logStreamingTaskClient.closeStream(null);
      }
    }
  }

  @Override
  public void execute(final String infraId, final String logKey, final InputData taskData, final InputData runnerData,
      Map<String, char[]> decrypted, final Context context) {
    String target =
        RunnerSetupHelper.fetchServiceTarget(infraId, config.getNamespace(), RESERVED_LE_PORT, IS_LOCAL_BIJOU_RUNNER);
    try {
      K8sExecution k8sExecution = K8sExecution.parseFrom(runnerData.getBinaryData());
      var executeStepBuilder = ExecuteStep.newBuilder();
      if (Objects.nonNull(taskData)) {
        executeStepBuilder.setTaskParameters(taskData.getBinaryData());
      }
      if (k8sExecution.hasEntryPoint()) {
        executeStepBuilder.setExecuteCommand(k8sExecution.getEntryPoint().getCommand())
            .setShellType(LEShellTypeMapper.INSTANCE.map(k8sExecution.getEntryPoint().getShellType()));
      }
      if (k8sExecution.getEnvVarOutputsCount() > 0) {
        executeStepBuilder.addAllEnvVarOutputs(
            k8sExecution.getEnvVarOutputsList().stream().collect(Collectors.toList()));
      }

      UnitStep unitStep = UnitStep.newBuilder()
                              .setId(infraId)
                              // set addon port from agent side, so that it's controlled by agent config.
                              .setContainerPort(RESERVED_ADDON_PORT)
                              .setAccountId(context.get(Context.ACCOUNT_ID))
                              .setTaskId(context.get(Context.TASK_ID))
                              .setCallbackToken(k8sExecution.getCallBackToken())
                              .setLogKey(k8sExecution.getLogKey())
                              .setExecuteTask(executeStepBuilder.build())
                              .build();
      ExecuteStepRequest executeStepRequest = ExecuteStepRequest.newBuilder().setStep(unitStep).build();

      String accountKey = delegateConfiguration.getDelegateToken();
      String managerUrl =
          RunnerSetupHelper.fetchManagerUrl(delegateConfiguration.getManagerUrl(), IS_LOCAL_BIJOU_RUNNER);
      String delegateID = context.get(Context.DELEGATE_ID);
      if (isNotEmpty(managerUrl)) {
        managerUrl = managerUrl.replace("/api/", "");
        executeStepRequest = executeStepRequest.toBuilder().setManagerSvcEndpoint(managerUrl).build();
      }
      if (isNotEmpty(accountKey)) {
        executeStepRequest =
            executeStepRequest.toBuilder().setAccountKey(TokenUtils.getDecodedTokenString(accountKey)).build();
      }
      if (isNotEmpty(delegateID)) {
        executeStepRequest = executeStepRequest.toBuilder().setDelegateId(delegateID).build();
      }

      ManagedChannelBuilder managedChannelBuilder = ManagedChannelBuilder.forTarget(target).usePlaintext();
      ManagedChannel channel = managedChannelBuilder.build();

      final ExecuteStepRequest finalExecuteStepRequest = executeStepRequest;
      try {
        RetryPolicy<Object> retryPolicy =
            getRetryPolicy(format("[Retrying failed call to send execution call to pod %s: {}", target),
                format("Failed to send execution to pod %s after retrying {} times", RESERVED_LE_PORT));

        Failsafe.with(retryPolicy).get(() -> {
          LiteEngineGrpc.LiteEngineBlockingStub liteEngineBlockingStub = LiteEngineGrpc.newBlockingStub(channel);
          liteEngineBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS).executeStep(finalExecuteStepRequest);
          return K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
        });

      } finally {
        // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
        // resources the channel should be shut down when it will no longer be used. If it may be used
        // again leave it running.
        channel.shutdownNow();
      }
    } catch (InvalidProtocolBufferException e) {
      log.error("Failed to parse protobuf data for task {}", context.get(Context.TASK_ID), e);
    } catch (Exception e) {
      log.error(
          "Failed to execute task {} on lite engine target {} with err: {}", context.get(Context.TASK_ID), target, e);
    }
  }

  @Override
  public void cleanup(final String infraId, final Context context) {
    try {
      infraCleaner.deletePod(infraId, config.getNamespace());
      infraCleaner.deleteSecrets(infraId, config.getNamespace());
      infraCleaner.deleteServiceEndpoint(infraId, config.getNamespace());
    } catch (ApiException e) {
      log.error("Failed to cleanup the task {}. {}", infraId, ApiExceptionLogger.format(e), e);
    } catch (Exception e) {
      log.error("Failed to cleanup the task {}", infraId, e);
      throw e;
    }
  }

  private V1Secret createLoggingSecret(final String infraId, final String logServiceUri, final String loggingToken,
      final String loggingPrefix) throws ApiException {
    final var secretName = K8SResourceHelper.getSecretName(infraId + "-logging");
    final var namespace = config.getNamespace();
    return K8SSecret.secret(secretName, namespace, infraId)
        .putStringDataItem(LOG_SERVICE_ENDPOINT_VARIABLE, logServiceUri)
        .putStringDataItem(LOG_SERVICE_TOKEN_VARIABLE, loggingToken)
        .putStringDataItem(HARNESS_LOG_PREFIX_VARIABLE, loggingPrefix)
        .create(coreApi);
  }

  @NonNull
  private Stream<V1Secret> createTaskSecrets(
      final String infraId, final K8SStep task, final Map<String, char[]> decrypted, final Context context) {
    return task.getInputSecretsList().stream().map(secret -> {
      return secretsBuilder.createSecret(
          infraId, task.getId(), secret.getScopedSecretId(), decrypted.get(secret.getScopedSecretId()));
    });
  }

  private List<V1Container> createContainers(final K8SInfra k8SInfra, final Map<String, List<V1Secret>> taskSecrets,
      final V1Secret loggingSecret, final List<V1VolumeMount> volumeMounts, final PortMap portMap) {
    return k8SInfra.getStepsList()
        .stream()
        .map(descriptor
            -> createContainer(descriptor, taskSecrets.get(descriptor.getId()), loggingSecret, volumeMounts,
                portMap.getPort(descriptor.getId())))
        .collect(Collectors.toList());
  }

  private V1Container createContainer(final K8SStep k8SStep, final List<V1Secret> secrets, final V1Secret loggingSecret,
      final List<V1VolumeMount> volumeMounts, final int port) {
    secrets.add(loggingSecret);
    return containerFactory.createContainer(k8SStep.getId(), k8SStep.getRuntime(), port)
        .addAllToVolumeMounts(volumeMounts)
        .addAllToEnvFrom(createSecretRefs(secrets))
        .build();
  }

  @NonNull
  private static List<V1EnvFromSource> createSecretRefs(final List<V1Secret> secrets) {
    return secrets.stream().map(K8SEnvVar::fromSecret).collect(toList());
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
