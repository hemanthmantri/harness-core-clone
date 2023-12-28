/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans.instance;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceDTO {
  private String id;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;

  private String envIdentifier;
  private String envName;
  private String envType;

  private String lastDeployedById;
  private String lastDeployedByName;
  private long lastDeployedAt;

  private String lastPipelineExecutionId;
  private String lastPipelineExecutionName;
  private String stageNodeExecutionId;
  private String stageSetupId;

  private boolean isDeleted;
  private long deletedAt;
  Long createdAt;
  Long lastModifiedAt;
  private Long podCreatedAt;

  private ArtifactDetailsDTO primaryArtifact;
  private InstanceInfoDTO instanceInfo;
}
