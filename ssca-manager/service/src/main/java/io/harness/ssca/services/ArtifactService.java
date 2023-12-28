/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDetailResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse;
import io.harness.spec.server.ssca.v1.model.PipelineInfo;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.SbomDTO;
import io.harness.ssca.beans.remediation_tracker.PatchedPendingArtifactEntitiesResult;
import io.harness.ssca.entities.ArtifactEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public interface ArtifactService {
  // TODO: Migrate to getLatestArtifact
  ArtifactEntity getArtifactFromSbomPayload(
      String accountId, String orgIdentifier, String projectIdentifier, SbomProcessRequestBody body, SbomDTO sbomDTO);

  Optional<ArtifactEntity> getArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String orchestrationId);

  Optional<ArtifactEntity> getArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, Sort sort);

  String getArtifactName(String accountId, String orgIdentifier, String projectIdentifier, String artifactId);

  ArtifactEntity getArtifactByCorrelationId(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactCorrelationId);

  ArtifactEntity getLatestArtifactByImageNameAndTag(
      String accountId, String orgIdentifier, String projectIdentifier, String imageName, String tag);

  ArtifactEntity getLatestArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String tag);

  ArtifactEntity getLatestArtifact(String accountId, String orgIdentifier, String projectIdentifier, String artifactId);

  ArtifactDetailResponse getArtifactDetails(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String tag);

  PipelineInfo getPipelineInfo(
      String accountId, String orgIdentifier, String projectIdentifier, ArtifactEntity artifact);
  String generateArtifactId(String registryUrl, String name);

  void saveArtifactAndInvalidateOldArtifact(ArtifactEntity artifact);

  void saveArtifact(ArtifactEntity artifact);

  Page<ArtifactListingResponse> listLatestArtifacts(
      String accountId, String orgIdentifier, String projectIdentifier, Pageable pageable);

  Page<ArtifactListingResponse> listArtifacts(String accountId, String orgIdentifier, String projectIdentifier,
      ArtifactListingRequestBody body, Pageable pageable);

  List<PatchedPendingArtifactEntitiesResult> listDeployedArtifactsFromIdsWithCriteria(String accountId,
      String orgIdentifier, String projectIdentifier, Set<String> artifactIds, List<String> orchestrationIds);
  Set<String> getDistinctArtifactIds(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> orchestrationIds);

  Page<ArtifactComponentViewResponse> getArtifactComponentView(String accountId, String orgIdentifier,
      String projectIdentifier, String artifactId, String tag, ArtifactComponentViewRequestBody filterBody,
      Pageable pageable);

  Page<ArtifactDeploymentViewResponse> getArtifactDeploymentView(String accountId, String orgIdentifier,
      String projectIdentifier, String artifactId, String tag, ArtifactDeploymentViewRequestBody filterBody,
      Pageable pageable);

  void updateArtifactEnvCount(ArtifactEntity artifact, EnvType envType, long count);
  ArtifactEntity getLastGeneratedArtifactFromTime(
      String accountId, String orgId, String projectId, String artifactId, Instant time);
}
