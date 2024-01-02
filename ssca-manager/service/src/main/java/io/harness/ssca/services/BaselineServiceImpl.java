/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.repositories.BaselineRepository;
import io.harness.ssca.beans.BaselineDTO;
import io.harness.ssca.entities.BaselineEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity;

import com.google.inject.Inject;
import javax.ws.rs.NotFoundException;

public class BaselineServiceImpl implements BaselineService {
  @Inject BaselineRepository baselineRepository;

  @Inject ArtifactService artifactService;

  @Override
  public boolean setBaselineForArtifact(
      String accountId, String orgId, String projectId, String artifactId, String tag) {
    if (isNotEmpty(accountId) && isNotEmpty(tag)) {
      ArtifactEntity artifact = artifactService.getLatestArtifact(accountId, orgId, projectId, artifactId, tag);

      if (artifact == null) {
        throw new NotFoundException(
            String.format("Artifact does not exist with fields artifactId [%s] and tag [%s]", artifactId, tag));
      }

      BaselineEntity baselineEntity = BaselineEntity.builder()
                                          .accountIdentifier(accountId)
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .artifactId(artifactId)
                                          .orchestrationId(artifact.getOrchestrationId())
                                          .tag(tag)
                                          .build();

      baselineRepository.upsert(baselineEntity);
      return true;
    }
    return false;
  }

  @Override
  public BaselineDTO getBaselineForArtifact(String accountId, String orgId, String projectId, String artifactId) {
    BaselineEntity baselineEntity = baselineRepository.findOne(accountId, orgId, projectId, artifactId);

    if (baselineEntity == null) {
      throw new NotFoundException(String.format("Baseline for artifact with artifactId [%s] not found", artifactId));
    }

    return BaselineDTO.builder().artifactId(baselineEntity.getArtifactId()).tag(baselineEntity.getTag()).build();
  }
}
