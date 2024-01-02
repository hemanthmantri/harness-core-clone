/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.oidc;

import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpOidcDetailsDTO;
import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenRequest;
import io.harness.oidc.gcp.delegate.GcpOidcTokenExchangeDetailsForDelegate;
import io.harness.oidc.gcp.dto.GcpOidcAccessTokenRequestDTO;
import io.harness.oidc.gcp.dto.GcpOidcTokenRequestDTO;
import io.harness.oidc.gcp.utility.GcpOidcTokenUtility;

import com.google.inject.Inject;

public class OidcHelperUtility {
  @Inject GcpOidcTokenUtility gcpOidcTokenUtility;

  /**
   * Utility function to get the OIDC Token Exchange Details based on the GCP connector info.
   *
   * @param accountId accountId
   * @param connectorDTO GCP Connector info
   * @return OIDC ID Token
   */
  public GcpOidcTokenExchangeDetailsForDelegate getOidcTokenExchangeDetailsForDelegate(
      String accountId, GcpConnectorDTO connectorDTO) {
    if (connectorDTO.getCredential().getGcpCredentialType() != GcpCredentialType.OIDC_AUTHENTICATION) {
      return null;
    }

    // Get the OIDC Details from Connector Info
    GcpOidcDetailsDTO gcpOidcDetailsDTO = (GcpOidcDetailsDTO) connectorDTO.getCredential().getConfig();

    // Build the OIDC ID Token Request object
    GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO = GcpOidcTokenRequestDTO.builder()
                                                        .workloadPoolId(gcpOidcDetailsDTO.getWorkloadPoolId())
                                                        .providerId(gcpOidcDetailsDTO.getProviderId())
                                                        .gcpProjectId(gcpOidcDetailsDTO.getGcpProjectId())
                                                        .serviceAccountEmail(gcpOidcDetailsDTO.getServiceAccountEmail())
                                                        .accountId(accountId)
                                                        .build();

    // Generate the OIDC ID Token
    String oidcIdToken = gcpOidcTokenUtility.generateGcpOidcIdToken(gcpOidcTokenRequestDTO);

    // Build the OIDC Workload Access Token Request object
    GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO = GcpOidcAccessTokenRequestDTO.builder()
                                                                    .oidcIdToken(oidcIdToken)
                                                                    .gcpOidcTokenRequestDTO(gcpOidcTokenRequestDTO)
                                                                    .build();
    OidcWorkloadAccessTokenRequest oidcWorkloadAccessTokenRequest =
        gcpOidcTokenUtility.getOidcWorkloadAccessTokenRequest(gcpOidcAccessTokenRequestDTO);

    return GcpOidcTokenExchangeDetailsForDelegate.builder()
        .oidcIdToken(oidcIdToken)
        .oidcAccessTokenStsEndpoint(gcpOidcTokenUtility.getOidcAccessTokenStsEndpoint())
        .oidcAccessTokenIamSaEndpoint(gcpOidcTokenUtility.getOidcAccessTokenIamEndpoint(gcpOidcTokenRequestDTO))
        .gcpServiceAccountEmail(gcpOidcDetailsDTO.getServiceAccountEmail())
        .oidcWorkloadAccessTokenRequestStructure(oidcWorkloadAccessTokenRequest)
        .build();
  }
}
