/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.taskcontext.infra.GcpK8sInfraContext;
import io.harness.taskcontext.infra.InfraContext;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
@RecasterAlias("io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class GcpK8sInfraDelegateConfig implements K8sInfraDelegateConfig {
  String namespace;
  String cluster;
  GcpConnectorDTO gcpConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;

  @Override
  public InfraContext toInfraContext(String delegateId) {
    return GcpK8sInfraContext.builder().delegateId(delegateId).build();
  }
}
