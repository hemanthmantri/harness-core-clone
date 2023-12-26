/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.taskcontext.infra;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAuthenticationType;

import java.util.Optional;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
public class AzureK8sInfraContext implements InfraContext {
  String delegateId;
  String namespace;
  String cluster;
  String subscription;
  String resourceGroup;

  private AzureEnvironmentType azureConnectorEnvironmentType;
  private AzureAuthenticationType azureAuthenticationType;
  private String clientId;
  private String tenantId;

  @Override
  public Optional<String> getConnectorInfo() {
    try {
      switch (azureAuthenticationType) {
        case MANAGED_IDENTITY_SYSTEM_ASSIGNED: {
          return Optional.of(
              format("System Assigned Managed Identity Azure Connector with delegateId [%s]", delegateId));
        }
        case MANAGED_IDENTITY_USER_ASSIGNED: {
          return Optional.of(format("User Assigned Managed Identity Azure Connector with clientId [%s]", clientId));
        }
        case SERVICE_PRINCIPAL_CERT:
        case SERVICE_PRINCIPAL_SECRET: {
          return Optional.of(
              format("Service Principal Azure Connector with clientId [%s], tenantId [%s] and environment [%s]",
                  clientId, tenantId, azureConnectorEnvironmentType.getDisplayName()));
        }
        default: {
          return Optional.empty();
        }
      }
    } catch (Exception e) {
      log.error("Failed to generate AzureK8s ConnectorInfo for task context", e);
    }

    return Optional.empty();
  }
}
