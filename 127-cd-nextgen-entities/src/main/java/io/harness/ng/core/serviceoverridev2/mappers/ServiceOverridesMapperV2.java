/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverridev2.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitx.EntityGitInfo;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideSpecConfig;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec.ServiceOverridesSpecBuilder;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.template.CacheResponseMetadataDTO;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class ServiceOverridesMapperV2 {
  public NGServiceOverridesEntity toEntity(
      @NotNull String accountId, @NonNull ServiceOverrideRequestDTOV2 requestDTOV2) {
    return NGServiceOverridesEntity.builder()
        .accountId(accountId)
        .orgIdentifier(requestDTOV2.getOrgIdentifier())
        .projectIdentifier(requestDTOV2.getProjectIdentifier())
        .environmentRef(requestDTOV2.getEnvironmentRef())
        .serviceRef(requestDTOV2.getServiceRef())
        .infraIdentifier(requestDTOV2.getInfraIdentifier())
        .clusterIdentifier(requestDTOV2.getClusterIdentifier())
        .spec(requestDTOV2.getSpec())
        .type(requestDTOV2.getType())
        .yamlInternal(requestDTOV2.getYamlInternal())
        .build();
  }

  public ServiceOverridesResponseDTOV2 toResponseDTO(@NonNull NGServiceOverridesEntity entity, boolean isNewlyCreated) {
    return ServiceOverridesResponseDTOV2.builder()
        .identifier(entity.getIdentifier())
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .environmentRef(entity.getEnvironmentRef())
        .serviceRef(entity.getServiceRef())
        .infraIdentifier(entity.getInfraIdentifier())
        .clusterIdentifier(entity.getClusterIdentifier())
        .spec(entity.getSpec())
        .type(entity.getType())
        .isNewlyCreated(isNewlyCreated)
        .yamlInternal(entity.getYamlInternal())
        .entityGitInfo(getEntityGitInfo(entity))
        .storeType(entity.getStoreType())
        .connectorRef(entity.getConnectorRef())
        .fallbackBranch(entity.getFallBackBranch())
        .cacheResponseMetadataDTO(getCacheResponse(entity))
        .yaml(getYamlFromOverridesV2(entity))
        .build();
  }

  private EntityGitInfo getEntityGitInfo(NGServiceOverridesEntity overridesEntity) {
    if (StoreType.REMOTE.equals(overridesEntity.getStoreType())) {
      EntityGitInfo entityGitInfo = GitAwareContextHelper.getEntityInfo(overridesEntity);
      return GitAwareContextHelper.updateEntityGitInfoFromScmGitMetadata(entityGitInfo);
    }

    return null;
  }

  private CacheResponseMetadataDTO getCacheResponse(NGServiceOverridesEntity overridesEntity) {
    if (overridesEntity.getStoreType() == StoreType.REMOTE) {
      CacheResponse cacheResponse = GitAwareContextHelper.getCacheResponseFromScmGitMetadata();

      if (cacheResponse != null) {
        return createCacheResponseMetadataDTO(cacheResponse);
      }
    }

    return null;
  }

  private CacheResponseMetadataDTO createCacheResponseMetadataDTO(CacheResponse cacheResponse) {
    return CacheResponseMetadataDTO.builder()
        .cacheState(cacheResponse.getCacheState())
        .ttlLeft(cacheResponse.getTtlLeft())
        .lastUpdatedAt(cacheResponse.getLastUpdatedAt())
        .isSyncEnabled(cacheResponse.isSyncEnabled())
        .build();
  }

  // This method is used for redirecting overrides v1 request overrides v2
  public ServiceOverrideRequestDTOV2 toRequestV2(NGServiceOverridesEntity requestedEntity) {
    NGServiceOverrideConfig serviceOverrideConfig =
        NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(requestedEntity);
    NGServiceOverrideInfoConfig serviceOverrideInfoConfig = serviceOverrideConfig.getServiceOverrideInfoConfig();
    return ServiceOverrideRequestDTOV2.builder()
        .orgIdentifier(requestedEntity.getOrgIdentifier())
        .projectIdentifier(requestedEntity.getProjectIdentifier())
        .serviceRef(requestedEntity.getServiceRef())
        .environmentRef(requestedEntity.getEnvironmentRef())
        .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
        .spec(ServiceOverridesSpec.builder()
                  .variables(serviceOverrideInfoConfig.getVariables())
                  .manifests(serviceOverrideInfoConfig.getManifests())
                  .configFiles(serviceOverrideInfoConfig.getConfigFiles())
                  .applicationSettings(serviceOverrideInfoConfig.getApplicationSettings())
                  .connectionStrings(serviceOverrideInfoConfig.getConnectionStrings())
                  .build())
        .v1Api(true)
        .yamlInternal(requestedEntity.getYaml())
        .build();
  }
  public void updateEnvConfigFromOverrideV2(NGServiceOverridesEntity entity, NGEnvironmentConfig environmentConfig) {
    ServiceOverridesSpec spec = entity.getSpec();

    if (isNotEmpty(spec.getVariables())) {
      environmentConfig.getNgEnvironmentInfoConfig().setVariables(spec.getVariables());
    }
    if (isNotEmpty(spec.getManifests()) || isNotEmpty(spec.getConfigFiles()) || spec.getApplicationSettings() != null
        || spec.getConnectionStrings() != null) {
      NGEnvironmentGlobalOverride ngEnvironmentGlobalOverride = NGEnvironmentGlobalOverride.builder()
                                                                    .manifests(spec.getManifests())
                                                                    .configFiles(spec.getConfigFiles())
                                                                    .applicationSettings(spec.getApplicationSettings())
                                                                    .connectionStrings(spec.getConnectionStrings())
                                                                    .build();
      environmentConfig.getNgEnvironmentInfoConfig().setNgEnvironmentGlobalOverride(ngEnvironmentGlobalOverride);
    }
  }

  public ServiceOverrideResponseDTO toResponseDTOV1(ServiceOverridesResponseDTOV2 responseDTOV2, String yaml) {
    return ServiceOverrideResponseDTO.builder()
        .accountId(responseDTOV2.getAccountId())
        .orgIdentifier(responseDTOV2.getOrgIdentifier())
        .projectIdentifier(responseDTOV2.getProjectIdentifier())
        .environmentRef(responseDTOV2.getEnvironmentRef())
        .serviceRef(responseDTOV2.getServiceRef())
        .yaml(yaml)
        .build();
  }

  public Optional<ServiceOverrideRequestDTOV2> toRequestDTOV2(NGEnvironmentConfig environmentConfig, String accountId) {
    NGEnvironmentInfoConfig envInfoConfig = environmentConfig.getNgEnvironmentInfoConfig();
    if (isEmpty(envInfoConfig.getVariables()) && envInfoConfig.getNgEnvironmentGlobalOverride() == null) {
      return Optional.empty();
    }

    ServiceOverridesSpecBuilder serviceOverridesSpecBuilder =
        ServiceOverridesSpec.builder().variables(envInfoConfig.getVariables());
    NGEnvironmentGlobalOverride ngEnvironmentGlobalOverride = envInfoConfig.getNgEnvironmentGlobalOverride();
    if (ngEnvironmentGlobalOverride != null) {
      serviceOverridesSpecBuilder.manifests(ngEnvironmentGlobalOverride.getManifests())
          .configFiles(ngEnvironmentGlobalOverride.getConfigFiles())
          .connectionStrings(ngEnvironmentGlobalOverride.getConnectionStrings())
          .applicationSettings(ngEnvironmentGlobalOverride.getApplicationSettings());
    }

    return Optional.of(
        ServiceOverrideRequestDTOV2.builder()
            .projectIdentifier(envInfoConfig.getProjectIdentifier())
            .orgIdentifier(envInfoConfig.getOrgIdentifier())
            .environmentRef(IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, envInfoConfig.getOrgIdentifier(),
                envInfoConfig.getProjectIdentifier(), envInfoConfig.getIdentifier()))
            .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
            .spec(serviceOverridesSpecBuilder.build())
            .build());
  }

  public ServiceOverridesSpec toServiceOverrideSpec(@NonNull NGServiceOverrideConfig configV1) {
    NGServiceOverrideInfoConfig infoConfigV1 = configV1.getServiceOverrideInfoConfig();
    return ServiceOverridesSpec.builder()
        .manifests(infoConfigV1.getManifests())
        .configFiles(infoConfigV1.getConfigFiles())
        .variables(infoConfigV1.getVariables())
        .connectionStrings(infoConfigV1.getConnectionStrings())
        .applicationSettings(infoConfigV1.getApplicationSettings())
        .build();
  }

  public ServiceOverridesSpec toServiceOverrideSpec(@NonNull NGEnvironmentInfoConfig infoConfigV1) {
    ServiceOverridesSpecBuilder builder = ServiceOverridesSpec.builder().variables(infoConfigV1.getVariables());
    if (infoConfigV1.getNgEnvironmentGlobalOverride() != null) {
      NGEnvironmentGlobalOverride ngEnvironmentGlobalOverride = infoConfigV1.getNgEnvironmentGlobalOverride();
      builder.manifests(ngEnvironmentGlobalOverride.getManifests())
          .configFiles(ngEnvironmentGlobalOverride.getConfigFiles())
          .connectionStrings(ngEnvironmentGlobalOverride.getConnectionStrings())
          .applicationSettings(ngEnvironmentGlobalOverride.getApplicationSettings());
    }
    return builder.build();
  }

  public ServiceOverridesSpec toServiceOverrideSpec(String entityYaml) {
    try {
      return YamlPipelineUtils.read(entityYaml, ServiceOverridesSpec.class);
    } catch (IOException e) {
      throw new InvalidRequestException(String.format("Cannot read serviceOverride yaml %s ", entityYaml));
    }
  }

  public void setYamlV2IfNotPresent(NGServiceOverridesEntity requestedEntity) {
    if (isEmpty(requestedEntity.getYamlV2())) {
      requestedEntity.setYamlV2(getYamlFromOverridesV2(requestedEntity));
    }
  }

  public String getYamlFromOverridesV2(NGServiceOverridesEntity requestedEntity) {
    if (isEmpty(requestedEntity.getYamlV2())) {
      ServiceOverridesSpec serviceOverrideSpec = requestedEntity.getSpec();
      ServiceOverrideSpecConfig specConfig = ServiceOverrideSpecConfig.builder().spec(serviceOverrideSpec).build();
      return YamlUtils.writeYamlString(specConfig);
    }

    return requestedEntity.getYamlV2();
  }
}
