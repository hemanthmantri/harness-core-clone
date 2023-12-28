/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler;

import io.harness.delegate.GetTaskStatusRequest;
import io.harness.delegate.GetTaskStatusResponse;
import io.harness.delegate.TaskStatusServiceGrpc.TaskStatusServiceImplBase;
import io.harness.grpc.scheduler.mapper.TaskResponseMapper;
import io.harness.taskresponse.TaskResponseService;

import com.google.inject.Inject;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TaskStatusServiceGrpcImpl extends TaskStatusServiceImplBase {
  private final TaskResponseService responseService;

  @Override
  public void getTaskStatus(
      final GetTaskStatusRequest request, final StreamObserver<GetTaskStatusResponse> responseObserver) {
    if (StringUtils.isEmpty(request.getAccountId()) || !request.hasTaskId()) {
      log.error("accountId or taskId are empty");
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("accountId and taskId are mandatory").asRuntimeException());
    }

    try {
      final var taskResponse = responseService.getTaskResponse(request.getAccountId(), request.getTaskId().getId());
      // Mark response for deletion once consumed
      responseService.deleteResponse(request.getAccountId(), request.getTaskId().getId());

      responseObserver.onNext(TaskResponseMapper.INSTANCE.toProto(taskResponse));
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing submit task request {}.", request.getTaskId().getId(), ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }
}
