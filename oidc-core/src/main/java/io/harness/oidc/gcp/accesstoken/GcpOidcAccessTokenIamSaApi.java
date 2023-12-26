/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp.accesstoken;

import io.harness.oidc.gcp.constants.GcpOidcServiceAccountAccessTokenRequest;
import io.harness.oidc.gcp.constants.GcpOidcServiceAccountAccessTokenResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface GcpOidcAccessTokenIamSaApi {
  @POST("{service_account_email}:generateAccessToken/")
  @Headers("Content-Type: application/json")
  Call<GcpOidcServiceAccountAccessTokenResponse> exchangeServiceAccountAccessToken(
      @Path("service_account_email") String serviceAccount, @Body GcpOidcServiceAccountAccessTokenRequest request);
}