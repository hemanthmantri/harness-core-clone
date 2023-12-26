/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.taskcontext.infra.InfraContext;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class RancherK8sInfraDelegateConfigTest extends CategoryTest {
  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void toInfraContextEmptyTest() {
    RancherK8sInfraDelegateConfig k8sInfraDelegateConfig = RancherK8sInfraDelegateConfig.builder().build();
    InfraContext result = k8sInfraDelegateConfig.toInfraContext(null);
    assertThat(result).isNotNull();
    assertThat(result.getConnectorInfo()).isEmpty();
  }
}
