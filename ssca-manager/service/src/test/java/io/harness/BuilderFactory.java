/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.k8s.model.K8sContainer;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.Attestation;
import io.harness.spec.server.ssca.v1.model.CategoryScorecard;
import io.harness.spec.server.ssca.v1.model.CategoryScorecardChecks;
import io.harness.spec.server.ssca.v1.model.EnforcementResultDTO;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryDTO;
import io.harness.spec.server.ssca.v1.model.NormalizedSbomComponentDTO;
import io.harness.spec.server.ssca.v1.model.RemediationTrackerCreateRequestBody;
import io.harness.spec.server.ssca.v1.model.SbomDetailsForScorecard;
import io.harness.spec.server.ssca.v1.model.SbomMetadata;
import io.harness.spec.server.ssca.v1.model.SbomProcess;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.spec.server.ssca.v1.model.SbomScorecardRequestBody;
import io.harness.spec.server.ssca.v1.model.ScorecardInfo;
import io.harness.ssca.beans.CyclonedxDTO;
import io.harness.ssca.beans.CyclonedxDTO.CyclonedxDTOBuilder;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.SpdxDTO;
import io.harness.ssca.beans.SpdxDTO.SpdxDTOBuilder;
import io.harness.ssca.beans.instance.ArtifactCorrelationDetailsDTO;
import io.harness.ssca.beans.instance.ArtifactDetailsDTO;
import io.harness.ssca.beans.instance.InstanceDTO;
import io.harness.ssca.beans.instance.InstanceDTO.InstanceDTOBuilder;
import io.harness.ssca.beans.instance.K8sInstanceInfoDTO;
import io.harness.ssca.beans.remediation_tracker.PatchedPendingArtifactEntitiesResult;
import io.harness.ssca.entities.BaselineEntity;
import io.harness.ssca.entities.BaselineEntity.BaselineEntityBuilder;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryBuilder;
import io.harness.ssca.entities.ConfigEntity;
import io.harness.ssca.entities.ConfigEntity.ConfigEntityBuilder;
import io.harness.ssca.entities.ConfigEntity.ConfigInfo;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.EnforcementResultEntity.EnforcementResultEntityBuilder;
import io.harness.ssca.entities.EnforcementSummaryEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity.EnforcementSummaryEntityBuilder;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMComponentEntityBuilder;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity.ArtifactEntityBuilder;
import io.harness.ssca.entities.remediation_tracker.DefaultVulnerability;
import io.harness.ssca.entities.remediation_tracker.RemediationCondition;
import io.harness.ssca.entities.remediation_tracker.RemediationTrackerEntity;
import io.harness.ssca.entities.remediation_tracker.RemediationTrackerEntity.RemediationTrackerEntityBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder(buildMethodName = "unsafeBuild")
public class BuilderFactory {
  public static final String CONNECTOR_IDENTIFIER = "connectorIdentifier";
  @Getter @Setter(AccessLevel.PRIVATE) private Clock clock;
  @Getter @Setter(AccessLevel.PRIVATE) private Context context;

  public static BuilderFactory getDefault() {
    return BuilderFactory.builder().build();
  }

  public static class BuilderFactoryBuilder {
    public BuilderFactory build() {
      BuilderFactory builder = unsafeBuild();
      if (builder.clock == null) {
        builder.setClock(Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC));
      }
      if (builder.getContext() == null) {
        builder.setContext(BuilderFactory.Context.defaultContext());
      }
      return builder;
    }
  }

  @Data
  @Builder
  public static class Context {
    String accountId;
    String orgIdentifier;
    String projectIdentifier;
    String serviceIdentifier;
    String envIdentifier;

    public String getMonitoredServiceIdentifier() {
      return serviceIdentifier + "_" + envIdentifier;
    }

    public static BuilderFactory.Context defaultContext() {
      return Context.builder()
          .accountId(randomAlphabetic(20))
          .orgIdentifier(randomAlphabetic(20))
          .projectIdentifier(randomAlphabetic(20))
          .envIdentifier(randomAlphabetic(20))
          .serviceIdentifier(randomAlphabetic(20))
          .build();
    }

    public String getAccountId() {
      return accountId;
    }

    public String getOrgIdentifier() {
      return orgIdentifier;
    }

    public String getProjectIdentifier() {
      return projectIdentifier;
    }

    public void setAccountId(String accountId) {
      this.accountId = accountId;
    }
    public void setOrgIdentifier(String orgIdentifier) {
      this.orgIdentifier = orgIdentifier;
    }
    public void setProjectIdentifier(String projectIdentifier) {
      this.projectIdentifier = projectIdentifier;
    }
  }

  public CyclonedxDTOBuilder getCyclonedxDTOBuilder() {
    return CyclonedxDTO.builder().specVersion("3.0").components(Arrays.asList());
  }

  public SpdxDTOBuilder getSpdxDTOBuilder() {
    return SpdxDTO.builder().spdxVersion("3.0").packages(Arrays.asList());
  }

  public SbomProcessRequestBody getSbomProcessRequestBody(String format, String data) {
    return new SbomProcessRequestBody()
        .sbomProcess(new SbomProcess()
                         .name("testSbom")
                         .format(format)
                         .url("sbomUrl")
                         .data(data.getBytes(StandardCharsets.UTF_8)))
        .sbomMetadata(new SbomMetadata()
                          .buildUrl("buildUrl")
                          .format(format)
                          .pipelineIdentifier("pipelineId")
                          .pipelineExecutionId("execution-1")
                          .stepExecutionId("stepExecution-1")
                          .sequenceId("1")
                          .stageIdentifier("stageId")
                          .tool("syft")
                          .stepIdentifier("orchestrationStepId"))
        .artifact(new Artifact()
                      .registryUrl("https://index.docker.com")
                      .type("image/repo")
                      .name("test/image")
                      .tag("tag")
                      .id("id"))
        .attestation(new Attestation().isAttested(true).url("www.google.com"));
  }

  public ArtifactEntityBuilder getArtifactEntityBuilder() {
    return ArtifactEntity.builder()
        .accountId(context.accountId)
        .orgId(context.getOrgIdentifier())
        .projectId(context.getProjectIdentifier())
        .artifactId("artifactId")
        .orchestrationId("stepExecutionId")
        .artifactCorrelationId("artifactCorrelationId")
        .url("testUrl")
        .name("test/image")
        .type("imgae/repo")
        .tag("tag")
        .pipelineExecutionId("pipelineExecutionId")
        .pipelineId("pipelineId")
        .stageId("stageId")
        .sequenceId("1")
        .stepId("stepId")
        .sbomName("sbomName")
        .createdOn(Instant.now())
        .isAttested(true)
        .attestedFileUrl("www.google.com")
        .sbom(ArtifactEntity.Sbom.builder()
                  .sbomVersion("3.0")
                  .sbomFormat("spdx-json")
                  .toolVersion("2.0")
                  .tool("syft")
                  .build())
        .invalid(false)
        .lastUpdatedAt(Instant.now().toEpochMilli())
        .componentsCount(35l)
        .nonProdEnvCount(1l)
        .prodEnvCount(2l);
  }

  public RemediationTrackerCreateRequestBody getRemediationTrackerCreateRequestBody() {
    return new RemediationTrackerCreateRequestBody()
        .comments("test")
        .contact(new io.harness.spec.server.ssca.v1.model.ContactInfo().name("test").email("test@gmail.com"))
        .vulnerabilityInfo(new io.harness.spec.server.ssca.v1.model.DefaultVulnerability()
                               .type("Default")
                               .severity(io.harness.spec.server.ssca.v1.model.VulnerabilitySeverity.HIGH)
                               .componentName("log4j"))
        .targetEndDate(LocalDate.of(2020, 5, 22))
        .remediationCondition(new io.harness.spec.server.ssca.v1.model.RemediationCondition().operator(
            io.harness.spec.server.ssca.v1.model.RemediationCondition.OperatorEnum.ALL));
  }

  public RemediationTrackerEntityBuilder getRemediationTrackerEntityBuilder() {
    return RemediationTrackerEntity.builder()
        .accountIdentifier(context.accountId)
        .orgIdentifier(context.orgIdentifier)
        .projectIdentifier(context.projectIdentifier)
        .contactInfo(io.harness.ssca.entities.remediation_tracker.ContactInfo.builder()
                         .name("test")
                         .email("test@gmail.com")
                         .build())
        .vulnerabilityInfo(DefaultVulnerability.builder().component("log4j").build())
        .condition(RemediationCondition.builder().operator(RemediationCondition.Operator.ALL).build());
  }

  public PatchedPendingArtifactEntitiesResult getPatchedPendingArtifactEntitiesResult() {
    return PatchedPendingArtifactEntitiesResult.builder()
        .patchedArtifacts(
            Collections.singletonList(getArtifactEntityBuilder().artifactCorrelationId("patched").build()))
        .pendingArtifacts(
            Collections.singletonList(getArtifactEntityBuilder().artifactCorrelationId("pending").build()))
        .build();
  }
  public EnforcementSummaryEntityBuilder getEnforcementSummaryBuilder() {
    return EnforcementSummaryEntity.builder()
        .accountId(context.accountId)
        .orgIdentifier(context.orgIdentifier)
        .projectIdentifier(context.projectIdentifier)
        .pipelineExecutionId("pipelineExecutionId")
        .enforcementId("enforcementId")
        .createdAt(1000l)
        .denyListViolationCount(1)
        .allowListViolationCount(5)
        .artifact(io.harness.ssca.beans.Artifact.builder()
                      .artifactId("artifactId")
                      .tag("tag")
                      .name("test/image")
                      .type("image/repo")
                      .url("https://index.docker.com/v2/")
                      .build())
        .orchestrationId("orchestrationId")
        .status("Failed");
  }

  public EnforcementSummaryDTO getEnforcementSummaryDTO() {
    return new EnforcementSummaryDTO()
        .accountId(context.accountId)
        .orgIdentifier(context.orgIdentifier)
        .projectIdentifier(context.projectIdentifier)
        .pipelineExecutionId("pipelineExecutionId")
        .created(new BigDecimal(1000))
        .enforcementId("enforcementId")
        .allowListViolationCount(new BigDecimal(5))
        .denyListViolationCount(new BigDecimal(1))
        .orchestrationId("orchestrationId")
        .status("Failed")
        .artifact(new io.harness.spec.server.ssca.v1.model.Artifact()
                      .id("artifactId")
                      .name("test/image")
                      .tag("tag")
                      .type("image/repo")
                      .registryUrl("https://index.docker.com/v2/"));
  }

  public CdInstanceSummaryBuilder getCdInstanceSummaryBuilder() {
    Set<String> instanceIdSet = new HashSet<>();
    instanceIdSet.add("instance1");
    instanceIdSet.add("instance2");
    return CdInstanceSummary.builder()
        .artifactCorrelationId("artifactCorrelationId")
        .accountIdentifier(context.accountId)
        .orgIdentifier(context.orgIdentifier)
        .projectIdentifier(context.projectIdentifier)
        .lastPipelineExecutionId("lastExecutionId")
        .lastPipelineExecutionName("K8sDeploy")
        .lastDeployedAt(clock.millis())
        .lastDeployedById("userId")
        .lastDeployedByName("username")
        .envIdentifier("envId")
        .envName("envName")
        .envType(EnvType.Production)
        .instanceIds(instanceIdSet);
  }

  public NormalizedSBOMComponentEntityBuilder getNormalizedSBOMComponentBuilder() {
    return NormalizedSBOMComponentEntity.builder()
        .orchestrationId("orchestrationId")
        .sbomVersion("3.9")
        .artifactUrl("https://index.docker.com/v2/")
        .artifactId("artifactId")
        .artifactName("test/image")
        .tags(List.of("tag"))
        .createdOn(clock.instant())
        .toolVersion("2.0")
        .toolName("syft")
        .toolVendor("syft.org")
        .packageId("packageId")
        .packageName("packageName")
        .packageDescription("packageDescription")
        .packageLicense(List.of("license1", "license2"))
        .packageSourceInfo("packageSourceInfo")
        .packageVersion("packageVersion")
        .packageSupplierName("packageSupplierName")
        .packageOriginatorName("packageOriginatorName")
        .originatorType("originatorType")
        .packageType("packageType")
        .packageCpe("packageCpe")
        .packageProperties("packageProperties")
        .purl("purl")
        .packageManager("packageManager")
        .packageNamespace("packageNamespace")
        .majorVersion(1)
        .minorVersion(2)
        .patchVersion(1)
        .pipelineIdentifier("pipelineIdentifier")
        .projectIdentifier(context.projectIdentifier)
        .orgIdentifier(context.orgIdentifier)
        .accountId(context.accountId)
        .sequenceId("1");
  }

  public NormalizedSbomComponentDTO getNormalizedSbomComponentDTO() {
    return new NormalizedSbomComponentDTO()
        .packageManager("packageManager")
        .packageNamespace("packageNamespace")
        .purl("purl")
        .patchVersion(new BigDecimal(1))
        .minorVersion(new BigDecimal(2))
        .majorVersion(new BigDecimal(1))
        .artifactUrl("https://index.docker.com/v2/")
        .accountId(context.accountId)
        .orgIdentifier(context.orgIdentifier)
        .projectIdentifier(context.projectIdentifier)
        .artifactId("artifactId")
        .artifactName("test/image")
        .created(new BigDecimal(clock.millis()))
        .orchestrationId("orchestrationId")
        .originatorType("originatorType")
        .packageCpe("packageCpe")
        .packageDescription("packageDescription")
        .packageId("packageId")
        .packageLicense(Arrays.asList("license1", "license2"))
        .packageName("packageName")
        .packageOriginatorName("packageOriginatorName")
        .packageProperties("packageProperties")
        .packageSourceInfo("packageSourceInfo")
        .packageSupplierName("packageSupplierName")
        .packageType("packageType")
        .packageVersion("packageVersion")
        .sbomVersion("3.9")
        .pipelineIdentifier("pipelineIdentifier")
        .sequenceId("1")
        .tags(List.of("tag"))
        .toolName("syft")
        .toolVendor("syft.org")
        .toolVersion("2.0");
  }

  public InstanceDTOBuilder getInstanceNGEntityBuilder() {
    return InstanceDTO.builder()
        .id("instanceId")
        .accountIdentifier(context.accountId)
        .orgIdentifier(context.orgIdentifier)
        .projectIdentifier(context.projectIdentifier)
        .envIdentifier("envId")
        .envName("envName")
        .envType(EnvironmentType.Production.toString())
        .lastDeployedAt(clock.millis())
        .lastDeployedById("userId")
        .lastDeployedByName("username")
        .lastPipelineExecutionId("executionId")
        .lastPipelineExecutionName("K8sDeploy")
        .primaryArtifact(
            ArtifactDetailsDTO.builder()
                .artifactId("artifactId")
                .displayName("autosscauser/autosscauser-auto:5")
                .tag("5")
                .artifactIdentity(ArtifactCorrelationDetailsDTO.builder().image("artifactCorrelationId").build())
                .build())
        .isDeleted(false);
  }

  public InstanceDTOBuilder getK8sInstanceDTOBuilder() {
    return getInstanceNGEntityBuilder().instanceInfo(
        K8sInstanceInfoDTO.builder()
            .containerList(List.of(K8sContainer.builder().image("image1:tag1").build(),
                K8sContainer.builder().image("image2:tag2").build(),
                K8sContainer.builder().image("some/path/to/image3:tag3").build()))
            .build());
  }

  public EnforcementResultEntityBuilder getEnforcementResultEntityBuilder() {
    return EnforcementResultEntity.builder()
        .accountId("accountId")
        .purl("purl")
        .enforcementID("enforcementId")
        .artifactId("artifactId")
        .imageName("imageName")
        .license(Arrays.asList("license1", "license2"))
        .name("name")
        .orchestrationID("orchestrationId")
        .orgIdentifier("orgIdentifier")
        .packageManager("packageManager")
        .projectIdentifier("projectIdentifier")
        .supplier("supplier")
        .supplierType("supplierType")
        .tag("tag")
        .version("version")
        .violationDetails("violationDetails")
        .violationType("violationType");
  }

  public EnforcementResultDTO getEnforcementResultDTO() {
    return new EnforcementResultDTO()
        .accountId("accountId")
        .purl("purl")
        .enforcementId("enforcementId")
        .artifactId("artifactId")
        .imageName("imageName")
        .license(Arrays.asList("license1", "license2"))
        .name("name")
        .orchestrationId("orchestrationId")
        .orgIdentifier("orgIdentifier")
        .packageManager("packageManager")
        .projectIdentifier("projectIdentifier")
        .supplier("supplier")
        .supplierType("supplierType")
        .tag("tag")
        .version("version")
        .violationDetails("violationDetails")
        .violationType("violationType");
  }

  public BaselineEntityBuilder getBaselineEntityBuilder() {
    return BaselineEntity.builder()
        .accountIdentifier(context.getAccountId())
        .orgIdentifier(context.orgIdentifier)
        .projectIdentifier(context.projectIdentifier)
        .artifactId("artifactId")
        .tag("tag");
  }

  public ConfigEntityBuilder getConfigEntityBuilder() {
    return ConfigEntity.builder()
        .accountId(context.accountId)
        .orgId(context.getOrgIdentifier())
        .projectId(context.getProjectIdentifier())
        .configId("configId")
        .name("sbomqs")
        .type("scorecard")
        .creationOn("2023-10-17T16:00:54+00:00")
        .userId("example user")
        .configInfos(getConfigInfoList());
  }

  private List<ConfigInfo> getConfigInfoList() {
    List<ConfigInfo> configInfoList = new ArrayList<>();

    Map<String, String> config = new HashMap<>();
    config.put("key1", "value1");
    config.put("key2", "value2");
    configInfoList.add(ConfigEntity.ConfigInfo.builder().categoryName("example category name").config(config).build());

    return configInfoList;
  }

  public SbomScorecardRequestBody getSbomScorecardRequestBody() {
    return new SbomScorecardRequestBody()
        .accountId(context.accountId)
        .orgId(context.getOrgIdentifier())
        .projectId(context.getProjectIdentifier())
        .orchestrationId("orchestrationId")
        .avgScore("8.0")
        .maxScore("10.0")
        .creationOn(clock.instant().toString())
        .sbomDetails(getSbomScorecardDetails())
        .scoreCardInfo(new ScorecardInfo().toolName("sbomqs").toolVersion("v0.0.25"))
        .category(getScorecardCategories());
  }

  private SbomDetailsForScorecard getSbomScorecardDetails() {
    return new SbomDetailsForScorecard()
        .toolName("trivy")
        .toolVersion("0.46.0")
        .sbomFileName("working_sbom.json")
        .sbomFormat("cyclonedx")
        .sbomVersion("1.5")
        .fileFormat("json");
  }

  private List<CategoryScorecard> getScorecardCategories() {
    List<CategoryScorecard> categories = new ArrayList<>();

    List<CategoryScorecardChecks> checkList = new ArrayList<>();

    checkList.add(new CategoryScorecardChecks()
                      .name("sbom_spec")
                      .isEnabled("true")
                      .score("10")
                      .maxScore("10")
                      .description("provided sbom is in a supported sbom format of spdx,cyclonedx"));

    categories.add(new CategoryScorecard()
                       .name("Structural")
                       .score("9.0")
                       .maxScore("10.0")
                       .isEnabled("true")
                       .weightage("0")
                       .checks(checkList));

    return categories;
  }
}
