/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ssca.services.remediation_tracker;

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
import io.harness.spec.server.ssca.v1.model.RemediationTrackerUpdateRequestBody;
import io.harness.spec.server.ssca.v1.model.RemediationTrackersOverallSummaryResponseBody;
import io.harness.ssca.entities.remediation_tracker.RemediationTrackerEntity;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RemediationTrackerService {
  String createRemediationTracker(
      String accountId, String orgId, String projectId, RemediationTrackerCreateRequestBody body);

  String updateRemediationTracker(String accountId, String orgId, String projectId, String remediationTrackerId,
      RemediationTrackerUpdateRequestBody body);

  RemediationDetailsResponse getRemediationDetails(
      String accountId, String orgId, String projectId, String remediationTrackerId);

  RemediationArtifactDetailsResponse getRemediationArtifactDetails(
      String accountId, String orgId, String projectId, String remediationTrackerId, String artifactId);

  boolean close(String accountId, String orgId, String projectId, String remediationTrackerId);

  boolean excludeArtifact(
      String accountId, String orgId, String projectId, String remediationTrackerId, ExcludeArtifactRequestBody body);

  void updateArtifactsAndEnvironments(RemediationTrackerEntity remediationTracker);

  RemediationTrackerEntity getRemediationTracker(String remediationTrackerId);

  RemediationTrackersOverallSummaryResponseBody getOverallSummaryForRemediationTrackers(
      String accountId, String orgId, String projectId);

  String createTicket(
      String projectId, String remediationTrackerId, String orgId, CreateTicketRequestBody body, String accountId);

  Page<RemediationListingResponse> listRemediations(
      String accountId, String orgId, String projectId, RemediationListingRequestBody body, Pageable pageable);

  Page<RemediationArtifactListingResponse> listRemediationArtifacts(String accountId, String orgId, String projectId,
      String remediationTrackerId, RemediationArtifactListingRequestBody body, Pageable pageable);

  Page<RemediationArtifactDeploymentsListingResponse> listRemediationArtifactDeployments(String accountId, String orgId,
      String projectId, String remediationTrackerId, String artifactId,
      RemediationArtifactDeploymentsListingRequestBody body, Pageable pageable);
  List<EnvironmentInfo> getAllEnvironmentsInArtifact(String accountId, String orgId, String projectId,
      String remediationTrackerId, String artifactId, EnvironmentTypeFilter environmentType);
}
