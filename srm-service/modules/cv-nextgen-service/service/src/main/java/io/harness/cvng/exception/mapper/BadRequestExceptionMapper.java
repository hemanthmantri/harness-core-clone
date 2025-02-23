/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.exception.mapper;

import io.harness.eraro.ErrorCode;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {
  @Context private ResourceInfo resourceInfo;

  @Override
  public Response toResponse(BadRequestException exception) {
    ErrorDTO errorBody = ErrorDTO.newError(Status.ERROR, ErrorCode.INVALID_REQUEST, exception.getMessage());
    return Response.status(Response.Status.BAD_REQUEST).entity(errorBody).type(MediaType.APPLICATION_JSON).build();
  }
}
