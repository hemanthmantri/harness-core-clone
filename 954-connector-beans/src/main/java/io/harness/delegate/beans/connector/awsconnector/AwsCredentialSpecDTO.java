/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsManualConfigSpecDTO.class, name = AwsConstants.MANUAL_CONFIG)
  , @JsonSubTypes.Type(value = AwsOidcSpecDTO.class, name = AwsConstants.OIDC_AUTHENTICATION)
})
@ApiModel("AwsCredentialSpec")
@Schema(name = "AwsCredentialSpec", description = "This contains AWS connector credential spec")
public interface AwsCredentialSpecDTO extends DecryptableEntity {}
