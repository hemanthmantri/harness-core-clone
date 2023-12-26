/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import static io.harness.cvng.core.beans.params.ProjectParams.fromProjectPathParams;
import static io.harness.cvng.core.beans.params.ProjectParams.fromResourcePathParams;
import static io.harness.cvng.core.services.CVNextGenConstants.ERROR_BUDGET_BURN_DOWN_PROJECT_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.RESOURCE_IDENTIFIER_PATH;

import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.beans.params.ResourcePathParams;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetBurnDownDTO;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetBurnDownResponse;
import io.harness.cvng.servicelevelobjective.services.api.ErrorBudgetBurnDownService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.ng.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import retrofit.http.Body;

@Api("error-budget-burn-down")
@Path(ERROR_BUDGET_BURN_DOWN_PROJECT_PATH)
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CV)
public class ErrorBudgetBurnDownResource {
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Inject private ErrorBudgetBurnDownService errorBudgetBurnDownService;

  @POST
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @Consumes("application/json")
  @ApiOperation(value = "saves error budget burn down", nickname = "saveErrorBudgetBurnDown")
  public RestResponse<ErrorBudgetBurnDownResponse> saveErrorBudgetBurnDown(
      @Valid @BeanParam ProjectPathParams projectPathParams,
      @NotNull @Valid @Body ErrorBudgetBurnDownDTO errorBudgetBurnDownDTO) {
    ProjectParams projectParams = fromProjectPathParams(projectPathParams);
    return new RestResponse<>(
        serviceLevelObjectiveV2Service.saveErrorBudgetBurnDown(projectParams, errorBudgetBurnDownDTO));
  }

  @GET
  @Timed
  @NextGenManagerAuth
  @ExceptionMetered
  @ApiOperation(value = "get error budget burn down", nickname = "getErrorBudgetBurnDown")
  @Path(RESOURCE_IDENTIFIER_PATH)
  public RestResponse<PageResponse<ErrorBudgetBurnDownDTO>> getErrorBudgetBurnDown(
      @Valid @BeanParam ResourcePathParams resourcePathParams, @BeanParam PageParams pageParams) {
    ProjectParams projectParams = fromResourcePathParams(resourcePathParams);
    return new RestResponse<>(
        serviceLevelObjectiveV2Service.get(projectParams, resourcePathParams.getIdentifier(), pageParams));
  }

  @DELETE
  @Timed
  @Path("/{uuid}")
  @NextGenManagerAuth
  @ExceptionMetered
  @Consumes("application/json")
  @ApiOperation(value = "delete error budget burn down", nickname = "deleteErrorBudgetBurnDown")
  public RestResponse<Boolean> deleteErrorBudgetBurnDown(
      @ApiParam(required = true) @NotNull @PathParam("uuid") @ResourceIdentifier String uuid) {
    return new RestResponse<>(errorBudgetBurnDownService.deleteErrorBudgetBurnDown(uuid));
  }
}
