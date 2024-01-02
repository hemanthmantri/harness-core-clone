/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.repositories.EnforcementResultRepo;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.EnforceSbomRequestBody;
import io.harness.spec.server.ssca.v1.model.EnforceSbomResponseBody;
import io.harness.ssca.beans.OpaPolicyEvaluationResult;
import io.harness.ssca.beans.PolicyType;
import io.harness.ssca.beans.Violation;
import io.harness.ssca.enforcement.constants.ViolationType;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class EnforcementStepServiceImplTest extends SSCAManagerTestBase {
  @Inject EnforcementStepService enforcementStepService;
  @Inject Map<PolicyType, PolicyEvaluationService> policyEvaluationServiceMapBinder;
  @Mock ArtifactService artifactService;
  @Mock EnforcementSummaryService enforcementSummaryService;
  @Mock EnforcementResultService enforcementResultService;
  @Mock PolicyMgmtService policyMgmtService;
  @Mock NormalisedSbomComponentService normalisedSbomComponentService;
  @Mock FeatureFlagService featureFlagService;
  @Mock EnforcementResultRepo enforcementResultRepo;
  private BuilderFactory builderFactory;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  OpaPolicyEvaluationService opaPolicyEvaluationService;

  @Before
  public void setup() throws IllegalAccessException {
    opaPolicyEvaluationService = (OpaPolicyEvaluationService) policyEvaluationServiceMapBinder.get(PolicyType.OPA);
    FieldUtils.writeField(opaPolicyEvaluationService, "policyMgmtService", policyMgmtService, true);
    FieldUtils.writeField(
        opaPolicyEvaluationService, "normalisedSbomComponentService", normalisedSbomComponentService, true);
    FieldUtils.writeField(enforcementStepService, "enforcementResultRepo", enforcementResultRepo, true);
    FieldUtils.writeField(enforcementStepService, "artifactService", artifactService, true);
    FieldUtils.writeField(enforcementStepService, "enforcementSummaryService", enforcementSummaryService, true);
    FieldUtils.writeField(enforcementStepService, "enforcementResultService", enforcementResultService, true);
    FieldUtils.writeField(enforcementStepService, "featureFlagService", featureFlagService, true);
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testEnforceSbom_featureFlagIsOff_sscaPolicyNotConfigured() {
    ArtifactEntity artifactEntity = builderFactory.getArtifactEntityBuilder().build();
    EnforceSbomRequestBody enforceSbomRequestBody = getEnforcementSbomRequestBody();
    enforceSbomRequestBody.setPolicyFileId(null);
    when(featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_ENFORCEMENT_OPA.name())).thenReturn(false);
    when(featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_ENFORCEMENT_EXEMPTIONS_ENABLED.name()))
        .thenReturn(false);
    when(artifactService.generateArtifactId(any(), any())).thenReturn(artifactEntity.getArtifactId());
    when(artifactService.getArtifact(any(), any(), any(), any(), any())).thenReturn(Optional.of(artifactEntity));
    assertThatThrownBy(
        () -> enforcementStepService.enforceSbom(accountId, orgIdentifier, projectIdentifier, enforceSbomRequestBody))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("policy_file_id must not be blank");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testEnforceSbom_featureFlagIsOn_bothOpaAndSscaPolicyConfigured() {
    ArtifactEntity artifactEntity = builderFactory.getArtifactEntityBuilder().build();
    EnforceSbomRequestBody enforceSbomRequestBody = getEnforcementSbomRequestBody();
    OpaPolicyEvaluationResult opaPolicyEvaluationResult = getOpaPolicyEvaluationResult();
    when(featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_ENFORCEMENT_OPA.name())).thenReturn(true);
    when(featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_ENFORCEMENT_EXEMPTIONS_ENABLED.name()))
        .thenReturn(false);
    when(artifactService.generateArtifactId(any(), any())).thenReturn(artifactEntity.getArtifactId());
    when(artifactService.getArtifact(any(), any(), any(), any(), any())).thenReturn(Optional.of(artifactEntity));
    when(
        normalisedSbomComponentService.getNormalizedSbomComponentsForOrchestrationId(any(), any(), any(), any(), any()))
        .thenReturn(getNormalizedSBOMComponentEntities());
    when(policyMgmtService.evaluate(any(), any(), any(), any(), any())).thenReturn(opaPolicyEvaluationResult);
    when(enforcementSummaryService.persistEnforcementSummary(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn("status");
    EnforceSbomResponseBody enforceSbomResponseBody =
        enforcementStepService.enforceSbom(accountId, orgIdentifier, projectIdentifier, enforceSbomRequestBody);
    ArgumentCaptor<List<EnforcementResultEntity>> argument = ArgumentCaptor.forClass(List.class);

    verify(normalisedSbomComponentService, times(1))
        .getNormalizedSbomComponentsForOrchestrationId(any(), any(), any(), any(), any());
    verify(policyMgmtService, times(1)).evaluate(any(), any(), any(), any(), any());
    verify(enforcementResultRepo, times(1)).saveAll(argument.capture());
    List<EnforcementResultEntity> capturedViolations = argument.getAllValues().get(0);
    assertThat(capturedViolations.size()).isEqualTo(4);
    assertThat(capturedViolations.get(0).getViolationType()).isEqualTo(ViolationType.DENYLIST_VIOLATION.getViolation());
    assertThat(capturedViolations.get(1).getViolationType()).isEqualTo(ViolationType.DENYLIST_VIOLATION.getViolation());
    assertThat(capturedViolations.get(2).getViolationType())
        .isEqualTo(ViolationType.ALLOWLIST_VIOLATION.getViolation());
    assertThat(capturedViolations.get(3).getViolationType())
        .isEqualTo(ViolationType.ALLOWLIST_VIOLATION.getViolation());
    assertThat(enforceSbomResponseBody.getStatus()).isEqualTo("status");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testEnforceSbom_featureFlagIsOn_onlyOpaPolicyIsConfigured() {
    ArtifactEntity artifactEntity = builderFactory.getArtifactEntityBuilder().build();
    EnforceSbomRequestBody enforceSbomRequestBody = getEnforcementSbomRequestBody();
    enforceSbomRequestBody.setPolicyFileId(null);
    OpaPolicyEvaluationResult opaPolicyEvaluationResult = getOpaPolicyEvaluationResult();
    when(featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_ENFORCEMENT_OPA.name())).thenReturn(true);
    when(featureFlagService.isFeatureFlagEnabled(accountId, FeatureName.SSCA_ENFORCEMENT_EXEMPTIONS_ENABLED.name()))
        .thenReturn(false);
    when(artifactService.generateArtifactId(any(), any())).thenReturn(artifactEntity.getArtifactId());
    when(artifactService.getArtifact(any(), any(), any(), any(), any())).thenReturn(Optional.of(artifactEntity));
    when(
        normalisedSbomComponentService.getNormalizedSbomComponentsForOrchestrationId(any(), any(), any(), any(), any()))
        .thenReturn(getNormalizedSBOMComponentEntities());
    when(policyMgmtService.evaluate(any(), any(), any(), any(), any())).thenReturn(opaPolicyEvaluationResult);
    when(enforcementSummaryService.persistEnforcementSummary(any(), any(), any(), any(), any(), anyInt()))
        .thenReturn("status");
    EnforceSbomResponseBody enforceSbomResponseBody =
        enforcementStepService.enforceSbom(accountId, orgIdentifier, projectIdentifier, enforceSbomRequestBody);
    ArgumentCaptor<List<EnforcementResultEntity>> argument = ArgumentCaptor.forClass(List.class);

    verify(normalisedSbomComponentService, times(1))
        .getNormalizedSbomComponentsForOrchestrationId(any(), any(), any(), any(), any());
    verify(policyMgmtService, times(1)).evaluate(any(), any(), any(), any(), any());
    verify(enforcementResultRepo, times(1)).saveAll(argument.capture());
    List<EnforcementResultEntity> capturedViolations = argument.getAllValues().get(0);
    assertThat(capturedViolations.size()).isEqualTo(4);
    assertThat(capturedViolations.get(0).getViolationType()).isEqualTo(ViolationType.DENYLIST_VIOLATION.getViolation());
    assertThat(capturedViolations.get(1).getViolationType()).isEqualTo(ViolationType.DENYLIST_VIOLATION.getViolation());
    assertThat(capturedViolations.get(2).getViolationType())
        .isEqualTo(ViolationType.ALLOWLIST_VIOLATION.getViolation());
    assertThat(capturedViolations.get(3).getViolationType())
        .isEqualTo(ViolationType.ALLOWLIST_VIOLATION.getViolation());
    assertThat(enforceSbomResponseBody.getStatus()).isEqualTo("status");
  }

  private static Artifact getArtifact() {
    Artifact artifact = new Artifact();
    artifact.setId("setId");
    artifact.setName("setName");
    artifact.setTag("setTag");
    artifact.setType("setType");
    artifact.setRegistryUrl("setRegistryUrl");
    return artifact;
  }
  private static EnforceSbomRequestBody getEnforcementSbomRequestBody() {
    Artifact artifact = getArtifact();
    EnforceSbomRequestBody enforceSbomRequestBody = new EnforceSbomRequestBody();
    enforceSbomRequestBody.setEnforcementId("setEnforcementId");
    enforceSbomRequestBody.setArtifact(artifact);
    enforceSbomRequestBody.setPolicyFileId("setPolicyFileId");
    enforceSbomRequestBody.setPolicySetRef(List.of("setPolicySetRef"));
    enforceSbomRequestBody.setPipelineExecutionId("setPipelineExecutionId");
    return enforceSbomRequestBody;
  }

  private static List<NormalizedSBOMComponentEntity> getNormalizedSBOMComponentEntities() {
    List<NormalizedSBOMComponentEntity> entities = new ArrayList<>();
    entities.add(NormalizedSBOMComponentEntity.builder().uuid("artifactId1").build());
    entities.add(NormalizedSBOMComponentEntity.builder().uuid("artifactId2").build());
    return entities;
  }

  private static List<Violation> getAllowListViolations() {
    List<Violation> violations = new ArrayList<>();
    violations.add(Violation.builder()
                       .rule(JsonUtils.readTree("[{\"license\":{\"operator\":\"==\",\"value\":\"GPL-29999.0-only\"}}]"))
                       .type("allow")
                       .artifactUuids(List.of("artifactId1", "artifactId2"))
                       .build());
    return violations;
  }

  private static List<Violation> getDenyListViolations() {
    List<Violation> violations = new ArrayList<>();
    violations.add(Violation.builder()
                       .rule(JsonUtils.readTree("{\"name\":{\"operator\":\"~\",\"value\":\"d.*\"}}"))
                       .type("deny")
                       .artifactUuids(List.of("artifactId1", "artifactId2"))
                       .build());
    return violations;
  }

  private static OpaPolicyEvaluationResult getOpaPolicyEvaluationResult() {
    return OpaPolicyEvaluationResult.builder()
        .allowListViolations(getAllowListViolations())
        .denyListViolations(getDenyListViolations())
        .build();
  }
}