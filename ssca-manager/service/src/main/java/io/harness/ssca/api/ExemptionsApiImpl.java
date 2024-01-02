/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ssca.v1.ExemptionsApi;
import io.harness.spec.server.ssca.v1.model.ExemptionInitiatorDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionRequestDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionReviewRequestDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionStatusDTO;
import io.harness.ssca.services.exemption.ExemptionService;
import io.harness.ssca.utils.PageResponseUtils;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.springframework.data.domain.PageRequest;

@NextGenManagerAuth
public class ExemptionsApiImpl implements ExemptionsApi {
  @Inject ExemptionService exemptionService;
  @Override
  public Response createExemptionForArtifact(
      String project, String org, String artifact, @Valid ExemptionRequestDTO body, String harnessAccount) {
    populateInitiationDetails(project, artifact, body);
    return Response.status(Status.CREATED)
        .entity(exemptionService.createExemption(harnessAccount, project, org, artifact, body))
        .build();
  }

  @Override
  public Response createExemptionForProject(
      String org, String project, @Valid ExemptionRequestDTO body, String harnessAccount) {
    populateInitiationDetails(project, body);
    return Response.status(Status.CREATED)
        .entity(exemptionService.createExemption(harnessAccount, project, org, null, body))
        .build();
  }

  @Override
  public Response deleteExemptionForArtifact(
      String org, String project, String exemption, String artifact, String harnessAccount) {
    exemptionService.deleteExemption(harnessAccount, org, project, artifact, exemption);
    return Response.ok().build();
  }

  @Override
  public Response deleteExemptionForProject(String org, String project, String exemption, String harnessAccount) {
    exemptionService.deleteExemption(harnessAccount, org, project, null, exemption);
    return Response.ok().build();
  }

  @Override
  public Response getExemptionForArtifact(
      String org, String project, String exemption, String artifact, String harnessAccount) {
    return Response.ok(exemptionService.getExemption(harnessAccount, org, project, artifact, exemption)).build();
  }

  @Override
  public Response getExemptionForProject(String org, String project, String exemption, String harnessAccount) {
    return Response.ok(exemptionService.getExemption(harnessAccount, org, project, null, exemption)).build();
  }

  @Override
  public Response listExemptionsForProject(String org, String project, String harnessAccount,
      @Min(1L) @Max(1000L) Integer limit, @Min(0L) Integer page, List<ExemptionStatusDTO> status, String artifactId,
      String searchTerm) {
    return PageResponseUtils.getPagedResponse(exemptionService.getExemptions(
        harnessAccount, org, project, artifactId, status, searchTerm, PageRequest.of(page, limit)));
  }

  @Override
  public Response reviewExemptionForArtifact(String org, String project, String exemption, String artifact,
      @Valid ExemptionReviewRequestDTO body, String harnessAccount) {
    return Response.ok(exemptionService.reviewExemption(harnessAccount, project, org, artifact, exemption, body))
        .build();
  }

  @Override
  public Response reviewExemptionForProject(
      String org, String project, String exemption, @Valid ExemptionReviewRequestDTO body, String harnessAccount) {
    return Response.ok(exemptionService.reviewExemption(harnessAccount, project, org, null, exemption, body)).build();
  }

  @Override
  public Response updateExemptionForArtifact(String org, String project, String exemption, String artifact,
      @Valid ExemptionRequestDTO body, String harnessAccount) {
    populateInitiationDetails(project, artifact, body);
    return Response.ok(exemptionService.updateExemption(harnessAccount, project, org, artifact, exemption, body))
        .build();
  }

  @Override
  public Response updateExemptionForProject(
      String org, String project, String exemption, @Valid ExemptionRequestDTO body, String harnessAccount) {
    populateInitiationDetails(project, body);
    return Response.ok(exemptionService.updateExemption(harnessAccount, project, org, null, exemption, body)).build();
  }

  private static void populateInitiationDetails(String project, ExemptionRequestDTO body) {
    populateInitiationDetails(project, null, body);
  }

  private static void populateInitiationDetails(String project, String artifact, ExemptionRequestDTO body) {
    ExemptionInitiatorDTO exemptionInitiatorDTO = body.getExemptionInitiator();
    if (exemptionInitiatorDTO == null) {
      exemptionInitiatorDTO = new ExemptionInitiatorDTO();
    }
    exemptionInitiatorDTO.setArtifactId(artifact);
    exemptionInitiatorDTO.setProjectIdentifier(project);
    body.setExemptionInitiator(exemptionInitiatorDTO);
  }
}