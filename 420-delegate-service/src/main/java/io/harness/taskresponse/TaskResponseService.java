/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskresponse;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateTask;
import io.harness.delegate.Status;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.SerializedResponseData;
import io.harness.delegate.beans.scheduler.CleanupInfraResponse;
import io.harness.delegate.beans.scheduler.InitializeExecutionInfraResponse;
import io.harness.delegate.core.beans.ExecutionStatus;
import io.harness.delegate.core.beans.ResponseCode;
import io.harness.delegate.core.beans.SetupInfraResponse;
import io.harness.delegate.core.beans.StatusCode;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.logging.AutoLogContext;
import io.harness.mapstruct.protobuf.StandardProtobufMappers;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.taskresponse.TaskResponse.TaskResponseKeys;
import io.harness.taskresponse.event.TaskResponseEventProducer;

import com.google.inject.Inject;
import com.google.protobuf.Duration;
import dev.morphia.query.Query;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TaskResponseService {
  private final HPersistence persistence;
  private final TaskResponseEventProducer eventProducer;
  private final ExecutionInfrastructureService infraService;
  private final DelegateTaskService taskService;

  public boolean handleInitInfraResponse(final SetupInfraResponse response, final String accountId, final String taskId,
      final String delegateId, final DelegateTask task) {
    final var code = mapCode(response.getResponseCode());

    // In case of init failing, acknowledge the failure to delegate and send failure response to task submitter
    if (response.getResponseCode() != ResponseCode.RESPONSE_OK) {
      log.error("Error response from delegate {} for execution {}. {}", delegateId, taskId, response.getErrorMessage());
      final var callbackResponse =
          InitializeExecutionInfraResponse
              .builder(task.getUuid(), io.harness.delegate.beans.scheduler.ExecutionStatus.FAILED)
              .errorMessage(response.getErrorMessage())
              .build();
      sendResponse(accountId, taskId, code, response.getErrorMessage(), task, callbackResponse);
      return true;
    }

    final var updated =
        infraService.updateDelegateInfo(accountId, taskId, delegateId, response.getLocation().getDelegateName());

    if (!updated) {
      log.error("Error updating delegate info for account {} and execution {}", accountId, taskId);
      final var callbackResponse =
          InitializeExecutionInfraResponse.builder(taskId, io.harness.delegate.beans.scheduler.ExecutionStatus.FAILED)
              .errorMessage("Failed to update the infrastructure details")
              .build();
      sendResponse(accountId, taskId, code, response.getErrorMessage(), task, callbackResponse);
      return false;
    }

    final var callbackResponse =
        InitializeExecutionInfraResponse.builder(taskId, io.harness.delegate.beans.scheduler.ExecutionStatus.SUCCESS)
            .build();
    sendResponse(accountId, taskId, code, response.getErrorMessage(), task, callbackResponse);
    return true;
  }

  public void handleStatusResponse(final String accountId, final String taskId, final ExecutionStatus status,
      final String delegateId, final DelegateTask task) {
    try (AutoLogContext ignore = new ExecutionLogContext(taskId, OVERRIDE_ERROR)) {
      final var code = mapCode(status.getCode());
      final var data = getResponseData(status);
      storeResponse(accountId, taskId, data, code, status.getError(), status.getExecutionTime(), delegateId);

      final var legacyResponse = SerializedResponseData.builder().data(data).build();
      sendResponse(accountId, taskId, code, status.getError(), task, legacyResponse);
    }
  }

  public boolean handleCleanupInfraResponse(final io.harness.delegate.core.beans.CleanupInfraResponse response,
      final String accountId, final String taskId, final String delegateId, final DelegateTask task,
      final String infraRefId) {
    try {
      final var code = mapCode(response.getResponseCode());

      if (response.getResponseCode() != ResponseCode.RESPONSE_OK) {
        log.error(
            "Error response from delegate {} for execution {}. {}", delegateId, taskId, response.getErrorMessage());
        final var callbackResponse =
            CleanupInfraResponse.builder(taskId, infraRefId, io.harness.delegate.beans.scheduler.ExecutionStatus.FAILED)
                .errorMessage(response.getErrorMessage())
                .build();
        sendResponse(accountId, taskId, code, response.getErrorMessage(), task, callbackResponse);
      } else {
        final var callbackResponse =
            CleanupInfraResponse
                .builder(taskId, infraRefId, io.harness.delegate.beans.scheduler.ExecutionStatus.SUCCESS)
                .build();
        sendResponse(accountId, taskId, code, response.getErrorMessage(), task, callbackResponse);
      }
      return true;
    } finally {
      final var deleted = infraService.deleteInfra(accountId, infraRefId);
      if (!deleted) {
        log.warn("Problem deleting infra for account {} and task {}", accountId, infraRefId);
      }
    }
  }

  public TaskResponse getTaskResponse(final String accountId, final String taskId) {
    final var response = findResponse(accountId, taskId).first();
    if (response == null) {
      throw new IllegalArgumentException("TaskResponse not found for task " + taskId);
    }
    return response;
  }

  /**
   * We don't want to hard delete the response as the client might want to retry. We should just mark it for deletion in
   * 5 minutes.
   * @param accountId account id
   * @param taskId task id
   * @return true if the response was marked for deletion, false if it was not found
   */
  public boolean deleteResponse(final String accountId, final String taskId) {
    final var updateOperation =
        persistence.createUpdateOperations(TaskResponse.class)
            .set(TaskResponseKeys.accountId, accountId)
            .set(TaskResponseKeys.uuid, taskId)
            .set(TaskResponseKeys.validUntil, new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)));
    return persistence.update(findResponse(accountId, taskId), updateOperation).getUpdatedExisting();
  }

  private String storeResponse(final String accountId, final String taskId, final byte[] response, final Status code,
      final String errorMessage, final Duration executionTime, final String delegateId) {
    try (AutoLogContext ignore = new ExecutionLogContext(taskId, OVERRIDE_ERROR)) {
      final var builder = TaskResponse.builder()
                              .uuid(taskId)
                              .accountId(accountId)
                              .data(response)
                              .code(code)
                              .executionTime(StandardProtobufMappers.INSTANCE.mapDuration(executionTime))
                              .createdByDelegateId(delegateId);

      if (isNotBlank(errorMessage)) {
        builder.errorMessage(errorMessage);
      }
      return persistence.save(builder.build(), false);
    }
  }

  private Query<TaskResponse> findResponse(final String accountId, final String taskId) {
    return persistence.createQuery(TaskResponse.class)
        .filter(TaskResponseKeys.accountId, accountId)
        .filter(TaskResponseKeys.uuid, taskId);
  }

  private static byte[] getResponseData(final io.harness.delegate.core.beans.ExecutionStatus status) {
    if (status.hasProtoData()) {
      throw new IllegalArgumentException("Proto response data not supported yet");
    }
    return status.hasBinaryData() ? status.getBinaryData().toByteArray() : null;
  }

  private static Status mapCode(final StatusCode code) {
    switch (code) {
      case CODE_SUCCESS:
        return Status.SUCCESS;
      case CODE_FAILED:
        return Status.FAILURE;
      case CODE_TIMEOUT:
        return Status.TIMEOUT;
      case CODE_UNKNOWN:
      default:
        throw new IllegalArgumentException("Unknown status code " + code);
    }
  }

  private static Status mapCode(final ResponseCode code) {
    switch (code) {
      case RESPONSE_OK:
        return Status.SUCCESS;
      case RESPONSE_FAILED:
        return Status.FAILURE;
      case RESPONSE_UNKNOWN:
      default:
        throw new IllegalArgumentException("Unknown status code " + code);
    }
  }

  private void sendResponse(final String accountId, final String taskId, final Status code, final String error,
      final DelegateTask task, final DelegateResponseData legacyResponse) {
    // Send events framework response
    eventProducer.handleTaskResponse(accountId, taskId, code, error);
    // Send legacy response
    taskService.handleResponseV2(
        task, DelegateTaskResponse.builder().response(legacyResponse).accountId(task.getAccountId()).build());
  }
}
