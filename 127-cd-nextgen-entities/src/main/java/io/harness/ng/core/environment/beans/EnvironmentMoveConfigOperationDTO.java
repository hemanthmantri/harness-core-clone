/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.MoveConfigOperationType;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class EnvironmentMoveConfigOperationDTO {
  String connectorRef;
  String repoName;
  String branch;
  String filePath;
  String baseBranch;
  String commitMessage;
  boolean isNewBranch;
  @NotNull MoveConfigOperationType moveConfigOperationType;
}
