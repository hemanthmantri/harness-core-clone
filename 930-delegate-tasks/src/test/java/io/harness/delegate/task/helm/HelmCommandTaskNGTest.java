/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.k8s.utils.K8sTaskCleaner;
import io.harness.delegate.task.ManifestDelegateConfigHelper;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.HelmTaskDTO;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.taskcontext.HelmTaskContext;
import io.harness.taskcontext.HelmTaskContextHolder;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
public class HelmCommandTaskNGTest extends CategoryTest {
  @Mock private HelmDeployServiceNG helmDeployServiceNG;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;
  @Mock private ManifestDelegateConfigHelper manifestDelegateConfigHelper;
  @Mock private HelmCommandRequestNG dummyCommandRequest;
  @Mock private KubernetesConfig kubernetesConfig;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback logCallback;
  @Mock private HelmTaskHelperBase helmTaskHelperBase;
  @Mock private K8sTaskCleaner k8sTaskCleaner;
  private HelmCommandTaskNG spyHelmCommandTask;
  private HelmTaskDTO taskDTO = HelmTaskDTO.builder().build();
  @InjectMocks
  private final HelmCommandTaskNG helmCommandTaskNG = new HelmCommandTaskNG(
      DelegateTaskPackage.builder().delegateId("delegateId").data(TaskData.builder().async(false).build()).build(),
      null, notifyResponseData -> {}, () -> true);

  private final ExecutorService executorService = Executors.newFixedThreadPool(1);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    spyHelmCommandTask = spy(helmCommandTaskNG);

    HelmCommandResponseNG ensureHelmInstalledResponse =
        new HelmCommandResponseNG(CommandExecutionStatus.SUCCESS, "Helm3 is installed at [mock]");

    doReturn(logCallback).when(spyHelmCommandTask).getLogCallback(any(), anyString(), anyBoolean(), any());
    doReturn("some string").when(spyHelmCommandTask).getDeploymentMessage(any());
    doNothing().when(logCallback).saveExecutionLog(anyString(), any(), any());
    doReturn(ensureHelmInstalledResponse)
        .when(helmDeployServiceNG)
        .ensureHelmInstalled(any(HelmCommandRequestNG.class));
    when(k8sGlobalConfigService.getOcPath()).thenReturn("/tmp");
    when(helmTaskHelperBase.isHelmLocalRepoSet()).thenReturn(false);
    when(helmTaskHelperBase.getHelmLocalRepositoryPath()).thenReturn("");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testInitPrerequisite() {
    String kubeConfigLocation = ".kube/config";

    doReturn(logCallback).when(dummyCommandRequest).getLogCallback();
    doReturn(kubernetesConfig).when(containerDeploymentDelegateBaseHelper).createKubernetesConfig(any(), any(), any());
    doReturn(kubeConfigLocation).when(containerDeploymentDelegateBaseHelper).createKubeConfig(eq(kubernetesConfig));

    assertThatThrownBy(() -> spyHelmCommandTask.run(dummyCommandRequest)).isInstanceOf(TaskNGDataException.class);

    verify(helmDeployServiceNG, times(1)).ensureHelmInstalled(dummyCommandRequest);
    ArgumentCaptor<String> kubeLocationCaptor = ArgumentCaptor.forClass(String.class);
    verify(dummyCommandRequest, times(1)).setKubeConfigLocation(kubeLocationCaptor.capture());
    assertThat(kubeLocationCaptor.getValue()).isEqualTo(kubeConfigLocation);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunTaskWithInstallCommand() throws Exception {
    HelmInstallCommandRequestNG request = HelmInstallCommandRequestNG.builder().accountId("accountId").build();
    HelmInstallCmdResponseNG deployResponse =
        HelmInstallCmdResponseNG.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(deployResponse).when(helmDeployServiceNG).deploy(request, taskDTO);

    HelmCmdExecResponseNG response = spyHelmCommandTask.run(request);

    verify(helmDeployServiceNG, times(1)).deploy(request, taskDTO);
    verify(k8sTaskCleaner, times(1)).cleanup(any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getHelmCommandResponse()).isSameAs(deployResponse);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunTaskWithRollbackCommand() throws Exception {
    HelmRollbackCommandRequestNG request = HelmRollbackCommandRequestNG.builder().accountId("accountId").build();
    HelmInstallCmdResponseNG rollbackResponse =
        HelmInstallCmdResponseNG.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(rollbackResponse).when(helmDeployServiceNG).rollback(request, taskDTO);
    HelmCmdExecResponseNG response = spyHelmCommandTask.run(request);

    verify(helmDeployServiceNG, times(1)).rollback(request, taskDTO);
    verify(k8sTaskCleaner, times(1)).cleanup(any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getHelmCommandResponse()).isSameAs(rollbackResponse);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunTaskWithReleaseHistoryCommand() {
    HelmReleaseHistoryCommandRequestNG request =
        HelmReleaseHistoryCommandRequestNG.builder().accountId("accountId").build();
    HelmReleaseHistoryCmdResponseNG releaseHistoryResponse =
        HelmReleaseHistoryCmdResponseNG.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(releaseHistoryResponse).when(helmDeployServiceNG).releaseHistory(request);
    HelmCmdExecResponseNG response = spyHelmCommandTask.run(request);

    verify(helmDeployServiceNG, times(1)).releaseHistory(request);
    verify(k8sTaskCleaner, times(1)).cleanup(any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getHelmCommandResponse()).isSameAs(releaseHistoryResponse);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunTaskWithException() throws Exception {
    HelmInstallCommandRequestNG request = HelmInstallCommandRequestNG.builder().accountId("accountId").build();
    doThrow(new IOException("Unable to deploy")).when(helmDeployServiceNG).deploy(request, taskDTO);

    assertThatThrownBy(() -> spyHelmCommandTask.run(request))
        .isInstanceOf(TaskNGDataException.class)
        .getRootCause()
        .hasMessageContaining("Unable to deploy");
    verify(k8sTaskCleaner, times(1)).cleanup(any());
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunTaskWithFailure() throws Exception {
    HelmInstallCommandRequestNG request = HelmInstallCommandRequestNG.builder().accountId("accountId").build();
    HelmInstallCmdResponseNG deployResponse = HelmInstallCmdResponseNG.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                  .output("Error while deploying")
                                                  .build();
    doReturn(deployResponse).when(helmDeployServiceNG).deploy(request, taskDTO);

    HelmCmdExecResponseNG response = spyHelmCommandTask.run(request);
    verify(k8sTaskCleaner, times(1)).cleanup(any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("Error while deploying");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetExecutionCallback() {
    ArgumentCaptor<LogCallback> logCallbackCaptor = ArgumentCaptor.forClass(LogCallback.class);
    doReturn(mock(LogCallback.class)).when(dummyCommandRequest).getLogCallback();

    assertThatThrownBy(() -> spyHelmCommandTask.run(dummyCommandRequest)).isInstanceOf(TaskNGDataException.class);

    verify(dummyCommandRequest, times(2)).setLogCallback(logCallbackCaptor.capture());
  }

  @Test(expected = NotImplementedException.class)
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunWithObjectList() {
    helmCommandTaskNG.run(new Object[] {});
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testTaskContextThreadLocal() throws Exception {
    assertThat(HelmTaskContextHolder.isNull()).isTrue();
    HelmTaskContextHolder.set(HelmTaskContext.builder().build());
    assertThat(HelmTaskContextHolder.isNull()).isFalse();

    HelmInstallCommandRequestNG request = HelmInstallCommandRequestNG.builder()
                                              .k8sInfraDelegateConfig(DirectK8sInfraDelegateConfig.builder().build())
                                              .accountId("accountId")
                                              .build();
    HelmInstallCmdResponseNG deployResponse =
        HelmInstallCmdResponseNG.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    doReturn(deployResponse).when(helmDeployServiceNG).deploy(request, taskDTO);

    HelmCmdExecResponseNG response = spyHelmCommandTask.run(request);

    verify(helmDeployServiceNG, times(1)).deploy(request, taskDTO);
    verify(k8sTaskCleaner, times(1)).cleanup(any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getHelmCommandResponse()).isSameAs(deployResponse);
    assertThat(HelmTaskContextHolder.isNull()).isTrue();
  }
}
