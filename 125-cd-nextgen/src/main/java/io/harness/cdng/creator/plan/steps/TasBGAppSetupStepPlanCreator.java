/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BG_APP_SETUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.tas.TasBGAppSetupStep;
import io.harness.cdng.tas.TasBGAppSetupStepNode;
import io.harness.cdng.tas.asyncsteps.TasBGAppSetupStepV2;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasBGAppSetupStepPlanCreator extends CDPMSStepPlanCreatorV2<TasBGAppSetupStepNode> {
  @Inject private CDFeatureFlagHelper featureFlagService;
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(TAS_BG_APP_SETUP);
  }

  @Override
  public Class<TasBGAppSetupStepNode> getFieldClass() {
    return TasBGAppSetupStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TasBGAppSetupStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TasBGAppSetupStepNode stepElement) {
    return super.getStepParameters(ctx, stepElement);
  }

  @Override
  public StepType getStepSpecType(PlanCreationContext ctx, TasBGAppSetupStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_TAS_ASYNC_STEP_STRATEGY)) {
      return TasBGAppSetupStepV2.STEP_TYPE;
    }
    return TasBGAppSetupStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType(PlanCreationContext ctx, TasBGAppSetupStepNode stepElement) {
    if (featureFlagService.isEnabled(
            ctx.getMetadata().getAccountIdentifier(), FeatureName.CDS_TAS_ASYNC_STEP_STRATEGY)) {
      return OrchestrationFacilitatorType.ASYNC_CHAIN;
    }
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }
}
