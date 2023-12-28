/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.expression.Expression;
import io.harness.k8s.trafficrouting.TrafficRoutingInfoDTO;

import software.wings.beans.ServiceHookDelegateConfig;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@Value
@Builder
@OwnedBy(CDP)
public class K8sTrafficRoutingRequest implements K8sDeployRequest {
  K8sTaskType taskType;
  String commandName;
  K8sInfraDelegateConfig k8sInfraDelegateConfig;
  Integer timeoutIntervalInMin;
  String accountId;
  CommandUnitsProgress commandUnitsProgress;
  @Expression(DISALLOW_SECRETS) String releaseName;
  @Builder.Default boolean shouldOpenFetchFilesLogStream = true;
  K8sTrafficRoutingConfig trafficRoutingConfig;
  boolean useDeclarativeRollback;
  TrafficRoutingInfoDTO trafficRoutingInfo;

  @Override
  public boolean hasTrafficRoutingConfig() {
    return trafficRoutingConfig != null;
  }

  @Override
  public List<String> getValuesYamlList() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getKustomizePatchesList() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getOpenshiftParamList() {
    return Collections.emptyList();
  }

  @Override
  public ManifestDelegateConfig getManifestDelegateConfig() {
    return null;
  }

  @Override
  public boolean isUseLatestKustomizeVersion() {
    return false;
  }

  @Override
  public boolean isUseNewKubectlVersion() {
    return false;
  }

  @Override
  public List<ServiceHookDelegateConfig> getServiceHooks() {
    return Collections.emptyList();
  }
}
