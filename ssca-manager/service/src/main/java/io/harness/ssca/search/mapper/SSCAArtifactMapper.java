/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.search.mapper;

import static io.harness.ssca.search.framework.Constants.ARTIFACT_ENTITY;

import io.harness.ssca.beans.Scorecard;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.search.beans.RelationshipType;
import io.harness.ssca.search.entities.SSCAArtifact;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SSCAArtifactMapper {
  public SSCAArtifact toSSCAArtifact(ArtifactEntity artifactEntity) {
    if (artifactEntity == null) {
      return null;
    }

    return SSCAArtifact.builder()
        .id(artifactEntity.getId())
        .artifactId(artifactEntity.getArtifactId())
        .orchestrationId(artifactEntity.getOrchestrationId())
        .artifactCorrelationId(artifactEntity.getArtifactCorrelationId())
        .url(artifactEntity.getUrl())
        .name(artifactEntity.getName())
        .type(artifactEntity.getType())
        .tag(artifactEntity.getTag())
        .accountId(artifactEntity.getAccountId())
        .orgIdentifier(artifactEntity.getOrgId())
        .projectIdentifier(artifactEntity.getProjectId())
        .pipelineExecutionId(artifactEntity.getPipelineExecutionId())
        .pipelineIdentifier(artifactEntity.getPipelineId())
        .stageIdentifier(artifactEntity.getStageId())
        .sequenceIdentifier(artifactEntity.getSequenceId())
        .stepIdentifier(artifactEntity.getStepId())
        .sbomName(artifactEntity.getSbomName())
        .createdOn(artifactEntity.getCreatedOn() != null ? artifactEntity.getCreatedOn().toEpochMilli() : null)
        .isAttested(artifactEntity.isAttested())
        .attestedFileUrl(artifactEntity.getAttestedFileUrl())
        .sbom(artifactEntity.getSbom() != null ? SSCAArtifact.Sbom.builder()
                                                     .tool(artifactEntity.getSbom().getTool())
                                                     .toolVersion(artifactEntity.getSbom().getToolVersion())
                                                     .sbomFormat(artifactEntity.getSbom().getSbomFormat())
                                                     .sbomVersion(artifactEntity.getSbom().getSbomVersion())
                                                     .build()
                                               : null)
        .invalid(artifactEntity.getInvalid())
        .lastUpdatedAt(artifactEntity.getLastUpdatedAt())
        .componentsCount(artifactEntity.getComponentsCount())
        .prodEnvCount(artifactEntity.getProdEnvCount())
        .nonProdEnvCount(artifactEntity.getNonProdEnvCount())
        .scorecard(artifactEntity.getScorecard() != null ? Scorecard.builder()
                                                               .avgScore(artifactEntity.getScorecard().getAvgScore())
                                                               .maxScore(artifactEntity.getScorecard().getMaxScore())
                                                               .build()
                                                         : null)
        .relation_type(RelationshipType.builder().name(ARTIFACT_ENTITY).build())
        .build();
  }
}
