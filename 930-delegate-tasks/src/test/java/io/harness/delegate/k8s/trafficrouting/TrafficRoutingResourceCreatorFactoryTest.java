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
import io.harness.delegate.task.k8s.trafficrouting.IstioProviderConfig;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.SMIProviderConfig;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TrafficRoutingResourceCreatorFactoryTest extends CategoryTest {
  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetResourceCreatorIstio() {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder().providerConfig(IstioProviderConfig.builder().build()).build();
    TrafficRoutingResourceCreator resourceCreator =
        TrafficRoutingResourceCreatorFactory.create(k8sTrafficRoutingConfig);
    assertThat(resourceCreator).isInstanceOf(IstioTrafficRoutingResourceCreator.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetResourceCreatorSMI() {
    K8sTrafficRoutingConfig k8sTrafficRoutingConfig =
        K8sTrafficRoutingConfig.builder().providerConfig(SMIProviderConfig.builder().build()).build();
    TrafficRoutingResourceCreator resourceCreator =
        TrafficRoutingResourceCreatorFactory.create(k8sTrafficRoutingConfig);
    assertThat(resourceCreator).isInstanceOf(SMITrafficRoutingResourceCreator.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateResourceCreatorFromTrafficSplits() {
    TrafficRoutingResourceCreator trafficRoutingResourceCreator =
        TrafficRoutingResourceCreatorFactory.create("trafficsplits");

    assertThat(trafficRoutingResourceCreator).isInstanceOf(SMITrafficRoutingResourceCreator.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateResourceCreatorFromVirtualServices() {
    TrafficRoutingResourceCreator trafficRoutingResourceCreator =
        TrafficRoutingResourceCreatorFactory.create("virtualservices");

    assertThat(trafficRoutingResourceCreator).isInstanceOf(IstioTrafficRoutingResourceCreator.class);
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateResourceCreatorFromDummy() {
    TrafficRoutingResourceCreatorFactory.create("dummy");
  }
}
