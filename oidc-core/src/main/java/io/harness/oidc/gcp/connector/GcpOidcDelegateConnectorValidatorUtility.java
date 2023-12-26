/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp.connector;

import io.harness.oidc.exception.OidcException;
import io.harness.oidc.gcp.connector.GcpOidcConnectorValidatorUtility;

public class GcpOidcDelegateConnectorValidatorUtility implements GcpOidcConnectorValidatorUtility {
  @Override
  public void validateOidcAccessTokenExchange(
      String workloadPoolId, String providerId, String gcpProjectId, String serviceAccountEmail, String accountId) {
    throw new OidcException(
        "Connector Validation using OIDC not supported in Delegate and should be done through Platform");
  }
}
