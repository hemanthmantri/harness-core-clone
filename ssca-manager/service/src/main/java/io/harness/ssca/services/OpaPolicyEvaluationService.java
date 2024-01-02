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
import io.harness.ssca.beans.OpaPolicyEvaluationResult;
import io.harness.ssca.beans.PolicyEvaluationResult;
import io.harness.ssca.beans.Violation;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@OwnedBy(HarnessTeam.SSCA)
@Slf4j
public class OpaPolicyEvaluationService implements PolicyEvaluationService {
  @Inject PolicyMgmtService policyMgmtService;
  @Inject NormalisedSbomComponentService normalisedSbomComponentService;
  @Override
  public PolicyEvaluationResult evaluatePolicy(String accountId, String orgIdentifier, String projectIdentifier,
      EnforceSbomRequestBody body, ArtifactEntity artifactEntity) {
    if (CollectionUtils.isEmpty(body.getPolicySetRef())) {
      throw new BadRequestException("policy_set_ref must not be empty");
    }
    log.info("Evaluating policy violations using opa policies for accountId: {} enforcementId: {} policySetRef: {}",
        accountId, body.getEnforcementId(), body.getPolicySetRef());
    List<NormalizedSBOMComponentEntity> normalizedSBOMComponentEntities =
        getNormalizedSBOMComponentEntities(accountId, orgIdentifier, projectIdentifier, body, artifactEntity);
    OpaPolicyEvaluationResult opaPolicyEvaluationResult = policyMgmtService.evaluate(
        accountId, orgIdentifier, projectIdentifier, body.getPolicySetRef(), normalizedSBOMComponentEntities);
    Map<String, NormalizedSBOMComponentEntity> components = normalizedSBOMComponentEntities.stream().collect(
        Collectors.toMap(NormalizedSBOMComponentEntity::getUuid, entity -> entity, (u, v) -> v));
    List<EnforcementResultEntity> allowListViolations =
        getEnforcementResultEntitiesFromOpaViolations(accountId, orgIdentifier, projectIdentifier,
            body.getEnforcementId(), components, artifactEntity, opaPolicyEvaluationResult.getAllowListViolations());
    List<EnforcementResultEntity> denyListViolations =
        getEnforcementResultEntitiesFromOpaViolations(accountId, orgIdentifier, projectIdentifier,
            body.getEnforcementId(), components, artifactEntity, opaPolicyEvaluationResult.getDenyListViolations());
    return PolicyEvaluationResult.builder()
        .denyListViolations(denyListViolations)
        .allowListViolations(allowListViolations)
        .build();
  }

  private List<NormalizedSBOMComponentEntity> getNormalizedSBOMComponentEntities(String accountId, String orgIdentifier,
      String projectIdentifier, EnforceSbomRequestBody body, ArtifactEntity artifactEntity) {
    Instant start = Instant.now();
    List<String> fieldsToBeIncluded = new ArrayList<>();
    fieldsToBeIncluded.add(NormalizedSBOMEntityKeys.packageName);
    fieldsToBeIncluded.add(NormalizedSBOMEntityKeys.purl);
    fieldsToBeIncluded.add(NormalizedSBOMEntityKeys.packageLicense);
    fieldsToBeIncluded.add(NormalizedSBOMEntityKeys.packageOriginatorName);
    fieldsToBeIncluded.add(NormalizedSBOMEntityKeys.packageVersion);
    fieldsToBeIncluded.add(NormalizedSBOMEntityKeys.originatorType);
    fieldsToBeIncluded.add(NormalizedSBOMEntityKeys.packageManager);
    List<NormalizedSBOMComponentEntity> entities =
        normalisedSbomComponentService.getNormalizedSbomComponentsForOrchestrationId(
            accountId, orgIdentifier, projectIdentifier, artifactEntity.getOrchestrationId(), fieldsToBeIncluded);
    Instant end = Instant.now();
    log.info("Time taken to fetch {} sbom entities for enforcementId {} is {} ms", entities.size(),
        body.getEnforcementId(), Duration.between(start, end).toMillis());
    return entities;
  }

  private static List<EnforcementResultEntity> getEnforcementResultEntitiesFromOpaViolations(String accountId,
      String orgIdentifier, String projectIdentifier, String enforcementId,
      Map<String, NormalizedSBOMComponentEntity> components, ArtifactEntity artifactEntity,
      List<Violation> violations) {
    List<EnforcementResultEntity> enforcementResultEntities = new ArrayList<>();
    violations.forEach(opaViolation
        -> opaViolation.getArtifactUuids().forEach(uuid
            -> enforcementResultEntities.add(getEnforcementResultEntity(accountId, orgIdentifier, projectIdentifier,
                enforcementId, components.get(uuid), artifactEntity, opaViolation))));
    return enforcementResultEntities;
  }

  private static EnforcementResultEntity getEnforcementResultEntity(String accountId, String orgIdentifier,
      String projectIdentifier, String enforcementId, NormalizedSBOMComponentEntity component,
      ArtifactEntity artifactEntity, Violation opaViolation) {
    return EnforcementResultEntity.builder()
        .artifactId(artifactEntity.getArtifactId())
        .enforcementID(enforcementId)
        .accountId(accountId)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .supplierType(component.getOriginatorType())
        .supplier(component.getPackageOriginatorName())
        .name(component.getPackageName())
        .packageManager(component.getPackageManager())
        .purl(component.getPurl())
        .license(component.getPackageLicense())
        .violationType(opaViolation.getType())
        .violationDetails(opaViolation.getViolationDetail())
        .tag(artifactEntity.getTag())
        .imageName(artifactEntity.getName())
        .orchestrationID(artifactEntity.getOrchestrationId())
        .version(component.getPackageVersion())
        .build();
  }
}