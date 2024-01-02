/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.backstage.beans.BackstageCatalogEntityTypes.GROUP;
import static io.harness.idp.backstage.beans.BackstageCatalogEntityTypes.USER;
import static io.harness.idp.backstage.beans.BackstageScaffolderTask.toEntities;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.idp.common.JacksonUtils.readValueForObject;
import static io.harness.idp.common.YamlUtils.writeObjectAsYaml;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.UnexpectedException;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.BackstageScaffolderTask;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity.Relation;
import io.harness.idp.backstage.entities.BackstageCatalogEntity.Target;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.events.BackstageCatalogEntityCreateEvent;
import io.harness.idp.backstage.events.BackstageCatalogEntityDeleteEvent;
import io.harness.idp.backstage.events.BackstageCatalogEntityUpdateEvent;
import io.harness.idp.backstage.repositories.BackstageCatalogEntityRepository;
import io.harness.idp.backstage.repositories.BackstageScaffolderTasksEntityRepository;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.events.producers.IdpEntityCrudStreamProducer;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.transaction.support.TransactionTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BackstageServiceImpl implements BackstageService {
  private static final String ENTITY_NOT_FOUND_MAPPED_ERROR = "HTTP Error Status (404 - Resource Not Found) received. ";

  @Inject @Named("allowedKindsForCatalogSync") private List<String> allowedKindsForCatalogSync;
  @Inject NamespaceService namespaceService;
  @Inject BackstageResourceClient backstageResourceClient;
  @Inject BackstageCatalogEntityRepository backstageCatalogEntityRepository;
  @Inject IdpEntityCrudStreamProducer idpEntityCrudStreamProducer;
  @Inject IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @Inject OutboxService outboxService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject NamespaceRepository namespaceRepository;
  @Inject BackstageScaffolderTasksEntityRepository scaffolderTasksEntityRepository;

  @Override
  public void sync() {
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    log.info("Fetched {} IDP active accounts for IdpCatalogEntitiesAsHarnessEntities sync", accountIdentifiers.size());
    accountIdentifiers.forEach(this::sync);
  }

  @Override
  public boolean sync(String accountIdentifier) {
    try {
      log.info("Syncing IDP catalog entities as Harness entities for accountIdentifier = {}", accountIdentifier);
      String url = String.format("%s/idp/api/catalog/entities", accountIdentifier);
      Object response = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));
      List<BackstageCatalogEntity> backstageCatalogEntities = convert(response, BackstageCatalogEntity.class);
      backstageCatalogEntities = filter(backstageCatalogEntities);
      syncInternal(
          accountIdentifier, "", BackstageHarnessSyncRequest.ActionEnum.UPSERT.value(), backstageCatalogEntities);
    } catch (Exception ex) {
      log.error("Error in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} Error = {}",
          accountIdentifier, ex.getMessage(), ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean sync(String accountIdentifier, String entityUid, String action, String syncMode) {
    switch (BackstageHarnessSyncRequest.SyncModeEnum.fromValue(syncMode)) {
      case SYNC:
        return syncInSynchronousMode(accountIdentifier, entityUid, action);
      case ASYNC:
        return syncInAsynchronousMode(accountIdentifier, entityUid, action);
      default:
        throw new UnexpectedException("Unsupported sync mode for syncing IdpCatalogEntitiesAsHarnessEntities");
    }
  }

  @Override
  public void syncScaffolderTasks() {
    List<NamespaceEntity> activeAccounts = namespaceService.getActiveAccounts();
    log.info("Fetched {} IDP active accounts for scaffolder tasks sync", activeAccounts.size());
    activeAccounts.forEach(namespaceEntity -> {
      String accountIdentifier = namespaceEntity.getAccountIdentifier();
      long syncFrom = Objects.nonNull(namespaceEntity.getMetadata())
          ? namespaceEntity.getMetadata().getScaffolderTasksSyncFrom()
          : 0;
      boolean result = syncScaffolderTasks(namespaceEntity.getAccountIdentifier(), syncFrom);
      if (result) {
        log.info("Successfully synced scaffolder tasks for accountIdentifier = {} syncFrom = {}", accountIdentifier,
            syncFrom);
        updateScaffolderTasksSyncFrom(namespaceEntity);
        log.info(
            "Updated scaffolder tasks sync from to current timestamp for accountIdentifier = {}", accountIdentifier);
      } else {
        log.error(
            "Error in syncing scaffolder tasks for accountIdentifier = {} syncFrom = {} Skipping update to metadata scaffolderTasksSyncFrom",
            accountIdentifier, syncFrom);
      }
    });
  }

  @Override
  public boolean syncScaffolderTasks(String accountIdentifier, long syncFrom) {
    log.info("Syncing scaffolder tasks for accountIdentifier = {} syncFrom = {}", accountIdentifier, syncFrom);
    try {
      Object response = getGeneralResponse(backstageResourceClient.scaffolderListTasks(accountIdentifier, syncFrom));
      List<BackstageScaffolderTask> scaffolderTasks = convert(response, BackstageScaffolderTask.class);
      log.info("Fetched {} scaffolder tasks for accountIdentifier = {} syncFrom = {}", scaffolderTasks.size(),
          accountIdentifier, syncFrom);
      List<BackstageScaffolderTaskEntity> scaffolderTasksEntities = toEntities(accountIdentifier, scaffolderTasks);
      scaffolderTasksEntityRepository.saveAll(scaffolderTasksEntities);
    } catch (Exception ex) {
      log.error("Error in syncing scaffolder tasks for accountIdentifier = {} syncFrom = {} Error = {}",
          accountIdentifier, syncFrom, ex.getMessage(), ex);
      return false;
    }
    return true;
  }

  private List<BackstageCatalogEntity> filter(List<BackstageCatalogEntity> backstageCatalogEntities) {
    return backstageCatalogEntities.stream()
        .filter(backstageCatalogEntity -> allowedKindsForCatalogSync.contains(backstageCatalogEntity.getKind()))
        .collect(Collectors.toList());
  }

  private void syncInternal(String accountIdentifier, String entityUid, String action,
      List<BackstageCatalogEntity> backstageCatalogEntities) {
    if (backstageCatalogEntities.isEmpty()) {
      return;
    }
    log.info(
        "Fetched {} catalog entities in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} EntityUid = {} Action = {}",
        backstageCatalogEntities.size(), accountIdentifier, entityUid, action);
    List<Pair<BackstageCatalogEntity, BackstageCatalogEntity>> entitiesList =
        prepareEntitiesForSave(accountIdentifier, backstageCatalogEntities);

    Map<BackstageCatalogEntity, String> entitiesActions = categorizeEntitiesActions(backstageCatalogEntities);
    for (Map.Entry<BackstageCatalogEntity, String> entry : entitiesActions.entrySet()) {
      if (BackstageHarnessSyncRequest.ActionEnum.CREATE.toString().equals(entry.getValue())) {
        String uuid = entry.getKey().getMetadata().getUid();
        boolean producerResult =
            idpEntityCrudStreamProducer.publishAsyncScoreComputationChangeEventToRedis(accountIdentifier, null, uuid);
        if (!producerResult) {
          log.error(
              "Error in producing event for async score computation. AccountIdentifier = {} BackstageCatalogEntityUid = {} Action = {}",
              accountIdentifier, uuid, action);
        }
      }
    }

    AtomicReference<Iterable<BackstageCatalogEntity>> savedBackstageCatalogEntities = new AtomicReference<>();
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      savedBackstageCatalogEntities.set(backstageCatalogEntityRepository.saveAll(backstageCatalogEntities));
      if (StringUtils.isNotBlank(entityUid)) {
        for (Pair<BackstageCatalogEntity, BackstageCatalogEntity> entityPair : entitiesList) {
          BackstageCatalogEntity oldEntity = entityPair.getFirst();
          BackstageCatalogEntity newEntity = entityPair.getSecond();
          if (oldEntity == null) {
            outboxService.save(new BackstageCatalogEntityCreateEvent(
                accountIdentifier, newEntity.getEntityUid(), newEntity.getYaml()));
          } else if (!oldEntity.getYaml().equals(newEntity.getYaml())) {
            outboxService.save(new BackstageCatalogEntityUpdateEvent(
                accountIdentifier, newEntity.getEntityUid(), oldEntity.getYaml(), newEntity.getYaml()));
            handleEntityDeletionIfRequired(accountIdentifier, oldEntity, newEntity);
          }
        }
      }
      return true;
    }));

    log.info(
        "Saved {} catalog entities into DB in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} EntityUid = {} Action = {}",
        backstageCatalogEntities.size(), accountIdentifier, entityUid, action);
    publishBackstageCatalogEntityChange(accountIdentifier, savedBackstageCatalogEntities.get(), action);
    log.info("Synced IDP catalog entities as Harness entities for accountIdentifier = {} EntityUid = {} Action = {}",
        accountIdentifier, entityUid, action);
  }

  private void handleEntityDeletionIfRequired(
      String accountIdentifier, BackstageCatalogEntity oldEntity, BackstageCatalogEntity newEntity) {
    if (GROUP.equals(BackstageCatalogEntityTypes.fromString(oldEntity.getKind()))
        || USER.equals(BackstageCatalogEntityTypes.fromString(oldEntity.getKind()))) {
      Set<Relation> oldRelations = oldEntity.getRelations();
      Set<Relation> newRelations = newEntity.getRelations();
      oldRelations.removeAll(newRelations);
      for (Relation oldRelation : oldRelations) {
        if ("ownerOf".equals(oldRelation.getType())) {
          Target target = oldRelation.getTarget();
          String kind = BackstageCatalogEntityTypes.fromString(target.getKind()).kind;
          String entityUidToDelete = getEntityUniqueId(kind, target.getNamespace(), target.getName());
          handleCreateOrUpdateOrUpsertAction(
              accountIdentifier, entityUidToDelete, BackstageHarnessSyncRequest.ActionEnum.UPSERT.value());
        }
      }
    }
  }

  private boolean syncInSynchronousMode(String accountIdentifier, String entityUid, String action) {
    try {
      log.info("Syncing IDP catalog entities as Harness entities for accountIdentifier = {} EntityUid = {} Action = {}",
          accountIdentifier, entityUid, action);
      switch (BackstageHarnessSyncRequest.ActionEnum.fromValue(action)) {
        case CREATE:
        case UPDATE:
        case UPSERT:
          handleCreateOrUpdateOrUpsertAction(accountIdentifier, entityUid, action);
          break;
        case DELETE:
          handleDeleteAction(accountIdentifier, entityUid);
          break;
        default:
          throw new UnexpectedException(
              "Unsupported action for syncing IdpCatalogEntitiesAsHarnessEntities in synchronous mode");
      }
    } catch (Exception ex) {
      log.error(
          "Error in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {} EntityUid = {} Action = {} Error = {}",
          accountIdentifier, entityUid, action, ex.getMessage(), ex);
      return false;
    }
    return true;
  }

  private boolean syncInAsynchronousMode(String accountIdentifier, String entityUid, String action) {
    idpServiceMiscRedisProducer.publishIDPCatalogEntitiesSyncCaptureToRedis(accountIdentifier, entityUid, action);
    return true;
  }

  private List<Pair<BackstageCatalogEntity, BackstageCatalogEntity>> prepareEntitiesForSave(
      String accountIdentifier, List<BackstageCatalogEntity> backstageCatalogEntities) {
    List<Pair<BackstageCatalogEntity, BackstageCatalogEntity>> entitesList = new ArrayList<>();

    backstageCatalogEntities.forEach(backstageCatalogEntity -> {
      String entityUid = getEntityUniqueId(backstageCatalogEntity);
      Optional<BackstageCatalogEntity> optionalBackstageCatalogEntity =
          backstageCatalogEntityRepository.findByAccountIdentifierAndEntityUid(accountIdentifier, entityUid);
      optionalBackstageCatalogEntity.ifPresentOrElse(backstageCatalogEntityExisting -> {
        entitesList.add(new Pair<>(backstageCatalogEntityExisting, backstageCatalogEntity));
        backstageCatalogEntity.setId(backstageCatalogEntityExisting.getId());
        backstageCatalogEntity.setCreatedAt(backstageCatalogEntityExisting.getCreatedAt());
      }, () -> entitesList.add(new Pair<>(null, backstageCatalogEntity)));

      backstageCatalogEntity.setAccountIdentifier(accountIdentifier);
      backstageCatalogEntity.setEntityUid(entityUid);
      backstageCatalogEntity.setYaml(writeObjectAsYaml(backstageCatalogEntity));
    });
    return entitesList;
  }

  private Map<BackstageCatalogEntity, String> categorizeEntitiesActions(
      List<BackstageCatalogEntity> backstageCatalogEntities) {
    Map<BackstageCatalogEntity, String> entitiesActions = new HashMap<>();
    backstageCatalogEntities.forEach(backstageCatalogEntity -> {
      if (backstageCatalogEntity.getCreatedAt() == 0) {
        entitiesActions.put(backstageCatalogEntity, BackstageHarnessSyncRequest.ActionEnum.CREATE.value());
      } else {
        entitiesActions.put(backstageCatalogEntity, BackstageHarnessSyncRequest.ActionEnum.UPDATE.value());
      }
    });
    return entitiesActions;
  }

  private String getEntityUniqueId(BackstageCatalogEntity backstageCatalogEntity) {
    return getEntityUniqueId(backstageCatalogEntity.getKind(), backstageCatalogEntity.getMetadata().getNamespace(),
        backstageCatalogEntity.getMetadata().getName());
  }

  private String getEntityUniqueId(String kind, String namespace, String name) {
    namespace = isEmpty(namespace) ? "default" : namespace;
    return kind + "/" + namespace + "/" + name;
  }

  private void publishBackstageCatalogEntityChange(
      String accountIdentifier, Iterable<BackstageCatalogEntity> backstageCatalogEntities, String action) {
    backstageCatalogEntities.forEach(backstageCatalogEntity -> {
      boolean producerResult = idpEntityCrudStreamProducer.publishBackstageCatalogEntityChangeEventToRedis(
          accountIdentifier, backstageCatalogEntity.getEntityUid(), action);
      if (!producerResult) {
        log.error(
            "Error in producing entity change event for backstage catalog. AccountIdentifier = {} BackstageCatalogEntityUid = {} Action = {}",
            accountIdentifier, backstageCatalogEntity.getEntityUid(), action);
      }
    });
  }

  private void handleCreateOrUpdateOrUpsertAction(String accountIdentifier, String entityUid, String action) {
    Object response = null;
    try {
      response = getGeneralResponse(backstageResourceClient.getCatalogEntityByName(accountIdentifier, entityUid));
    } catch (Exception ex) {
      log.error("Error in fetching catalog entity by name for account = {} entityUid = {} Error = {}",
          accountIdentifier, entityUid, ex.getMessage(), ex);
      if (ex.getMessage().equals(ENTITY_NOT_FOUND_MAPPED_ERROR)) {
        syncInSynchronousMode(accountIdentifier, entityUid, BackstageHarnessSyncRequest.ActionEnum.DELETE.value());
        return;
      }
    }
    BackstageCatalogEntity backstageCatalogEntity = readValueForObject(response, BackstageCatalogEntity.class);
    List<BackstageCatalogEntity> backstageCatalogEntities = Collections.singletonList(backstageCatalogEntity);
    backstageCatalogEntities = filter(backstageCatalogEntities);
    syncInternal(accountIdentifier, entityUid, action, backstageCatalogEntities);
  }

  private void handleDeleteAction(String accountIdentifier, String entityUid) {
    log.info(
        "Delete action received in IdpCatalogEntitiesAsHarnessEntities sync for accountIdentifier = {}, entityUid = {}",
        accountIdentifier, entityUid);
    Optional<BackstageCatalogEntity> optionalBackstageCatalogEntity =
        backstageCatalogEntityRepository.findByAccountIdentifierAndEntityUid(accountIdentifier, entityUid);
    optionalBackstageCatalogEntity.ifPresent(backstageCatalogEntityExisting -> {
      log.info("Found BackstageCatalogEntity for delete, deleting it. AccountIdentifier = {}, entityUid = {}",
          accountIdentifier, entityUid);
      Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        backstageCatalogEntityRepository.delete(backstageCatalogEntityExisting);
        outboxService.save(new BackstageCatalogEntityDeleteEvent(accountIdentifier,
            backstageCatalogEntityExisting.getEntityUid(), backstageCatalogEntityExisting.getYaml()));
        return true;
      }));
    });
  }

  private void updateScaffolderTasksSyncFrom(NamespaceEntity namespaceEntity) {
    NamespaceEntity.Metadata metadata = Objects.isNull(namespaceEntity.getMetadata())
        ? NamespaceEntity.Metadata.builder().build()
        : namespaceEntity.getMetadata();
    metadata.setScaffolderTasksSyncFrom(System.currentTimeMillis());
    namespaceEntity.setMetadata(metadata);
    namespaceRepository.save(namespaceEntity);
  }
}
