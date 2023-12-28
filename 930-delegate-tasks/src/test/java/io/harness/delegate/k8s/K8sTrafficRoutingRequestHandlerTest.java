/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.trafficrouting.TrafficRoutingResourceCreator;
import io.harness.delegate.k8s.trafficrouting.TrafficRoutingResourceCreatorFactory;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.K8sTrafficRoutingRequest;
import io.harness.delegate.task.k8s.K8sTrafficRoutingResponse;
import io.harness.delegate.task.k8s.client.K8sApiClient;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfigType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.trafficrouting.TrafficRoutingInfoDTO;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sTrafficRoutingRequestHandlerTest extends CategoryTest {
  @Mock ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock K8sTaskHelperBase k8sTaskHelperBase;

  @Mock LogCallback logCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock KubernetesConfig kubernetesConfig;
  @Mock K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock K8sReleaseHandler releaseHandler;
  @Mock IK8sReleaseHistory releaseHistory;
  @Mock IK8sRelease release;
  @Mock TrafficRoutingResourceCreator trafficRoutingResourceCreator;
  @Mock K8sApiClient kubernetesApiClient;
  @Spy @InjectMocks K8sTrafficRoutingRequestHandler k8sTrafficRoutingRequestHandler;
  K8sDelegateTaskParams k8sDelegateTaskParams;
  CommandUnitsProgress commandUnitsProgress;
  final String workingDirectory = "/tmp";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this);

    k8sDelegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    commandUnitsProgress = CommandUnitsProgress.builder().build();

    doReturn(logCallback)
        .when(k8sTaskHelperBase)
        .getLogCallback(eq(logStreamingTaskClient), anyString(), anyBoolean(), eq(commandUnitsProgress));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(
            any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), anyBoolean(), eq(null));
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(any(K8sInfraDelegateConfig.class), anyString(), eq(logCallback));
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), anyString());
    doReturn(10).when(releaseHistory).getAndIncrementLastReleaseNumber();
    doReturn(release).when(releaseHandler).createRelease(any(), anyInt(), any());
    doReturn(release).when(releaseHistory).getLatestRelease();
    doReturn(9).when(release).getReleaseNumber();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testInitWithReleaseHistory() throws Exception {
    testInit(false, true);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testInitWithourReleaseHistory() throws Exception {
    testInit(true, false);
  }

  private void testInit(boolean releaseHistoryIsEmpty, boolean kubernetesConfigSetup) throws Exception {
    final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    final K8sTrafficRoutingRequest k8sTrafficRoutingRequest = K8sTrafficRoutingRequest.builder()
                                                                  .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                                  .releaseName("releaseName")
                                                                  .timeoutIntervalInMin(10)
                                                                  .build();
    on(k8sTrafficRoutingRequestHandler).set("releaseName", "releaseName");

    if (kubernetesConfigSetup) {
      on(k8sTrafficRoutingRequestHandler).set("kubernetesConfig", kubernetesConfig);
    } else {
      on(k8sTrafficRoutingRequestHandler).set("kubernetesConfig", null);
    }

    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);

    doReturn(releaseHistoryIsEmpty).when(releaseHistory).isEmpty();
    k8sTrafficRoutingRequestHandler.init(
        k8sTrafficRoutingRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

    if (!kubernetesConfigSetup) {
      verify(containerDeploymentDelegateBaseHelper)
          .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);
    }

    verify(releaseHandler).getReleaseHistory(any(), eq("releaseName"));
    verify(releaseHistory).isEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testPrepareForTrafficRouting() {
    K8sTrafficRoutingConfig trafficRoutingProvider = K8sTrafficRoutingConfig.builder().build();
    Set<String> availableApis =
        Set.of("networking.istio.io/v1alpha1", "networking.istio.io/v1alpha2", "networking.istio.io/v1alpha3");
    final List<KubernetesResource> resources = new ArrayList<>();
    final IK8sReleaseHistory releaseHistory = mock(IK8sReleaseHistory.class);

    on(k8sTrafficRoutingRequestHandler).set("resources", resources);
    on(k8sTrafficRoutingRequestHandler).set("releaseName", "releaseName");
    on(k8sTrafficRoutingRequestHandler).set("releaseHistory", releaseHistory);
    on(k8sTrafficRoutingRequestHandler).set("kubernetesConfig", kubernetesConfig);

    doReturn("default").when(kubernetesConfig).getNamespace();
    doReturn(availableApis).when(kubernetesApiClient).getApiVersions(any(), any(), any(), any());
    doNothing()
        .when(k8sTrafficRoutingRequestHandler)
        .checkForDestinationNormalization(any(K8sTrafficRoutingConfig.class), eq(logCallback));

    final K8sTrafficRoutingRequest k8sTrafficRoutingRequest = K8sTrafficRoutingRequest.builder()
                                                                  .trafficRoutingConfig(trafficRoutingProvider)
                                                                  .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                                  .build();

    try (MockedStatic<TrafficRoutingResourceCreatorFactory> utilities =
             Mockito.mockStatic(TrafficRoutingResourceCreatorFactory.class)) {
      utilities.when(() -> TrafficRoutingResourceCreatorFactory.create(trafficRoutingProvider))
          .thenReturn(trafficRoutingResourceCreator);

      List<KubernetesResource> tfResources = List.of(KubernetesResource.builder()
                                                         .resourceId(KubernetesResourceId.builder()
                                                                         .name("test-virtual-service")
                                                                         .kind("VirtualService")
                                                                         .namespace("default")
                                                                         .build())
                                                         .value(Map.of("apiVersion", "networking.istio.io/v1alpha3"))
                                                         .build());

      doReturn(tfResources)
          .when(trafficRoutingResourceCreator)
          .createTrafficRoutingResources(anyString(), anyString(), any(), any());

      Optional<TrafficRoutingInfoDTO> optionalTrafficRoutingInfoDTO =
          Optional.of(TrafficRoutingInfoDTO.builder()
                          .name("test-virtual-service")
                          .plural("virtualservices")
                          .version("networking.istio.io/v1alpha3")
                          .build());

      doReturn(optionalTrafficRoutingInfoDTO).when(trafficRoutingResourceCreator).getTrafficRoutingInfo(tfResources);
      Optional<TrafficRoutingInfoDTO> result = k8sTrafficRoutingRequestHandler.prepareForTrafficRouting(
          k8sTrafficRoutingRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

      verify(trafficRoutingResourceCreator).getTrafficRoutingInfo(any());
      assertThat(result).isPresent();
      assertThat(result.get().getName()).isEqualTo("test-virtual-service");
      assertThat(result.get().getPlural()).isEqualTo("virtualservices");
      assertThat(result.get().getVersion()).isEqualTo("networking.istio.io/v1alpha3");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testApplyTrafficRouting() throws Exception {
    final Kubectl client = Kubectl.client("", "");
    on(k8sTrafficRoutingRequestHandler).set("client", client);
    on(k8sTrafficRoutingRequestHandler).set("resources", new ArrayList<>());

    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), anyBoolean(),
            anyBoolean(), any());

    k8sTrafficRoutingRequestHandler.applyTrafficRouting(
        k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

    verify(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), anyBoolean(),
            anyBoolean(), any());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalInvalidRequest() {
    final K8sRollingDeployRequest rollingDeployRequest = K8sRollingDeployRequest.builder().build();

    assertThatThrownBy(()
                           -> k8sTrafficRoutingRequestHandler.executeTaskInternal(rollingDeployRequest,
                               k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalPrepareForTrafficRoutingFailed() throws Exception {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder().type(K8sTrafficRoutingConfigType.CONFIG).build();
    final K8sTrafficRoutingRequest k8sTrafficRoutingRequest = K8sTrafficRoutingRequest.builder()
                                                                  .releaseName("releaseName")
                                                                  .trafficRoutingConfig(k8sTrafficRoutingConfig)
                                                                  .build();
    final RuntimeException thrownException = new RuntimeException("failed");

    doNothing().when(k8sTrafficRoutingRequestHandler).init(any(), any(), any(), any());
    doThrow(thrownException)
        .when(k8sTrafficRoutingRequestHandler)
        .prepareForTrafficRouting(
            k8sTrafficRoutingRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

    assertThatThrownBy(()
                           -> k8sTrafficRoutingRequestHandler.executeTaskInternal(k8sTrafficRoutingRequest,
                               k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isSameAs(thrownException);

    verify(k8sTrafficRoutingRequestHandler, never()).applyTrafficRouting(any(), any(), any());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalUnknownTrafficRoutingConfigType() throws Exception {
    K8sTrafficRoutingConfig trafficRoutingProvider = K8sTrafficRoutingConfig.builder().build();
    final K8sTrafficRoutingRequest k8sTrafficRoutingRequest = K8sTrafficRoutingRequest.builder()
                                                                  .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                                  .releaseName("releaseName")
                                                                  .trafficRoutingConfig(trafficRoutingProvider)
                                                                  .build();
    doNothing().when(k8sTrafficRoutingRequestHandler).init(any(), any(), any(), any());
    assertThatThrownBy(()
                           -> k8sTrafficRoutingRequestHandler.executeTaskInternal(k8sTrafficRoutingRequest,
                               k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalApplyManifestFailed() throws Exception {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder().type(K8sTrafficRoutingConfigType.CONFIG).build();
    final Kubectl client = Kubectl.client("", "");
    final K8sTrafficRoutingRequest k8sTrafficRoutingRequest = K8sTrafficRoutingRequest.builder()
                                                                  .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                                  .releaseName("releaseName")
                                                                  .trafficRoutingConfig(k8sTrafficRoutingConfig)
                                                                  .build();

    on(k8sTrafficRoutingRequestHandler).set("client", client);
    final RuntimeException exception = new RuntimeException("failed");
    doNothing().when(k8sTrafficRoutingRequestHandler).init(any(), any(), any(), any());
    doReturn(Optional.empty())
        .when(k8sTrafficRoutingRequestHandler)
        .prepareForTrafficRouting(any(), any(), any(), any());
    doThrow(exception)
        .when(k8sTaskHelperBase)
        .applyManifests(
            any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), eq(true), eq(true), any());

    assertThatThrownBy(()
                           -> k8sTrafficRoutingRequestHandler.executeTaskInternal(k8sTrafficRoutingRequest,
                               k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isSameAs(exception);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() throws Exception {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder().type(K8sTrafficRoutingConfigType.CONFIG).build();
    final List<KubernetesResource> resources = new ArrayList<>();
    final IK8sReleaseHistory releaseHistory = mock(IK8sReleaseHistory.class);
    final Kubectl client = Kubectl.client("", "");

    on(k8sTrafficRoutingRequestHandler).set("resources", resources);
    on(k8sTrafficRoutingRequestHandler).set("releaseName", "releaseName");
    on(k8sTrafficRoutingRequestHandler).set("releaseHistory", releaseHistory);
    on(k8sTrafficRoutingRequestHandler).set("client", client);

    final K8sTrafficRoutingRequest k8sTrafficRoutingRequest = K8sTrafficRoutingRequest.builder()
                                                                  .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                                  .releaseName("releaseName")
                                                                  .trafficRoutingConfig(k8sTrafficRoutingConfig)
                                                                  .build();

    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(Set.of("networking.istio.io/v1alpha1", "networking.istio.io/v1alpha2", "networking.istio.io/v1alpha3"))
        .when(kubernetesApiClient)
        .getApiVersions(any(), any(), any(), any());
    doNothing()
        .when(k8sTrafficRoutingRequestHandler)
        .checkForDestinationNormalization(any(K8sTrafficRoutingConfig.class), eq(logCallback));

    try (MockedStatic<TrafficRoutingResourceCreatorFactory> utilities =
             Mockito.mockStatic(TrafficRoutingResourceCreatorFactory.class)) {
      utilities.when(() -> TrafficRoutingResourceCreatorFactory.create(k8sTrafficRoutingConfig))
          .thenReturn(trafficRoutingResourceCreator);

      doReturn(List.of(KubernetesResource.builder().build()))
          .when(trafficRoutingResourceCreator)
          .createTrafficRoutingResources(anyString(), anyString(), any(), any(), any(), any());

      Optional<TrafficRoutingInfoDTO> optionalTrafficRoutingInfoDTO =
          Optional.of(TrafficRoutingInfoDTO.builder()
                          .name("test-virtual-service")
                          .plural("virtualservices")
                          .version("networking.istio.io/v1alpha3")
                          .build());

      doReturn(optionalTrafficRoutingInfoDTO).when(trafficRoutingResourceCreator).getTrafficRoutingInfo(any());

      doReturn(true)
          .when(k8sTaskHelperBase)
          .applyManifests(
              any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), anyBoolean(), eq(null));

      K8sDeployResponse response = k8sTrafficRoutingRequestHandler.executeTaskInternal(
          k8sTrafficRoutingRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

      assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
      assertThat(response.getK8sNGTaskResponse()).isInstanceOf(K8sTrafficRoutingResponse.class);
      assertThat(response.getK8sNGTaskResponse()).isNotNull();
      assertThat(((K8sTrafficRoutingResponse) response.getK8sNGTaskResponse()).getInfo())
          .isEqualTo(optionalTrafficRoutingInfoDTO.get());
    }
  }
}
