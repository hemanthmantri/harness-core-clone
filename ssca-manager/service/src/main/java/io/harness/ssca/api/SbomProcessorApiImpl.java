/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.annotations.SSCAServiceAuth;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ssca.v1.SbomProcessorApi;
import io.harness.spec.server.ssca.v1.model.EnforceSbomRequestBody;
import io.harness.spec.server.ssca.v1.model.EnforceSbomResponseBody;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.spec.server.ssca.v1.model.SbomProcessResponseBody;
import io.harness.ssca.services.EnforcementStepService;
import io.harness.ssca.services.OrchestrationStepService;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.SSCA)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@SSCAServiceAuth
public class SbomProcessorApiImpl implements SbomProcessorApi {
  @Inject OrchestrationStepService orchestrationStepService;

  @Inject EnforcementStepService enforcementStepService;

  @Override
  public Response enforceSbom(
      String orgIdentifier, String projectIdentifier, @Valid EnforceSbomRequestBody body, String accountId) {
    EnforceSbomResponseBody response =
        enforcementStepService.enforceSbom(accountId, orgIdentifier, projectIdentifier, body);
    return Response.ok().entity(response).build();
  }

  @SneakyThrows
  @Override
  public Response processSbom(
      String orgIdentifier, String projectIdentifier, SbomProcessRequestBody sbomProcessRequestBody, String accountId) {
    SbomProcessResponseBody response = new SbomProcessResponseBody();
    response.setArtifactId(
        orchestrationStepService.processSBOM(accountId, orgIdentifier, projectIdentifier, sbomProcessRequestBody));
    return Response.ok().entity(response).build();
  }
}
