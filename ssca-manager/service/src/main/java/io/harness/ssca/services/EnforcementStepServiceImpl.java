/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.FeatureName;
import io.harness.exception.DuplicateEntityException;
import io.harness.repositories.EnforcementResultRepo;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.EnforceSbomRequestBody;
import io.harness.spec.server.ssca.v1.model.EnforceSbomResponseBody;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryResponse;
import io.harness.spec.server.ssca.v1.model.PolicyViolation;
import io.harness.ssca.beans.PolicyEvaluationResult;
import io.harness.ssca.beans.PolicyType;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.entities.exemption.Exemption;
import io.harness.ssca.services.exemption.ExemptionHelper;
import io.harness.ssca.services.exemption.ExemptionService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Slf4j
public class EnforcementStepServiceImpl implements EnforcementStepService {
  @Inject ArtifactService artifactService;
  @Inject EnforcementSummaryService enforcementSummaryService;
  @Inject EnforcementResultService enforcementResultService;
  @Inject FeatureFlagService featureFlagService;
  @Inject Map<PolicyType, PolicyEvaluationService> policyEvaluationServiceMapBinder;
  @Inject EnforcementResultRepo enforcementResultRepo;
  @Inject ExemptionService exemptionService;

  @Override
  public EnforceSbomResponseBody enforceSbom(
      String accountId, String orgIdentifier, String projectIdentifier, EnforceSbomRequestBody body) {
    if (enforcementSummaryService
            .getEnforcementSummary(accountId, orgIdentifier, projectIdentifier, body.getEnforcementId())
            .isPresent()) {
      throw new DuplicateEntityException(
          String.format("Enforcement Summary already present with enforcement id [%s]", body.getEnforcementId()));
    }
    String artifactId =
        artifactService.generateArtifactId(body.getArtifact().getRegistryUrl(), body.getArtifact().getName());
    ArtifactEntity artifactEntity =
        artifactService
            .getArtifact(accountId, orgIdentifier, projectIdentifier, artifactId,
                Sort.by(ArtifactEntityKeys.createdOn).descending())
            .orElseThrow(()
                             -> new NotFoundException(
                                 String.format("Artifact with image name [%s] and registry Url [%s] is not found",
                                     body.getArtifact().getName(), body.getArtifact().getRegistryUrl())));
    PolicyEvaluationResult policyEvaluationResult;
    if (featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_ENFORCEMENT_OPA.name())
        && CollectionUtils.isNotEmpty(body.getPolicySetRef())) {
      policyEvaluationResult = policyEvaluationServiceMapBinder.get(PolicyType.OPA)
                                   .evaluatePolicy(accountId, orgIdentifier, projectIdentifier, body, artifactEntity);
    } else {
      policyEvaluationResult = policyEvaluationServiceMapBinder.get(PolicyType.SSCA)
                                   .evaluatePolicy(accountId, orgIdentifier, projectIdentifier, body, artifactEntity);
    }
    int exemptedComponentCount = 0;
    if (featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_ENFORCEMENT_EXEMPTIONS_ENABLED.name())) {
      Map<String, String> exemptedComponents = findExemptedComponentsWithExemptionIds(
          accountId, orgIdentifier, projectIdentifier, artifactId, policyEvaluationResult);
      if (isNotEmpty(exemptedComponents)) {
        exemptedComponentCount = exemptedComponents.size();
        policyEvaluationResult.getDenyListViolations().forEach(
            resultEntity -> checkAndMarkViolationAsExempted(exemptedComponents, resultEntity));
        policyEvaluationResult.getAllowListViolations().forEach(
            resultEntity -> checkAndMarkViolationAsExempted(exemptedComponents, resultEntity));
      }
    }
    enforcementResultRepo.saveAll(Stream
                                      .concat(policyEvaluationResult.getDenyListViolations().stream(),
                                          policyEvaluationResult.getAllowListViolations().stream())
                                      .toList());
    String status = enforcementSummaryService.persistEnforcementSummary(body.getEnforcementId(),
        policyEvaluationResult.getDenyListViolations(), policyEvaluationResult.getAllowListViolations(), artifactEntity,
        body.getPipelineExecutionId(), exemptedComponentCount);
    EnforceSbomResponseBody responseBody = new EnforceSbomResponseBody();
    responseBody.setEnforcementId(body.getEnforcementId());
    responseBody.setStatus(status);
    return responseBody;
  }

  @Override
  public EnforcementSummaryResponse getEnforcementSummary(
      String accountId, String orgIdentifier, String projectIdentifier, String enforcementId) {
    EnforcementSummaryEntity enforcementSummary =
        enforcementSummaryService.getEnforcementSummary(accountId, orgIdentifier, projectIdentifier, enforcementId)
            .orElseThrow(()
                             -> new NotFoundException(String.format(
                                 "Enforcement with enforcementIdentifier [%s] is not found", enforcementId)));

    return new EnforcementSummaryResponse()
        .enforcementId(enforcementSummary.getEnforcementId())
        .artifact(new Artifact()
                      .id(enforcementSummary.getArtifact().getArtifactId())
                      .name(enforcementSummary.getArtifact().getName())
                      .type(enforcementSummary.getArtifact().getType())
                      .registryUrl(enforcementSummary.getArtifact().getUrl())
                      .tag(enforcementSummary.getArtifact().getTag())

                )
        .allowListViolationCount(enforcementSummary.getAllowListViolationCount())
        .denyListViolationCount(enforcementSummary.getDenyListViolationCount())
        .status(enforcementSummary.getStatus());
  }

  @Override
  public Page<PolicyViolation> getPolicyViolations(String accountId, String orgIdentifier, String projectIdentifier,
      String enforcementId, String searchText, Pageable pageable) {
    return enforcementResultService
        .getPolicyViolations(accountId, orgIdentifier, projectIdentifier, enforcementId, searchText, pageable)
        .map(enforcementResultEntity
            -> new PolicyViolation()
                   .enforcementId(enforcementResultEntity.getEnforcementID())
                   .account(enforcementResultEntity.getAccountId())
                   .org(enforcementResultEntity.getOrgIdentifier())
                   .project(enforcementResultEntity.getProjectIdentifier())
                   .artifactId(enforcementResultEntity.getArtifactId())
                   .imageName(enforcementResultEntity.getImageName())
                   .purl(enforcementResultEntity.getPurl())
                   .orchestrationId(enforcementResultEntity.getOrchestrationID())
                   .license(enforcementResultEntity.getLicense())
                   .tag(enforcementResultEntity.getTag())
                   .supplier(enforcementResultEntity.getSupplier())
                   .supplierType(enforcementResultEntity.getSupplierType())
                   .name(enforcementResultEntity.getName())
                   .version(enforcementResultEntity.getVersion())
                   .packageManager(enforcementResultEntity.getPackageManager())
                   .violationType(enforcementResultEntity.getViolationType())
                   .violationDetails(enforcementResultEntity.getViolationDetails()));
  }

  private Map<String, String> findExemptedComponentsWithExemptionIds(String accountId, String orgIdentifier,
      String projectIdentifier, String artifactId, PolicyEvaluationResult policyEvaluationResult) {
    Set<String> componentNames = new HashSet<>();
    Set<String> uniqueComponents = new HashSet<>();
    for (EnforcementResultEntity resultEntity : policyEvaluationResult.getDenyListViolations()) {
      componentNames.add(resultEntity.getName());
      uniqueComponents.add(ExemptionHelper.getUniqueComponentKeyFromEnforcementResultEntity(resultEntity));
    }
    for (EnforcementResultEntity resultEntity : policyEvaluationResult.getAllowListViolations()) {
      componentNames.add(resultEntity.getName());
      uniqueComponents.add(ExemptionHelper.getUniqueComponentKeyFromEnforcementResultEntity(resultEntity));
    }
    List<Exemption> exemptions = exemptionService.getApplicableExemptionsForEnforcement(
        accountId, orgIdentifier, projectIdentifier, artifactId, componentNames.stream().toList());
    return ExemptionHelper.getExemptedComponents(uniqueComponents, exemptions);
  }

  private static void checkAndMarkViolationAsExempted(
      Map<String, String> exemptedComponents, EnforcementResultEntity resultEntity) {
    String uniqueComponentKey = ExemptionHelper.getUniqueComponentKeyFromEnforcementResultEntity(resultEntity);
    if (exemptedComponents.containsKey(uniqueComponentKey)) {
      resultEntity.setExempted(true);
      resultEntity.setExemptionId(exemptedComponents.get(uniqueComponentKey));
    }
  }
}