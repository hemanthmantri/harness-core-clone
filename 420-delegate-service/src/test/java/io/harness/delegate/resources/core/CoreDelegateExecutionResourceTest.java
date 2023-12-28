/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources.core;

import static io.harness.rule.OwnerRule.MARKO;

import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.Status;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.scheduler.ExecutionStatus;
import io.harness.delegate.beans.scheduler.InitializeExecutionInfraResponse;
import io.harness.delegate.core.beans.CleanupInfraResponse;
import io.harness.delegate.core.beans.ExecutionInfraInfo;
import io.harness.delegate.core.beans.ExecutionStatusResponse;
import io.harness.delegate.core.beans.ResponseCode;
import io.harness.delegate.core.beans.SetupInfraResponse;
import io.harness.delegate.core.beans.StatusCode;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.taskresponse.TaskResponseService;
import io.harness.taskresponse.event.TaskResponseEventProducer;

import software.wings.jersey.KryoMessageBodyProvider;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.dropwizard.jersey.protobuf.ProtocolBufferMessageBodyProvider;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class CoreDelegateExecutionResourceTest extends JerseyTest {
  private static final String TASK_ID = "taskId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String DELEGATE_ID = "delegateId";
  private static final String INFRA_ID = "infraId";
  private static final String INIT_INFRA_RESPONSE_URL = String.format(
      "/executions/%s/infra-setup/%s?accountId=%s&delegateId=%s", TASK_ID, TASK_ID, ACCOUNT_ID, DELEGATE_ID);
  private static final String EXECUTION_RESPONSE_URL =
      String.format("/executions/%s/status?accountId=%s&delegateId=%s", TASK_ID, ACCOUNT_ID, DELEGATE_ID);
  private static final String CLEANUP_INFRA_RESPONSE_URL = String.format(
      "/executions/%s/infra-cleanup/%s?accountId=%s&delegateId=%s", TASK_ID, INFRA_ID, ACCOUNT_ID, DELEGATE_ID);
  private static final String DELEGATE_NAME = "delegate_name";
  private static final String RUNNER_TYPE = "K8S";
  @Mock private HttpServletRequest httpServletRequest;
  @Mock private DelegateTaskServiceClassic taskServiceClassic;
  @Mock private HPersistence persistence;
  @Mock private TaskResponseEventProducer eventProducer;
  @Mock private ExecutionInfrastructureService infraService;
  @Mock private DelegateTaskService taskService;

  @Override
  protected Application configure() {
    // need to initialize mocks here, MockitoJUnitRunner won't help since this is not @Before, but happens only once per
    // test.
    initMocks(this);
    final ResourceConfig resourceConfig = new ResourceConfig();
    final var responseService = new TaskResponseService(persistence, eventProducer, infraService, taskService);
    resourceConfig.register(new CoreDelegateExecutionResource(taskServiceClassic, responseService, taskService));
    resourceConfig.register(new ProtocolBufferMessageBodyProvider());
    resourceConfig.register(new AbstractBinder() {
      @Override
      protected void configure() {
        bind(httpServletRequest).to(HttpServletRequest.class);
      }
    });
    return resourceConfig;
  }

  @Override
  protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
    return new InMemoryTestContainerFactory();
  }

  @Override
  protected void configureClient(final ClientConfig config) {
    config.register(KryoMessageBodyProvider.class, 0);
    config.register(new ProtocolBufferMessageBodyProvider());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenInitInfraAndNoRequestThen400() {
    final Response actual =
        client()
            .target(INIT_INFRA_RESPONSE_URL)
            .request()
            .post(entity(null, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});
    assertThat(actual.getStatus()).isEqualTo(400);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenInitInfraAndNoDbTaskThen500() {
    final var data = buildInitInfraResponse(ResponseCode.RESPONSE_OK);

    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.empty());

    final Response actual =
        client()
            .target(INIT_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});
    assertThat(actual.getStatus()).isEqualTo(500);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenInitInfraFailsThen200AndSendExecutionFailureCallback() {
    final var data = buildInitInfraResponse(ResponseCode.RESPONSE_FAILED);

    final var task = buildTask();
    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.of(task));

    final Response actual =
        client()
            .target(INIT_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    final var expectedCallback = buildInitInfraCallback(ExecutionStatus.FAILED);

    assertThat(actual.getStatus()).isEqualTo(200);
    verify(taskService).handleResponseV2(task, expectedCallback);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenInitInfraAndNoDbInfraThen500AndExecutionFailedCallback() {
    final var data = buildInitInfraResponse(ResponseCode.RESPONSE_OK);

    final var task = buildTask();
    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.of(task));
    when(infraService.updateDelegateInfo(ACCOUNT_ID, TASK_ID, DELEGATE_ID, DELEGATE_NAME)).thenReturn(false);

    final Response actual =
        client()
            .target(INIT_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    final var expectedCallback = buildFailureInitInfraCallback("Failed to update the infrastructure details");
    assertThat(actual.getStatus()).isEqualTo(500);
    verify(taskService).handleResponseV2(task, expectedCallback);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenInitInfraOkThen200AndExecutionSucceededCallback() {
    final var data = buildInitInfraResponse(ResponseCode.RESPONSE_OK);

    final var task = buildTask();
    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.of(task));
    when(infraService.updateDelegateInfo(ACCOUNT_ID, TASK_ID, DELEGATE_ID, DELEGATE_NAME)).thenReturn(true);

    final Response actual =
        client()
            .target(INIT_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    final var expectedCallback = buildInitInfraCallback(ExecutionStatus.SUCCESS);
    assertThat(actual.getStatus()).isEqualTo(200);
    verify(taskService).handleResponseV2(task, expectedCallback);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenInitInfraAndGenericExceptionThen500() {
    final var data = buildInitInfraResponse(ResponseCode.RESPONSE_OK);

    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenThrow(new RuntimeException());

    final Response actual =
        client()
            .target(INIT_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    assertThat(actual.getStatus()).isEqualTo(500);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenExecuteAndNoRequestThen400() {
    final Response actual =
        client()
            .target(EXECUTION_RESPONSE_URL)
            .request()
            .post(entity(null, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});
    assertThat(actual.getStatus()).isEqualTo(400);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenExecuteOkThen200AndExecutionSucceededCallback() {
    final var data = buildExecuteResponse();

    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.of(buildTask()));

    final Response actual =
        client()
            .target(EXECUTION_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    verify(eventProducer).handleTaskResponse(ACCOUNT_ID, TASK_ID, Status.SUCCESS, "");
    assertThat(actual.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenExecuteAndGenericExceptionThen500() {
    final var data = buildExecuteResponse();

    doThrow(new NullPointerException()).when(eventProducer).handleTaskResponse(any(), any(), any(), any());

    final Response actual =
        client()
            .target(EXECUTION_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    assertThat(actual.getStatus()).isEqualTo(500);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenCleanupInfraAndNoRequestThen400() {
    final Response actual =
        client()
            .target(CLEANUP_INFRA_RESPONSE_URL)
            .request()
            .post(entity(null, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});
    assertThat(actual.getStatus()).isEqualTo(400);
    verifyNoMoreInteractions(infraService);
    verifyNoMoreInteractions(eventProducer);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenCleanupInfraAndNoDbTaskThen500() {
    final var data = buildCleanupInfraResponse(ResponseCode.RESPONSE_OK);

    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.empty());

    final Response actual =
        client()
            .target(CLEANUP_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});
    assertThat(actual.getStatus()).isEqualTo(500);
    verifyNoMoreInteractions(infraService);
    verifyNoMoreInteractions(eventProducer);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenCleanupInfraFailsThen200AndSendExecutionFailureCallback() {
    final var data = buildCleanupInfraResponse(ResponseCode.RESPONSE_FAILED);

    final var task = buildTask();
    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.of(task));

    final Response actual =
        client()
            .target(CLEANUP_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    final var expectedCallback = buildCleanupInfraCallback(ExecutionStatus.FAILED);

    assertThat(actual.getStatus()).isEqualTo(200);
    verify(taskService).handleResponseV2(task, expectedCallback);
    verify(infraService).deleteInfra(ACCOUNT_ID, INFRA_ID);
    verifyNoMoreInteractions(infraService);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenCleanupInfraOkThen200AndExecutionSucceededCallback() {
    final var data = buildCleanupInfraResponse(ResponseCode.RESPONSE_OK);

    final var task = buildTask();
    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.of(task));
    when(infraService.updateDelegateInfo(ACCOUNT_ID, TASK_ID, DELEGATE_ID, DELEGATE_NAME)).thenReturn(true);

    final Response actual =
        client()
            .target(CLEANUP_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    final var expectedCallback = buildCleanupInfraCallback(ExecutionStatus.SUCCESS);
    assertThat(actual.getStatus()).isEqualTo(200);
    verify(taskService).handleResponseV2(task, expectedCallback);
    verify(infraService).deleteInfra(ACCOUNT_ID, INFRA_ID);
    verifyNoMoreInteractions(infraService);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenCleanupInfraAndGenericExceptionThen500() {
    final var data = buildCleanupInfraResponse(ResponseCode.RESPONSE_OK);

    when(eventProducer.handleTaskResponse(ACCOUNT_ID, TASK_ID, Status.SUCCESS, "")).thenThrow(new RuntimeException());
    when(taskService.fetchDelegateTask(ACCOUNT_ID, TASK_ID)).thenReturn(Optional.of(buildTask()));

    final Response actual =
        client()
            .target(CLEANUP_INFRA_RESPONSE_URL)
            .request()
            .post(entity(data, ProtocolBufferMediaType.APPLICATION_PROTOBUF), new GenericType<>() {});

    assertThat(actual.getStatus()).isEqualTo(500);
    verify(infraService).deleteInfra(ACCOUNT_ID, INFRA_ID);
    verifyNoMoreInteractions(infraService);
  }

  private static SetupInfraResponse buildInitInfraResponse(final ResponseCode code) {
    return SetupInfraResponse.newBuilder()
        .setResponseCode(code)
        .setLocation(ExecutionInfraInfo.newBuilder().setDelegateName(DELEGATE_NAME).setRunnerType(RUNNER_TYPE).build())
        .build();
  }

  private static ExecutionStatusResponse buildExecuteResponse() {
    final io.harness.delegate.core.beans.ExecutionStatus status =
        io.harness.delegate.core.beans.ExecutionStatus.newBuilder().setCode(StatusCode.CODE_SUCCESS).build();
    return ExecutionStatusResponse.newBuilder().setStatus(status).build();
  }

  private static CleanupInfraResponse buildCleanupInfraResponse(final ResponseCode code) {
    return CleanupInfraResponse.newBuilder().setResponseCode(code).build();
  }

  private static DelegateTask buildTask() {
    return DelegateTask.builder().uuid(TASK_ID).accountId(ACCOUNT_ID).build();
  }

  private static DelegateTaskResponse buildInitInfraCallback(final ExecutionStatus status) {
    return DelegateTaskResponse.builder()
        .response(InitializeExecutionInfraResponse.builder(TASK_ID, status)
                      .errorMessage(status != ExecutionStatus.SUCCESS ? "" : null)
                      .build())
        .accountId(ACCOUNT_ID)
        .build();
  }

  private static DelegateTaskResponse buildFailureInitInfraCallback(final String errorMessage) {
    return DelegateTaskResponse.builder()
        .response(InitializeExecutionInfraResponse.builder(TASK_ID, ExecutionStatus.FAILED)
                      .errorMessage(errorMessage)
                      .build())
        .accountId(ACCOUNT_ID)
        .build();
  }

  private static DelegateTaskResponse buildCleanupInfraCallback(final ExecutionStatus status) {
    return DelegateTaskResponse.builder()
        .response(io.harness.delegate.beans.scheduler.CleanupInfraResponse.builder(TASK_ID, INFRA_ID, status)
                      .errorMessage(status != ExecutionStatus.SUCCESS ? "" : null)
                      .build())
        .accountId(ACCOUNT_ID)
        .build();
  }
}
