/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskcontext.infra;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class RancherK8sInfraContextTest extends CategoryTest {
  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToConnectorInfoEmpty() {
    RancherK8sInfraContext rancherK8sInfraContext = RancherK8sInfraContext.builder().build();
    Optional<String> result = rancherK8sInfraContext.getConnectorInfo();
    assertThat(result).isEmpty();
  }
}
