/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@OwnedBy(PIPELINE)
public class WaitStepParameters implements SpecParameters {
  ParameterField<String> duration;

  @Builder(builderMethodName = "infoBuilder")
  public WaitStepParameters(ParameterField<String> duration) {
    this.duration = duration;
  }

  public io.harness.steps.wait.WaitStepParameters toWaitStepParameterV0() {
    return io.harness.steps.wait.WaitStepParameters.infoBuilder().duration(getDuration()).build();
  }

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }
}
