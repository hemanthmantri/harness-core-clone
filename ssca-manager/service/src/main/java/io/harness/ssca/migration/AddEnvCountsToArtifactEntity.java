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
import com.mongodb.client.FindIterable;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class AddEnvCountsToArtifactEntity implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  @Inject ArtifactRepository artifactRepository;
  @Override
  public void migrate() {
    log.info("Starting Artifact Entity Null Env Counts Migration");
    Criteria criteria = new Criteria().orOperator(Criteria.where(ArtifactEntityKeys.nonProdEnvCount).isNull(),
        Criteria.where(ArtifactEntityKeys.prodEnvCount).isNull());
    Query query = new Query(criteria);
    FindIterable<Document> iterable =
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(ArtifactEntity.class)).find(query.getQueryObject());

    for (Document document : iterable) {
      try {
        ArtifactEntity artifact = mongoTemplate.getConverter().read(ArtifactEntity.class, document);
        artifact.setNonProdEnvCount(0l);
        artifact.setProdEnvCount(0l);
        artifactRepository.save(artifact);
      } catch (Exception e) {
        log.error(String.format("Failed to update env counts for artifact: %s, Exception: %s", document, e));
      }
    }
  }
}
