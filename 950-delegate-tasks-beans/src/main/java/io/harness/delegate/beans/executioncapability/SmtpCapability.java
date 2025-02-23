/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SmtpCapability implements ExecutionCapability {
  private boolean useSSL;
  private boolean startTLS;
  private String host;
  private int port;
  private String username;

  @Builder.Default private final CapabilityType capabilityType = CapabilityType.SMTP;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    return host + ":" + port;
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }

  @Override
  public String getCapabilityToString() {
    return isNotEmpty(fetchCapabilityBasis()) ? String.format("Capability reach host, %s ", fetchCapabilityBasis())
                                              : null;
  }

  /**
   * Error message to show mostly in delegate selection log if none of the delegates passed the validation check
   */
  @Override
  public String getCapabilityValidationError() {
    return isNotEmpty(fetchCapabilityBasis())
        ? String.format(
            "Delegate(s) unable to connect to  %s, make sure to provide the connectivity with the following delegates",
            fetchCapabilityBasis())
        : ExecutionCapability.super.getCapabilityValidationError();
  }
}
