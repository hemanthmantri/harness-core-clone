/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody.PolicyViolationEnum;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.instance.ArtifactDetailsDTO;
import io.harness.ssca.beans.instance.InstanceDTO;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryBuilder;
import io.harness.ssca.helpers.CdInstanceSummaryServiceHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class CdInstanceSummaryServiceImplTest extends SSCAManagerTestBase {
  @Inject @InjectMocks CdInstanceSummaryServiceImpl cdInstanceSummaryService;
  @Mock CdInstanceSummaryRepo cdInstanceSummaryRepo;
  @Mock ArtifactService artifactService;
  @Mock FeatureFlagService featureFlagService;
  @Mock CdInstanceSummaryServiceHelper cdInstanceSummaryServiceHelper;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(cdInstanceSummaryService, "cdInstanceSummaryRepo", cdInstanceSummaryRepo, true);
    FieldUtils.writeField(cdInstanceSummaryService, "artifactService", artifactService, true);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpsertInstance_noArtifactIdentity() {
    Boolean response = cdInstanceSummaryService.upsertInstance(
        builderFactory.getInstanceNGEntityBuilder()
            .primaryArtifact(
                ArtifactDetailsDTO.builder().artifactId("artifactId").displayName("image").tag("tag").build())
            .build());
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpsertInstance_noArtifactIdentity_FFEnabled() {
    InstanceDTO k8sInstance = builderFactory.getK8sInstanceDTOBuilder().primaryArtifact(null).build();
    when(featureFlagService.isFeatureFlagEnabled(
             k8sInstance.getAccountIdentifier(), FeatureName.SSCA_MATCH_INSTANCE_IMAGE_NAME.name()))
        .thenReturn(true);
    when(cdInstanceSummaryServiceHelper.isK8sInstanceInfo(k8sInstance)).thenReturn(true);
    ArtifactEntity artifact = builderFactory.getArtifactEntityBuilder()
                                  .name("image1")
                                  .artifactCorrelationId("image1:tag1")
                                  .tag("tag1")
                                  .build();
    when(cdInstanceSummaryServiceHelper.findCorrelatedArtifactsForK8sInstance(k8sInstance))
        .thenReturn(Set.of(artifact));
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(null);
    Boolean response = cdInstanceSummaryService.upsertInstance(k8sInstance);
    assertThat(response).isEqualTo(true);
    verify(artifactService, times(1)).updateArtifactEnvCount(artifact, EnvType.Production, 1);
    verify(cdInstanceSummaryRepo, times(1)).save(any());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpsertInstance() {
    Boolean response = cdInstanceSummaryService.upsertInstance(
        builderFactory.getInstanceNGEntityBuilder()
            .primaryArtifact(
                ArtifactDetailsDTO.builder().artifactId("artifactId").displayName("image").tag("tag").build())
            .build());
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRemoveInstance() {
    Boolean response = cdInstanceSummaryService.removeInstance(builderFactory.getInstanceNGEntityBuilder().build());
    assertThat(response).isEqualTo(true);
    when(cdInstanceSummaryRepo.findOne(Mockito.any())).thenReturn(builderFactory.getCdInstanceSummaryBuilder().build());

    when(artifactService.getArtifactByCorrelationId(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(builderFactory.getArtifactEntityBuilder().build());

    response = cdInstanceSummaryService.removeInstance(builderFactory.getInstanceNGEntityBuilder().build());
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRemoveInstance_noArtifactIdentity_FFEnabled() {
    InstanceDTO k8sInstance = builderFactory.getK8sInstanceDTOBuilder().primaryArtifact(null).build();
    when(featureFlagService.isFeatureFlagEnabled(
             k8sInstance.getAccountIdentifier(), FeatureName.SSCA_MATCH_INSTANCE_IMAGE_NAME.name()))
        .thenReturn(true);
    when(cdInstanceSummaryServiceHelper.isK8sInstanceInfo(k8sInstance)).thenReturn(true);
    ArtifactEntity artifact = builderFactory.getArtifactEntityBuilder()
                                  .name("image1")
                                  .artifactCorrelationId("image1:tag1")
                                  .tag("tag1")
                                  .build();
    when(cdInstanceSummaryServiceHelper.findCorrelatedArtifactsForK8sInstance(k8sInstance))
        .thenReturn(Set.of(artifact));
    when(cdInstanceSummaryRepo.findOne(Mockito.any())).thenReturn(builderFactory.getCdInstanceSummaryBuilder().build());
    when(artifactService.getArtifactByCorrelationId(k8sInstance.getAccountIdentifier(), k8sInstance.getOrgIdentifier(),
             k8sInstance.getProjectIdentifier(), "image1:tag1"))
        .thenReturn(artifact);

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(null);
    Boolean response = cdInstanceSummaryService.removeInstance(k8sInstance);
    assertThat(response).isEqualTo(true);
    verify(artifactService, times(1)).updateArtifactEnvCount(artifact, EnvType.Production, 0);
    verify(cdInstanceSummaryRepo, times(1)).save(any());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetCdInstanceSummaries() {
    CdInstanceSummaryBuilder builder = builderFactory.getCdInstanceSummaryBuilder();
    Page<CdInstanceSummary> entities =
        new PageImpl<>(List.of(builder.envIdentifier("env1").build(), builder.envIdentifier("env2").build(),
                           builder.envIdentifier("env3").build()),
            Pageable.ofSize(2).withPage(0), 5);

    when(cdInstanceSummaryRepo.findAll(Mockito.any(), Mockito.any())).thenReturn(entities);

    Page<CdInstanceSummary> cdInstanceSummaryPage =
        cdInstanceSummaryService.getCdInstanceSummaries(builderFactory.getContext().getAccountId(),
            builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
            ArtifactEntity.builder().build(), null, Pageable.ofSize(3).withPage(0));
    List<CdInstanceSummary> cdInstanceSummaryList = cdInstanceSummaryPage.get().collect(Collectors.toList());
    assertThat(cdInstanceSummaryList.size()).isEqualTo(3);
    assertThat(cdInstanceSummaryList.get(0))
        .isEqualTo(builderFactory.getCdInstanceSummaryBuilder().envIdentifier("env1").build());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetCdInstanceSummary() {
    when(cdInstanceSummaryRepo.findOne(Mockito.any())).thenReturn(builderFactory.getCdInstanceSummaryBuilder().build());

    CdInstanceSummary cdInstanceSummary = cdInstanceSummaryService.getCdInstanceSummary(
        builderFactory.getContext().getAccountId(), builderFactory.getContext().getOrgIdentifier(),
        builderFactory.getContext().getProjectIdentifier(), "artifactCorrelationId", "envId");

    assertThat(cdInstanceSummary).isEqualTo(builderFactory.getCdInstanceSummaryBuilder().build());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateCdInstanceSummary() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("pms-execution-response-with-slsa.json");
    Map<String, Object> pmsJsonResponse = objectMapper.readValue(inputStream, Map.class);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(pmsJsonResponse);
    ArtifactEntity artifact =
        builderFactory.getArtifactEntityBuilder().name("autosscauser/autosscauser-auto").tag("5").build();
    CdInstanceSummary cdInstanceSummary =
        cdInstanceSummaryService.createInstanceSummary(builderFactory.getInstanceNGEntityBuilder().build(), artifact);
    assertThat(cdInstanceSummary.getArtifactCorrelationId()).isEqualTo("artifactCorrelationId");
    assertThat(cdInstanceSummary.getAccountIdentifier()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(cdInstanceSummary.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(cdInstanceSummary.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(cdInstanceSummary.getEnvIdentifier()).isEqualTo("envId");
    assertThat(cdInstanceSummary.getEnvName()).isEqualTo("envName");
    assertThat(cdInstanceSummary.getEnvType()).isEqualTo(EnvType.Production);

    assertThat(cdInstanceSummary.getLastPipelineName()).isEqualTo("SLSA attestation and verification");
    assertThat(cdInstanceSummary.getLastPipelineExecutionName()).isEqualTo("K8sDeploy");
    assertThat(cdInstanceSummary.getLastPipelineExecutionId()).isEqualTo("executionId");
    assertThat(cdInstanceSummary.getLastDeployedAt())
        .isEqualTo(Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC).millis());
    assertThat(cdInstanceSummary.getLastDeployedByName()).isEqualTo("username");
    assertThat(cdInstanceSummary.getLastDeployedById()).isEqualTo("userId");
    assertThat(cdInstanceSummary.getTriggerType()).isEqualTo("MANUAL");
    assertThat(cdInstanceSummary.getSequenceId()).isEqualTo("5");
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getSlsaPolicyOutcomeStatus()).isEqualTo("warning");
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getProvenanceArtifact()).isNotNull();
    assertThat(cdInstanceSummary.getSlsaVerificationSummary().getProvenanceArtifact())
        .isEqualTo(
            "{\"predicateType\":\"https://slsa.dev/provenance/v1\",\"predicate\":{\"buildDefinition\":{\"buildType\":\"https://developer.harness.io/docs/continuous-integration\",\"externalParameters\":{\"codeMetadata\":{\"repositoryURL\":\"https://github.com/nginxinc/docker-nginx\",\"branch\":\"master\"},\"triggerMetadata\":{\"triggerType\":\"MANUAL\",\"triggeredBy\":\"inderpreet.chera@harness.io\"},\"buildMetadata\":{\"image\":\"autosscauser/autosscauser-auto\",\"dockerFile\":\"./stable/alpine/Dockerfile\"}},\"internalParameters\":{\"pipelineExecutionId\":\"jnz3-IB1Q_KlmLAaiS3Guw\",\"accountId\":\"ppbLW9YpRharzPs_JtWT7g\",\"pipelineIdentifier\":\"SLSA_attestation_and_verification\"}},\"runDetails\":{\"builder\":{\"id\":\"https://developer.harness.io/docs/continuous-integration\",\"version\":{\"ci-manager\":\"1.0.6402-000\",\"plugins/kaniko\":\"1.8.0\"}},\"runDetailsMetadata\":{\"invocationId\":\"TCfWZ3j8QSSvN3x3KnxZJA\",\"startedOn\":\"2023-10-26T07:35:21.438Z\",\"finishedOn\":\"2023-10-26T07:36:02.733Z\"}}}}");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateCdInstanceSummary_withUnrelatedSlsa() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("pms-execution-response-with-uncorrelated-slsa.json");
    Map<String, Object> pmsJsonResponse = objectMapper.readValue(inputStream, Map.class);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(pmsJsonResponse);
    ArtifactEntity artifact =
        builderFactory.getArtifactEntityBuilder().name("autosscauser/autosscauser-auto").tag("5").build();
    CdInstanceSummary cdInstanceSummary =
        cdInstanceSummaryService.createInstanceSummary(builderFactory.getInstanceNGEntityBuilder().build(), artifact);
    assertThat(cdInstanceSummary.getArtifactCorrelationId()).isEqualTo("artifactCorrelationId");
    assertThat(cdInstanceSummary.getAccountIdentifier()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(cdInstanceSummary.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(cdInstanceSummary.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(cdInstanceSummary.getEnvIdentifier()).isEqualTo("envId");
    assertThat(cdInstanceSummary.getEnvName()).isEqualTo("envName");
    assertThat(cdInstanceSummary.getEnvType()).isEqualTo(EnvType.Production);

    assertThat(cdInstanceSummary.getLastPipelineName()).isEqualTo("SLSA attestation and verification");
    assertThat(cdInstanceSummary.getLastPipelineExecutionName()).isEqualTo("K8sDeploy");
    assertThat(cdInstanceSummary.getLastPipelineExecutionId()).isEqualTo("executionId");
    assertThat(cdInstanceSummary.getLastDeployedAt())
        .isEqualTo(Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC).millis());
    assertThat(cdInstanceSummary.getLastDeployedByName()).isEqualTo("username");
    assertThat(cdInstanceSummary.getLastDeployedById()).isEqualTo("userId");
    assertThat(cdInstanceSummary.getTriggerType()).isEqualTo("MANUAL");
    assertThat(cdInstanceSummary.getSequenceId()).isEqualTo("5");
    assertThat(cdInstanceSummary.getSlsaVerificationSummary()).isNull();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateCdInstanceSummary_withoutSlsa() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream inputStream = classLoader.getResourceAsStream("pms-execution-response-without-slsa.json");
    Map<String, Object> pmsJsonResponse = objectMapper.readValue(inputStream, Map.class);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(pmsJsonResponse);
    ArtifactEntity artifact =
        builderFactory.getArtifactEntityBuilder().name("autosscauser/autosscauser-auto").tag("5").build();
    CdInstanceSummary cdInstanceSummary =
        cdInstanceSummaryService.createInstanceSummary(builderFactory.getInstanceNGEntityBuilder().build(), artifact);
    assertThat(cdInstanceSummary.getArtifactCorrelationId()).isEqualTo("artifactCorrelationId");
    assertThat(cdInstanceSummary.getAccountIdentifier()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(cdInstanceSummary.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(cdInstanceSummary.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(cdInstanceSummary.getEnvIdentifier()).isEqualTo("envId");
    assertThat(cdInstanceSummary.getEnvName()).isEqualTo("envName");
    assertThat(cdInstanceSummary.getEnvType()).isEqualTo(EnvType.Production);

    assertThat(cdInstanceSummary.getLastPipelineName()).isNull();
    assertThat(cdInstanceSummary.getLastPipelineExecutionName()).isEqualTo("K8sDeploy");
    assertThat(cdInstanceSummary.getLastPipelineExecutionId()).isEqualTo("executionId");
    assertThat(cdInstanceSummary.getLastDeployedAt())
        .isEqualTo(Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC).millis());
    assertThat(cdInstanceSummary.getLastDeployedByName()).isEqualTo("username");
    assertThat(cdInstanceSummary.getLastDeployedById()).isEqualTo("userId");
    assertThat(cdInstanceSummary.getTriggerType()).isEqualTo("MANUAL");
    assertThat(cdInstanceSummary.getSequenceId()).isEqualTo("5");
    assertThat(cdInstanceSummary.getSlsaVerificationSummary()).isNull();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetPolicyViolationEnforcementCriteria() {
    ArtifactDeploymentViewRequestBody body = new ArtifactDeploymentViewRequestBody();
    body.setPolicyViolation(PolicyViolationEnum.ALLOW);
    Criteria criteria = new CdInstanceSummaryServiceImpl().getPolicyViolationEnforcementCriteria(
        "accountId", "orgId", "projectId", body);
    assertThat(new Query(criteria).toString())
        .isEqualTo(
            "Query: { \"accountId\" : \"accountId\", \"orgIdentifier\" : \"orgId\", \"projectIdentifier\" : \"projectId\", \"allowListViolationCount\" : { \"$gt\" : 0}}, Fields: {}, Sort: {}");
    body.setPolicyViolation(PolicyViolationEnum.DENY);
    criteria = new CdInstanceSummaryServiceImpl().getPolicyViolationEnforcementCriteria(
        "accountId", "orgId", "projectId", body);
    assertThat(new Query(criteria).toString())
        .isEqualTo(
            "Query: { \"accountId\" : \"accountId\", \"orgIdentifier\" : \"orgId\", \"projectIdentifier\" : \"projectId\", \"denyListViolationCount\" : { \"$gt\" : 0}}, Fields: {}, Sort: {}");
    body.setPolicyViolation(PolicyViolationEnum.ANY);
    criteria = new CdInstanceSummaryServiceImpl().getPolicyViolationEnforcementCriteria(
        "accountId", "orgId", "projectId", body);
    assertThat(new Query(criteria).toString())
        .isEqualTo(
            "Query: { \"$and\" : [{ \"accountId\" : \"accountId\", \"orgIdentifier\" : \"orgId\", \"projectIdentifier\" : \"projectId\"}, { \"$or\" : [{ \"allowListViolationCount\" : { \"$gt\" : 0}}, { \"denyListViolationCount\" : { \"$gt\" : 0}}]}]}, Fields: {}, Sort: {}");
    body.setPolicyViolation(PolicyViolationEnum.NONE);
    criteria = new CdInstanceSummaryServiceImpl().getPolicyViolationEnforcementCriteria(
        "accountId", "orgId", "projectId", body);
    assertThat(new Query(criteria).toString())
        .isEqualTo(
            "Query: { \"accountId\" : \"accountId\", \"orgIdentifier\" : \"orgId\", \"projectIdentifier\" : \"projectId\", \"denyListViolationCount\" : 0, \"allowListViolationCount\" : 0}, Fields: {}, Sort: {}");
  }
}
