/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.trafficrouting;

import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sTrafficRoutingConfigTest extends CategoryTest {
  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetNormalizedDestinations() {
    List<TrafficRoutingDestination> destinations =
        List.of(TrafficRoutingDestination.builder().host("host1").weight(20).build(),
            TrafficRoutingDestination.builder().host("host2").weight(30).build(),
            TrafficRoutingDestination.builder().host("host3").weight(50).build());
    K8sTrafficRoutingConfig config = K8sTrafficRoutingConfig.builder().destinations(destinations).build();

    List<TrafficRoutingDestination> normalizedDestinations = config.getNormalizedDestinations();

    assertThat(normalizedDestinations.get(0).getHost()).isEqualTo("host1");
    assertThat(normalizedDestinations.get(0).getWeight()).isEqualTo(20);
    assertThat(normalizedDestinations.get(1).getHost()).isEqualTo("host2");
    assertThat(normalizedDestinations.get(1).getWeight()).isEqualTo(30);
    assertThat(normalizedDestinations.get(2).getHost()).isEqualTo("host3");
    assertThat(normalizedDestinations.get(2).getWeight()).isEqualTo(50);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetNormalizedDestinationsUnder10() {
    List<TrafficRoutingDestination> destinations =
        List.of(TrafficRoutingDestination.builder().host("host1").weight(2).build(),
            TrafficRoutingDestination.builder().host("host2").weight(3).build(),
            TrafficRoutingDestination.builder().host("host3").weight(5).build());

    K8sTrafficRoutingConfig config = K8sTrafficRoutingConfig.builder().destinations(destinations).build();

    List<TrafficRoutingDestination> normalizedDestinations = config.getNormalizedDestinations();

    assertThat(normalizedDestinations.get(0).getHost()).isEqualTo("host1");
    assertThat(normalizedDestinations.get(0).getWeight()).isEqualTo(20);
    assertThat(normalizedDestinations.get(1).getHost()).isEqualTo("host2");
    assertThat(normalizedDestinations.get(1).getWeight()).isEqualTo(30);
    assertThat(normalizedDestinations.get(2).getHost()).isEqualTo("host3");
    assertThat(normalizedDestinations.get(2).getWeight()).isEqualTo(50);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetNormalizedDestinationsOver100() {
    List<TrafficRoutingDestination> destinations =
        List.of(TrafficRoutingDestination.builder().host("host1").weight(200).build(),
            TrafficRoutingDestination.builder().host("host2").weight(300).build(),
            TrafficRoutingDestination.builder().host("host3").weight(500).build());
    K8sTrafficRoutingConfig config = K8sTrafficRoutingConfig.builder().destinations(destinations).build();

    List<TrafficRoutingDestination> normalizedDestinations = config.getNormalizedDestinations();

    assertThat(normalizedDestinations.get(0).getHost()).isEqualTo("host1");
    assertThat(normalizedDestinations.get(0).getWeight()).isEqualTo(20);
    assertThat(normalizedDestinations.get(1).getHost()).isEqualTo("host2");
    assertThat(normalizedDestinations.get(1).getWeight()).isEqualTo(30);
    assertThat(normalizedDestinations.get(2).getHost()).isEqualTo("host3");
    assertThat(normalizedDestinations.get(2).getWeight()).isEqualTo(50);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetNormalizedDestinationsCustomNumber() {
    List<TrafficRoutingDestination> destinations =
        List.of(TrafficRoutingDestination.builder().host("host1").weight(1).build(),
            TrafficRoutingDestination.builder().host("host2").weight(3).build(),
            TrafficRoutingDestination.builder().host("host3").weight(4).build());
    K8sTrafficRoutingConfig config = K8sTrafficRoutingConfig.builder().destinations(destinations).build();

    List<TrafficRoutingDestination> normalizedDestinations = config.getNormalizedDestinations();

    assertThat(normalizedDestinations.get(0).getHost()).isEqualTo("host1");
    assertThat(normalizedDestinations.get(0).getWeight()).isEqualTo(12);
    assertThat(normalizedDestinations.get(1).getHost()).isEqualTo("host2");
    assertThat(normalizedDestinations.get(1).getWeight()).isEqualTo(38);
    assertThat(normalizedDestinations.get(2).getHost()).isEqualTo("host3");
    assertThat(normalizedDestinations.get(2).getWeight()).isEqualTo(50);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetNormalizedDestinationsWhenWeightAre0() {
    List<TrafficRoutingDestination> destinations =
        List.of(TrafficRoutingDestination.builder().host("host1").weight(0).build(),
            TrafficRoutingDestination.builder().host("host2").weight(0).build(),
            TrafficRoutingDestination.builder().host("host3").weight(0).build());
    K8sTrafficRoutingConfig config = K8sTrafficRoutingConfig.builder().destinations(destinations).build();

    List<TrafficRoutingDestination> normalizedDestinations = config.getNormalizedDestinations();

    assertThat(normalizedDestinations.get(0).getHost()).isEqualTo("host1");
    assertThat(normalizedDestinations.get(0).getWeight()).isEqualTo(34);
    assertThat(normalizedDestinations.get(1).getHost()).isEqualTo("host2");
    assertThat(normalizedDestinations.get(1).getWeight()).isEqualTo(33);
    assertThat(normalizedDestinations.get(2).getHost()).isEqualTo("host3");
    assertThat(normalizedDestinations.get(2).getWeight()).isEqualTo(33);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetNormalizedDestinationsWhenWeightNotProvided() {
    List<TrafficRoutingDestination> destinations = List.of(TrafficRoutingDestination.builder().host("host1").build(),
        TrafficRoutingDestination.builder().host("host2").build(),
        TrafficRoutingDestination.builder().host("host3").build(),
        TrafficRoutingDestination.builder().host("host4").build(),
        TrafficRoutingDestination.builder().host("host5").build(),
        TrafficRoutingDestination.builder().host("host6").build(),
        TrafficRoutingDestination.builder().host("host7").build(),
        TrafficRoutingDestination.builder().host("host8").build());
    K8sTrafficRoutingConfig config = K8sTrafficRoutingConfig.builder().destinations(destinations).build();

    List<TrafficRoutingDestination> normalizedDestinations = config.getNormalizedDestinations();

    assertThat(normalizedDestinations.get(0).getHost()).isEqualTo("host1");
    assertThat(normalizedDestinations.get(0).getWeight()).isEqualTo(12);
    assertThat(normalizedDestinations.get(1).getHost()).isEqualTo("host2");
    assertThat(normalizedDestinations.get(1).getWeight()).isEqualTo(12);
    assertThat(normalizedDestinations.get(2).getHost()).isEqualTo("host3");
    assertThat(normalizedDestinations.get(2).getWeight()).isEqualTo(12);
    assertThat(normalizedDestinations.get(3).getHost()).isEqualTo("host4");
    assertThat(normalizedDestinations.get(3).getWeight()).isEqualTo(12);
    assertThat(normalizedDestinations.get(4).getHost()).isEqualTo("host5");
    assertThat(normalizedDestinations.get(4).getWeight()).isEqualTo(13);
    assertThat(normalizedDestinations.get(5).getHost()).isEqualTo("host6");
    assertThat(normalizedDestinations.get(5).getWeight()).isEqualTo(13);
    assertThat(normalizedDestinations.get(6).getHost()).isEqualTo("host7");
    assertThat(normalizedDestinations.get(6).getWeight()).isEqualTo(13);
    assertThat(normalizedDestinations.get(7).getHost()).isEqualTo("host8");
    assertThat(normalizedDestinations.get(7).getWeight()).isEqualTo(13);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetNormalizedDestinationsWhenMixed7Weights() {
    List<TrafficRoutingDestination> destinations =
        List.of(TrafficRoutingDestination.builder().host("host1").weight(0).build(),
            TrafficRoutingDestination.builder().host("host2").weight(34).build(),
            TrafficRoutingDestination.builder().host("host3").weight(13).build(),
            TrafficRoutingDestination.builder().host("host4").weight(23).build(),
            TrafficRoutingDestination.builder().host("host5").weight(6).build(),
            TrafficRoutingDestination.builder().host("host6").weight(25).build(),
            TrafficRoutingDestination.builder().host("host7").weight(0).build());
    K8sTrafficRoutingConfig config = K8sTrafficRoutingConfig.builder().destinations(destinations).build();

    List<TrafficRoutingDestination> normalizedDestinations = config.getNormalizedDestinations();

    assertThat(normalizedDestinations.get(0).getHost()).isEqualTo("host1");
    assertThat(normalizedDestinations.get(0).getWeight()).isEqualTo(0);
    assertThat(normalizedDestinations.get(1).getHost()).isEqualTo("host2");
    assertThat(normalizedDestinations.get(1).getWeight()).isEqualTo(33);
    assertThat(normalizedDestinations.get(2).getHost()).isEqualTo("host3");
    assertThat(normalizedDestinations.get(2).getWeight()).isEqualTo(13);
    assertThat(normalizedDestinations.get(3).getHost()).isEqualTo("host4");
    assertThat(normalizedDestinations.get(3).getWeight()).isEqualTo(23);
    assertThat(normalizedDestinations.get(4).getHost()).isEqualTo("host5");
    assertThat(normalizedDestinations.get(4).getWeight()).isEqualTo(6);
    assertThat(normalizedDestinations.get(5).getHost()).isEqualTo("host6");
    assertThat(normalizedDestinations.get(5).getWeight()).isEqualTo(25);
    assertThat(normalizedDestinations.get(6).getHost()).isEqualTo("host7");
    assertThat(normalizedDestinations.get(6).getWeight()).isEqualTo(0);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetNormalizedDestinationsWhenMixed8Weights() {
    List<TrafficRoutingDestination> destinations =
        List.of(TrafficRoutingDestination.builder().host("host1").weight(0).build(),
            TrafficRoutingDestination.builder().host("host2").weight(34).build(),
            TrafficRoutingDestination.builder().host("host3").weight(13).build(),
            TrafficRoutingDestination.builder().host("host4").weight(0).build(),
            TrafficRoutingDestination.builder().host("host5").weight(23).build(),
            TrafficRoutingDestination.builder().host("host6").weight(6).build(),
            TrafficRoutingDestination.builder().host("host7").weight(28).build(),
            TrafficRoutingDestination.builder().host("host8").weight(0).build());
    K8sTrafficRoutingConfig config = K8sTrafficRoutingConfig.builder().destinations(destinations).build();

    List<TrafficRoutingDestination> normalizedDestinations = config.getNormalizedDestinations();

    assertThat(normalizedDestinations.get(0).getHost()).isEqualTo("host1");
    assertThat(normalizedDestinations.get(0).getWeight()).isEqualTo(0);
    assertThat(normalizedDestinations.get(1).getHost()).isEqualTo("host2");
    assertThat(normalizedDestinations.get(1).getWeight()).isEqualTo(32);
    assertThat(normalizedDestinations.get(2).getHost()).isEqualTo("host3");
    assertThat(normalizedDestinations.get(2).getWeight()).isEqualTo(13);
    assertThat(normalizedDestinations.get(3).getHost()).isEqualTo("host4");
    assertThat(normalizedDestinations.get(3).getWeight()).isEqualTo(0);
    assertThat(normalizedDestinations.get(4).getHost()).isEqualTo("host5");
    assertThat(normalizedDestinations.get(4).getWeight()).isEqualTo(22);
    assertThat(normalizedDestinations.get(5).getHost()).isEqualTo("host6");
    assertThat(normalizedDestinations.get(5).getWeight()).isEqualTo(6);
    assertThat(normalizedDestinations.get(6).getHost()).isEqualTo("host7");
    assertThat(normalizedDestinations.get(6).getWeight()).isEqualTo(27);
    assertThat(normalizedDestinations.get(7).getHost()).isEqualTo("host8");
    assertThat(normalizedDestinations.get(7).getWeight()).isEqualTo(0);
  }
}
