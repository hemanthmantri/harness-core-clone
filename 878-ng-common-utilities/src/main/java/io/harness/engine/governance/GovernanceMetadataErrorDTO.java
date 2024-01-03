/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.engine.governance;

import io.harness.exception.ngexception.ErrorMetadataConstants;
import io.harness.exception.ngexception.ErrorMetadataDTO;
import io.harness.governance.GovernanceMetadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("GovernanceMetadataErrorDTO")
@JsonTypeName(ErrorMetadataConstants.GOVERNANCE_METADATA_ERROR)
public class GovernanceMetadataErrorDTO implements ErrorMetadataDTO {
  GovernanceMetadata governanceMetadata;

  @Override
  public String getType() {
    return ErrorMetadataConstants.GOVERNANCE_METADATA_ERROR;
  }
}