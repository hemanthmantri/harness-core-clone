/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp.constants;

import static io.harness.oidc.gcp.constants.GcpOidcIdTokenConstants.DELEGATES;
import static io.harness.oidc.gcp.constants.GcpOidcIdTokenConstants.LIFETIME;
import static io.harness.oidc.gcp.constants.GcpOidcIdTokenConstants.SCOPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

@OwnedBy(HarnessTeam.PL)
@Builder
public class GcpOidcServiceAccountAccessTokenRequest {
  // The sequence of service accounts in a delegation chain.
  // This field is required for delegated requests. For direct
  // requests, which are more common, do not specify this field.
  @JsonProperty(DELEGATES) private List<String> delegates;
  // Code to identify the scopes to be included in the OAuth 2.0 access token
  @JsonProperty(SCOPE) private List<String> scope;
  // The desired lifetime duration of the access token in seconds.
  // By default, the maximum allowed value is 1 hour.
  @JsonProperty(LIFETIME) private String lifetime;
}