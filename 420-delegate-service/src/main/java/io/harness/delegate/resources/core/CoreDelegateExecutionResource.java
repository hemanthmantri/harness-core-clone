/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources.core;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.scheduler.CleanupInfraResponse;
import io.harness.delegate.beans.scheduler.ExecutionStatus;
import io.harness.delegate.beans.scheduler.InitializeExecutionInfraResponse;
import io.harness.delegate.core.beans.ExecutionStatusResponse;
import io.harness.delegate.core.beans.ResponseCode;
import io.harness.delegate.core.beans.SetupInfraResponse;
import io.harness.delegate.core.beans.StatusCode;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.annotations.DelegateAuth;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.taskresponse.TaskResponseService;

import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.swagger.annotations.Api;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("/executions")
@Path("/executions")
@Consumes(MediaType.APPLICATION_JSON)
@Scope(DELEGATE)
@Slf4j
@OwnedBy(DEL)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CoreDelegateExecutionResource {
  private final DelegateTaskServiceClassic delegateTaskServiceClassic;
  private final ExecutionInfrastructureService infraService;
  private final TaskResponseService responseService;
  private final DelegateTaskService taskService;

  @DelegateAuth
  @GET
  @Path("{executionId}/payload")
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Timed
  @ExceptionMetered
  public Response acquireRequestPayload(@PathParam("executionId") final String taskId,
      @QueryParam("accountId") @NotEmpty final String accountId, @QueryParam("delegateId") final String delegateId,
      @QueryParam("delegateInstanceId") final String delegateInstanceId) {
    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      final var optionalDelegateTask =
          delegateTaskServiceClassic.acquireTask(accountId, delegateId, taskId, delegateInstanceId);
      if (optionalDelegateTask.isEmpty()) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      return Response.ok(optionalDelegateTask.get()).build();
    } catch (final Exception e) {
      log.error("Exception serializing task {} data ", taskId, e);
      return Response.serverError().build();
    }
  }

  @DelegateAuth
  @POST
  @Path("{executionId}/infra-setup/{infraId}")
  @Consumes(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Timed
  @ExceptionMetered
  public Response handleSetupInfraResponse(@PathParam("executionId") final String executionId,
      @PathParam("infraId") final String infraId, @QueryParam("accountId") @NotEmpty final String accountId,
      @QueryParam("delegateId") final String delegateId, final SetupInfraResponse response) {
    try (AutoLogContext ignore1 = new ExecutionLogContext(executionId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (response.getResponseCode() == ResponseCode.RESPONSE_UNKNOWN) {
        log.warn("Unknown init infra response from delegate {} for execution {}", delegateId, executionId);
        return Response.status(BAD_REQUEST).build();
      }

      final var task = taskService.fetchDelegateTask(accountId, executionId);
      if (task.isEmpty()) {
        log.error("Task not found when processing infra setup response from delegate {}", delegateId);
        return Response.serverError().build();
      }

      if (response.getResponseCode() != ResponseCode.RESPONSE_OK) {
        log.error("Error response from delegate {} for execution {}. {}", delegateId, executionId,
            response.getErrorMessage());
        final var callbackResponse =
            InitializeExecutionInfraResponse.builder(task.get().getUuid(), ExecutionStatus.FAILED)
                .errorMessage(response.getErrorMessage())
                .build();
        handleResponse(task.get(), callbackResponse);
        return Response.ok().build();
      }

      final var updated = infraService.updateDelegateInfo(
          accountId, task.get().getUuid(), delegateId, response.getLocation().getDelegateName());

      if (!updated) {
        log.error("Error updating delegate info for account {} and execution {}", accountId, executionId);
        final var callbackResponse =
            InitializeExecutionInfraResponse.builder(task.get().getUuid(), ExecutionStatus.FAILED)
                .errorMessage("Failed to update the infrastructure details")
                .build();
        handleResponse(task.get(), callbackResponse);
        return Response.serverError().build();
      }

      final var callbackResponse =
          InitializeExecutionInfraResponse.builder(task.get().getUuid(), ExecutionStatus.SUCCESS).build();
      handleResponse(task.get(), callbackResponse);
      return Response.ok().build();
    } catch (final Exception e) {
      log.error("Exception updating execution infra for account {}, with delegate details {}, for execution {}",
          accountId, delegateId, executionId, e);
      return Response.serverError().build();
    }
  }

  @DelegateAuth
  @POST
  @Path("{executionId}/status")
  @Consumes(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Timed
  @ExceptionMetered
  public Response handleExecutionResponse(@PathParam("executionId") final String taskId,
      @QueryParam("accountId") @NotEmpty final String accountId, @QueryParam("delegateId") final String delegateId,
      final ExecutionStatusResponse response) {
    try (AutoLogContext ignore1 = new ExecutionLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (!response.hasStatus() || response.getStatus().getCode() == StatusCode.CODE_UNKNOWN) {
        log.warn("Unknown execute response from delegate {} for execution {}", delegateId, taskId);
        // Don't send the callback, let the client retry
        return Response.status(BAD_REQUEST).build();
      }

      responseService.handleResponse(accountId, taskId, response.getStatus(), delegateId);
      return Response.ok().build();
    } catch (final Exception e) {
      log.error("Exception handling execution response for account {}, with delegate {}, for execution {}", accountId,
          delegateId, taskId, e);
      return Response.serverError().build();
    }
  }

  @DelegateAuth
  @POST
  @Path("{executionId}/infra-cleanup/{infraId}")
  @Consumes(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Timed
  @ExceptionMetered
  public Response handleCleanupInfraResponse(@PathParam("executionId") final String executionId,
      @PathParam("infraId") final String infraRefId, @QueryParam("accountId") @NotEmpty final String accountId,
      @QueryParam("delegateId") final String delegateId,
      final io.harness.delegate.core.beans.CleanupInfraResponse response) {
    try (AutoLogContext ignore1 = new ExecutionLogContext(executionId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (response.getResponseCode() == ResponseCode.RESPONSE_UNKNOWN) {
        log.warn("Unknown cleanup infra response from delegate {} for execution {}", delegateId, executionId);
        return Response.status(BAD_REQUEST).build();
      }

      final var task = taskService.fetchDelegateTask(accountId, executionId);
      if (task.isEmpty()) {
        log.error("Task not found when processing infra setup response from delegate {}", delegateId);
        return Response.serverError().build();
      }

      if (response.getResponseCode() != ResponseCode.RESPONSE_OK) {
        log.error("Error response from delegate {} for execution {}. {}", delegateId, executionId,
            response.getErrorMessage());
        final var callbackResponse = CleanupInfraResponse.builder(executionId, infraRefId, ExecutionStatus.FAILED)
                                         .errorMessage(response.getErrorMessage())
                                         .build();
        handleResponse(task.get(), callbackResponse);
        return Response.ok().build();
      }

      final var callbackResponse =
          CleanupInfraResponse.builder(executionId, infraRefId, ExecutionStatus.SUCCESS).build();
      handleResponse(task.get(), callbackResponse);
      return Response.ok().build();
    } catch (final Exception e) {
      log.error("Exception updating execution infra for account {}, with delegate details {}, for execution {}",
          accountId, delegateId, executionId, e);
      return Response.serverError().build();
    } finally {
      final var deleted = infraService.deleteInfra(accountId, infraRefId);
      if (!deleted) {
        log.warn("Problem deleting infra for account {} and task {}", accountId, infraRefId);
      }
    }
  }

  private void handleResponse(final DelegateTask task, final DelegateResponseData response) {
    taskService.handleResponseV2(
        task, DelegateTaskResponse.builder().response(response).accountId(task.getAccountId()).build());
  }
}
