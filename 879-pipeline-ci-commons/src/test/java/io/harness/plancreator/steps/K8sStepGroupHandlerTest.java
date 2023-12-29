/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.ContainerTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.plugin.InitContainerV2StepInfo;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
@OwnedBy(CDP)
public class K8sStepGroupHandlerTest extends ContainerTestBase {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks private K8sStepGroupHandler k8sStepGroupHandler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHandleGroupWithParallelSteps() throws IOException {
    String groupWithParallelSteps = ""
        + "                  name: containerStepGroup_1\n"
        + "                  identifier: containerStepGroup\n"
        + "                  steps:\n"
        + "                    - parallel:\n"
        + "                        - step:\n"
        + "                            type: Run\n"
        + "                            name: Run_1\n"
        + "                            identifier: Run_1\n"
        + "                            spec:\n"
        + "                              connectorRef: account.harnessImage\n"
        + "                              image: alpine\n"
        + "                              shell: Sh\n"
        + "                              command: echo 'test'\n"
        + "                        - step:\n"
        + "                            type: GitClone\n"
        + "                            name: GitClone_1\n"
        + "                            identifier: GitClone_1\n"
        + "                            spec:\n"
        + "                              connectorRef: AwsSamGitHub\n"
        + "                              build:\n"
        + "                                type: branch\n"
        + "                                spec:\n"
        + "                                  branch: ivan\n"
        + "                    - step:\n"
        + "                        type: Run\n"
        + "                        name: Run_2\n"
        + "                        identifier: Run_2\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          command: sleep 4m\n"
        + "                  stepGroupInfra:\n"
        + "                    type: KubernetesDirect\n"
        + "                    spec:\n"
        + "                      connectorRef: K8sClusterCiPlay\n"
        + "                      namespace: tmp-dev-testing";
    StepGroupElementConfig stepGroupElementConfig =
        YamlUtils.read(groupWithParallelSteps, StepGroupElementConfig.class);

    PlanCreationContext ctx = getPlanCreationContext(groupWithParallelSteps);

    YamlField stepsField = YamlUtils.readTree(""
        + "                    - parallel:\n"
        + "                        - step:\n"
        + "                            type: Run\n"
        + "                            name: Run_1\n"
        + "                            identifier: Run_1\n"
        + "                            spec:\n"
        + "                              connectorRef: account.harnessImage\n"
        + "                              image: alpine\n"
        + "                              shell: Sh\n"
        + "                              command: echo 'test'\n"
        + "                        - step:\n"
        + "                            type: GitClone\n"
        + "                            name: GitClone_1\n"
        + "                            identifier: GitClone_1\n"
        + "                            spec:\n"
        + "                              connectorRef: AwsSamGitHub\n"
        + "                              build:\n"
        + "                                type: branch\n"
        + "                                spec:\n"
        + "                                  branch: ivan\n"
        + "                    - step:\n"
        + "                        type: Run\n"
        + "                        name: Run_2\n"
        + "                        identifier: Run_2\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          command: sleep 4m");

    PlanNode handle = k8sStepGroupHandler.handle(stepGroupElementConfig, ctx, stepsField);

    List<ExecutionWrapperConfig> steps =
        ((InitContainerV2StepInfo) handle.getStepParameters()).getStepsExecutionConfig().getSteps();
    assertThat(steps.size()).isEqualTo(2);
    assertThat(steps.get(1).getStep()).isNotNull();
    JsonNode parallel = steps.get(0).getParallel();
    assertThat(parallel).isNotNull();
    assertThat(parallel.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHandleGroupWithStep() throws IOException {
    String groupWithStep = ""
        + "                  name: containerStepGroup_1\n"
        + "                  identifier: containerStepGroup\n"
        + "                  steps:\n"
        + "                    - step:\n"
        + "                        type: Run\n"
        + "                        name: Run_2\n"
        + "                        identifier: Run_2\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          command: sleep 4m\n"
        + "                  stepGroupInfra:\n"
        + "                    type: KubernetesDirect\n"
        + "                    spec:\n"
        + "                      connectorRef: K8sClusterCiPlay\n"
        + "                      namespace: tmp-dev-testing";

    StepGroupElementConfig stepGroupElementConfig = YamlUtils.read(groupWithStep, StepGroupElementConfig.class);

    PlanCreationContext ctx = getPlanCreationContext(groupWithStep);

    YamlField stepsField = YamlUtils.readTree(""
        + "                    - step:\n"
        + "                        type: Run\n"
        + "                        name: Run_2\n"
        + "                        identifier: Run_2\n"
        + "                        spec:\n"
        + "                          connectorRef: account.harnessImage\n"
        + "                          image: alpine\n"
        + "                          shell: Sh\n"
        + "                          command: sleep 4m");

    PlanNode handle = k8sStepGroupHandler.handle(stepGroupElementConfig, ctx, stepsField);

    List<ExecutionWrapperConfig> steps =
        ((InitContainerV2StepInfo) handle.getStepParameters()).getStepsExecutionConfig().getSteps();
    assertThat(steps.size()).isEqualTo(1);
    assertThat(steps.get(0).getStep()).isNotNull();
  }

  private PlanCreationContext getPlanCreationContext(String groupWithStep) throws IOException {
    String parallelStepsYaml = YamlUtils.injectUuid(groupWithStep);
    YamlField parallelStepsField = YamlUtils.readTree(parallelStepsYaml);
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(parallelStepsField).build();
    doReturn(new byte[] {'a'}).when(kryoSerializer).asBytes(any());
    return ctx;
  }
}
