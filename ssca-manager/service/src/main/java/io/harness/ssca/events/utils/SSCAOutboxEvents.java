/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.events.utils;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(SSCA)
public class SSCAOutboxEvents {
  public static final String SSCA_ARTIFACT_CREATED_EVENT = "SSCAArtifactCreatedEvent";
  public static final String SSCA_ARTIFACT_UPDATED_EVENT = "SSCAArtifactUpdatedEvent";
}
