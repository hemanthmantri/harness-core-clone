/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ssca.v1.model.ExemptionDurationDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionInitiatorDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionRequestDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionResponseDTO;
import io.harness.spec.server.ssca.v1.model.ExemptionStatusDTO;
import io.harness.spec.server.ssca.v1.model.Operator;
import io.harness.ssca.entities.OperatorEntity;
import io.harness.ssca.entities.exemption.Exemption;
import io.harness.ssca.entities.exemption.Exemption.ExemptionDuration;
import io.harness.ssca.entities.exemption.Exemption.ExemptionInitiator;
import io.harness.ssca.entities.exemption.Exemption.ExemptionInitiator.ExemptionInitiatorBuilder;
import io.harness.ssca.entities.exemption.Exemption.ExemptionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
public class ExemptionMapper {
  public Exemption toExemption(ExemptionRequestDTO exemptionRequestDTO, Exemption exemption) {
    return exemption.toBuilder()
        .componentName(exemptionRequestDTO.getComponentName())
        .componentVersion(exemptionRequestDTO.getComponentVersion())
        .versionOperator(toOperatorEntity(exemptionRequestDTO.getVersionOperator()))
        .reason(exemptionRequestDTO.getReason())
        .exemptionDuration(toExemptionDuration(exemptionRequestDTO.getExemptionDuration()))
        .exemptionInitiator(toExemptionInitiator(exemptionRequestDTO.getExemptionInitiator()))
        .build();
  }
  public Exemption toExemption(ExemptionRequestDTO exemptionRequestDTO) {
    return toExemption(exemptionRequestDTO, Exemption.builder().build());
  }
  public List<ExemptionResponseDTO> toExemptionResponseDTOs(List<Exemption> exemptions, Map<String, String> users) {
    List<ExemptionResponseDTO> exemptionResponseDTOs = new ArrayList<>();
    for (Exemption exemption : exemptions) {
      exemptionResponseDTOs.add(toExemptionResponseDTO(exemption, users));
    }
    return exemptionResponseDTOs;
  }
  public ExemptionResponseDTO toExemptionResponseDTO(Exemption exemption, Map<String, String> users) {
    ExemptionResponseDTO exemptionResponseDTO = new ExemptionResponseDTO();
    exemptionResponseDTO.accountId(exemption.getAccountId());
    exemptionResponseDTO.orgIdentifier(exemption.getOrgIdentifier());
    exemptionResponseDTO.projectIdentifier(exemption.getProjectIdentifier());
    exemptionResponseDTO.artifactId(exemption.getArtifactId());
    exemptionResponseDTO.uuid(exemption.getUuid());
    exemptionResponseDTO.createdByUserId(exemption.getCreatedBy());
    exemptionResponseDTO.updatedBy(exemption.getUpdatedBy());
    exemptionResponseDTO.reviewedByUserId(exemption.getReviewedBy());
    exemptionResponseDTO.reviewComment(exemption.getReviewComment());
    exemptionResponseDTO.componentName(exemption.getComponentName());
    exemptionResponseDTO.componentVersion(exemption.getComponentVersion());
    exemptionResponseDTO.reason(exemption.getReason());
    exemptionResponseDTO.versionOperator(toOperator(exemption.getVersionOperator()));
    exemptionResponseDTO.exemptionDuration(toExemptionDurationDTO(exemption.getExemptionDuration()));
    exemptionResponseDTO.exemptionInitiator(toExemptionInitiatorDTO(exemption.getExemptionInitiator()));
    exemptionResponseDTO.exemptionStatus(toExemptionStatusDTO(exemption.getExemptionStatus()));
    exemptionResponseDTO.validUntil(exemption.getValidUntil());
    exemptionResponseDTO.createdAt(exemption.getCreatedAt());
    exemptionResponseDTO.updatedAt(exemption.getUpdatedAt());
    exemptionResponseDTO.reviewedAt(exemption.getReviewedAt());
    if (Objects.nonNull(users)) {
      exemptionResponseDTO.createdByName(users.get(exemption.getCreatedBy()));
      exemptionResponseDTO.reviewedByName(users.get(exemption.getReviewedBy()));
    }
    return exemptionResponseDTO;
  }

  public ExemptionDurationDTO toExemptionDurationDTO(ExemptionDuration exemptionDuration) {
    return new ExemptionDurationDTO()
        .alwaysExempt(exemptionDuration.isAlwaysExempt())
        .days(exemptionDuration.getDays());
  }

  public ExemptionDuration toExemptionDuration(ExemptionDurationDTO exemptionDurationDTO) {
    return ExemptionDuration.builder()
        .alwaysExempt(exemptionDurationDTO.isAlwaysExempt())
        .days(exemptionDurationDTO.getDays())
        .build();
  }

  public ExemptionInitiatorDTO toExemptionInitiatorDTO(ExemptionInitiator exemptionInitiator) {
    return new ExemptionInitiatorDTO()
        .projectIdentifier(exemptionInitiator.getProjectId())
        .artifactId(exemptionInitiator.getArtifactId())
        .enforcementId(exemptionInitiator.getEnforcementId());
  }

  public ExemptionInitiator toExemptionInitiator(ExemptionInitiatorDTO exemptionInitiatorDTO) {
    ExemptionInitiatorBuilder exemptionInitiatorBuilder = ExemptionInitiator.builder();
    if (exemptionInitiatorDTO != null) {
      exemptionInitiatorBuilder = exemptionInitiatorBuilder.projectId(exemptionInitiatorDTO.getProjectIdentifier())
                                      .artifactId(exemptionInitiatorDTO.getArtifactId())
                                      .enforcementId(exemptionInitiatorDTO.getEnforcementId());
    }
    return exemptionInitiatorBuilder.build();
  }

  public Operator toOperator(OperatorEntity operatorEntity) {
    if (Objects.isNull(operatorEntity)) {
      return null;
    }
    return Operator.valueOf(operatorEntity.name());
  }

  public OperatorEntity toOperatorEntity(Operator operator) {
    if (Objects.isNull(operator)) {
      return null;
    }
    return OperatorEntity.valueOf(operator.name());
  }

  public ExemptionStatusDTO toExemptionStatusDTO(ExemptionStatus exemptionStatus) {
    return ExemptionStatusDTO.valueOf(exemptionStatus.name());
  }

  public ExemptionStatus toExemptionStatus(ExemptionStatusDTO exemptionStatusDTO) {
    return ExemptionStatus.valueOf(exemptionStatusDTO.name());
  }
}