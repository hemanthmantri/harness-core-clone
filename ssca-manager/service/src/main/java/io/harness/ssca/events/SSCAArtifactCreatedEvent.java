/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.events;

import static io.harness.annotations.dev.HarnessTeam.SSCA;
import static io.harness.audit.ResourceTypeConstants.SSCA_ARTIFACT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.events.utils.SSCAOutboxEvents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@OwnedBy(SSCA)
@Getter
@AllArgsConstructor
public class SSCAArtifactCreatedEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;

  private ArtifactEntity artifact;

  @Override
  public ResourceScope getResourceScope() {
    return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, artifact.getName());
    return Resource.builder().identifier(artifact.getArtifactId()).type(SSCA_ARTIFACT).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return SSCAOutboxEvents.SSCA_ARTIFACT_CREATED_EVENT;
  }
}
