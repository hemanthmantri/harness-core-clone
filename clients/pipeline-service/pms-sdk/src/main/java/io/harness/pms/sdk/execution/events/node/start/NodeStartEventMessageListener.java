/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events.node.start;

import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.execution.start.NodeStartEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;
import io.harness.pms.sdk.core.execution.events.node.start.NodeStartEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class NodeStartEventMessageListener extends PmsAbstractMessageListener<NodeStartEvent, NodeStartEventHandler> {
  @Inject
  public NodeStartEventMessageListener(
      @Named(SDK_SERVICE_NAME) String serviceName, NodeStartEventHandler nodeStartEventHandler) {
    super(serviceName, NodeStartEvent.class, nodeStartEventHandler);
  }

  @Override
  protected NodeStartEvent extractEntity(ByteString message) throws InvalidProtocolBufferException {
    return NodeStartEvent.parseFrom(message);
  }
}
