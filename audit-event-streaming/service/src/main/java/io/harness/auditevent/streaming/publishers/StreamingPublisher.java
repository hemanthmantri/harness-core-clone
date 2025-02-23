/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.publishers;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.streaming.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.beans.PublishResponse;
import io.harness.auditevent.streaming.entities.StreamingBatch;

import java.util.List;

public interface StreamingPublisher {
  PublishResponse publish(StreamingDestination streamingDestination, StreamingBatch streamingBatch,
      List<OutgoingAuditMessage> outgoingAuditMessages);
}
