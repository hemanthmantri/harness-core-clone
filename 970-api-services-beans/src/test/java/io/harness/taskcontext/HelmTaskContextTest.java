/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskcontext;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.taskcontext.infra.AzureK8sInfraContext;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class HelmTaskContextTest extends CategoryTest {
  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorInfoEmpty() {
    HelmTaskContext helmTaskContext = HelmTaskContext.builder().build();
    Optional<String> result = helmTaskContext.getConnectorInfo();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorInfo() {
    HelmTaskContext helmTaskContext =
        HelmTaskContext.builder()
            .infraContext(AzureK8sInfraContext.builder()
                              .azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET)
                              .azureConnectorEnvironmentType(AzureEnvironmentType.AZURE)
                              .clientId("clientId")
                              .tenantId("tenantId")
                              .delegateId("delegateId")
                              .build())
            .build();
    Optional<String> result = helmTaskContext.getConnectorInfo();
    assertThat(result).isNotEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testAddHint() {
    HelmTaskContext helmTaskContext = HelmTaskContext.builder().build();
    helmTaskContext.addHint("hint");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testAddHints() {
    HelmTaskContext helmTaskContext = HelmTaskContext.builder().build();
    helmTaskContext.addHints(Collections.singletonList("hint"));
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetHintsEmpty() {
    HelmTaskContext helmTaskContext = HelmTaskContext.builder().build();
    Set<String> hints = helmTaskContext.getHints();
    assertThat(hints).isEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetHints() {
    HelmTaskContext helmTaskContext = HelmTaskContext.builder().build();
    helmTaskContext.addHints(Collections.singletonList("hint"));
    Set<String> hints = helmTaskContext.getHints();
    assertThat(hints).isNotEmpty();
    assertThat(hints.stream().findFirst().get()).isEqualTo("hint");
  }
}
