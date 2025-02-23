/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.TerragruntCommandFlagType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.InlineFileConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.terragrunt.request.TerragruntDestroyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntDestroyTaskResponse;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.telemetry.helpers.DeploymentsInstrumentationHelper;

import software.wings.beans.VaultConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({TaskRequestsUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerragruntDestroyStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private DeploymentsInstrumentationHelper deploymentsInstrumentationHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private TerragruntStepHelper terragruntStepHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private StepHelper stepHelper;
  @InjectMocks private TerragruntDestroyStep terragruntDestroyStep = new TerragruntDestroyStep();
  @Captor ArgumentCaptor<List<EntityDetail>> captor;
  @Mock private TerragruntConfigDAL terragruntConfigDAL;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesWhenApplyInline() {
    TerragruntConfigFilesWrapper configFilesWrapper = TerragruntTestStepUtils.createConfigFilesWrapper();
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = TerragruntTestStepUtils.createVarFilesRemote();
    TerragruntBackendConfig terragruntBackendConfig = TerragruntTestStepUtils.createRemoteBackendConfig();

    TerragruntDestroyStepParameters parameters =
        TerragruntDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntStepConfigurationParameters.builder()
                               .type(TerragruntStepConfigurationType.INLINE)
                               .spec(TerragruntExecutionDataParameters.builder()
                                         .configFiles(configFilesWrapper)
                                         .backendConfig(terragruntBackendConfig)
                                         .varFiles(varFilesMap)
                                         .build())
                               .build())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();
    terragruntDestroyStep.validateResources(TerragruntTestStepUtils.getAmbiance(), stepElementParameters);
    verify(pipelineRbacHelper, times(1))
        .checkRuntimePermissions(eq(TerragruntTestStepUtils.getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(3);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("terragrunt-configFiles");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("terragrunt-varFiles");
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo("terragrunt-backendFile");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesWhenDestroyInheritFromPLan() {
    TerragruntDestroyStepParameters parameters =
        TerragruntDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntStepConfigurationParameters.builder()
                               .type(TerragruntStepConfigurationType.INHERIT_FROM_PLAN)
                               .spec(TerragruntExecutionDataParameters.builder().build())
                               .build())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();
    terragruntDestroyStep.validateResources(TerragruntTestStepUtils.getAmbiance(), stepElementParameters);
    verify(pipelineRbacHelper, times(1))
        .checkRuntimePermissions(eq(TerragruntTestStepUtils.getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesWhenDestroyInheritFromApply() {
    TerragruntDestroyStepParameters parameters =
        TerragruntDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntStepConfigurationParameters.builder()
                               .type(TerragruntStepConfigurationType.INHERIT_FROM_APPLY)
                               .spec(TerragruntExecutionDataParameters.builder().build())
                               .build())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();
    terragruntDestroyStep.validateResources(TerragruntTestStepUtils.getAmbiance(), stepElementParameters);
    verify(pipelineRbacHelper, times(1))
        .checkRuntimePermissions(eq(TerragruntTestStepUtils.getAmbiance()), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    TerragruntConfigFilesWrapper configFilesWrapper = TerragruntTestStepUtils.createConfigFilesWrapper();
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = TerragruntTestStepUtils.createVarFilesInline();
    TerragruntBackendConfig backendConfig = TerragruntTestStepUtils.createInlineBackendConfig();

    Map<String, Object> envVars = new HashMap<>() {
      { put("envKey", "envVal"); }
    };

    TerragruntDestroyStepParameters parameters =
        TerragruntDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(
                TerragruntStepConfigurationParameters.builder()
                    .type(TerragruntStepConfigurationType.INLINE)
                    .commandFlags(List.of(TerragruntCliOptionFlag.builder()
                                              .commandType(TerragruntCommandFlagType.DESTROY)
                                              .flag(ParameterField.createValueField("-lock-timeout=0s"))
                                              .build()))
                    .spec(TerragruntExecutionDataParameters.builder()
                              .configFiles(configFilesWrapper)
                              .backendConfig(backendConfig)
                              .varFiles(varFilesMap)
                              .terragruntModuleConfig(TerragruntTestStepUtils.createTerragruntModuleConfig())
                              .targets(ParameterField.createValueField(Collections.singletonList("test-target")))
                              .environmentVariables(envVars)
                              .workspace(ParameterField.createValueField("test-workspace"))
                              .build())
                    .build())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    when(terragruntStepHelper.generateFullIdentifier(any(), any())).thenReturn("testEntityId");
    when(terragruntStepHelper.getGitFetchFilesConfig(any(), any(), any()))
        .thenReturn(TerragruntTestStepUtils.createGitStoreDelegateConfig());
    when(terragruntStepHelper.toStoreDelegateVarFiles(anyMap(), any()))
        .thenReturn(Collections.singletonList(
            InlineStoreDelegateConfig.builder()
                .identifier("test-var1-id")
                .files(Collections.singletonList(InlineFileConfig.builder().content("test-var1-content").build()))
                .build()));
    when(terragruntStepHelper.getBackendConfig(any(), any()))
        .thenReturn(InlineStoreDelegateConfig.builder().identifier("test-backend-id").build());
    when(terragruntStepHelper.getEnvironmentVariablesMap(any())).thenReturn(new HashMap<>() {
      { put("envKey", "envVal"); }
    });
    when(terragruntStepHelper.getLatestFileId(any())).thenReturn(null);
    when(terragruntStepHelper.getEncryptionDetails(any(), any(), any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder()
                                .encryptionConfig(VaultConfig.builder().build())
                                .encryptedData(EncryptedRecordData.builder().build())
                                .build()));
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terragruntStepHelper)
        .getTerragruntCliFlags(any());
    Mockito.mockStatic(TaskRequestsUtils.class);

    terragruntDestroyStep.obtainTaskAfterRbac(
        TerragruntTestStepUtils.getAmbiance(), stepElementParameters, StepInputPackage.builder().build());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), eq("Terragrunt Destroy Task V2"), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerragruntDestroyTaskParameters params =
        (TerragruntDestroyTaskParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(params.getAccountId()).isEqualTo("test-account");
    assertThat(params.getWorkspace()).isEqualTo("test-workspace");
    assertThat(params.getRunConfiguration().getRunType()).isEqualTo(TerragruntTaskRunType.RUN_MODULE);
    assertThat(params.getRunConfiguration().getPath()).isEqualTo("test-path");
    assertThat(params.getTargets().get(0)).isEqualTo("test-target");
    assertThat(params.getEnvVars().get("envKey")).isEqualTo("envVal");
    assertThat(params.getEncryptedDataDetailList()).isNotEmpty();
    List<StoreDelegateConfig> inlineVar = params.getVarFiles();
    assertThat(((InlineStoreDelegateConfig) inlineVar.get(0)).getIdentifier()).isEqualTo("test-var1-id");
    assertThat(((InlineStoreDelegateConfig) params.getBackendFilesStore()).getIdentifier())
        .isEqualTo("test-backend-id");
    assertThat(((GitStoreDelegateConfig) params.getConfigFilesStore()).getConnectorName()).isEqualTo("terragrunt");
    assertThat(params.getTerragruntCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithInheritFromPlan() {
    Map<String, String> envVars = new HashMap<>() {
      { put("envKey", "envVal"); }
    };

    TerragruntDestroyStepParameters parameters =
        TerragruntDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntStepConfigurationParameters.builder()
                               .commandFlags(List.of(TerragruntCliOptionFlag.builder()
                                                         .commandType(TerragruntCommandFlagType.DESTROY)
                                                         .flag(ParameterField.createValueField("-lock-timeout=0s"))
                                                         .build()))
                               .type(TerragruntStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();

    when(terragruntStepHelper.getSavedInheritOutput(any(), any(), any()))
        .thenReturn(TerragruntInheritOutput.builder()
                        .workspace("test-workspace")
                        .environmentVariables(envVars)
                        .targets(new ArrayList<>() {
                          { add("test-target"); }
                        })
                        .runConfiguration(TerragruntRunConfiguration.builder()
                                              .runType(TerragruntTaskRunType.RUN_MODULE)
                                              .path("test-path")
                                              .build())
                        .configFiles(GithubStore.builder()
                                         .branch(ParameterField.createValueField("test-branch"))
                                         .gitFetchType(FetchType.BRANCH)
                                         .folderPath(ParameterField.createValueField("test-folder-path"))
                                         .connectorRef(ParameterField.createValueField("test-connector"))
                                         .build())
                        .varFileConfigs(new ArrayList<>() {
                          { add(TerragruntInlineVarFileConfig.builder().varFileContent("test-var1-content").build()); }
                        })
                        .backendConfigFile(TerragruntInlineBackendConfigFileConfig.builder()
                                               .backendConfigFileContent("test-backend1-content")
                                               .build())
                        .encryptionConfig(VaultConfig.builder().build())
                        .encryptedPlan(EncryptedRecordData.builder().build())
                        .build());

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    when(terragruntStepHelper.generateFullIdentifier(any(), any())).thenReturn("testEntityId");
    when(terragruntStepHelper.getGitFetchFilesConfig(any(), any(), any()))
        .thenReturn(TerragruntTestStepUtils.createGitStoreDelegateConfig());
    when(terragruntStepHelper.toStoreDelegateVarFilesFromTgConfig(any(), any()))
        .thenReturn(Collections.singletonList(
            InlineStoreDelegateConfig.builder()
                .identifier("test-var1-id")
                .files(Collections.singletonList(InlineFileConfig.builder().content("test-var1-content").build()))
                .build()));
    when(terragruntStepHelper.getBackendConfigFromTgConfig(any(), any()))
        .thenReturn(InlineStoreDelegateConfig.builder().identifier("test-backend-id").build());
    when(terragruntStepHelper.getLatestFileId(any())).thenReturn(null);
    when(terragruntStepHelper.getEncryptionDetailsFromTgInheritConfig(any(), any(), any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder()
                                .encryptionConfig(VaultConfig.builder().build())
                                .encryptedData(EncryptedRecordData.builder().build())
                                .build()));
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terragruntStepHelper)
        .getTerragruntCliFlags(any());
    Mockito.mockStatic(TaskRequestsUtils.class);

    terragruntDestroyStep.obtainTaskAfterRbac(
        TerragruntTestStepUtils.getAmbiance(), stepElementParameters, StepInputPackage.builder().build());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), eq("Terragrunt Destroy Task V2"), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerragruntDestroyTaskParameters params =
        (TerragruntDestroyTaskParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(params.getAccountId()).isEqualTo("test-account");
    assertThat(params.getWorkspace()).isEqualTo("test-workspace");
    assertThat(params.getRunConfiguration().getRunType()).isEqualTo(TerragruntTaskRunType.RUN_MODULE);
    assertThat(params.getRunConfiguration().getPath()).isEqualTo("test-path");
    assertThat(params.getTargets().get(0)).isEqualTo("test-target");
    assertThat(params.getEncryptedDataDetailList()).isNotEmpty();
    assertThat(params.getEnvVars().get("envKey")).isEqualTo("envVal");
    List<StoreDelegateConfig> inlineVar = params.getVarFiles();
    assertThat(((InlineStoreDelegateConfig) inlineVar.get(0)).getIdentifier()).isEqualTo("test-var1-id");
    assertThat(((InlineStoreDelegateConfig) params.getBackendFilesStore()).getIdentifier())
        .isEqualTo("test-backend-id");
    assertThat(((GitStoreDelegateConfig) params.getConfigFilesStore()).getConnectorName()).isEqualTo("terragrunt");
    assertThat(params.getTerragruntCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithInheritFromApply() {
    Map<String, String> envVars = new HashMap<>() {
      { put("envKey", "envVal"); }
    };

    TerragruntDestroyStepParameters parameters =
        TerragruntDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntStepConfigurationParameters.builder()
                               .type(TerragruntStepConfigurationType.INHERIT_FROM_APPLY)
                               .commandFlags(List.of(TerragruntCliOptionFlag.builder()
                                                         .commandType(TerragruntCommandFlagType.DESTROY)
                                                         .flag(ParameterField.createValueField("-lock-timeout=0s"))
                                                         .build()))
                               .build())
            .build();

    when(terragruntStepHelper.getLastSuccessfulApplyConfig(any(), any()))
        .thenReturn(TerragruntConfig.builder()
                        .workspace("test-workspace")
                        .environmentVariables(envVars)
                        .targets(new ArrayList<>() {
                          { add("test-target"); }
                        })
                        .runConfiguration(TerragruntRunConfiguration.builder()
                                              .runType(TerragruntTaskRunType.RUN_MODULE)
                                              .path("test-path")
                                              .build())
                        .configFiles(GithubStore.builder()
                                         .branch(ParameterField.createValueField("test-branch"))
                                         .gitFetchType(FetchType.BRANCH)
                                         .folderPath(ParameterField.createValueField("test-folder-path"))
                                         .connectorRef(ParameterField.createValueField("test-connector"))
                                         .build()
                                         .toGitStoreConfigDTO())
                        .varFileConfigs(new ArrayList<>() {
                          { add(TerragruntInlineVarFileConfig.builder().varFileContent("test-var1-content").build()); }
                        })
                        .backendConfigFile(TerragruntInlineBackendConfigFileConfig.builder()
                                               .backendConfigFileContent("test-backend1-content")
                                               .build())
                        .build());

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    when(terragruntStepHelper.generateFullIdentifier(any(), any())).thenReturn("testEntityId");
    when(terragruntStepHelper.getGitFetchFilesConfig(any(), any(), any()))
        .thenReturn(TerragruntTestStepUtils.createGitStoreDelegateConfig());
    when(terragruntStepHelper.toStoreDelegateVarFilesFromTgConfig(any(), any()))
        .thenReturn(Collections.singletonList(
            InlineStoreDelegateConfig.builder()
                .identifier("test-var1-id")
                .files(Collections.singletonList(InlineFileConfig.builder().content("test-var1-content").build()))
                .build()));
    when(terragruntStepHelper.getBackendConfigFromTgConfig(any(), any()))
        .thenReturn(InlineStoreDelegateConfig.builder().identifier("test-backend-id").build());
    when(terragruntStepHelper.getLatestFileId(any())).thenReturn(null);
    when(terragruntStepHelper.getEncryptionDetailsFromTgInheritConfig(any(), any(), any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder()
                                .encryptionConfig(VaultConfig.builder().build())
                                .encryptedData(EncryptedRecordData.builder().build())
                                .build()));
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terragruntStepHelper)
        .getTerragruntCliFlags(any());
    Mockito.mockStatic(TaskRequestsUtils.class);

    terragruntDestroyStep.obtainTaskAfterRbac(
        TerragruntTestStepUtils.getAmbiance(), stepElementParameters, StepInputPackage.builder().build());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), eq("Terragrunt Destroy Task V2"), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerragruntDestroyTaskParameters params =
        (TerragruntDestroyTaskParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(params.getAccountId()).isEqualTo("test-account");
    assertThat(params.getWorkspace()).isEqualTo("test-workspace");
    assertThat(params.getRunConfiguration().getRunType()).isEqualTo(TerragruntTaskRunType.RUN_MODULE);
    assertThat(params.getRunConfiguration().getPath()).isEqualTo("test-path");
    assertThat(params.getTargets().get(0)).isEqualTo("test-target");
    assertThat(params.getEnvVars().get("envKey")).isEqualTo("envVal");
    assertThat(params.getEncryptedDataDetailList()).isNotEmpty();
    List<StoreDelegateConfig> inlineVar = params.getVarFiles();
    assertThat(((InlineStoreDelegateConfig) inlineVar.get(0)).getIdentifier()).isEqualTo("test-var1-id");
    assertThat(((InlineStoreDelegateConfig) params.getBackendFilesStore()).getIdentifier())
        .isEqualTo("test-backend-id");
    assertThat(((GitStoreDelegateConfig) params.getConfigFilesStore()).getConnectorName()).isEqualTo("terragrunt");
    assertThat(params.getTerragruntCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextWhenDestroyInline() throws Exception {
    Ambiance ambiance = TerragruntTestStepUtils.getAmbiance();

    List<UnitProgress> unitProgresses = new ArrayList<>();
    unitProgresses.add(UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).build());

    TerragruntDestroyTaskResponse terragruntTaskNGResponse =
        TerragruntDestroyTaskResponse.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(unitProgresses).build())
            .stateFileId("test-stateFileId")
            .build();

    TerragruntDestroyStepParameters parameters =
        TerragruntDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(
                TerragruntStepConfigurationParameters.builder()
                    .type(TerragruntStepConfigurationType.INLINE)
                    .spec(TerragruntExecutionDataParameters.builder()
                              .configFiles(TerragruntTestStepUtils.createConfigFilesWrapper())
                              .backendConfig(TerragruntTestStepUtils.createInlineBackendConfig())
                              .varFiles(TerragruntTestStepUtils.createVarFilesInline())
                              .terragruntModuleConfig(TerragruntTestStepUtils.createTerragruntModuleConfig())
                              .targets(ParameterField.createValueField(Collections.singletonList("test-target")))
                              .environmentVariables(new HashMap<>())
                              .workspace(ParameterField.createValueField("test-workspace"))
                              .build())

                    .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

    StepResponse stepResponse = terragruntDestroyStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terragruntTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList().get(0).getStatus()).isEqualTo(UnitStatus.SUCCESS);

    verify(terragruntConfigDAL, times(1)).clearTerragruntConfig(eq(ambiance), any());
    verify(terragruntStepHelper, times(1)).updateParentEntityIdAndVersion(any(), eq("test-stateFileId"));
    verify(terragruntStepHelper, times(2)).generateFullIdentifier(eq("test-provisionerId"), eq(ambiance));
  }
}
