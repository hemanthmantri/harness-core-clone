/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskresponse.event;

import io.harness.delegate.TaskStatusCallback;
import io.harness.mapstruct.protobuf.ProtobufMapperConfig;
import io.harness.mapstruct.protobuf.StandardProtobufMappers;
import io.harness.taskresponse.TaskResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(config = ProtobufMapperConfig.class, uses = StandardProtobufMappers.class)
public interface TaskResponseCallbackMapper {
  TaskResponseCallbackMapper INSTANCE = Mappers.getMapper(TaskResponseCallbackMapper.class);

  @Mapping(source = "uuid", target = "taskId.id")
  @Mapping(source = "code", target = "status")
  TaskStatusCallback toCallback(TaskResponse taskResponse);
}
