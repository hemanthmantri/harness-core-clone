/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskresponse.event;

import static io.harness.eventsframework.EventsFrameworkConstants.TASK_RESPONSE_TOPIC;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELEGATE_TASK_RESPONSE_ENTITY;

import io.harness.delegate.Status;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskStatusCallback;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.mapstruct.protobuf.StandardProtobufMappers;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskResponseEventProducer {
  private final Producer producer;

  @Inject
  public TaskResponseEventProducer(@Named(TASK_RESPONSE_TOPIC) final Producer producer) {
    this.producer = producer;
  }

  public boolean handleTaskResponse(
      final String accountId, final String taskId, final Status status, final String error) {
    try {
      final var callback = TaskStatusCallback.newBuilder()
                               .setAccountId(accountId)
                               .setTaskId(TaskId.newBuilder().setId(taskId).build())
                               .setStatus(status)
                               .setSentAt(StandardProtobufMappers.INSTANCE.mapToTimestamp(Instant.now()))
                               .setError(error)
                               .build();

      final var message =
          Message.newBuilder()
              .putAllMetadata(Map.of("accountId", accountId, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                  DELEGATE_TASK_RESPONSE_ENTITY, EventsFrameworkMetadataConstants.ACTION, CREATE_ACTION))
              .setData(callback.toByteString())
              .build();

      producer.send(message);
    } catch (EventsFrameworkDownException e) {
      log.error(
          "Failed to send TaskResponseEvent to events framework for account {} and task {}: ", accountId, taskId, e);
      return false;
    }
    return true;
  }
}
