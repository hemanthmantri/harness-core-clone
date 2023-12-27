/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.trafficrouting;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.ProviderType;

import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class TrafficRoutingResourceCreatorFactory {
  public TrafficRoutingResourceCreator create(K8sTrafficRoutingConfig k8sTrafficRoutingConfig) {
    ProviderType providerType = k8sTrafficRoutingConfig.getProviderConfig().getProviderType();
    switch (providerType) {
      case SMI:
        return new SMITrafficRoutingResourceCreator(k8sTrafficRoutingConfig);
      case ISTIO:
        return new IstioTrafficRoutingResourceCreator(k8sTrafficRoutingConfig);
      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported Traffic Routing provider type: %s", providerType.name()));
    }
  }

  public TrafficRoutingResourceCreator create(String plural) {
    switch (plural) {
      case SMITrafficRoutingResourceCreator.PLURAL: {
        return new SMITrafficRoutingResourceCreator();
      }
      case IstioTrafficRoutingResourceCreator.PLURAL: {
        return new IstioTrafficRoutingResourceCreator();
      }
      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported Plural Kind. Can't create Traffic Routing provider for kind type: %s", plural));
    }
  }
}
