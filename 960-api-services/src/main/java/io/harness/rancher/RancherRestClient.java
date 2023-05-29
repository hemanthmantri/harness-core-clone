/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rancher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

@OwnedBy(HarnessTeam.CDP)
public interface RancherRestClient {
  @GET("/v3/clusters") Call<RancherListClustersResponse> listClusters();

  @POST("/v3/clusters/{clusterName}?action=generateKubeconfig")
  Call<RancherGenerateKubeconfigResponse> generateKubeconfig(
      @Path(value = "clusterName", encoded = true) String clusterName);
}