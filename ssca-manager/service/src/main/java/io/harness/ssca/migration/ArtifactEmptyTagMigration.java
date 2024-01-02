/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.repositories.ArtifactRepository;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity.ArtifactEntityKeys;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class ArtifactEmptyTagMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  @Inject ArtifactRepository artifactRepository;
  private static final String DEBUG_LOG = "EMPTY_TAG_FIELD_MIGRATION: ";

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Starting migration to update empty tag field in ssca artifacts");
    Criteria criteria = Criteria.where(ArtifactEntityKeys.tag).is("");

    CloseableIterator<ArtifactEntity> iterator = mongoTemplate.stream(new Query(criteria), ArtifactEntity.class);

    while (iterator.hasNext()) {
      ArtifactEntity artifact = iterator.next();
      if (artifact.getInvalid()) {
        artifact.setTag("latest");
        artifactRepository.save(artifact);
      } else {
        try {
          ArtifactEntity artifactForLatestVersion =
              artifactRepository.findOne(Criteria.where(ArtifactEntityKeys.accountId)
                                             .is(artifact.getAccountId())
                                             .and(ArtifactEntityKeys.orgId)
                                             .is(artifact.getOrgId())
                                             .and(ArtifactEntityKeys.projectId)
                                             .is(artifact.getProjectId())
                                             .and(ArtifactEntityKeys.artifactId)
                                             .is(artifact.getArtifactId())
                                             .and(ArtifactEntityKeys.tag)
                                             .is("latest")
                                             .and(ArtifactEntityKeys.invalid)
                                             .is(false));

          if (Objects.nonNull(artifactForLatestVersion)) {
            if (artifact.getCreatedOn().compareTo(artifactForLatestVersion.getCreatedOn()) < 0) {
              artifact.setInvalid(true);
            } else {
              artifactForLatestVersion.setInvalid(true);
              artifactRepository.save(artifactForLatestVersion);
              artifact.setArtifactCorrelationId(artifactForLatestVersion.getArtifactCorrelationId());
              artifact.setProdEnvCount(artifactForLatestVersion.getProdEnvCount());
              artifact.setNonProdEnvCount(artifactForLatestVersion.getProdEnvCount());
            }
          } else {
            artifact.setArtifactCorrelationId(artifact.getArtifactCorrelationId() + "latest");
          }
          artifact.setTag("latest");
          artifactRepository.save(artifact);

        } catch (Exception e) {
          log.error(String.format("%s Error while migrating artifact _id: %s", DEBUG_LOG, artifact.getId()), e);
        }
      }
    }
    log.info(DEBUG_LOG + "Migration to update empty tag field in ssca artifacts completed");
  }
}
