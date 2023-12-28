/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_TRAFFIC_ROUTING_INFO;
import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.trafficrouting.TrafficRoutingInfoDTO;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1Secret;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class K8sReleaseTest extends CategoryTest {
  @Mock V1Secret v1Secret;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSetTrafficRoutingInfo() {
    try (MockedStatic<K8sReleaseSecretHelper> utilities = Mockito.mockStatic(K8sReleaseSecretHelper.class)) {
      K8sRelease k8sRelease = K8sRelease.builder().releaseSecret(v1Secret).build();
      TrafficRoutingInfoDTO trafficRoutingInfoDTO =
          TrafficRoutingInfoDTO.builder().name("resourceName").version("api1").plural("trafficsplits").build();
      k8sRelease.setTrafficRoutingInfo(trafficRoutingInfoDTO);
      utilities.verify(
          ()
              -> K8sReleaseSecretHelper.putAnnotationsItem(eq(v1Secret), eq(RELEASE_SECRET_TRAFFIC_ROUTING_INFO),
                  eq("{\"name\":\"resourceName\",\"plural\":\"trafficsplits\",\"version\":\"api1\"}")));
    }
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSetTrafficRoutingInfoWhenNull() {
    try (MockedStatic<K8sReleaseSecretHelper> utilities = Mockito.mockStatic(K8sReleaseSecretHelper.class)) {
      K8sRelease k8sRelease = K8sRelease.builder().releaseSecret(v1Secret).build();

      k8sRelease.setTrafficRoutingInfo(null);
      utilities.verifyNoInteractions();
    }
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTrafficRoutingInfo() {
    try (MockedStatic<K8sReleaseSecretHelper> utilities = Mockito.mockStatic(K8sReleaseSecretHelper.class)) {
      K8sRelease k8sRelease = K8sRelease.builder().releaseSecret(v1Secret).build();
      TrafficRoutingInfoDTO trafficRoutingInfoExpected =
          TrafficRoutingInfoDTO.builder().name("resourceName").version("api1").plural("trafficsplits").build();

      utilities
          .when(() -> K8sReleaseSecretHelper.getReleaseAnnotationValue(v1Secret, RELEASE_SECRET_TRAFFIC_ROUTING_INFO))
          .thenReturn("{\"name\":\"resourceName\",\"plural\":\"trafficsplits\",\"version\":\"api1\"}");

      TrafficRoutingInfoDTO trafficRoutingInfo = k8sRelease.getTrafficRoutingInfo();

      assertThat(trafficRoutingInfo).isEqualTo(trafficRoutingInfoExpected);
    }
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTrafficRoutingInfoReturnsNull() {
    try (MockedStatic<K8sReleaseSecretHelper> utilities = Mockito.mockStatic(K8sReleaseSecretHelper.class)) {
      K8sRelease k8sRelease = K8sRelease.builder().releaseSecret(v1Secret).build();
      utilities
          .when(() -> K8sReleaseSecretHelper.getReleaseAnnotationValue(v1Secret, RELEASE_SECRET_TRAFFIC_ROUTING_INFO))
          .thenReturn(null);

      TrafficRoutingInfoDTO trafficRoutingInfo = k8sRelease.getTrafficRoutingInfo();

      assertThat(trafficRoutingInfo).isNull();
    }
  }
}
