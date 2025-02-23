/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.polling.bean.ArtifactInfo;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class GoogleCloudStorageArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String project;
  String bucket;
  String artifactPath;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return GoogleCloudStorageArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .project(ParameterField.<String>builder().value(project).build())
        .bucket(ParameterField.<String>builder().value(bucket).build())
        .artifactPath(ParameterField.<String>builder().value(artifactPath).build())
        .build();
  }
}
