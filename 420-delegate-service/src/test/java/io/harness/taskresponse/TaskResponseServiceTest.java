/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskresponse;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.delegate.beans.scheduler.ExecutionStatus.FAILED;
import static io.harness.delegate.beans.scheduler.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.Status;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.scheduler.InitializeExecutionInfraResponse;
import io.harness.delegate.core.beans.CleanupInfraResponse;
import io.harness.delegate.core.beans.ExecutionInfraInfo;
import io.harness.delegate.core.beans.ExecutionStatus;
import io.harness.delegate.core.beans.ResponseCode;
import io.harness.delegate.core.beans.SetupInfraResponse;
import io.harness.delegate.core.beans.StatusCode;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.taskresponse.event.TaskResponseEventProducer;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import java.nio.charset.Charset;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(DEL)
@RunWith(MockitoJUnitRunner.class)
public class TaskResponseServiceTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String TASK_ID = "taskId";
  private static final String DELEGATE_ID = "delegateId";
  private static final int DURATION = 3;
  private static final String DELEGATE_NAME = "delegateName";
  @Mock private HPersistence persistence;
  @Mock private TaskResponseEventProducer eventProducer;
  @Mock private ExecutionInfrastructureService infraService;
  @Mock private DelegateTaskService taskService;
  @Captor private ArgumentCaptor<TaskResponse> responseCaptor;
  private TaskResponseService underTest;

  @Before
  public void setUp() {
    underTest = new TaskResponseService(persistence, eventProducer, infraService, taskService);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenHandleInitInfraResponseThenOk() {
    final var initResponse = buildSetupInfraResponse(null);
    final var task = buildTask();

    when(infraService.updateDelegateInfo(ACCOUNT_ID, TASK_ID, DELEGATE_ID, DELEGATE_NAME)).thenReturn(true);

    final var actual = underTest.handleInitInfraResponse(initResponse, ACCOUNT_ID, TASK_ID, DELEGATE_ID, task);

    final var expectedLegacyResponse = buildLegacyInitInfraCallback(null);
    verify(infraService).updateDelegateInfo(ACCOUNT_ID, TASK_ID, DELEGATE_ID, DELEGATE_NAME);
    verify(eventProducer).handleTaskResponse(ACCOUNT_ID, TASK_ID, Status.SUCCESS, "");
    // Verify legacy callback
    verify(taskService).handleResponseV2(task, expectedLegacyResponse);
    assertThat(actual).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenHandleInitInfraResponseAndFailedThenSendEventAndOk() {
    final var error = "init failed";
    final var initResponse = buildSetupInfraResponse(error);
    final var task = buildTask();

    final var actual = underTest.handleInitInfraResponse(initResponse, ACCOUNT_ID, TASK_ID, DELEGATE_ID, task);

    final var expectedLegacyResponse = buildLegacyInitInfraCallback(error);
    verify(eventProducer).handleTaskResponse(ACCOUNT_ID, TASK_ID, Status.FAILURE, error);
    verify(taskService).handleResponseV2(task, expectedLegacyResponse);
    assertThat(actual).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenHandleInitInfraResponseAndUpdateInfoFailedThenSendEventAndFailure() {
    final var initResponse = buildSetupInfraResponse(null);
    final var task = buildTask();

    when(infraService.updateDelegateInfo(ACCOUNT_ID, TASK_ID, DELEGATE_ID, DELEGATE_NAME)).thenReturn(false);

    final var actual = underTest.handleInitInfraResponse(initResponse, ACCOUNT_ID, TASK_ID, DELEGATE_ID, task);

    final var expectedLegacyResponse = buildLegacyInitInfraCallback("Failed to update the infrastructure details");
    verify(infraService).updateDelegateInfo(ACCOUNT_ID, TASK_ID, DELEGATE_ID, DELEGATE_NAME);
    verify(eventProducer).handleTaskResponse(ACCOUNT_ID, TASK_ID, Status.SUCCESS, "");
    verify(taskService).handleResponseV2(task, expectedLegacyResponse);
    assertThat(actual).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleSuccessResponse() {
    final var execution = buildExecutionStatus(null);
    final var task = buildTask();
    underTest.handleStatusResponse(ACCOUNT_ID, TASK_ID, execution, DELEGATE_ID, task);

    final var expected = expectedResponse(execution, null);
    verify(persistence).save(responseCaptor.capture(), eq(false));
    assertThat(responseCaptor.getValue()).usingRecursiveComparison().ignoringFields("validUntil").isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleFailureResponse() {
    final var error = "error";
    final var execution = buildExecutionStatus(error);
    final var task = buildTask();
    underTest.handleStatusResponse(ACCOUNT_ID, TASK_ID, execution, DELEGATE_ID, task);

    final var expected = expectedResponse(execution, error);
    verify(persistence).save(responseCaptor.capture(), eq(false));
    assertThat(responseCaptor.getValue()).usingRecursiveComparison().ignoringFields("validUntil").isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenHandleCleanupResponseThenOk() {
    final var cleanupResponse = buildCleanupInfraResponse(null);
    final var task = buildTask();

    final var actual =
        underTest.handleCleanupInfraResponse(cleanupResponse, ACCOUNT_ID, TASK_ID, DELEGATE_ID, task, TASK_ID);

    final var expectedLegacyResponse = buildLegacyCleanupInfraCallback(null);
    verify(eventProducer).handleTaskResponse(ACCOUNT_ID, TASK_ID, Status.SUCCESS, "");
    verify(taskService).handleResponseV2(task, expectedLegacyResponse);
    assertThat(actual).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenHandleCleanupResponseAndFailureThenSendFailureEventAndOk() {
    final var error = "cleanup failed";
    final var cleanupResponse = buildCleanupInfraResponse(error);
    final var task = buildTask();

    final var actual =
        underTest.handleCleanupInfraResponse(cleanupResponse, ACCOUNT_ID, TASK_ID, DELEGATE_ID, task, TASK_ID);

    final var expectedLegacyResponse = buildLegacyCleanupInfraCallback(error);
    verify(eventProducer).handleTaskResponse(ACCOUNT_ID, TASK_ID, Status.FAILURE, error);
    verify(taskService).handleResponseV2(task, expectedLegacyResponse);
    assertThat(actual).isTrue();
  }

  private static TaskResponse expectedResponse(final ExecutionStatus execution, final String error) {
    final var status = error != null ? Status.FAILURE : Status.SUCCESS;
    final var builder = TaskResponse.builder()
                            .uuid(TASK_ID)
                            .accountId(ACCOUNT_ID)
                            .data(execution.getBinaryData().toByteArray())
                            .code(status)
                            .executionTime(java.time.Duration.ofSeconds(DURATION))
                            .createdByDelegateId(DELEGATE_ID);
    if (error != null) {
      builder.errorMessage(error);
    }
    return builder.build();
  }

  private SetupInfraResponse buildSetupInfraResponse(final String error) {
    final var statusCode = error != null ? ResponseCode.RESPONSE_FAILED : ResponseCode.RESPONSE_OK;
    final var builder = SetupInfraResponse.newBuilder()
                            .setResponseCode(statusCode)
                            .setLocation(ExecutionInfraInfo.newBuilder().setDelegateName(DELEGATE_NAME).build());

    if (error != null) {
      builder.setErrorMessage(error);
    }
    return builder.build();
  }

  private ExecutionStatus buildExecutionStatus(final String error) {
    final var statusCode = error != null ? StatusCode.CODE_FAILED : StatusCode.CODE_SUCCESS;
    final var builder = ExecutionStatus.newBuilder()
                            .setCode(statusCode)
                            .setExecutionTime(Duration.newBuilder().setSeconds(DURATION).build())
                            .setBinaryData(ByteString.copyFrom("some data", Charset.defaultCharset()));
    if (error != null) {
      builder.setError(error);
    }
    return builder.build();
  }

  private CleanupInfraResponse buildCleanupInfraResponse(final String error) {
    final var statusCode = error != null ? ResponseCode.RESPONSE_FAILED : ResponseCode.RESPONSE_OK;
    final var builder = CleanupInfraResponse.newBuilder().setResponseCode(statusCode);

    if (error != null) {
      builder.setErrorMessage(error);
    }
    return builder.build();
  }

  // === Legacy Callbacks ===

  private static DelegateTask buildTask() {
    return DelegateTask.builder().uuid(TASK_ID).accountId(ACCOUNT_ID).build();
  }

  private static DelegateTaskResponse buildLegacyInitInfraCallback(final String error) {
    final var status = error != null ? FAILED : SUCCESS;
    return DelegateTaskResponse.builder()
        .response(InitializeExecutionInfraResponse.builder(TASK_ID, status).errorMessage(error).build())
        .accountId(ACCOUNT_ID)
        .build();
  }

  private static DelegateTaskResponse buildLegacyCleanupInfraCallback(final String error) {
    final var status = error != null ? FAILED : SUCCESS;
    return DelegateTaskResponse.builder()
        .response(io.harness.delegate.beans.scheduler.CleanupInfraResponse.builder(TASK_ID, TASK_ID, status)
                      .errorMessage(error)
                      .build())
        .accountId(ACCOUNT_ID)
        .build();
  }
}
