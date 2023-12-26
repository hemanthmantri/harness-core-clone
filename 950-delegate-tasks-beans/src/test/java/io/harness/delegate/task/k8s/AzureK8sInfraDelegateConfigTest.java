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
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.rule.Owner;
import io.harness.taskcontext.infra.InfraContext;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AzureK8sInfraDelegateConfigTest extends CategoryTest {
  private String clientId = "123456";
  private String tenantId = "654321";
  private String delegateId = "delId";

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void toInfraContextEmptyTest() {
    AzureK8sInfraDelegateConfig azureK8sInfraDelegateConfig = AzureK8sInfraDelegateConfig.builder().build();
    InfraContext result = azureK8sInfraDelegateConfig.toInfraContext(null);
    assertThat(result).isNotNull();
    assertThat(result.getConnectorInfo()).isEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void toInfraContextManualCredentialsServicePrincipalSecretTest() {
    AzureK8sInfraDelegateConfig azureK8sInfraDelegateConfig =
        AzureK8sInfraDelegateConfig.builder()
            .azureConnectorDTO(
                AzureConnectorDTO.builder()
                    .azureEnvironmentType(AzureEnvironmentType.AZURE)
                    .credential(
                        AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                AzureManualDetailsDTO.builder()
                                    .clientId(clientId)
                                    .tenantId(tenantId)
                                    .authDTO(AzureAuthDTO.builder().azureSecretType(AzureSecretType.SECRET_KEY).build())
                                    .build())
                            .build())
                    .build())
            .build();
    InfraContext result = azureK8sInfraDelegateConfig.toInfraContext(delegateId);
    assertThat(result).isNotNull();
    assertThat(result.getConnectorInfo()).isNotEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void toInfraContextManualCredentialsServicePrincipalCertTest() {
    AzureK8sInfraDelegateConfig azureK8sInfraDelegateConfig =
        AzureK8sInfraDelegateConfig.builder()
            .azureConnectorDTO(
                AzureConnectorDTO.builder()
                    .azureEnvironmentType(AzureEnvironmentType.AZURE)
                    .credential(
                        AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                AzureManualDetailsDTO.builder()
                                    .clientId(clientId)
                                    .tenantId(tenantId)
                                    .authDTO(AzureAuthDTO.builder().azureSecretType(AzureSecretType.KEY_CERT).build())
                                    .build())
                            .build())
                    .build())
            .build();
    InfraContext result = azureK8sInfraDelegateConfig.toInfraContext(delegateId);
    assertThat(result).isNotNull();
    assertThat(result.getConnectorInfo()).isNotEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void toInfraContextManualCredentialsUserAssignedMSITest() {
    AzureK8sInfraDelegateConfig azureK8sInfraDelegateConfig =
        AzureK8sInfraDelegateConfig.builder()
            .azureConnectorDTO(

                AzureConnectorDTO.builder()
                    .azureEnvironmentType(AzureEnvironmentType.AZURE)
                    .credential(

                        AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                            .config(
                                AzureInheritFromDelegateDetailsDTO.builder()
                                    .authDTO(AzureMSIAuthUADTO.builder()
                                                 .azureManagedIdentityType(
                                                     AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
                                                 .credentials(
                                                     AzureUserAssignedMSIAuthDTO.builder().clientId(clientId).build())
                                                 .build())
                                    .build())
                            .build())
                    .build())
            .build();
    InfraContext result = azureK8sInfraDelegateConfig.toInfraContext(delegateId);
    assertThat(result).isNotNull();
    assertThat(result.getConnectorInfo()).isNotEmpty();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void toInfraContextManualCredentialsSystemAssignedMSITest() {
    AzureK8sInfraDelegateConfig azureK8sInfraDelegateConfig =
        AzureK8sInfraDelegateConfig.builder()
            .azureConnectorDTO(

                AzureConnectorDTO.builder()
                    .azureEnvironmentType(AzureEnvironmentType.AZURE)
                    .credential(

                        AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                            .config(AzureInheritFromDelegateDetailsDTO.builder()
                                        .authDTO(AzureMSIAuthSADTO.builder()
                                                     .azureManagedIdentityType(
                                                         AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
                                                     .build())
                                        .build())
                            .build())
                    .build())
            .build();
    InfraContext result = azureK8sInfraDelegateConfig.toInfraContext(delegateId);
    assertThat(result).isNotNull();
    assertThat(result.getConnectorInfo()).isNotEmpty();
  }
}
