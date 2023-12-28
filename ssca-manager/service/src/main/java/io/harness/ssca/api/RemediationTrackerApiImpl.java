/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ssca.api;

import static io.harness.ssca.search.framework.Constants.REMEDIATION_TRACKER_CLOSE;
import static io.harness.ssca.search.framework.Constants.REMEDIATION_TRACKER_EDIT;
import static io.harness.ssca.search.framework.Constants.REMEDIATION_TRACKER_RESOURCE;
import static io.harness.ssca.search.framework.Constants.REMEDIATION_TRACKER_VIEW;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ssca.v1.RemediationApi;
import io.harness.spec.server.ssca.v1.model.CreateTicketRequest;
import io.harness.spec.server.ssca.v1.model.EnvironmentInfo;
import io.harness.spec.server.ssca.v1.model.ExcludeArtifactRequest;
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
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.mapper.RemediationTrackerMapper;
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
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_EDIT)
  public Response createRemediationTracker(@OrgIdentifier String orgId, @ProjectIdentifier String projectId,
      @Valid RemediationTrackerCreateRequestBody body, @AccountIdentifier String harnessAccount) {
    String remediationTrackerId =
        remediationTrackerService.createRemediationTracker(harnessAccount, orgId, projectId, body);
    RemediationTrackerCreateResponseBody response = new RemediationTrackerCreateResponseBody().id(remediationTrackerId);
    return Response.ok().entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_EDIT)
  public Response updateRemediationTracker(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String remediation, @Valid RemediationTrackerUpdateRequestBody body,
      @AccountIdentifier String harnessAccount) {
    String remediationTrackerId =
        remediationTrackerService.updateRemediationTracker(harnessAccount, org, project, remediation, body);
    RemediationTrackerUpdateResponseBody response = new RemediationTrackerUpdateResponseBody().id(remediationTrackerId);
    return Response.ok().entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_CLOSE)
  public Response closeRemediationTracker(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String remediation, @AccountIdentifier String harnessAccount) {
    boolean response = remediationTrackerService.close(harnessAccount, org, project, remediation);
    return Response.ok().entity(new SaveResponse().status(response ? "SUCCESS" : "FAILURE")).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_EDIT)
  public Response excludeArtifact(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String remediation, @Valid ExcludeArtifactRequest body,
      @AccountIdentifier String harnessAccount) {
    boolean response = remediationTrackerService.excludeArtifact(harnessAccount, org, project, remediation, body);
    return Response.ok().entity(new SaveResponse().status(response ? "SUCCESS" : "FAILURE")).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_VIEW)
  public Response getOverallSummary(
      @OrgIdentifier String org, @ProjectIdentifier String project, @AccountIdentifier String harnessAccount) {
    RemediationTrackersOverallSummaryResponseBody response =
        remediationTrackerService.getOverallSummaryForRemediationTrackers(harnessAccount, org, project);
    return Response.ok().entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_VIEW)
  public Response listRemediations(@OrgIdentifier String org, @ProjectIdentifier String project,
      @Valid RemediationListingRequestBody body, @AccountIdentifier String harnessAccount,
      @Min(1L) @Max(1000L) Integer limit, String order, @Min(0L) Integer page, String sort) {
    sort = RemediationTrackerApiUtils.getSortFieldMapping(sort);
    Pageable pageable = PageResponseUtils.getPageable(page, limit, sort, order);
    Page<RemediationListingResponse> artifactEntities =
        remediationTrackerService.listRemediations(harnessAccount, org, project, body, pageable);
    return PageResponseUtils.getPagedResponse(artifactEntities);
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_VIEW)
  public Response getRemediationDetails(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String remediation, @AccountIdentifier String harnessAccount) {
    RemediationDetailsResponse response =
        remediationTrackerService.getRemediationDetails(harnessAccount, org, project, remediation);
    return Response.ok().entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_VIEW)
  public Response getArtifactListForRemediation(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String remediation, @Valid RemediationArtifactListingRequestBody body,
      @AccountIdentifier String harnessAccount, @Min(1L) @Max(1000L) Integer limit, @Min(0L) Integer page) {
    Pageable pageable = PageResponseUtils.getPageable(page, limit);
    Page<RemediationArtifactListingResponse> artifactEntities =
        remediationTrackerService.listRemediationArtifacts(harnessAccount, org, project, remediation, body, pageable);
    return PageResponseUtils.getPagedResponse(artifactEntities);
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_VIEW)
  public Response getArtifactInRemediationDetails(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String remediation, String artifact, @AccountIdentifier String harnessAccount) {
    RemediationArtifactDetailsResponse response =
        remediationTrackerService.getRemediationArtifactDetails(harnessAccount, org, project, remediation, artifact);
    return Response.ok().entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_VIEW)
  public Response getDeploymentsListForArtifactInRemediation(@OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String remediation, String artifact,
      @Valid RemediationArtifactDeploymentsListingRequestBody body, @AccountIdentifier String harnessAccount,
      @Min(1L) @Max(1000L) Integer limit, @Min(0L) Integer page) {
    Pageable pageable = PageResponseUtils.getPageable(page, limit);
    Page<RemediationArtifactDeploymentsListingResponse> artifactEntities =
        remediationTrackerService.listRemediationArtifactDeployments(
            harnessAccount, org, project, remediation, artifact, body, pageable);
    return PageResponseUtils.getPagedResponse(artifactEntities);
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_VIEW)
  public Response getEnvironmentListForRemediation(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String remediation, String artifact, @AccountIdentifier String harnessAccount,
      String envType) {
    EnvType env = RemediationTrackerMapper.mapEnvType(envType);
    List<EnvironmentInfo> response = remediationTrackerService.getAllEnvironmentsInArtifact(
        harnessAccount, org, project, remediation, artifact, env);
    return Response.ok().entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = REMEDIATION_TRACKER_RESOURCE, permission = REMEDIATION_TRACKER_EDIT)
  public Response createTicket(@ProjectIdentifier String project, @ResourceIdentifier String remediation,
      @OrgIdentifier String org, @Valid CreateTicketRequest body, @AccountIdentifier String harnessAccount) {
    String ticketId = remediationTrackerService.createTicket(project, remediation, org, body, harnessAccount);
    RemediationTrackerCreateResponseBody response = new RemediationTrackerCreateResponseBody().id(ticketId);
    return Response.ok().entity(response).build();
  }
}
