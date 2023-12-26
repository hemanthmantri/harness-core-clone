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
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AzureK8sInfraContextTest extends CategoryTest {
  private String clientId = "123456";
  private String tenantId = "654321";
  private String delegateId = "delId";

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToConnectorInfoEmpty() {
    AzureK8sInfraContext azureK8sInfraContext = AzureK8sInfraContext.builder().build();
    Optional<String> result = azureK8sInfraContext.getConnectorInfo();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToConnectorInfoServicePrincipalSecret() {
    AzureK8sInfraContext azureK8sInfraContext =
        AzureK8sInfraContext.builder()
            .azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET)
            .azureConnectorEnvironmentType(AzureEnvironmentType.AZURE)
            .clientId(clientId)
            .tenantId(tenantId)
            .delegateId(delegateId)
            .build();
    Optional<String> result = azureK8sInfraContext.getConnectorInfo();
    assertThat(result).isNotEmpty();
    assertThat(result.get())
        .isEqualTo(
            "Service Principal Azure Connector with clientId [123456], tenantId [654321] and environment [AzurePublicCloud]");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToConnectorInfoServicePrincipalCert() {
    AzureK8sInfraContext azureK8sInfraContext =
        AzureK8sInfraContext.builder()
            .azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT)
            .azureConnectorEnvironmentType(AzureEnvironmentType.AZURE)
            .clientId(clientId)
            .tenantId(tenantId)
            .delegateId(delegateId)
            .build();
    Optional<String> result = azureK8sInfraContext.getConnectorInfo();
    assertThat(result).isNotEmpty();
    assertThat(result.get())
        .isEqualTo(
            "Service Principal Azure Connector with clientId [123456], tenantId [654321] and environment [AzurePublicCloud]");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToConnectorInfoUserAssignedMSI() {
    AzureK8sInfraContext azureK8sInfraContext =
        AzureK8sInfraContext.builder()
            .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
            .azureConnectorEnvironmentType(AzureEnvironmentType.AZURE)
            .clientId(clientId)
            .delegateId(delegateId)
            .build();
    Optional<String> result = azureK8sInfraContext.getConnectorInfo();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo("User Assigned Managed Identity Azure Connector with clientId [123456]");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToConnectorInfoSystemAssignedMSI() {
    AzureK8sInfraContext azureK8sInfraContext =
        AzureK8sInfraContext.builder()
            .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED)
            .azureConnectorEnvironmentType(AzureEnvironmentType.AZURE)
            .delegateId(delegateId)
            .build();
    Optional<String> result = azureK8sInfraContext.getConnectorInfo();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo("System Assigned Managed Identity Azure Connector with delegateId [delId]");
  }
}
