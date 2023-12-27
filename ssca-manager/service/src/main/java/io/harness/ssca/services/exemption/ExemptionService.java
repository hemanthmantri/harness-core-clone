/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services.exemption;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ssca.v1.model.ExemptionRequestDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionResponseDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionReviewRequestDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionStatusDTO;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.SSCA)
public interface ExemptionService {
  Page<ExemptionResponseDTO> getExemptions(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, List<ExemptionStatusDTO> exemptionStatusDTOs, String searchTerm, Pageable pageable);

  ExemptionResponseDTO getExemption(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String exemptionId);

  ExemptionResponseDTO createExemption(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, ExemptionRequestDTO exemptionRequestDTO);

  ExemptionResponseDTO updateExemption(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, String exemptionId, ExemptionRequestDTO exemptionRequestDTO);

  void deleteExemption(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String exemptionId);

  ExemptionResponseDTO reviewExemption(String accountId, String orgIdentifier, String projectIdentifier,
      String artifactId, String exemptionId, ExemptionReviewRequestDTO exemptionReviewRequestDTO);
}
