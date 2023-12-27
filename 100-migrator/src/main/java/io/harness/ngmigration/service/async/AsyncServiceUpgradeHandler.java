/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.async;

import static software.wings.ngmigration.NGMigrationEntityType.MANIFEST;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.MigrationTrackReqPayload;
import io.harness.beans.MigrationTrackRespPayload;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.ServiceFilter;
import io.harness.ngmigration.service.MigrationResourceService;
import io.harness.ngmigration.service.importer.ServiceImportService;
import io.harness.persistence.HPersistence;

import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.ApplicationManifestKeys;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class AsyncServiceUpgradeHandler extends AsyncTaskHandler {
  @Inject MigrationResourceService migrationResourceService;
  @Inject private HPersistence hPersistence;
  @Inject private ServiceImportService serviceImportService;
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private ServiceTemplateService serviceTemplateService;
  private static final String TASK_TYPE = "SERVICE_MIGRATION";

  @Override
  String getTaskType() {
    return TASK_TYPE;
  }

  @Override
  MigrationTrackRespPayload processTask(
      String apiKey, String accountId, String requestId, MigrationTrackReqPayload reqPayload) {
    var importDTO = (ImportDTO) reqPayload;
    importDTO.setAccountIdentifier(accountId);
    importDTO.setEntityType(NGMigrationEntityType.SERVICE);
    importDTO.setSkipEntities(Sets.newHashSet(NGMigrationEntityType.INFRA));
    importDTO.setShouldMigrateOverrides(true);

    var filter = (ServiceFilter) importDTO.getFilter();
    var appId = filter.getAppId();
    var serviceIds = filter.getIds();

    //
    var discoveryInput = serviceImportService.getDiscoveryInput(importDTO);
    // Fetch all the environments and then add them to discovery
    var environments = hPersistence.createQuery(Environment.class)
                           .filter(EnvironmentKeys.accountId, accountId)
                           .filter(EnvironmentKeys.appId, appId)
                           .asList();

    environments.stream()
        .filter(env -> importDTO.getEnvironmentIdentifiers().containsKey(env.getName()))
        .forEach(env -> {
          var envId = env.getUuid();
          var envIdentifier = importDTO.getEnvironmentIdentifiers().get(env.getName());
          importDTO.getInputs().getOverrides().put(
              CgEntityId.builder().id(envId).type(NGMigrationEntityType.ENVIRONMENT).build(),
              BaseProvidedInput.builder().identifier(envIdentifier).build());
          discoveryInput.getEntities().add(DiscoverEntityInput.builder()
                                               .appId(appId)
                                               .entityId(envId)
                                               .type(NGMigrationEntityType.ENVIRONMENT)
                                               .build());
        });

    // We are filtering based on service template because service variables & environment types are handled with
    // Environment migration. These variables depend on both service & environment to be migrated.
    List<ServiceVariable> serviceVariables =
        hPersistence.createQuery(ServiceVariable.class)
            .filter(ServiceVariableKeys.appId, appId)
            .filter(ServiceVariableKeys.accountId, accountId)
            .filter(ServiceVariableKeys.entityType, EntityType.SERVICE_TEMPLATE.name())
            .asList();
    if (EmptyPredicate.isNotEmpty(serviceVariables)) {
      serviceVariables.stream().distinct().forEach(serviceVariable -> {
        if (!EntityType.SERVICE_TEMPLATE.equals(serviceVariable.getEntityType())) {
          return;
        }
        var serviceTemplate = serviceTemplateService.get(serviceVariable.getAppId(), serviceVariable.getTemplateId());
        if (serviceTemplate == null || StringUtils.isBlank(serviceTemplate.getServiceId())
            || StringUtils.isBlank(serviceTemplate.getEnvId())
            || !serviceIds.contains(serviceTemplate.getServiceId())) {
          return;
        }
        discoveryInput.getEntities().add(DiscoverEntityInput.builder()
                                             .appId(appId)
                                             .entityId(serviceVariable.getUuid())
                                             .type(NGMigrationEntityType.SERVICE_VARIABLE)
                                             .build());
      });
    }

    List<ApplicationManifest> applicationManifests = hPersistence.createQuery(ApplicationManifest.class)
                                                         .filter(ApplicationKeys.appId, appId)
                                                         .field(ApplicationManifestKeys.serviceId)
                                                         .notEqual(null)
                                                         .field(ApplicationManifestKeys.envId)
                                                         .notEqual(null)
                                                         .asList();

    if (EmptyPredicate.isNotEmpty(applicationManifests)) {
      discoveryInput.getEntities().addAll(
          applicationManifests.stream()
              .distinct()
              .filter(manifest -> StringUtils.isNotBlank(manifest.getServiceId()))
              .filter(manifest -> StringUtils.isNotBlank(manifest.getEnvId()))
              .filter(manifest -> serviceIds.contains(manifest.getServiceId()))
              .map(manifest
                  -> DiscoverEntityInput.builder().entityId(manifest.getUuid()).appId(appId).type(MANIFEST).build())
              .collect(Collectors.toList()));
    }

    return migrationResourceService.saveServiceWithOverrides(apiKey, discoveryInput, importDTO);
  }

  @Override
  HPersistence getHPersistence() {
    return hPersistence;
  }
}
