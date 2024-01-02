/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp.delegate;

import static io.harness.oidc.accesstoken.OidcAccessTokenUtility.getOidcWorkloadAccessToken;
// import static io.harness.oidc.gcp.accesstoken.GcpOidcAccessTokenUtility.getOidcServiceAccountAccessToken;
import static io.harness.oidc.gcp.accesstoken.GcpOidcAccessTokenUtility.getOidcServiceAccountAccessTokenV2;
import static io.harness.oidc.gcp.constants.GcpOidcIdTokenConstants.BEARER_TOKEN_TYPE;

import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenRequest;
import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenResponse;
import io.harness.oidc.exception.OidcException;
import io.harness.oidc.gcp.constants.GcpOidcServiceAccountAccessTokenRequest;
import io.harness.oidc.gcp.constants.GcpOidcServiceAccountAccessTokenResponse;

import com.google.cloud.iam.credentials.v1.GenerateAccessTokenResponse;
import java.io.IOException;
import java.text.Format;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@AllArgsConstructor
@Slf4j
public class GcpOidcTokenExchangeDetailsForDelegate {
  private String oidcIdToken; // GCP OIDC ID Token for OIDC credential type
  private String oidcAccessTokenStsEndpoint; // GCP STS Endpoint
  private String oidcAccessTokenIamSaEndpoint; // GCP IAM Credentials Endpoint

  private String gcpServiceAccountEmail; // GCP Service Account Email
  private OidcWorkloadAccessTokenRequest
      oidcWorkloadAccessTokenRequestStructure; // Workload Access Token Request Structure

  public GcpOidcServiceAccountAccessTokenResponse exchangeOidcServiceAccountAccessToken() {
    // Get the Workload Access Token
    OidcWorkloadAccessTokenResponse oidcWorkloadAccessTokenResponse =
        getOidcWorkloadAccessToken(oidcAccessTokenStsEndpoint, oidcWorkloadAccessTokenRequestStructure);

    if (!oidcWorkloadAccessTokenResponse.getToken_type().equals(BEARER_TOKEN_TYPE)) {
      log.error("Invalid Workload Access Token Response received, not exchanging for Service Account Access Token");
      throw new OidcException("Invalid Workload Access Token Response received");
    }

    // Build the Request Body
    GcpOidcServiceAccountAccessTokenRequest gcpOidcServiceAccountAccessTokenRequest =
        GcpOidcServiceAccountAccessTokenRequest.builder()
            .scope(new ArrayList<>(Arrays.asList("https://www.googleapis.com/auth/cloud-platform")))
            .build();

    try {
      /*
      return getOidcServiceAccountAccessToken(oidcAccessTokenIamSaEndpoint, gcpOidcServiceAccountAccessTokenRequest,
          oidcWorkloadAccessTokenResponse.getAccess_token()); */
      GenerateAccessTokenResponse generateAccessTokenResponse =
          getOidcServiceAccountAccessTokenV2(oidcWorkloadAccessTokenResponse.getAccess_token(), gcpServiceAccountEmail);
      return new GcpOidcServiceAccountAccessTokenResponse(
          generateAccessTokenResponse.getAccessToken(), generateAccessTokenResponse.getExpireTime().getSeconds());
    } catch (IOException ex) {
      String exceptionMsg =
          String.format("Unable to exchange for OIDC Access Token for GCP Service Account - %s " + ex);
      throw new OidcException(exceptionMsg);
    } catch (OidcException ex) {
      throw ex;
    }
  }
}
