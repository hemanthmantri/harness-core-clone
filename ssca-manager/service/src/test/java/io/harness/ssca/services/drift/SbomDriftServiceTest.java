/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services.drift;

import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SHASHWAT_SACHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.drift.SbomDriftRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.ArtifactSbomDriftRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactSbomDriftResponse;
import io.harness.spec.server.ssca.v1.model.ComponentDriftSummary;
import io.harness.spec.server.ssca.v1.model.LicenseDriftSummary;
import io.harness.spec.server.ssca.v1.model.OrchestrationDriftSummary;
import io.harness.ssca.beans.BaselineDTO;
import io.harness.ssca.beans.drift.ComponentDrift;
import io.harness.ssca.beans.drift.ComponentDriftResults;
import io.harness.ssca.beans.drift.ComponentDriftStatus;
import io.harness.ssca.beans.drift.ComponentSummary;
import io.harness.ssca.beans.drift.DriftBase;
import io.harness.ssca.beans.drift.LicenseDrift;
import io.harness.ssca.beans.drift.LicenseDriftResults;
import io.harness.ssca.beans.drift.LicenseDriftStatus;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.drift.DriftEntity;
import io.harness.ssca.entities.drift.DriftEntity.DriftEntityBuilder;
import io.harness.ssca.helpers.SbomDriftCalculator;
import io.harness.ssca.mapper.SbomDriftMapper;
import io.harness.ssca.services.ArtifactService;
import io.harness.ssca.services.BaselineService;
import io.harness.ssca.services.NormalisedSbomComponentService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.SSCA)
public class SbomDriftServiceTest extends SSCAManagerTestBase {
  @InjectMocks SbomDriftServiceImpl sbomDriftService;
  @Mock ArtifactService artifactService;
  @Mock SbomDriftCalculator sbomDriftCalculator;
  @Mock NormalisedSbomComponentService normalisedSbomComponentService;
  @Mock BaselineService baselineService;
  @Mock SbomDriftRepository sbomDriftRepository;

  private final BuilderFactory builderFactory = BuilderFactory.getDefault();
  private static String ACCOUNT_ID;
  private static String ORG_ID;
  private static String PROJECT_ID;
  private static final String ARTIFACT_ID = "artifactId";
  private static final String ORCHESTRATION_ID = "stepExecutionId";
  private static final String BASE_ORCHESTRATION_ID = "baseStepExecutionId";
  private static final String TAG = "tag";
  private static final String BASE_TAG = "baseTag";
  private static final String DRIFT_ID = "uuid";

  @Before
  public void setup() {
    ACCOUNT_ID = builderFactory.getContext().getAccountId();
    ORG_ID = builderFactory.getContext().getOrgIdentifier();
    PROJECT_ID = builderFactory.getContext().getProjectIdentifier();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCalculateSbomDrift_withNoExistingDriftEntity() {
    ArtifactSbomDriftRequestBody requestBody = new ArtifactSbomDriftRequestBody().tag(TAG).baseTag(BASE_TAG);
    assertThatThrownBy(
        () -> sbomDriftService.calculateSbomDrift(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, requestBody))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not find artifact with tag: baseTag");

    when(artifactService.getLatestArtifact(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, BASE_TAG))
        .thenReturn(
            builderFactory.getArtifactEntityBuilder().orchestrationId(BASE_ORCHESTRATION_ID).tag(BASE_TAG).build());
    assertThatThrownBy(
        () -> sbomDriftService.calculateSbomDrift(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, requestBody))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not find artifact with tag: tag");

    when(artifactService.getLatestArtifact(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, TAG))
        .thenReturn(builderFactory.getArtifactEntityBuilder().build());
    when(sbomDriftRepository
             .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndOrchestrationIdAndBaseOrchestrationId(
                 ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, BASE_ORCHESTRATION_ID))
        .thenReturn(Optional.empty());
    when(sbomDriftCalculator.findComponentDrifts(BASE_ORCHESTRATION_ID, ORCHESTRATION_ID))
        .thenReturn(getComponentDrifts());
    when(sbomDriftCalculator.findLicenseDrift(BASE_ORCHESTRATION_ID, ORCHESTRATION_ID)).thenReturn(getLicenseDrifts());
    when(sbomDriftRepository.save(any()))
        .thenReturn(getDriftEntityBuilder().uuid(DRIFT_ID).base(DriftBase.MANUAL).build());

    ArtifactSbomDriftResponse response =
        sbomDriftService.calculateSbomDrift(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, requestBody);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(
        new ArtifactSbomDriftResponse()
            .driftId(DRIFT_ID)
            .tag(TAG)
            .baseTag(BASE_TAG)
            .totalDrifts(3)
            .componentDriftSummary(new ComponentDriftSummary().total(2).added(1).deleted(1).modified(0))
            .licenseDriftSummary(new LicenseDriftSummary().total(1).added(1).deleted(0))
            .artifactName("test/image"));
    ArgumentCaptor<DriftEntity> captor = ArgumentCaptor.forClass(DriftEntity.class);
    verify(sbomDriftRepository, times(1)).save(captor.capture());
    DriftEntity savedDriftEntity = captor.getValue();
    DriftEntity expectedDriftEntity =
        getDriftEntityBuilder().base(DriftBase.MANUAL).validUntil(savedDriftEntity.getValidUntil()).build();
    assertThat(savedDriftEntity).isEqualTo(expectedDriftEntity);
    assertThat(savedDriftEntity.getValidUntil())
        .isBeforeOrEqualTo(Date.from(OffsetDateTime.now().plusHours(1).toInstant()));
    verify(sbomDriftRepository, never()).update(any(), any());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCalculateSbomDrift_withExistingDriftEntity() {
    ArtifactSbomDriftRequestBody requestBody = new ArtifactSbomDriftRequestBody().tag(TAG).baseTag(BASE_TAG);
    when(artifactService.getLatestArtifact(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, BASE_TAG))
        .thenReturn(
            builderFactory.getArtifactEntityBuilder().orchestrationId(BASE_ORCHESTRATION_ID).tag(BASE_TAG).build());
    when(artifactService.getLatestArtifact(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, TAG))
        .thenReturn(builderFactory.getArtifactEntityBuilder().build());
    DriftEntity savedDriftEntity = getDriftEntityBuilder().uuid(DRIFT_ID).base(DriftBase.MANUAL).build();
    when(sbomDriftRepository
             .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndOrchestrationIdAndBaseOrchestrationId(
                 ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, BASE_ORCHESTRATION_ID))
        .thenReturn(Optional.of(savedDriftEntity));
    when(sbomDriftCalculator.findComponentDrifts(BASE_ORCHESTRATION_ID, ORCHESTRATION_ID))
        .thenReturn(getComponentDrifts());
    when(sbomDriftCalculator.findLicenseDrift(BASE_ORCHESTRATION_ID, ORCHESTRATION_ID)).thenReturn(getLicenseDrifts());

    ArtifactSbomDriftResponse response =
        sbomDriftService.calculateSbomDrift(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, requestBody);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(
        new ArtifactSbomDriftResponse()
            .driftId(DRIFT_ID)
            .tag(TAG)
            .baseTag(BASE_TAG)
            .totalDrifts(3)
            .componentDriftSummary(new ComponentDriftSummary().total(2).added(1).deleted(1).modified(0))
            .licenseDriftSummary(new LicenseDriftSummary().total(1).added(1).deleted(0))
            .artifactName("test/image"));
    verify(sbomDriftRepository, never()).save(any());
    verify(sbomDriftRepository, times(1)).update(any(), any());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCalculateSbomDriftForOrchestration_withBaselineAndNoExistingDriftEntity() {
    assertThatThrownBy(()
                           -> sbomDriftService.calculateSbomDriftForOrchestration(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, DriftBase.BASELINE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not find artifact with orchestration id: stepExecutionId");

    when(baselineService.getBaselineForArtifact(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID))
        .thenReturn(BaselineDTO.builder().artifactId(ARTIFACT_ID).tag(BASE_TAG).build());
    when(artifactService.getArtifact(ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID))
        .thenReturn(
            Optional.of(builderFactory.getArtifactEntityBuilder().orchestrationId(ORCHESTRATION_ID).tag(TAG).build()));
    assertThatThrownBy(()
                           -> sbomDriftService.calculateSbomDriftForOrchestration(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, DriftBase.BASELINE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not find BASELINE artifact for artifact name test/image");

    when(artifactService.getLatestArtifact(ACCOUNT_ID, ORG_ID, PROJECT_ID, ARTIFACT_ID, BASE_TAG))
        .thenReturn(
            builderFactory.getArtifactEntityBuilder().orchestrationId(BASE_ORCHESTRATION_ID).tag(BASE_TAG).build());
    when(sbomDriftRepository
             .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndOrchestrationIdAndBaseOrchestrationId(
                 ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, BASE_ORCHESTRATION_ID))
        .thenReturn(Optional.empty());
    when(sbomDriftCalculator.findComponentDrifts(BASE_ORCHESTRATION_ID, ORCHESTRATION_ID))
        .thenReturn(getComponentDrifts());
    when(sbomDriftCalculator.findLicenseDrift(BASE_ORCHESTRATION_ID, ORCHESTRATION_ID)).thenReturn(getLicenseDrifts());
    when(sbomDriftRepository.save(any()))
        .thenReturn(getDriftEntityBuilder().uuid(DRIFT_ID).base(DriftBase.BASELINE).build());

    ArtifactSbomDriftResponse response = sbomDriftService.calculateSbomDriftForOrchestration(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, DriftBase.BASELINE);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(
        new ArtifactSbomDriftResponse()
            .driftId(DRIFT_ID)
            .tag(TAG)
            .baseTag(BASE_TAG)
            .totalDrifts(3)
            .componentDriftSummary(new ComponentDriftSummary().total(2).added(1).deleted(1).modified(0))
            .licenseDriftSummary(new LicenseDriftSummary().total(1).added(1).deleted(0))
            .artifactName("test/image"));
    ArgumentCaptor<DriftEntity> captor = ArgumentCaptor.forClass(DriftEntity.class);
    verify(sbomDriftRepository, times(1)).save(captor.capture());
    DriftEntity savedDriftEntity = captor.getValue();
    DriftEntity expectedDriftEntity =
        getDriftEntityBuilder().base(DriftBase.BASELINE).validUntil(savedDriftEntity.getValidUntil()).build();
    assertThat(savedDriftEntity).isEqualTo(expectedDriftEntity);
    assertThat(savedDriftEntity.getValidUntil())
        .isBeforeOrEqualTo(Date.from(OffsetDateTime.now().plusMonths(6).toInstant()));
    assertThat(savedDriftEntity.getValidUntil()).isAfter(Date.from(OffsetDateTime.now().plusHours(2).toInstant()));
    verify(sbomDriftRepository, never()).update(any(), any());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCalculateSbomDriftForOrchestration_withLastGeneratedSbomAndNoExistingDriftEntity() {
    when(artifactService.getArtifact(ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID))
        .thenReturn(
            Optional.of(builderFactory.getArtifactEntityBuilder().orchestrationId(ORCHESTRATION_ID).tag(TAG).build()));
    assertThatThrownBy(()
                           -> sbomDriftService.calculateSbomDriftForOrchestration(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, DriftBase.LAST_GENERATED_SBOM))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not find LAST_GENERATED_SBOM artifact for artifact name test/image");

    when(artifactService.getLastGeneratedArtifactFromTime(any(), any(), any(), any(), any()))
        .thenReturn(
            builderFactory.getArtifactEntityBuilder().orchestrationId(BASE_ORCHESTRATION_ID).tag(BASE_TAG).build());
    when(sbomDriftRepository
             .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndOrchestrationIdAndBaseOrchestrationId(
                 ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, BASE_ORCHESTRATION_ID))
        .thenReturn(Optional.empty());
    when(sbomDriftCalculator.findComponentDrifts(BASE_ORCHESTRATION_ID, ORCHESTRATION_ID))
        .thenReturn(getComponentDrifts());
    when(sbomDriftCalculator.findLicenseDrift(BASE_ORCHESTRATION_ID, ORCHESTRATION_ID)).thenReturn(getLicenseDrifts());
    when(sbomDriftRepository.save(any()))
        .thenReturn(getDriftEntityBuilder().uuid(DRIFT_ID).base(DriftBase.LAST_GENERATED_SBOM).build());

    ArtifactSbomDriftResponse response = sbomDriftService.calculateSbomDriftForOrchestration(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID, DriftBase.LAST_GENERATED_SBOM);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(
        new ArtifactSbomDriftResponse()
            .driftId(DRIFT_ID)
            .tag(TAG)
            .baseTag(BASE_TAG)
            .totalDrifts(3)
            .componentDriftSummary(new ComponentDriftSummary().total(2).added(1).deleted(1).modified(0))
            .licenseDriftSummary(new LicenseDriftSummary().total(1).added(1).deleted(0))
            .artifactName("test/image"));
    ArgumentCaptor<DriftEntity> captor = ArgumentCaptor.forClass(DriftEntity.class);
    verify(sbomDriftRepository, times(1)).save(captor.capture());
    DriftEntity savedDriftEntity = captor.getValue();
    DriftEntity expectedDriftEntity = getDriftEntityBuilder()
                                          .base(DriftBase.LAST_GENERATED_SBOM)
                                          .validUntil(savedDriftEntity.getValidUntil())
                                          .build();
    assertThat(savedDriftEntity).isEqualTo(expectedDriftEntity);
    assertThat(savedDriftEntity.getValidUntil())
        .isBeforeOrEqualTo(Date.from(OffsetDateTime.now().plusMonths(6).toInstant()));
    assertThat(savedDriftEntity.getValidUntil()).isAfter(Date.from(OffsetDateTime.now().plusHours(2).toInstant()));
    verify(sbomDriftRepository, never()).update(any(), any());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetComponentDrifts() {
    when(sbomDriftRepository.exists(Criteria.where("_id").is(DRIFT_ID))).thenReturn(true);
    ArgumentCaptor<Aggregation> argumentCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(sbomDriftRepository.aggregate(any(), eq(ComponentDriftResults.class)))
        .thenReturn(List.of(
            ComponentDriftResults.builder().totalComponentDrifts(1).componentDrifts(getComponentDrifts()).build()));

    ComponentDriftResults componentDriftResults = sbomDriftService.getComponentDrifts(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, DRIFT_ID, ComponentDriftStatus.ADDED, PageRequest.of(0, 10), "comp");
    verify(sbomDriftRepository, times(1)).aggregate(argumentCaptor.capture(), eq(ComponentDriftResults.class));
    Aggregation aggregation = argumentCaptor.getValue();
    assertThat(aggregation.toString())
        .isEqualTo(
            "{ \"aggregate\" : \"__collection__\", \"pipeline\" : [{ \"$match\" : { \"_id\" : \"uuid\"}}, { \"$project\" : { \"componentDrifts\" : 1, \"_id\" : 1}}, { \"$unwind\" : \"$componentDrifts\"}, { \"$match\" : { \"componentDrifts.status\" : \"ADDED\", \"$or\" : [{ \"componentDrifts.oldComponent.packagename\" : { \"$regularExpression\" : { \"pattern\" : \"comp\", \"options\" : \"i\"}}}, { \"componentDrifts.newComponent.packagename\" : { \"$regularExpression\" : { \"pattern\" : \"comp\", \"options\" : \"i\"}}}]}}, { \"$group\" : { \"_id\" : \"$_id\", \"totalComponentDrifts\" : { \"$sum\" : 1}, \"componentDrifts\" : { \"$push\" : \"$componentDrifts\"}}}, { \"$project\" : { \"totalComponentDrifts\" : 1, \"componentDrifts\" : { \"$slice\" : [\"$componentDrifts\", 0, 10]}, \"_id\" : 0}}]}");
    assertThat(componentDriftResults.getTotalComponentDrifts()).isEqualTo(1);
    assertThat(componentDriftResults.getComponentDrifts()).isEqualTo(getComponentDrifts());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetLicenseDrifts() {
    when(sbomDriftRepository.find(Criteria.where("_id").is(DRIFT_ID))).thenReturn(getDriftEntityBuilder().build());
    ArgumentCaptor<Aggregation> argumentCaptor = ArgumentCaptor.forClass(Aggregation.class);
    when(sbomDriftRepository.aggregate(any(), eq(LicenseDriftResults.class)))
        .thenReturn(
            List.of(LicenseDriftResults.builder().totalLicenseDrifts(1).licenseDrifts(getLicenseDrifts()).build()));
    when(normalisedSbomComponentService.getComponentsOfSbomByLicense(ORCHESTRATION_ID, "license"))
        .thenReturn(List.of(builderFactory.getNormalizedSBOMComponentBuilder().build()));

    LicenseDriftResults licenseDriftResults = sbomDriftService.getLicenseDrifts(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, DRIFT_ID, LicenseDriftStatus.ADDED, PageRequest.of(0, 10), "MIT");
    verify(sbomDriftRepository, times(1)).aggregate(argumentCaptor.capture(), eq(LicenseDriftResults.class));
    Aggregation aggregation = argumentCaptor.getValue();
    assertThat(aggregation.toString())
        .isEqualTo(
            "{ \"aggregate\" : \"__collection__\", \"pipeline\" : [{ \"$match\" : { \"_id\" : \"uuid\"}}, { \"$project\" : { \"licenseDrifts\" : 1, \"_id\" : 1}}, { \"$unwind\" : \"$licenseDrifts\"}, { \"$match\" : { \"licenseDrifts.status\" : \"ADDED\", \"licenseDrifts.name\" : { \"$regularExpression\" : { \"pattern\" : \"MIT\", \"options\" : \"i\"}}}}, { \"$group\" : { \"_id\" : \"$_id\", \"totalLicenseDrifts\" : { \"$sum\" : 1}, \"licenseDrifts\" : { \"$push\" : \"$licenseDrifts\"}}}, { \"$project\" : { \"totalLicenseDrifts\" : 1, \"licenseDrifts\" : { \"$slice\" : [\"$licenseDrifts\", 0, 10]}, \"_id\" : 0}}]}");
    assertThat(licenseDriftResults.getTotalLicenseDrifts()).isEqualTo(1);
    assertThat(licenseDriftResults.getLicenseDrifts())
        .isEqualTo(List.of(LicenseDrift.builder()
                               .name("license")
                               .status(LicenseDriftStatus.ADDED)
                               .components(List.of(ComponentSummary.builder()
                                                       .packageName("packageName")
                                                       .packageOriginatorName("packageOriginatorName")
                                                       .packageVersion("packageVersion")
                                                       .packageManager("packageManager")
                                                       .purl("purl")
                                                       .packageLicense(List.of("license1", "license2"))
                                                       .build()))
                               .build()));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateSbomToolAndFormat() {
    ArtifactEntity baseArtifact = builderFactory.getArtifactEntityBuilder().tag(BASE_TAG).build();
    ArtifactEntity driftArtifact = builderFactory.getArtifactEntityBuilder()
                                       .tag(TAG)
                                       .sbom(ArtifactEntity.Sbom.builder()
                                                 .sbomVersion("3.0")
                                                 .sbomFormat("cyclonedx-json")
                                                 .toolVersion("2.0")
                                                 .tool("syft")
                                                 .build())
                                       .build();
    assertThatThrownBy(() -> sbomDriftService.validateSbomToolAndFormat(driftArtifact, baseArtifact))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Not proceeding with drift because sbom format spdx-json for base tag baseTag does not match with sbom format cyclonedx-json of tag tag");
    ArtifactEntity driftArtifactWithDiffTool = builderFactory.getArtifactEntityBuilder()
                                                   .tag(TAG)
                                                   .sbom(ArtifactEntity.Sbom.builder()
                                                             .sbomVersion("3.0")
                                                             .sbomFormat("spdx-json")
                                                             .toolVersion("2.0")
                                                             .tool("sbom-tool")
                                                             .build())
                                                   .build();
    assertThatThrownBy(() -> sbomDriftService.validateSbomToolAndFormat(driftArtifactWithDiffTool, baseArtifact))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Not proceeding with drift because sbom tool syft for base tag baseTag does not match with sbom tool sbom-tool of tag tag");
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGetSbomDriftSummary() {
    when(sbomDriftRepository.findOne(any())).thenReturn(getDriftEntityBuilder().base(DriftBase.BASELINE).build());

    OrchestrationDriftSummary sbomDriftSummary =
        sbomDriftService.getSbomDriftSummary(ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID);

    assertThat(sbomDriftSummary.getBaseTag()).isEqualTo(BASE_TAG);
    assertThat(sbomDriftSummary.getTotalDrifts()).isEqualTo(3);
    assertThat(sbomDriftSummary.getComponentDrifts()).isEqualTo(2);
    assertThat(sbomDriftSummary.getLicenseDrifts()).isEqualTo(1);
    assertThat(sbomDriftSummary.getComponentsAdded()).isEqualTo(1);
    assertThat(sbomDriftSummary.getComponentsDeleted()).isEqualTo(1);
    assertThat(sbomDriftSummary.getComponentsModified()).isEqualTo(0);
    assertThat(sbomDriftSummary.getLicenseAdded()).isEqualTo(1);
    assertThat(sbomDriftSummary.getLicenseDeleted()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testgetComponentDriftSummary() {
    when(sbomDriftRepository.findOne(any())).thenReturn(getDriftEntityBuilder().base(DriftBase.BASELINE).build());

    OrchestrationDriftSummary sbomDriftSummary =
        sbomDriftService.getSbomDriftSummary(ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID);

    ComponentDriftSummary componentDriftSummary = SbomDriftMapper.getComponentDriftSummary(sbomDriftSummary);

    assertThat(componentDriftSummary.getTotal()).isEqualTo(2);
    assertThat(componentDriftSummary.getAdded()).isEqualTo(1);
    assertThat(componentDriftSummary.getDeleted()).isEqualTo(1);
    assertThat(componentDriftSummary.getModified()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testgetLicenseDriftSummary() {
    when(sbomDriftRepository.findOne(any())).thenReturn(getDriftEntityBuilder().base(DriftBase.BASELINE).build());

    OrchestrationDriftSummary sbomDriftSummary =
        sbomDriftService.getSbomDriftSummary(ACCOUNT_ID, ORG_ID, PROJECT_ID, ORCHESTRATION_ID);

    LicenseDriftSummary licenseDriftSummary = SbomDriftMapper.getLicenseDriftSummary(sbomDriftSummary);

    assertThat(licenseDriftSummary.getTotal()).isEqualTo(1);
    assertThat(licenseDriftSummary.getAdded()).isEqualTo(1);
    assertThat(licenseDriftSummary.getDeleted()).isEqualTo(0);
  }

  private List<ComponentDrift> getComponentDrifts() {
    List<ComponentDrift> componentDrifts = new ArrayList<>();

    componentDrifts.add(ComponentDrift.builder()
                            .status(ComponentDriftStatus.ADDED)
                            .newComponent(ComponentSummary.builder().packageName("name").build())
                            .build());

    componentDrifts.add(ComponentDrift.builder()
                            .status(ComponentDriftStatus.DELETED)
                            .newComponent(ComponentSummary.builder().packageName("name").build())
                            .build());

    return componentDrifts;
  }

  private List<LicenseDrift> getLicenseDrifts() {
    LicenseDrift licenseDrift = LicenseDrift.builder().name("license").status(LicenseDriftStatus.ADDED).build();
    return List.of(licenseDrift);
  }

  private DriftEntityBuilder getDriftEntityBuilder() {
    return DriftEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .artifactId(ARTIFACT_ID)
        .orchestrationId(ORCHESTRATION_ID)
        .baseOrchestrationId(BASE_ORCHESTRATION_ID)
        .tag(TAG)
        .baseTag(BASE_TAG)
        .componentDrifts(getComponentDrifts())
        .licenseDrifts(getLicenseDrifts());
  }
}
