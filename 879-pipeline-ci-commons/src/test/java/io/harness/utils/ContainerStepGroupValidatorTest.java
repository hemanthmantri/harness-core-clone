/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerStepGroupValidatorTest extends CategoryTest {
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateContainerStepGroupsWithParallelSteps() throws IOException {
    String parallelSteps = ""
        + "                  name: stgGroups\n"
        + "                  identifier: stgGroup\n"
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
        + "                            type: Run\n"
        + "                            name: Run_2\n"
        + "                            identifier: Run_2\n"
        + "                            spec:\n"
        + "                              connectorRef: account.harnessImage\n"
        + "                              image: alpine\n"
        + "                              shell: Sh\n"
        + "                              command: echo 'testing'\n"
        + "                        - stepGroup:\n"
        + "                            name: innerContainerStepGroup\n"
        + "                            identifier: innerContainerStepGroup\n"
        + "                            steps:\n"
        + "                              - step:\n"
        + "                                  type: Run\n"
        + "                                  name: Run_3\n"
        + "                                  identifier: Run_3\n"
        + "                                  spec:\n"
        + "                                    connectorRef: account.harnessImage\n"
        + "                                    image: alpine\n"
        + "                                    shell: Sh\n"
        + "                                    command: echo 'testing'\n"
        + "                            stepGroupInfra:\n"
        + "                              type: KubernetesDirect\n"
        + "                              spec:\n"
        + "                                connectorRef: CiK8sNewConnector\n"
        + "                                namespace: tmp-dev-testing\n"
        + "                  stepGroupInfra:\n"
        + "                    type: KubernetesDirect\n"
        + "                    spec:\n"
        + "                      connectorRef: K8sClusterCiPlay\n"
        + "                      namespace: tmp-dev-testing\n";
    StepGroupElementConfig stepGroupElementConfig = YamlUtils.read(parallelSteps, StepGroupElementConfig.class);

    assertThatThrownBy(() -> ContainerStepGroupValidator.validateContainerStepGroup(stepGroupElementConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Nested step group [innerContainerStepGroup] not supported in container step group");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateContainerStepGroupsWithNestedStepGroup() throws IOException {
    String nestedStepGroup = ""
        + "                  name: stgGroups\n"
        + "                  identifier: stgGroup\n"
        + "                  steps:\n"
        + "                    - stepGroup:\n"
        + "                        name: innerContainerStepGroup\n"
        + "                        identifier: innerContainerStepGroup\n"
        + "                        steps:\n"
        + "                          - step:\n"
        + "                              type: Run\n"
        + "                              name: Run_3\n"
        + "                              identifier: Run_3\n"
        + "                              spec:\n"
        + "                                connectorRef: account.harnessImage\n"
        + "                                image: alpine\n"
        + "                                shell: Sh\n"
        + "                                command: echo 'testing'\n"
        + "                        stepGroupInfra:\n"
        + "                          type: KubernetesDirect\n"
        + "                          spec:\n"
        + "                            connectorRef: CiK8sNewConnector\n"
        + "                            namespace: tmp-dev-testing\n"
        + "                  stepGroupInfra:\n"
        + "                    type: KubernetesDirect\n"
        + "                    spec:\n"
        + "                      connectorRef: K8sClusterCiPlay\n"
        + "                      namespace: tmp-dev-testing\n";
    StepGroupElementConfig stepGroupElementConfig = YamlUtils.read(nestedStepGroup, StepGroupElementConfig.class);

    assertThatThrownBy(() -> ContainerStepGroupValidator.validateContainerStepGroup(stepGroupElementConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Nested step group [innerContainerStepGroup] not supported in container step group");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateContainerStepGroupsWithSerialSteps() throws IOException {
    String parallelSteps = ""
        + "                  name: containerStepGroup\n"
        + "                  identifier: containerStepGroup\n"
        + "                  steps:\n"
        + "                    - stepGroup:\n"
        + "                        name: innerStepGroup\n"
        + "                        identifier: innerStepGroup\n"
        + "                        steps:\n"
        + "                          - step:\n"
        + "                              type: ShellScript\n"
        + "                              name: ShellScript_1\n"
        + "                              identifier: ShellScript_1\n"
        + "                              spec:\n"
        + "                                shell: Bash\n"
        + "                                onDelegate: true\n"
        + "                                source:\n"
        + "                                  type: Inline\n"
        + "                                  spec:\n"
        + "                                    script: echo 'testing'\n"
        + "                                environmentVariables: []\n"
        + "                                outputVariables: []\n"
        + "                              timeout: 10m\n"
        + "                    - step:\n"
        + "                        type: ShellScript\n"
        + "                        name: ShellScript_2\n"
        + "                        identifier: ShellScript_2\n"
        + "                        spec:\n"
        + "                          shell: Bash\n"
        + "                          onDelegate: true\n"
        + "                          source:\n"
        + "                            type: Inline\n"
        + "                            spec:\n"
        + "                              script: echo 'testing'\n"
        + "                          environmentVariables: []\n"
        + "                          outputVariables: []\n"
        + "                        timeout: 10m\n"
        + "                  stepGroupInfra:\n"
        + "                    type: KubernetesDirect\n"
        + "                    spec:\n"
        + "                      connectorRef: CiK8sNewConnector\n"
        + "                      namespace: tmp-dev-testing";
    StepGroupElementConfig stepGroupElementConfig = YamlUtils.read(parallelSteps, StepGroupElementConfig.class);

    assertThatThrownBy(() -> ContainerStepGroupValidator.validateContainerStepGroup(stepGroupElementConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Nested step group [innerStepGroup] not supported in container step group");
  }
}
