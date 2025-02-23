/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.awscdk.beans.AwsCdkOutcome")
public class AwsCdkOutcome extends HashMap<String, Object> implements Outcome, ExecutionSweepingOutput {
  public AwsCdkOutcome(Map<String, ?> m) {
    super(m);
  }
}
