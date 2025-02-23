/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;

@OwnedBy(PL)
public class ScmRequestTimeoutException extends ScmException {
  private static final String MESSAGE_ARG = "message";

  public ScmRequestTimeoutException(String errorMessage) {
    super(errorMessage, null, ErrorCode.SCM_REQUEST_TIMEOUT);
    super.param(MESSAGE_ARG, errorMessage);
  }
}
