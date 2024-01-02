/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.ssca.beans.instance.InstanceDTO;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.artifact.ArtifactEntity;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CdInstanceSummaryService {
  boolean upsertInstance(InstanceDTO instance);

  boolean removeInstance(InstanceDTO instance);

  Page<CdInstanceSummary> getCdInstanceSummaries(String accountId, String orgIdentifier, String projectIdentifier,
      ArtifactEntity artifact, ArtifactDeploymentViewRequestBody filterBody, Pageable pageable);

  List<CdInstanceSummary> getCdInstanceSummaries(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> artifactCorelationIds);

  CdInstanceSummary getCdInstanceSummary(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactCorrelationId, String envIdentifier);

  @VisibleForTesting CdInstanceSummary createInstanceSummary(InstanceDTO instance, ArtifactEntity artifact);
}
