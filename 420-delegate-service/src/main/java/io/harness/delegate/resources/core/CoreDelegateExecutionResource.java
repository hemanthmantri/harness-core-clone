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
import io.harness.delegate.beans.DelegateTaskResponseV2;
import io.harness.delegate.core.beans.ExecutionStatusResponse;
import io.harness.delegate.core.beans.ResponseCode;
import io.harness.delegate.core.beans.SetupInfraResponse;
import io.harness.delegate.core.beans.StatusCode;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.delegate.task.tasklogging.TaskLogContext;
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
      return responseService.handleInitInfraResponse(response, accountId, executionId, delegateId, task.get())
          ? Response.ok().build()
          : Response.serverError().build();
    } catch (final Exception e) {
      log.error("Exception updating execution infra for account {}, with delegate details {}, for execution {}",
          accountId, delegateId, executionId, e);
      return Response.serverError().build();
    }
  }

  /**
   * This endpoint is used by the delegate to send the execution status back to the platform for runner flows.
   * It should be similar to {@link io.harness.delegate.resources.DelegateTaskResourceV2#updateTaskResponseV2(String,
   * String, String, DelegateTaskResponseV2)}, but currently doesn't support all the same features (mostly related to
   * delegate whitelisting)
   * @param taskId The execution id
   * @param accountId The account id
   * @param delegateId The delegate id
   * @param response The execution status response
   * @return A response code
   */
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

      final var task = taskService.fetchDelegateTask(accountId, taskId);
      if (task.isEmpty()) {
        log.error("Task not found when processing infra setup response from delegate {}", delegateId);
        return Response.serverError().build();
      }
      responseService.handleStatusResponse(accountId, taskId, response.getStatus(), delegateId, task.get());
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

      return responseService.handleCleanupInfraResponse(
                 response, accountId, executionId, delegateId, task.get(), infraRefId)
          ? Response.ok().build()
          : Response.serverError().build();
    } catch (final Exception e) {
      log.error("Exception updating execution infra for account {}, with delegate details {}, for execution {}",
          accountId, delegateId, executionId, e);
      return Response.serverError().build();
    }
  }
}
