/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.plancreator.steps.StepGroupInfra.Type.KUBERNETES_DIRECT;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidArgumentsException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
public class ContainerStepGroupValidator {
  public static void validateContainerStepGroup(@NotNull StepGroupElementConfig stepGroup) {
    if (!isContainerStepGroup(stepGroup)) {
      return;
    }

    try {
      validateContainerStepGroupSteps(stepGroup.getSteps());
    } catch (InvalidArgumentsException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("Unable to validate container step group", ex);
    }
  }

  private static boolean isContainerStepGroup(StepGroupElementConfig stepGroup) {
    return stepGroup != null && stepGroup.getStepGroupInfra() != null
        && stepGroup.getStepGroupInfra().getType() == KUBERNETES_DIRECT;
  }

  private static void validateContainerStepGroupSteps(List<ExecutionWrapperConfig> containerStepGroupSteps)
      throws IOException {
    if (isEmpty(containerStepGroupSteps)) {
      return;
    }

    for (ExecutionWrapperConfig containerStepGroupStep : containerStepGroupSteps) {
      if (containerStepGroupStep == null) {
        continue;
      }

      if (isContainerStepGroup(containerStepGroupStep)) {
        throw new InvalidArgumentsException(
            format("Nested container step group [%s] not supported in container step group",
                containerStepGroupStep.getStepGroup().get("identifier").asText()));
      }
      if (containerStepGroupStep.getParallel() != null && !containerStepGroupStep.getParallel().isNull()) {
        List<ExecutionWrapperConfig> parallelSteps = getParallelStepElementConfigSections(containerStepGroupStep);

        validateContainerStepGroupSteps(parallelSteps);
      }
    }
  }

  private static boolean isContainerStepGroup(ExecutionWrapperConfig containerStepGroupStep) throws IOException {
    if (isNull(containerStepGroupStep.getStepGroup())) {
      return false;
    }

    StepGroupElementConfig stepGroupElementConfig =
        YamlUtils.convert(containerStepGroupStep.getStepGroup(), StepGroupElementConfig.class);
    return isContainerStepGroup(stepGroupElementConfig);
  }

  private static List<ExecutionWrapperConfig> getParallelStepElementConfigSections(
      ExecutionWrapperConfig containerStepGroupStep) throws IOException {
    ParallelStepElementConfig parallelStepElementConfig =
        YamlUtils.convert(containerStepGroupStep.getParallel(), ParallelStepElementConfig.class);
    return parallelStepElementConfig != null ? parallelStepElementConfig.getSections() : Collections.emptyList();
  }
}
