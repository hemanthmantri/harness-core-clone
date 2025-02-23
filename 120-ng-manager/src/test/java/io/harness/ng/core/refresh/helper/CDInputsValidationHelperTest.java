/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.customdeployment.helper.CustomDeploymentEntitySetupHelper;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.environment.mappers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.ng.core.infrastructure.dto.NoInputMergeInputAction;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureEntityServiceImpl;
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.impl.ServiceEntityServiceImpl;
import io.harness.ng.core.service.services.impl.ServiceEntitySetupUsageHelper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.ng.core.utils.CDGitXService;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YamlNode;
import io.harness.repositories.environment.spring.EnvironmentRepository;
import io.harness.repositories.infrastructure.spring.InfrastructureRepository;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.setupusage.EnvironmentEntitySetupUsageHelper;
import io.harness.setupusage.InfrastructureEntitySetupUsageHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.io.Resources;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class CDInputsValidationHelperTest extends NgManagerTestBase {
  private static final String RESOURCE_PATH_PREFIX = "refresh/validate/";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  @InjectMocks CDInputsValidationHelper CDInputsValidationHelper;
  @InjectMocks EntityFetchHelper entityFetchHelper;
  @Mock ServiceRepository serviceRepository;
  @Mock EnvironmentRepository environmentRepository;
  @Mock InfrastructureRepository infrastructureRepository;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock Producer eventProducer;
  @Mock TransactionTemplate transactionTemplate;
  @Mock OutboxService outboxService;
  @Mock ServiceOverrideService serviceOverrideService;
  @Mock ServiceEntitySetupUsageHelper entitySetupUsageHelper;
  @Mock ClusterService clusterService;
  @Mock CustomDeploymentEntitySetupHelper customDeploymentEntitySetupHelper;
  @Mock InfrastructureEntitySetupUsageHelper infrastructureEntitySetupUsageHelper;
  @Mock AccountClient accountClient;
  @Mock NGSettingsClient settingsClient;

  @Mock HPersistence hPersistence;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock EnvironmentEntitySetupUsageHelper environmentEntitySetupUsageHelper;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private Call<RestResponse<Boolean>> restRequest;
  @Mock ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Mock ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Mock io.harness.utils.NGFeatureFlagHelperService ngFeatureFlagHelperService;
  ServiceEntityServiceImpl serviceEntityService;
  EnvironmentServiceImpl environmentService;
  InfrastructureEntityServiceImpl infrastructureEntityService;
  EnvironmentRefreshHelper environmentRefreshHelper;
  @Mock @Named(DEFAULT_CONNECTOR_SERVICE) private ConnectorService connectorService;
  @Mock EnvironmentFilterHelper environmentFilterHelper;
  @Mock GitXSettingsHelper gitXSettingsHelper;
  @Mock CDGitXService cdGitXService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Before
  public void setup() throws IOException {
    serviceEntityService = spy(new ServiceEntityServiceImpl(serviceRepository, entitySetupUsageService, eventProducer,
        outboxService, transactionTemplate, serviceOverrideService, serviceOverridesServiceV2, entitySetupUsageHelper,
        ngFeatureFlagHelperService, connectorService, cdGitXService, gitXSettingsHelper));
    infrastructureEntityService = spy(new InfrastructureEntityServiceImpl(infrastructureRepository, transactionTemplate,
        outboxService, customDeploymentEntitySetupHelper, infrastructureEntitySetupUsageHelper, hPersistence,
        serviceOverridesServiceV2, overrideV2ValidationHelper, null, environmentService, gitAwareEntityHelper,
        cdGitXService, gitXSettingsHelper));
    environmentService = spy(new EnvironmentServiceImpl(environmentRepository, entitySetupUsageService, eventProducer,
        outboxService, transactionTemplate, infrastructureEntityService, clusterService, serviceOverrideService,
        serviceOverridesServiceV2, serviceEntityService, accountClient, settingsClient,
        environmentEntitySetupUsageHelper, overrideV2ValidationHelper, environmentFilterHelper, gitXSettingsHelper,
        cdGitXService));
    environmentRefreshHelper = spy(new EnvironmentRefreshHelper(environmentService, infrastructureEntityService,
        serviceOverrideService, serviceOverridesServiceV2, accountClient, overrideV2ValidationHelper));
    on(entityFetchHelper).set("serviceEntityService", serviceEntityService);
    on(CDInputsValidationHelper).set("serviceEntityService", serviceEntityService);
    on(CDInputsValidationHelper).set("entityFetchHelper", entityFetchHelper);
    on(CDInputsValidationHelper).set("environmentRefreshHelper", environmentRefreshHelper);

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(settingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();
    doReturn(restRequest).when(accountClient).isFeatureFlagEnabled(anyString(), anyString());
    RestResponse<Boolean> mockResponse = new RestResponse<>(true);
    doReturn(Response.success(mockResponse)).when(restRequest).execute();
  }

  private String readFile(String filename) {
    String relativePath = RESOURCE_PATH_PREFIX + filename;
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(relativePath)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithValidServiceServiceEnvironmentAndInfra() {
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service-valid.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));
    doReturn(null)
        .when(environmentService)
        .createEnvironmentInputsYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testenv", null);
    doReturn("infrastructureDefinitions:\n"
        + "  - identifier: \"infra2\"\n")
        .when(infrastructureEntityService)
        .createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testenv", null,
            Collections.singletonList("infra2"), false, NoInputMergeInputAction.ADD_IDENTIFIER_NODE);

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithInvalidServiceHavingFixedPrimaryArtifactRef() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceRuntimeAndServiceInputsFixed() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-svc-runtime-serviceInputs-fixed.yaml");

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceRuntimeAndServiceInputsNotPresent() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-svc-runtime-serviceInputs-not-present.yaml");

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithPrimaryRefFixedAndSourcesRuntime() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-primaryRef-fixed-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceInputsEmptyInService() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceInputsEmptyInServiceAndNoServiceInputsInLinkedYaml() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-no-serviceInputs.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithEnvRefRuntimeButInfraDefsFixed() {
    String pipelineYmlWithService = readFile("env/pipeline-with-env-ref-runtime-and-envInputs-infraDefs-fixed.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithEnvRefInfraDefsAndEnvInputsRuntime() {
    String pipelineYmlWithService = readFile("env/pipeline-with-envRef-envInputs-infraDefs-runtime.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithEnvRefFixedAndEnvInputsIncorrect() {
    String pipelineYmlWithService = readFile("env/pipeline-with-fixed-envRef-incorrect-envInputs.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));
    doReturn(null)
        .when(environmentService)
        .createEnvironmentInputsYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testenv", null);

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithEnvRefFixedAndInfraDefsIncorrect() {
    String pipelineYmlWithService = readFile("env/pipeline-with-env-ref-fixed-and-infraDefs-incorrect.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));
    doReturn(null)
        .when(environmentService)
        .createEnvironmentInputsYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testenv", null);
    doReturn("infrastructureDefinitions:\n"
        + "- identifier: \"IDENTIFIER\"")
        .when(infrastructureEntityService)
        .createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testenv", null,
            Collections.singletonList("IDENTIFIER"), false, NoInputMergeInputAction.ADD_IDENTIFIER_NODE);

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInfraInTemplateInputsWithNoEnvRef() {
    String templateWithInfraFixed = readFile("env/pipTemplate-with-infra-fixed.yaml");
    String resolvedTemplateWithInfraFixed = readFile("env/pipTemplate-with-infra-fixed-resoved.yaml");

    doReturn("infrastructureDefinitions:\n"
        + "- identifier: \"infra1\"")
        .when(infrastructureEntityService)
        .createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testenv", null,
            Collections.singletonList("infra1"), false, NoInputMergeInputAction.ADD_IDENTIFIER_NODE);

    InputsValidationResponse validationResponse = CDInputsValidationHelper.validateInputsForYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, templateWithInfraFixed, resolvedTemplateWithInfraFixed);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithPrimaryRefExpressionAndSourcesRuntime() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-primaryRef-expression-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testValidateInputsForServiceYamlWithPrimaryRefExpressionForPipelineYamlWithPrimaryRefExpressionAndSourcesRuntime() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-primaryRef-expression-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service-with-primary-artifact-ref-expression.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testValidateInputsForServiceYamlWithPrimaryRefExpressionForPipelineYamlWithPrimaryRefExpressionAndSourcesRuntimeButDifferentExpression() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService =
        readFile("pipeline-with-primaryRef-expression-different-from-service-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service-with-primary-artifact-ref-expression.yaml");

    when(serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, "serverless", false))
        .thenReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }
}
