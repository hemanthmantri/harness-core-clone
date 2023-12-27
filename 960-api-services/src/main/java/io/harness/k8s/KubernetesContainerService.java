/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.container.ContainerInfo;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.logging.LogCallback;

import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.VersionInfo;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;

/**
 * Created by brett on 2/10/17.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
public interface KubernetesContainerService {
  HasMetadata getControllerUsingFabric8Client(KubernetesConfig kubernetesConfig, String name);

  V1ObjectMeta getControllerUsingK8sClient(KubernetesConfig kubernetesConfig, String fieldSelector);

  @SuppressWarnings("squid:S1452")
  List<? extends HasMetadata> getControllers(KubernetesConfig kubernetesConfig, Map<String, String> labels);

  void validate(KubernetesConfig kubernetesConfig, boolean useNewKubectlVersion);

  void validateMasterUrl(KubernetesConfig kubernetesConfig);

  @SuppressWarnings("squid:S1452") List<? extends HasMetadata> listControllers(KubernetesConfig kubernetesConfig);

  @SuppressWarnings("squid:S00107")
  List<ContainerInfo> getContainerInfosWhenReady(KubernetesConfig kubernetesConfig, String controllerName,
      int previousCount, int desiredCount, int serviceSteadyStateTimeout, List<Pod> originalPods,
      boolean isNotVersioned, LogCallback logCallback, boolean wait, long startTime, String namespace);

  Integer getControllerPodCount(HasMetadata controller);

  PodTemplateSpec getPodTemplateSpec(HasMetadata controller);

  LinkedHashMap<String, Integer> getActiveServiceCounts(KubernetesConfig kubernetesConfig, String containerServiceName);

  V1Service createOrReplaceService(KubernetesConfig kubernetesConfig, V1Service definition);

  V1Service getService(KubernetesConfig kubernetesConfig, String name, String namespace);

  V1Service getService(KubernetesConfig kubernetesConfig, String name);

  V1ServiceList getServiceListUsingK8sClient(KubernetesConfig kubernetesConfig, String labelSelector);

  List<Service> getServicesUsingFabric8Client(KubernetesConfig kubernetesConfig, Map<String, String> labels);

  V1ConfigMap createOrReplaceConfigMap(KubernetesConfig kubernetesConfig, V1ConfigMap definition);

  V1ConfigMap getConfigMap(KubernetesConfig kubernetesConfig, String name);

  void deleteConfigMap(KubernetesConfig kubernetesConfig, String name);

  VirtualService createOrReplaceVirtualServiceUsingFabric8Client(
      KubernetesConfig kubernetesConfig, VirtualService definition);

  KubernetesResource createOrReplaceVirtualServiceUsingK8sClient(
      KubernetesConfig kubernetesConfig, KubernetesResource virtualService);

  V1Secret getSecret(KubernetesConfig kubernetesConfig, String secretName);

  void deleteSecret(KubernetesConfig kubernetesConfig, String name);

  V1Secret createOrReplaceSecret(KubernetesConfig kubernetesConfig, V1Secret secret);

  String fetchReleaseHistoryFromConfigMap(KubernetesConfig kubernetesConfig, String infraMappingId) throws IOException;

  String fetchReleaseHistoryFromSecrets(KubernetesConfig kubernetesConfig, String infraMappingId) throws IOException;

  String fetchReleaseHistoryValue(V1ConfigMap configMap) throws IOException;

  String fetchReleaseHistoryValue(V1Secret secret) throws IOException;

  V1ObjectMeta saveReleaseHistory(KubernetesConfig kubernetesConfig, String releaseName, String releaseHistory,
      boolean storeInSecrets) throws IOException;

  List<V1Pod> getRunningPodsWithLabels(KubernetesConfig kubernetesConfig, String namespace, Map<String, String> labels);

  List<V1Pod> getRunningPodsWithLabels(KubernetesConfig kubernetesConfig, String namespace, List<String> labels);

  Object getVirtualServiceUsingK8sClient(KubernetesConfig kubernetesConfig, String name);

  VirtualService getVirtualServiceUsingFabric8Client(KubernetesConfig kubernetesConfig, String name);

  V1Deployment getDeployment(KubernetesConfig kubernetesConfig, String namespace, String name);

  VersionInfo getVersion(KubernetesConfig kubernetesConfig);

  String getVersionAsString(KubernetesConfig kubernetesConfig);

  void validateCEPermissions(KubernetesConfig kubernetesConfig);

  void validateCredentials(KubernetesConfig kubernetesConfig);

  void tryListControllersKubectl(KubernetesConfig kubernetesConfig, boolean useNewKubectlVersion);

  String getConfigFileContent(KubernetesConfig config);

  void persistKubernetesConfig(KubernetesConfig config, String workingDir) throws IOException;

  HasMetadata getControllerUsingFabric8Client(KubernetesConfig kubernetesConfig, String name, String namespace);

  CEK8sDelegatePrerequisite.MetricsServerCheck validateMetricsServer(KubernetesConfig kubernetesConfig);

  List<CEK8sDelegatePrerequisite.Rule> validateCEResourcePermissions(KubernetesConfig kubernetesConfig);

  List<V1SelfSubjectAccessReview> validateLightwingResourcePermissions(KubernetesConfig kubernetesConfig)
      throws Exception;

  List<V1Status> validateLightwingResourceExists(KubernetesConfig kubernetesConfig) throws Exception;

  @SneakyThrows V1TokenReviewStatus fetchTokenReviewStatus(KubernetesConfig kubernetesConfig);

  List<V1Secret> getSecretsWithLabelsAndFields(KubernetesConfig kubernetesConfig, String labels, String fields);

  V1Status deleteSecrets(KubernetesConfig kubernetesConfig, String labels, String fields);

  void modifyKubeConfigReadableProperties(String path);

  void modifyFileReadableProperties(String path);

  K8sServiceMetadata getK8sServiceMetadataUsingK8sClient(
      KubernetesConfig kubernetesConfig, String containerServiceName, String accountId);

  K8sServiceMetadata getK8sServiceMetadataUsingFabric8(
      KubernetesConfig kubernetesConfig, String containerServiceName, String accountId);

  Object patchCustomObject(
      KubernetesConfig kubernetesConfig, String name, K8sApiVersion apiVersion, String plural, String patch);
}
