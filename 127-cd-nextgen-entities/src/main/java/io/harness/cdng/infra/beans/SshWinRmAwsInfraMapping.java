/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.annotations.Id;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("SshWinRmAwsInfraMapping")
@JsonTypeName("SshWinRmAwsInfraMapping")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.infra.beans.SshWinRmAwsInfraMapping")
public class SshWinRmAwsInfraMapping implements InfraMapping {
  @Id private String uuid;
  private String connectorRef;
  private String credentialsRef;
  private String accountId;
  private String region;
}
