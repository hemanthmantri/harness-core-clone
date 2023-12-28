/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.trafficrouting;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.trafficrouting.TrafficRoutingInfoDTO;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TrafficRoutingResourceCreatorTest extends CategoryTest {
  private static final String STABLE_SERVICE = "stable-service-name";
  public static final String STAGE_SERVICE = "stage-service-name";
  @Mock LogCallback logCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testUpdatePlaceHolderWhenStable() {
    assertThat(getTrafficRoutingCreator().updatePlaceHoldersIfExist("stable", STABLE_SERVICE, STAGE_SERVICE))
        .isEqualTo(STABLE_SERVICE);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testUpdatePlaceHolderWhenStage() {
    assertThat(getTrafficRoutingCreator().updatePlaceHoldersIfExist("stage", STABLE_SERVICE, STAGE_SERVICE))
        .isEqualTo(STAGE_SERVICE);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testUpdatePlaceHolderWhenCanary() {
    assertThat(getTrafficRoutingCreator().updatePlaceHoldersIfExist("canary", STABLE_SERVICE, STAGE_SERVICE))
        .isEqualTo(STAGE_SERVICE);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testUpdatePlaceHolderWithoutStableOrStage() {
    assertThat(getTrafficRoutingCreator().updatePlaceHoldersIfExist("some-host", null, null)).isEqualTo("some-host");
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testUpdatePlaceHolderWhenEmptyHost() {
    getTrafficRoutingCreator().updatePlaceHoldersIfExist(null, STABLE_SERVICE, STAGE_SERVICE);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testUpdatePlaceHolderStableWithoutStableService() {
    assertThat(getTrafficRoutingCreator().updatePlaceHoldersIfExist("stable", null, null)).isEqualTo("stable");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testUpdatePlaceHolderStageWithoutStageService() {
    assertThat(getTrafficRoutingCreator().updatePlaceHoldersIfExist("stage", null, null)).isEqualTo("stage");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testUpdatePlaceHolderCanaryWithoutStageService() {
    assertThat(getTrafficRoutingCreator().updatePlaceHoldersIfExist("canary", null, null)).isEqualTo("canary");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTrafficRoutingResourceNameWhenNull() {
    assertThat(getTrafficRoutingCreator().getTrafficRoutingResourceName(null, "-some-suffix", "defaultName"))
        .isEqualTo("defaultName");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTrafficRoutingResourceName() {
    assertThat(getTrafficRoutingCreator().getTrafficRoutingResourceName("resource-name", "-some-suffix", "defaultName"))
        .isEqualTo("resource-name-some-suffix");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTrafficRoutingResourceWithOverLimitResourceName() {
    String overLimitResourceName = RandomStringUtils.randomAlphabetic(260);
    String result =
        getTrafficRoutingCreator().getTrafficRoutingResourceName(overLimitResourceName, "-some-suffix", "defaultName");
    assertThat(result).endsWith("-some-suffix").hasSize(253);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetApiVersion() {
    Set<String> availableApis = Set.of("api1", "api2", "api3", "api4");
    Map<String, String> result = getTrafficRoutingCreator().getApiVersions(availableApis, logCallback);
    assertThat(result).contains(entry("key1", "api3")).contains(entry("key2", "api4"));
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetApiVersionDefault() {
    Set<String> availableApis = Set.of("api99", "api98", "api97");
    Map<String, String> result = getTrafficRoutingCreator().getApiVersions(availableApis, logCallback);
    assertThat(result).contains(entry("key1", "api3")).contains(entry("key2", "api5"));
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTrafficRoutingInfo() {
    List<KubernetesResource> k8sResources =
        List.of(KubernetesResource.builder()
                    .resourceId(KubernetesResourceId.builder().name("resourceName").kind("kind").build())
                    .value(Map.of("apiVersion", "api1"))
                    .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("resourceName2").kind("kind2").build())
                .value(Map.of("apiVersion", "api2"))
                .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("resourceName3").kind("kind3").build())
                .value(Map.of("apiVersion", "api3"))
                .build());

    Optional<TrafficRoutingInfoDTO> trafficRoutingInfo = getTrafficRoutingCreator().getTrafficRoutingInfo(k8sResources);

    assertThat(trafficRoutingInfo).isPresent();
    assertThat(trafficRoutingInfo.get().getName()).isEqualTo("resourceName");
    assertThat(trafficRoutingInfo.get().getVersion()).isEqualTo("api1");
    assertThat(trafficRoutingInfo.get().getPlural()).isEqualTo("kinds");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTrafficRoutingInfoIsEmpty() {
    List<KubernetesResource> k8sResources =
        List.of(KubernetesResource.builder()
                    .resourceId(KubernetesResourceId.builder().name("resourceName1").kind("kind1").build())
                    .value(Map.of("apiVersion", "api1"))
                    .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("resourceName2").kind("kind2").build())
                .value(Map.of("apiVersion", "api2"))
                .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().name("resourceName3").kind("kind3").build())
                .value(Map.of("apiVersion", "api3"))
                .build());

    Optional<TrafficRoutingInfoDTO> trafficRoutingInfo = getTrafficRoutingCreator().getTrafficRoutingInfo(k8sResources);

    assertThat(trafficRoutingInfo).isNotPresent();
  }

  public TrafficRoutingResourceCreator getTrafficRoutingCreator() {
    return new TrafficRoutingResourceCreator(K8sTrafficRoutingConfig.builder().build()) {
      @Override
      protected List<String> getManifests(
          String namespace, String releaseName, String stableName, String stageName, Map<String, String> apiVersions) {
        return null;
      }

      @Override
      protected Map<String, List<String>> getProviderVersionMap() {
        return Map.of("key1", List.of("api1", "api2", "api3"), "key2", List.of("api4", "api5"));
      }

      @Override
      protected String getMainResourceKind() {
        return "kind";
      }

      @Override
      protected String getMainResourceKindPlural() {
        return "kinds";
      }

      @Override
      public Optional<String> getSwapTrafficRoutingPatch(String stable, String stage) {
        return Optional.of("patch");
      }
    };
  }
}
