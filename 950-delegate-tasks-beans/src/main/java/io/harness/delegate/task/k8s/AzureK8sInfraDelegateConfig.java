/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.taskcontext.infra.AzureK8sInfraContext;
import io.harness.taskcontext.infra.AzureK8sInfraContext.AzureK8sInfraContextBuilder;
import io.harness.taskcontext.infra.InfraContext;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@OwnedBy(CDP)
@RecasterAlias("io.harness.delegate.task.k8s.AzureK8sInfraDelegateConfig")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
public class AzureK8sInfraDelegateConfig implements K8sInfraDelegateConfig {
  String namespace;
  String cluster;
  String subscription;
  String resourceGroup;
  AzureConnectorDTO azureConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;
  boolean useClusterAdminCredentials;

  @Override
  public InfraContext toInfraContext(String delegateId) {
    AzureK8sInfraContextBuilder azureK8SInfraContextBuilder = AzureK8sInfraContext.builder();
    try {
      azureK8SInfraContextBuilder.delegateId(delegateId);
      azureK8SInfraContextBuilder.namespace(namespace);
      azureK8SInfraContextBuilder.cluster(cluster);
      azureK8SInfraContextBuilder.subscription(subscription);
      azureK8SInfraContextBuilder.resourceGroup(resourceGroup);
      azureK8SInfraContextBuilder.azureConnectorEnvironmentType(azureConnectorDTO.getAzureEnvironmentType());
      switch (azureConnectorDTO.getCredential().getAzureCredentialType()) {
        case INHERIT_FROM_DELEGATE: {
          AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
              (AzureInheritFromDelegateDetailsDTO) azureConnectorDTO.getCredential().getConfig();
          AzureMSIAuthDTO azureMSIAuthDTO = azureInheritFromDelegateDetailsDTO.getAuthDTO();

          if (azureMSIAuthDTO instanceof AzureMSIAuthUADTO) {
            AzureUserAssignedMSIAuthDTO azureUserAssignedMSIAuthDTO =
                ((AzureMSIAuthUADTO) azureMSIAuthDTO).getCredentials();
            azureK8SInfraContextBuilder.azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED);
            azureK8SInfraContextBuilder.clientId(azureUserAssignedMSIAuthDTO.getClientId());
          } else if (azureMSIAuthDTO instanceof AzureMSIAuthSADTO) {
            azureK8SInfraContextBuilder.azureAuthenticationType(
                AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED);
          }

          break;
        }
        case MANUAL_CREDENTIALS: {
          AzureManualDetailsDTO azureManualDetailsDTO =
              (AzureManualDetailsDTO) azureConnectorDTO.getCredential().getConfig();
          azureK8SInfraContextBuilder.clientId(azureManualDetailsDTO.getClientId());
          azureK8SInfraContextBuilder.tenantId(azureManualDetailsDTO.getTenantId());
          switch (azureManualDetailsDTO.getAuthDTO().getAzureSecretType()) {
            case SECRET_KEY:
              azureK8SInfraContextBuilder.azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET);
              break;
            case KEY_CERT:
              azureK8SInfraContextBuilder.azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT);
              break;
            default:
              break;
          }
          break;
        }
        default:
          break;
      }
    } catch (Exception e) {
      log.error("Failed to create Azure InfraContext for task context object", e);
    }

    return azureK8SInfraContextBuilder.build();
  }
}
