/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plan.consumers;

import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.eventsframework.consumer.Message;
import io.harness.pms.contracts.plan.PartialPlanResponse;
import io.harness.pms.events.base.PmsAbstractMessageListener;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class PartialPlanResponseEventMessageListener
    extends PmsAbstractMessageListener<PartialPlanResponse, PartialPlanResponseEventHandler> {
  @Inject
  public PartialPlanResponseEventMessageListener(
      @Named(SDK_SERVICE_NAME) String serviceName, PartialPlanResponseEventHandler partialPlanResponseEventHandler) {
    super(serviceName, PartialPlanResponse.class, partialPlanResponseEventHandler);
  }

  @Override
  protected PartialPlanResponse extractEntity(ByteString message) throws InvalidProtocolBufferException {
    return PartialPlanResponse.parseFrom(message);
  }

  @Override
  public boolean isProcessable(Message message) {
    return true;
  }
}
