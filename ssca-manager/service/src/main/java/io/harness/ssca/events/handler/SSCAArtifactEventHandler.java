/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.events.handler;

import static io.harness.annotations.dev.HarnessTeam.SSCA;
import static io.harness.ssca.events.utils.SSCAOutboxEvents.SSCA_ARTIFACT_CREATED_EVENT;
import static io.harness.ssca.events.utils.SSCAOutboxEvents.SSCA_ARTIFACT_UPDATED_EVENT;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.events.SSCAArtifactCreatedEvent;
import io.harness.ssca.events.SSCAArtifactUpdatedEvent;
import io.harness.ssca.helpers.BatchProcessor;
import io.harness.ssca.search.SearchService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(SSCA)
@Slf4j
public class SSCAArtifactEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  SearchService searchService;
  private final MongoTemplate mongoTemplate;
  @Inject @Named("isElasticSearchEnabled") boolean isElasticSearchEnabled;

  SBOMComponentRepo sbomComponentRepo;
  @Inject
  public SSCAArtifactEventHandler(
      SearchService searchService, MongoTemplate mongoTemplate, SBOMComponentRepo sbomComponentRepo) {
    this.searchService = searchService;
    this.mongoTemplate = mongoTemplate;
    this.sbomComponentRepo = sbomComponentRepo;
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case SSCA_ARTIFACT_CREATED_EVENT:
          return handleSSCAArtifactCreatedEvent(outboxEvent);
        case SSCA_ARTIFACT_UPDATED_EVENT:
          return handleSSCAArtifactUpdatedEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Couldn't handle ssca artifact outboxevent {}", outboxEvent, exception);
      return false;
    }
  }

  private void processComponents(String accountId, List<NormalizedSBOMComponentEntity> components) {
    if (!searchService.bulkSaveComponents(accountId, components)) {
      throw new InvalidRequestException("Unable to save bulk components for accountId: " + accountId);
    }
  }

  private boolean handleSSCAArtifactCreatedEvent(OutboxEvent outboxEvent) throws IOException {
    SSCAArtifactCreatedEvent sscaArtifactCreatedEvent =
        objectMapper.readValue(outboxEvent.getEventData(), SSCAArtifactCreatedEvent.class);
    if (isElasticSearchEnabled) {
      BatchProcessor<NormalizedSBOMComponentEntity> componentEntityBatchProcessor =
          new BatchProcessor<>(mongoTemplate, NormalizedSBOMComponentEntity.class);
      try {
        searchService.upsertArtifact(sscaArtifactCreatedEvent.getArtifact());
        componentEntityBatchProcessor.processBatch(
            new Query(Criteria.where(ArtifactEntityKeys.accountId)
                          .is(sscaArtifactCreatedEvent.getAccountIdentifier())
                          .and(ArtifactEntityKeys.orgId)
                          .is(sscaArtifactCreatedEvent.getOrgIdentifier())
                          .and(ArtifactEntityKeys.projectId)
                          .is(sscaArtifactCreatedEvent.getProjectIdentifier())
                          .and(ArtifactEntityKeys.orchestrationId)
                          .is(sscaArtifactCreatedEvent.getArtifact().getOrchestrationId())),
            null, this::processComponents);
      } catch (Exception e) {
        log.error("Couldn't save ssca artifact in ELK", e);
        return false;
      }
    }
    return true;
  }

  private boolean handleSSCAArtifactUpdatedEvent(OutboxEvent outboxEvent) throws IOException {
    SSCAArtifactUpdatedEvent sscaArtifactUpdatedEvent =
        objectMapper.readValue(outboxEvent.getEventData(), SSCAArtifactUpdatedEvent.class);
    if (isElasticSearchEnabled) {
      try {
        searchService.updateArtifact(sscaArtifactUpdatedEvent.getArtifact());
      } catch (Exception e) {
        log.error("Couldn't update ssca artifact in ELK", e);
        return false;
      }
    }
    return true;
  }
}
