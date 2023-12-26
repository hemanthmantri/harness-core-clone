/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.exception;

import static io.harness.eraro.ErrorCode.INVALID_OIDC_CONFIGURATION;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

import java.util.EnumSet;

public class OidcException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public OidcException(String message) {
    this(INVALID_OIDC_CONFIGURATION, message, null);
  }

  public OidcException(ErrorCode errorCode, String message, EnumSet<ReportTarget> reportTargets) {
    super(message, null, errorCode, Level.ERROR, reportTargets, null);
    param(MESSAGE_KEY, message);
  }
}
