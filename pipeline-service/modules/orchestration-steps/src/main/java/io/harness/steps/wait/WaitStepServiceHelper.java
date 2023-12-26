/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;

import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@UtilityClass
@OwnedBy(CDC)
public class WaitStepServiceHelper {
  public WaitStepParameters getWaitStepParameters(StepBaseParameters stepParameters) {
    String version = stepParameters.getSpec().getVersion();
    switch (version) {
      case HarnessYamlVersion.V0:
        return (WaitStepParameters) stepParameters.getSpec();
      case HarnessYamlVersion.V1:
        return ((io.harness.steps.wait.v1.WaitStepParameters) stepParameters.getSpec()).toWaitStepParameterV0();
      default:
        throw new InvalidRequestException(String.format("Version %s not supported", version));
    }
  }
}
