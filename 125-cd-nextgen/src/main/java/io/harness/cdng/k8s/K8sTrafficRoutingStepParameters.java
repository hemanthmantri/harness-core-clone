/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.k8s.trafficrouting.K8sTrafficRouting;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfigType;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("k8sTrafficRoutingStepParameters")
@RecasterAlias("io.harness.cdng.k8s.K8sTrafficRoutingStepParameters")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class K8sTrafficRoutingStepParameters extends K8sTrafficRoutingBaseStepInfo implements K8sSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public K8sTrafficRoutingStepParameters(K8sTrafficRoutingConfigType type, K8sTrafficRouting trafficRouting,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    super(type, trafficRouting, delegateSelectors);
  }

  @NotNull
  @Override
  public List<String> getCommandUnits() {
    return Arrays.asList(
        K8sCommandUnitConstants.Init, K8sCommandUnitConstants.TrafficRouting, K8sCommandUnitConstants.Apply);
  }
}
