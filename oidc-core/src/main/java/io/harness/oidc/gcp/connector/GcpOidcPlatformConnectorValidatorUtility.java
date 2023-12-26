/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp.connector;

import static io.harness.oidc.gcp.constants.GcpOidcIdTokenConstants.BEARER_TOKEN_TYPE;

import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenResponse;
import io.harness.oidc.exception.OidcException;
import io.harness.oidc.gcp.dto.GcpOidcAccessTokenRequestDTO;
import io.harness.oidc.gcp.dto.GcpOidcTokenRequestDTO;
import io.harness.oidc.gcp.utility.GcpOidcTokenUtility;

import com.google.inject.Inject;

public class GcpOidcPlatformConnectorValidatorUtility implements GcpOidcConnectorValidatorUtility {
  @Inject private GcpOidcTokenUtility gcpOidcTokenUtility;

  @Override
  public void validateOidcAccessTokenExchange(
      String workloadPoolId, String providerId, String gcpProjectId, String serviceAccountEmail, String accountId) {
    GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO = new GcpOidcTokenRequestDTO();
    gcpOidcTokenRequestDTO.setWorkloadPoolId(workloadPoolId);
    gcpOidcTokenRequestDTO.setProviderId(providerId);
    gcpOidcTokenRequestDTO.setGcpProjectId(gcpProjectId);
    gcpOidcTokenRequestDTO.setServiceAccountEmail(serviceAccountEmail);
    gcpOidcTokenRequestDTO.setAccountId(accountId);

    // 1. Generate the OIDC ID Token
    String idToken = gcpOidcTokenUtility.generateGcpOidcIdToken(gcpOidcTokenRequestDTO);

    // 2. Exchange the OIDC ID Token for a Federal Token
    GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO = new GcpOidcAccessTokenRequestDTO();
    gcpOidcAccessTokenRequestDTO.setOidcIdToken(idToken);
    gcpOidcAccessTokenRequestDTO.setGcpOidcTokenRequestDTO(gcpOidcTokenRequestDTO);

    try {
      OidcWorkloadAccessTokenResponse oidcWorkloadAccessTokenResponse =
          gcpOidcTokenUtility.exchangeOidcWorkloadAccessToken(gcpOidcAccessTokenRequestDTO);
      if (!oidcWorkloadAccessTokenResponse.getToken_type().equals(BEARER_TOKEN_TYPE)) {
        throw new OidcException("Invalid OIDC Token Exchange");
      }
    } catch (RuntimeException ex) {
      throw ex;
    }
  }
}
