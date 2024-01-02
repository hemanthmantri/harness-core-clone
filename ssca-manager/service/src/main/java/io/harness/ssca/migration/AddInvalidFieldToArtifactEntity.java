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
public class AddInvalidFieldToArtifactEntity implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  @Inject ArtifactRepository artifactRepository;

  private static final String DEBUG_LOG = "INVALID_FIELD_MIGRATION: ";

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Starting migration to add invalid field to ssca artifacts");
    Criteria criteria = Criteria.where(ArtifactEntityKeys.invalid).is(null);

    CloseableIterator<ArtifactEntity> iterator = mongoTemplate.stream(new Query(criteria), ArtifactEntity.class);

    while (iterator.hasNext()) {
      ArtifactEntity artifact = iterator.next();
      try {
        ArtifactEntity nextArtifactForAVersion = artifactRepository.findOne(Criteria.where(ArtifactEntityKeys.accountId)
                                                                                .is(artifact.getAccountId())
                                                                                .and(ArtifactEntityKeys.orgId)
                                                                                .is(artifact.getOrgId())
                                                                                .and(ArtifactEntityKeys.projectId)
                                                                                .is(artifact.getProjectId())
                                                                                .and(ArtifactEntityKeys.artifactId)
                                                                                .is(artifact.getArtifactId())
                                                                                .and(ArtifactEntityKeys.tag)
                                                                                .is(artifact.getTag())
                                                                                .and(ArtifactEntityKeys.createdOn)
                                                                                .gt(artifact.getCreatedOn()));

        if (Objects.isNull(nextArtifactForAVersion)) {
          artifact.setInvalid(false);
        } else {
          artifact.setInvalid(true);
        }

        artifactRepository.save(artifact);

      } catch (Exception e) {
        log.error(String.format("%s Error while migrating artifact _id: %s", DEBUG_LOG, artifact.getId()), e);
      }
    }
    log.info(DEBUG_LOG + "Migration to add invalid field to ssca artifacts completed");
  }
}
