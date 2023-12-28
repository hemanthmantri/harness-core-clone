/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ssca.api;

import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ssca.v1.RemediationApi;
import io.harness.spec.server.ssca.v1.model.CreateTicketRequestBody;
import io.harness.spec.server.ssca.v1.model.EnvironmentInfo;
import io.harness.spec.server.ssca.v1.model.EnvironmentTypeFilter;
import io.harness.spec.server.ssca.v1.model.ExcludeArtifactRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactDeploymentsListingRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactDeploymentsListingResponse;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactDetailsResponse;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactListingRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationArtifactListingResponse;
import io.harness.spec.server.ssca.v1.model.RemediationDetailsResponse;
import io.harness.spec.server.ssca.v1.model.RemediationListingRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationListingResponse;
import io.harness.spec.server.ssca.v1.model.RemediationTrackerCreateRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationTrackerCreateResponseBody;
import io.harness.spec.server.ssca.v1.model.RemediationTrackerUpdateRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationTrackerUpdateResponseBody;
import io.harness.spec.server.ssca.v1.model.RemediationTrackersOverallSummaryResponseBody;
import io.harness.spec.server.ssca.v1.model.SaveResponse;
import io.harness.ssca.services.remediation_tracker.RemediationTrackerService;
import io.harness.ssca.utils.PageResponseUtils;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.core.Response;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@NextGenManagerAuth
public class RemediationTrackerApiImpl implements RemediationApi {
  @Inject RemediationTrackerService remediationTrackerService;

  @Override
  public Response createRemediationTracker(
      String orgId, String projectId, @Valid RemediationTrackerCreateRequestBody body, String harnessAccount) {
    String remediationTrackerId =
        remediationTrackerService.createRemediationTracker(harnessAccount, orgId, projectId, body);
    RemediationTrackerCreateResponseBody response = new RemediationTrackerCreateResponseBody().id(remediationTrackerId);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response updateRemediationTracker(String org, String project, String remediation,
      @Valid RemediationTrackerUpdateRequestBody body, String harnessAccount) {
    String remediationTrackerId =
        remediationTrackerService.updateRemediationTracker(harnessAccount, org, project, remediation, body);
    RemediationTrackerUpdateResponseBody response = new RemediationTrackerUpdateResponseBody().id(remediationTrackerId);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response close(String org, String project, String remediation, String harnessAccount) {
    boolean response = remediationTrackerService.close(harnessAccount, org, project, remediation);
    return Response.ok().entity(new SaveResponse().status(response ? "SUCCESS" : "FAILURE")).build();
  }

  @Override
  public Response excludeArtifact(
      String org, String project, String remediation, @Valid ExcludeArtifactRequestBody body, String harnessAccount) {
    boolean response = remediationTrackerService.excludeArtifact(harnessAccount, org, project, remediation, body);
    return Response.ok().entity(new SaveResponse().status(response ? "SUCCESS" : "FAILURE")).build();
  }

  @Override
  public Response getOverallSummary(String org, String project, String harnessAccount) {
    RemediationTrackersOverallSummaryResponseBody response =
        remediationTrackerService.getOverallSummaryForRemediationTrackers(harnessAccount, org, project);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response listRemediations(String org, String project, @Valid RemediationListingRequestBody body,
      String harnessAccount, @Min(1L) @Max(1000L) Integer limit, String order, @Min(0L) Integer page, String sort) {
    sort = RemediationTrackerApiUtils.getSortFieldMapping(sort);
    Pageable pageable = PageResponseUtils.getPageable(page, limit, sort, order);
    Page<RemediationListingResponse> artifactEntities =
        remediationTrackerService.listRemediations(harnessAccount, org, project, body, pageable);
    return PageResponseUtils.getPagedResponse(artifactEntities);
  }

  @Override
  public Response getRemediationDetails(String org, String project, String remediation, String harnessAccount) {
    RemediationDetailsResponse response =
        remediationTrackerService.getRemediationDetails(harnessAccount, org, project, remediation);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response getArtifactListForRemediation(String org, String project, String remediation,
      @Valid RemediationArtifactListingRequestBody body, String harnessAccount, @Min(1L) @Max(1000L) Integer limit,
      @Min(0L) Integer page) {
    Pageable pageable = PageResponseUtils.getPageable(page, limit);
    Page<RemediationArtifactListingResponse> artifactEntities =
        remediationTrackerService.listRemediationArtifacts(harnessAccount, org, project, remediation, body, pageable);
    return PageResponseUtils.getPagedResponse(artifactEntities);
  }

  @Override
  public Response getArtifactInRemediationDetails(
      String org, String project, String remediation, String artifact, String harnessAccount) {
    RemediationArtifactDetailsResponse response =
        remediationTrackerService.getRemediationArtifactDetails(harnessAccount, org, project, remediation, artifact);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response getDeploymentsListForArtifactInRemediation(String org, String project, String remediation,
      String artifact, @Valid RemediationArtifactDeploymentsListingRequestBody body, String harnessAccount,
      @Min(1L) @Max(1000L) Integer limit, @Min(0L) Integer page) {
    Pageable pageable = PageResponseUtils.getPageable(page, limit);
    Page<RemediationArtifactDeploymentsListingResponse> artifactEntities =
        remediationTrackerService.listRemediationArtifactDeployments(
            harnessAccount, org, project, remediation, artifact, body, pageable);
    return PageResponseUtils.getPagedResponse(artifactEntities);
  }

  @Override
  public Response getEnvironmentListForRemediation(String org, String project, String remediation, String artifact,
      String harnessAccount, EnvironmentTypeFilter envType) {
    List<EnvironmentInfo> response = remediationTrackerService.getAllEnvironmentsInArtifact(
        harnessAccount, org, project, remediation, artifact, envType);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response createTicket(
      String orgId, String projectId, String remediation, @Valid CreateTicketRequestBody body, String harnessAccount) {
    String ticketId = remediationTrackerService.createTicket(projectId, remediation, orgId, body, harnessAccount);
    RemediationTrackerCreateResponseBody response = new RemediationTrackerCreateResponseBody().id(ticketId);
    return Response.ok().entity(response).build();
  }
}
