/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ssca.v1.model.EnforceSbomRequestBody;
import io.harness.ssca.beans.PolicyEvaluationResult;
import io.harness.ssca.entities.artifact.ArtifactEntity;

@OwnedBy(HarnessTeam.SSCA)
public interface PolicyEvaluationService {
  PolicyEvaluationResult evaluatePolicy(String accountId, String orgIdentifier, String projectIdentifier,
      EnforceSbomRequestBody body, ArtifactEntity artifactEntity);
}
