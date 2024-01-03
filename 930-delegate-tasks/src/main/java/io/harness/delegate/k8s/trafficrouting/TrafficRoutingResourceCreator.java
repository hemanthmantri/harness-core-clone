/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.trafficrouting;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.trafficrouting.TrafficRoutingInfoDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor
@AllArgsConstructor
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public abstract class TrafficRoutingResourceCreator {
  private static final int K8S_RESOURCE_NAME_MAX = 253;
  private static final String STABLE_PLACE_HOLDER = "stable";
  private static final String STAGE_PLACE_HOLDER = "stage";
  private static final String CANARY_PLACE_HOLDER = "canary";

  protected K8sTrafficRoutingConfig k8sTrafficRoutingConfig;

  public List<KubernetesResource> createTrafficRoutingResources(
      String namespace, String releaseName, Set<String> availableApiVersions, LogCallback logCallback) {
    return createTrafficRoutingResources(namespace, releaseName, null, null, availableApiVersions, logCallback);
  }

  public List<KubernetesResource> createTrafficRoutingResources(String namespace, String releaseName,
      KubernetesResource primaryService, KubernetesResource secondaryService, Set<String> availableApiVersions,
      LogCallback logCallback) {
    Map<String, String> apiVersions = getApiVersions(availableApiVersions, logCallback);

    List<String> trafficRoutingManifests =
        getTrafficRoutingManifests(namespace, releaseName, primaryService, secondaryService, apiVersions);

    logCallback.saveExecutionLog(
        format("Traffic Routing resources created: %n%s", String.join("\n---\n", trafficRoutingManifests)), INFO,
        CommandExecutionStatus.SUCCESS);
    return trafficRoutingManifests.stream()
        .map(ManifestHelper::getKubernetesResourcesFromSpec)
        .flatMap(List::stream)
        .toList();
  }

  private List<String> getTrafficRoutingManifests(String namespace, String releaseName,
      KubernetesResource stableService, KubernetesResource stageService, Map<String, String> apiVersions) {
    String stableName = stableService != null ? stableService.getResourceId().getName() : null;
    String stageName = stageService != null ? stageService.getResourceId().getName() : null;

    return getManifests(namespace, releaseName, stableName, stageName, apiVersions);
  }

  protected abstract List<String> getManifests(
      String namespace, String releaseName, String stableName, String stageName, Map<String, String> apiVersions);
  protected abstract Map<String, List<String>> getProviderVersionMap();

  public String updatePlaceHoldersIfExist(String host, String stable, String stage) {
    if (isEmpty(host)) {
      throw new InvalidArgumentsException("Host must be specified in the destination for traffic routing");
    } else if (STABLE_PLACE_HOLDER.equals(host) && isNotEmpty(stable)) {
      return stable;
    } else if ((STAGE_PLACE_HOLDER.equals(host) || CANARY_PLACE_HOLDER.equals(host)) && isNotEmpty(stage)) {
      return stage;
    } else {
      return host;
    }
  }

  public String getTrafficRoutingResourceName(String name, String suffix, String defaultName) {
    return name != null ? StringUtils.truncate(name, K8S_RESOURCE_NAME_MAX - suffix.length()) + suffix : defaultName;
  }

  public Map<String, String> getApiVersions(Set<String> clusterAvailableApis, LogCallback logCallback) {
    Map<String, String> apiVersions = new HashMap<>();
    getProviderVersionMap().forEach(
        (key, value)
            -> apiVersions.put(key,
                value.stream()
                    .sorted(Collections.reverseOrder())
                    .filter(clusterAvailableApis::contains)
                    .findFirst()
                    .orElseGet(() -> {
                      String firstVersion = value.get(value.size() - 1);
                      logCallback.saveExecutionLog(
                          format(
                              "CRD specification wasn't found for %s resource in the cluster. Version: %s will be used in case of creation of this resource.",
                              key, firstVersion),
                          LogLevel.WARN, CommandExecutionStatus.RUNNING);
                      return firstVersion;
                    })));
    return apiVersions;
  }

  public Optional<TrafficRoutingInfoDTO> getTrafficRoutingInfo(List<KubernetesResource> kubernetesResources) {
    return kubernetesResources.stream()
        .filter(resource -> resource.getResourceId().getKind().equals(getMainResourceKind()))
        .findFirst()
        .map(resource
            -> TrafficRoutingInfoDTO.builder()
                   .name(resource.getResourceId().getName())
                   .version(resource.getApiVersion())
                   .plural(getMainResourceKindPlural())
                   .build());
  }

  protected abstract String getMainResourceKind();

  protected abstract String getMainResourceKindPlural();
  public abstract Optional<String> getSwapTrafficRoutingPatch(String stable, String stage);
}
