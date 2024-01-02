/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.search;

import static io.harness.ssca.entities.migration.MigrationEntity.MigrationStatus.SUCCESS;
import static io.harness.ssca.search.utils.SSCAMigrationConstants.JOB_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.repositories.MigrationRepo;
import io.harness.ssca.beans.ElasticSearchConfig;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.entities.migration.MigrationEntity;
import io.harness.ssca.helpers.BatchProcessor;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class ElkMigrationService implements Runnable {
  private final SearchService searchService;
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;

  private MigrationRepo migrationRepo;
  private ElasticSearchConfig elasticSearchConfig;

  private static final String ELK_DEBUG_LOG = "[ElkMigrationService]: ";

  private static final String LOCK_NAME = "ElkMigrationJobLock";

  @Inject
  public ElkMigrationService(SearchService searchService, MongoTemplate mongoTemplate,
      PersistentLocker persistentLocker, MigrationRepo migrationRepo,
      @Named("elasticsearch") ElasticSearchConfig elasticSearchConfig) {
    this.searchService = searchService;
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.migrationRepo = migrationRepo;
    this.elasticSearchConfig = elasticSearchConfig;
  }

  @Override
  public void run() {
    log.info(ELK_DEBUG_LOG + "Started running...");
    log.info(ELK_DEBUG_LOG + "Trying to acquire lock...");

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(ELK_DEBUG_LOG + "Failed to acquire lock");
        return;
      }

      try {
        migrate();
      } catch (Exception ex) {
        log.error(ELK_DEBUG_LOG + "Unexpected error occurred during migration", ex);
      }
    } catch (Exception ex) {
      log.error(ELK_DEBUG_LOG + "Failed to acquire lock", ex);
    }

    log.info(ELK_DEBUG_LOG + "Stopped running...");
  }

  private void migrate() {
    log.info(ELK_DEBUG_LOG + "Starting ELK migration....");

    try {
      BatchProcessor<ArtifactEntity> artifactEntityBatchProcessor =
          new BatchProcessor<>(mongoTemplate, ArtifactEntity.class);
      BatchProcessor<NormalizedSBOMComponentEntity> componentEntityBatchProcessor =
          new BatchProcessor<>(mongoTemplate, NormalizedSBOMComponentEntity.class);

      artifactEntityBatchProcessor.processBatch(new Query(), ArtifactEntity::getAccountId, this::processArtifactGroup);
      componentEntityBatchProcessor.processBatch(
          new Query(), NormalizedSBOMComponentEntity::getAccountId, this::processComponents);
    } catch (Exception e) {
      searchService.deleteIndex(elasticSearchConfig.getIndexName());
      log.error(ELK_DEBUG_LOG + "Elk migration failed....", e);
      throw new GeneralException("Elk migration failed");
    }
    migrationRepo.save(MigrationEntity.builder().name(JOB_NAME).status(SUCCESS).build());

    log.info(ELK_DEBUG_LOG + "Elk migration successful....");
  }

  private void processArtifactGroup(String accountId, List<ArtifactEntity> artifactEntities) {
    if (!searchService.bulkSaveArtifacts(accountId, artifactEntities)) {
      throw new InvalidRequestException(ELK_DEBUG_LOG + "Unable to save bulk artifacts for accountId: " + accountId);
    }
  }

  private void processComponents(
      String accountId, List<NormalizedSBOMComponentEntity> normalizedSBOMComponentEntities) {
    if (!searchService.bulkSaveComponents(accountId, normalizedSBOMComponentEntities)) {
      throw new InvalidRequestException(ELK_DEBUG_LOG + "Unable to save bulk components for accountId: " + accountId);
    }
  }
}
