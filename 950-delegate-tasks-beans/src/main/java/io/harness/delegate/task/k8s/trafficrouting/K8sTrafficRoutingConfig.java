/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.trafficrouting;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.tuple.Triple;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@SuperBuilder
@Data
@Builder
public class K8sTrafficRoutingConfig {
  List<TrafficRoute> routes;
  List<TrafficRoutingDestination> destinations;
  ProviderConfig providerConfig;
  K8sTrafficRoutingConfigType type;

  public List<TrafficRoutingDestination> getNormalizedDestinations() {
    return getDestinationsNormalizationInfo()
        .stream()
        .map(dest -> TrafficRoutingDestination.builder().host(dest.getLeft()).weight(dest.getRight()).build())
        .collect(Collectors.toList());
  }

  public List<Triple<String, Integer, Integer>> getDestinationsNormalizationInfo() {
    int sum = getDestinationsWeightSum();

    List<Triple<String, Integer, Integer>> normalizedDestinations = new LinkedList<>();
    AtomicInteger normalizedSum = new AtomicInteger();
    destinations.stream().forEach(dest -> {
      int normalizedWeight = normalizeWeight(sum, dest.getWeight());
      normalizedDestinations.add(Triple.of(dest.getHost(), dest.getWeight(), normalizedWeight));
      normalizedSum.addAndGet(normalizedWeight);
    });

    int correctionCount = Math.abs(100 - normalizedSum.get());
    int step = 1;
    if (normalizedSum.get() > 100) {
      step = -1;
    }

    List<Triple<String, Integer, Integer>> dests = new LinkedList<>();
    for (int i = 0; i < normalizedDestinations.size(); i++) {
      if (normalizedDestinations.get(i).getRight() > 0 && correctionCount > 0) {
        dests.add(Triple.of(normalizedDestinations.get(i).getLeft(), normalizedDestinations.get(i).getMiddle(),
            normalizedDestinations.get(i).getRight() + step));
        correctionCount--;
      } else {
        dests.add(Triple.of(normalizedDestinations.get(i).getLeft(), normalizedDestinations.get(i).getMiddle(),
            normalizedDestinations.get(i).getRight()));
      }
    }

    return dests;
  }

  public boolean isNormalizationNeeded() {
    return getDestinationsWeightSum() != 100;
  }

  private int getDestinationsWeightSum() {
    return destinations.stream()
        .filter(destination -> destination.getWeight() != null)
        .mapToInt(TrafficRoutingDestination::getWeight)
        .sum();
  }

  private int normalizeWeight(int sum, Integer weight) {
    if (sum == 0) {
      return (int) Math.round((double) 100 / (double) destinations.size());
    }
    return weight == null ? 0 : (int) Math.round((double) weight * (double) 100 / (double) sum);
  }
}
