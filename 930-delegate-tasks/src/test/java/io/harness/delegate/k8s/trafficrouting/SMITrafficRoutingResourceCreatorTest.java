/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.trafficrouting;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.trafficrouting.HeaderConfig;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.RouteType;
import io.harness.delegate.task.k8s.trafficrouting.RuleType;
import io.harness.delegate.task.k8s.trafficrouting.SMIProviderConfig;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoute;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRouteRule;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoutingDestination;
import io.harness.exception.HintException;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class SMITrafficRoutingResourceCreatorTest extends CategoryTest {
  private static final String namespace = "namespace";
  private static final String releaseName = "release-name";
  private static final Set<String> apiVersions = Set.of("split.smi-spec.io/v1alpha3", "specs.smi-spec.io/v1alpha3");
  private final KubernetesResource stableService =
      KubernetesResource.builder().resourceId(KubernetesResourceId.builder().name("stableService").build()).build();
  private final KubernetesResource stageService =
      KubernetesResource.builder().resourceId(KubernetesResourceId.builder().name("stageService").build()).build();

  @Mock LogCallback logCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSMITrafficRoutingManifestsWithOnlyDestinations() throws IOException {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder()
            .destinations(List.of(TrafficRoutingDestination.builder().host("stable").weight(80).build(),
                TrafficRoutingDestination.builder().host("stage").weight(20).build()))
            .providerConfig(SMIProviderConfig.builder().build())
            .build();
    String path = "/k8s/trafficrouting/TrafficSplit1.yaml";

    testK8sResourceCreation(k8sTrafficRoutingConfig, path);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSMITrafficRoutingManifestsWithoutStableAndStage() throws IOException {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder()
            .destinations(List.of(TrafficRoutingDestination.builder().host("first").weight(30).build(),
                TrafficRoutingDestination.builder().host("second").weight(20).build(),
                TrafficRoutingDestination.builder().host("third").weight(50).build()))
            .routes(List.of(TrafficRoute.builder().build()))
            .providerConfig(SMIProviderConfig.builder().rootService("rootSvc").build())
            .build();
    String path = "/k8s/trafficrouting/TrafficSplit2.yaml";

    SMITrafficRoutingResourceCreator smiTrafficRoutingResourceCreator =
        new SMITrafficRoutingResourceCreator(k8sTrafficRoutingConfig);
    List<KubernetesResource> trafficRoutingManifests = smiTrafficRoutingResourceCreator.createTrafficRoutingResources(
        namespace, releaseName, apiVersions, logCallback);

    assertThat(trafficRoutingManifests.size()).isEqualTo(1);
    assertEqualYaml(trafficRoutingManifests.get(0), path);
  }

  @Test(expected = HintException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSMITrafficRoutingManifestsWithoutStableAndStageAndRootService() throws IOException {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder()
            .destinations(List.of(TrafficRoutingDestination.builder().host("first").weight(30).build(),
                TrafficRoutingDestination.builder().host("second").weight(20).build(),
                TrafficRoutingDestination.builder().host("third").weight(50).build()))
            .routes(List.of(TrafficRoute.builder().routeType(RouteType.HTTP).build()))
            .providerConfig(SMIProviderConfig.builder().build())
            .build();

    SMITrafficRoutingResourceCreator smiTrafficRoutingResourceCreator =
        new SMITrafficRoutingResourceCreator(k8sTrafficRoutingConfig);
    smiTrafficRoutingResourceCreator.createTrafficRoutingResources(namespace, releaseName, apiVersions, logCallback);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSMITrafficRoutingManifestsWithHttpRouteUriRule() throws IOException {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig = getProviderConfig(RuleType.URI, "uri", "/metrics", null);
    String path1 = "/k8s/trafficrouting/TrafficSplit7.yaml";
    String path2 = "/k8s/trafficrouting/HTTPRouteGroupUri.yaml";

    testK8sResourceCreation(k8sTrafficRoutingConfig, path1, path2);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSMITrafficRoutingManifestsWithHttpRouteUriRuleWithoutName() throws IOException {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig = getProviderConfig(RuleType.URI, null, "/metrics", null);
    String path1 = "/k8s/trafficrouting/TrafficSplit6.yaml";
    String path2 = "/k8s/trafficrouting/HTTPRouteGroupUri2.yaml";

    try (MockedStatic<RandomStringUtils> utilities = Mockito.mockStatic(RandomStringUtils.class)) {
      utilities.when(() -> RandomStringUtils.randomAlphanumeric(4)).thenReturn("test");
      testK8sResourceCreation(k8sTrafficRoutingConfig, path1, path2);
    }
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSMITrafficRoutingManifestsWithHttpRouteMethodRule() throws IOException {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig = getProviderConfig(RuleType.METHOD, "method", "GET", null);
    String path1 = "/k8s/trafficrouting/TrafficSplit4.yaml";
    String path2 = "/k8s/trafficrouting/HTTPRouteGroupMethod.yaml";

    testK8sResourceCreation(k8sTrafficRoutingConfig, path1, path2);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSMITrafficRoutingManifestsWithHttpRouteHeaderRule() throws IOException {
    List<HeaderConfig> headerConfigs =
        List.of(HeaderConfig.builder().key("Content-Type").value("application/json").build(),
            HeaderConfig.builder().key("cookie").value("^(.*?;)?(type=insider)(;.*)?$").build(),
            HeaderConfig.builder().key("user-agent").value(".*Android.*").build());

    K8sTrafficRoutingConfig k8sTrafficRoutingConfig = getProviderConfig(RuleType.HEADER, "header", null, headerConfigs);
    String path1 = "/k8s/trafficrouting/TrafficSplit5.yaml";
    String path2 = "/k8s/trafficrouting/HTTPRouteGroupHeader.yaml";

    testK8sResourceCreation(k8sTrafficRoutingConfig, path1, path2);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSMITrafficRoutingManifestsMultipleRoutes() throws IOException {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder()
            .providerConfig(SMIProviderConfig.builder().build())
            .destinations(List.of(TrafficRoutingDestination.builder().host("stable").weight(80).build(),
                TrafficRoutingDestination.builder().host("stage").weight(20).build()))
            .routes(List.of(
                TrafficRoute.builder()
                    .routeType(RouteType.HTTP)
                    .rules(List.of(
                        TrafficRouteRule.builder().ruleType(RuleType.URI).value("/metrics").name("uri").build()))
                    .build(),
                TrafficRoute.builder()
                    .routeType(RouteType.HTTP)
                    .rules(List.of(
                        TrafficRouteRule.builder().ruleType(RuleType.METHOD).value("GET").name("method").build()))
                    .build()))
            .build();

    String path1 = "/k8s/trafficrouting/TrafficSplit3.yaml";
    String path2 = "/k8s/trafficrouting/HTTPRouteGroupUri.yaml";
    String path3 = "/k8s/trafficrouting/HTTPRouteGroupMethod.yaml";

    testK8sResourceCreation(k8sTrafficRoutingConfig, path1, path2, path3);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetMainResourceKind() {
    assertThat(new SMITrafficRoutingResourceCreator().getMainResourceKind()).isEqualTo("TrafficSplit");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetMainResourceKindPlural() {
    assertThat(new SMITrafficRoutingResourceCreator().getMainResourceKindPlural()).isEqualTo("trafficsplits");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSwapTrafficRoutingPatch() {
    String expectedPatch =
        "[ { \"op\": \"replace\", \"path\": \"/spec/backends\", \"value\": [{\"service\":\"service\",\"weight\":100},{\"service\":\"service-stage\",\"weight\":0}] }]";

    Optional<String> optionalPatch =
        new SMITrafficRoutingResourceCreator().getSwapTrafficRoutingPatch("service", "service-stage");
    assertThat(optionalPatch).isPresent();
    assertThat(optionalPatch.get()).contains(expectedPatch);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSwapTrafficRoutingPatchIsEmpty() {
    assertThat(new SMITrafficRoutingResourceCreator().getSwapTrafficRoutingPatch("stable", null)).isNotPresent();
    assertThat(new SMITrafficRoutingResourceCreator().getSwapTrafficRoutingPatch(null, "stage")).isNotPresent();
    assertThat(new SMITrafficRoutingResourceCreator().getSwapTrafficRoutingPatch(null, null)).isNotPresent();
  }
  private void testK8sResourceCreation(K8sTrafficRoutingConfig k8sTrafficRoutingConfig, String... paths)
      throws IOException {
    SMITrafficRoutingResourceCreator smiTrafficRoutingResourceCreator =
        new SMITrafficRoutingResourceCreator(k8sTrafficRoutingConfig);
    List<KubernetesResource> trafficRoutingManifests = smiTrafficRoutingResourceCreator.createTrafficRoutingResources(
        namespace, releaseName, stableService, stageService, apiVersions, logCallback);

    assertThat(trafficRoutingManifests.size()).isEqualTo(paths.length);

    for (int i = 0; i < paths.length; i++) {
      assertEqualYaml(trafficRoutingManifests.get(i), paths[i]);
    }
  }

  private void assertEqualYaml(KubernetesResource k8sResource, String path) throws IOException {
    URL url = this.getClass().getResource(path);
    String fileContents = Resources.toString(url, Charsets.UTF_8);
    assertThat(k8sResource.getSpec()).isEqualTo(fileContents);
  }

  private K8sTrafficRoutingConfig getProviderConfig(
      RuleType ruleType, String name, String value, List<HeaderConfig> headerConfigs) {
    return K8sTrafficRoutingConfig.builder()
        .destinations(List.of(TrafficRoutingDestination.builder().host("stable").weight(80).build(),
            TrafficRoutingDestination.builder().host("stage").weight(20).build()))
        .providerConfig(SMIProviderConfig.builder().build())
        .routes(List.of(TrafficRoute.builder()
                            .routeType(RouteType.HTTP)
                            .rules(List.of(TrafficRouteRule.builder()
                                               .ruleType(ruleType)
                                               .name(name)
                                               .value(value)
                                               .headerConfigs(headerConfigs)
                                               .build()))
                            .build()))
        .build();
  }
}
