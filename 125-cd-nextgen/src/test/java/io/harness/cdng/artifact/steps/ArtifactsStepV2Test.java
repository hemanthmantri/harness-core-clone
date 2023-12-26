/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.steps;

import static io.harness.beans.FeatureName.CDS_ARTIFACTS_PRIMARY_IDENTIFIER;
import static io.harness.beans.FeatureName.CDS_SERVICE_AND_INFRA_STEP_DELEGATE_SELECTOR_PRECEDENCE;
import static io.harness.cdng.artifact.steps.ArtifactsStepV2.ARTIFACTS_STEP_V_2;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptInfo;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptSourceWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScripts;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.customartifact.FetchAllArtifacts;
import io.harness.cdng.artifact.mappers.ArtifactConfigToDelegateReqMapper;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.steps.constants.ArtifactsStepV2Constants;
import io.harness.cdng.artifact.utils.ArtifactStepHelper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.common.beans.StepDelegateInfo;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.oidc.OidcHelperUtility;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secretusage.SecretRuntimeUsageService;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.telemetry.helpers.ArtifactSourceInstrumentationHelper;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.SerializationFormat;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.jooq.tools.reflect.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactsStepV2Test extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  @Mock private NGLogCallback mockNgLogCallback;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ExecutionSweepingOutputService mockSweepingOutputService;
  @Mock private CDStepHelper cdStepHelper = Mockito.spy(CDStepHelper.class);
  @Mock private CDExpressionResolver expressionResolver;
  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private SecretRuntimeUsageService secretRuntimeUsageService;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private ArtifactSourceInstrumentationHelper artifactSourceInstrumentationHelper;
  @InjectMocks private ArtifactsStepV2 step = new ArtifactsStepV2();
  private final ArtifactStepHelper stepHelper = new ArtifactStepHelper();
  @Mock private ConnectorService connectorService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private TemplateResourceClient templateResourceClient;
  @Mock private DelegateMetricsService delegateMetricsService;

  @Mock private SecretManagerClientService ngSecretService;
  @Mock ExceptionManager exceptionManager;
  @Mock private StageExecutionInfoService stageExecutionInfoService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock ServiceEntityService serviceEntityService;
  @Mock ArtifactSourceInstrumentationHelper instrumentationHelper;
  @Mock OidcHelperUtility oidcHelperUtility;
  private ArtifactConfigToDelegateReqMapper artifactConfigToDelegateReqMapper =
      new ArtifactConfigToDelegateReqMapper(instrumentationHelper, oidcHelperUtility);
  private final EmptyStepParameters stepParameters = new EmptyStepParameters();
  private EmptyStepParameters stepParametersWithDelegateSelector = new EmptyStepParameters();
  private final StepInputPackage inputPackage = StepInputPackage.builder().build();
  private AutoCloseable mocks;

  private final Ambiance ambiance = buildAmbiance();
  private final ArtifactTaskResponse successResponse = sampleArtifactTaskResponse();
  private final ErrorNotifyResponseData errorNotifyResponse = sampleErrorNotifyResponse();

  private static final String NULL_TAG_TAG_REGEX_MESSAGE =
      "Artifact configuration: value for tag and tagRegex is empty or not provided";
  private static final String NULL_REGISTRY_HOST_NAME_MESSAGE =
      "Artifact configuration: value for registryHostname is empty or not provided";
  private static final String NULL_IMAGE_PATH_MESSAGE =
      "Artifact configuration: value for imagePath is empty or not provided";
  private static final String NULL_PACKAGE_TYPE_MESSAGE =
      "Artifact configuration: value for packageType is empty or not provided";
  private static final String NULL_FEED_MESSAGE = "Artifact configuration: value for feed is empty or not provided";
  private static final String NULL_REPOSITORY_MESSAGE =
      "Artifact configuration: value for repository is empty or not provided";
  private static final String NULL_REGION_MESSAGE = "Artifact configuration: value for region is empty or not provided";
  private static final String NULL_PACKAGE_MESSAGE =
      "Artifact configuration: value for package is empty or not provided";
  private static final String NULL_PROJECT_MESSAGE =
      "Artifact configuration: value for project is empty or not provided";
  private static final String NULL_VERSION_VERSION_REGEX_MESSAGE =
      "Artifact configuration: value for version and versionRegex is empty or not provided";
  private static final String NULL_SUBSCRIPTION_ID_MESSAGE =
      "Artifact configuration: value for subscriptionId is empty or not provided";
  private static final String NULL_REGISTRY_MESSAGE =
      "Artifact configuration: value for registry is empty or not provided";
  private static final String NULL_FILEPATH_FILEPATH_REGEX_MESSAGE =
      "Artifact configuration: value for filePath and filePathRegex is empty or not provided";
  private static final String NULL_BUCKET_NAME_MESSAGE =
      "Artifact configuration: value for bucketName is empty or not provided";
  private static final ParameterField<String> CONNECTOR = ParameterField.createValueField("connector");
  private static final ParameterField<String> BUCKET_NAME = ParameterField.createValueField("bucketName");
  private static final ParameterField<String> REGION = ParameterField.createValueField("region");
  private static final ParameterField<String> PROJECT = ParameterField.createValueField("project");
  private static final ParameterField<String> IMAGE_PATH = ParameterField.createValueField("imagePath");
  private static final ParameterField<String> PACKAGE_TYPE = ParameterField.createValueField("packageType");
  private static final ParameterField<String> PACKAGE = ParameterField.createValueField("package");
  private static final ParameterField<String> FEED = ParameterField.createValueField("feed");
  private static final ParameterField<String> PACKAGE_NAME = ParameterField.createValueField("packageName");
  private static final ParameterField<String> SUBSCRIPTION_ID = ParameterField.createValueField("subscriptionId");
  private static final ParameterField<String> REGISTRY = ParameterField.createValueField("registry");
  private static final ParameterField<String> REGISTRY_HOST_NAME = ParameterField.createValueField("regsitryHostName");
  private static final ParameterField<String> REPOSITORY = ParameterField.createValueField("repository");
  private static final ParameterField<String> TAG_NULL = ParameterField.createValueField(null);
  private static final ParameterField<String> TAG_EMPTY = ParameterField.createValueField("");
  private static final ParameterField<String> TAG_INPUT = ParameterField.createValueField("<+input>");
  private static final String CONNECTOR_DELEGATE = "connectorDelegate";
  private static final String DEFAULT_ORIGIN = "default";
  private static final String STAGE_DELEGATE = "stageDelegate";
  private static final String STAGE_ORIGIN = "stage";

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    Reflect.on(stepHelper).set("connectorService", connectorService);
    Reflect.on(stepHelper).set("secretManagerClientService", secretManagerClientService);
    Reflect.on(stepHelper).set("cdExpressionResolver", expressionResolver);
    Reflect.on(stepHelper).set("ngFeatureFlagHelperService", ngFeatureFlagHelperService);
    Reflect.on(stepHelper).set("stageExecutionInfoService", stageExecutionInfoService);
    Reflect.on(stepHelper).set("artifactConfigToDelegateReqMapper", artifactConfigToDelegateReqMapper);
    Reflect.on(step).set("artifactStepHelper", stepHelper);
    doReturn(false).when(ngFeatureFlagHelperService).isEnabled(anyString(), eq(CDS_ARTIFACTS_PRIMARY_IDENTIFIER));
    doReturn(false)
        .when(ngFeatureFlagHelperService)
        .isEnabled(anyString(), eq(CDS_SERVICE_AND_INFRA_STEP_DELEGATE_SELECTOR_PRECEDENCE));

    // setup mock for connector
    doReturn(Optional.of(
                 ConnectorResponseDTO.builder()
                     .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                     .connector(
                         ConnectorInfoDTO.builder()
                             .projectIdentifier("projectId")
                             .orgIdentifier("orgId")
                             .connectorConfig(
                                 DockerConnectorDTO.builder()
                                     .dockerRegistryUrl("https://index.docker.com/v1")
                                     .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
                                     .delegateSelectors(Set.of(CONNECTOR_DELEGATE))
                                     .build())
                             .build())
                     .build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), eq("connector"));

    // mock serviceStepsHelper
    doReturn(mockNgLogCallback)
        .when(serviceStepsHelper)
        .getServiceLogCallback(Mockito.any(), Mockito.anyBoolean(), Mockito.anyString());

    // mock delegateGrpcClientWrapper
    doAnswer(invocationOnMock -> UUIDGenerator.generateUuid())
        .when(delegateGrpcClientWrapper)
        .submitAsyncTaskV2(any(DelegateTaskRequest.class), any(Duration.class));

    doCallRealMethod().when(cdStepHelper).mapTaskRequestToDelegateTaskRequest(any(), any(), any());
    doCallRealMethod()
        .when(cdStepHelper)
        .mapTaskRequestToDelegateTaskRequest(any(), any(), anyList(), any(), anyBoolean());
    doCallRealMethod()
        .when(cdStepHelper)
        .mapTaskRequestToDelegateTaskRequest(any(), any(), anySet(), any(), anyBoolean());

    doAnswer(invocationOnMock -> invocationOnMock.getArgument(1, String.class))
        .when(expressionResolver)
        .renderExpression(any(Ambiance.class), anyString());

    TaskSelectorYaml stageDelegateSelectorYaml = new TaskSelectorYaml(STAGE_DELEGATE);
    stageDelegateSelectorYaml.setOrigin(STAGE_ORIGIN);
    stepParametersWithDelegateSelector.setDelegateSelectors(
        ParameterField.createValueField(Arrays.asList(stageDelegateSelectorYaml)));
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  private void enableFF_CDS_DELEGATE_SELECTOR_PRECEDENCE() {
    doReturn(true)
        .when(ngFeatureFlagHelperService)
        .isEnabled(anyString(), eq(CDS_SERVICE_AND_INFRA_STEP_DELEGATE_SELECTOR_PRECEDENCE));
  }

  private void checkResponse(ArtifactSource source1, String message) {
    String yaml = getServiceYaml(artifactListConfigHelper(Arrays.asList(source1), source1.getIdentifier()));
    doReturn(yaml).when(cdStepHelper).fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    assertThatThrownBy(() -> step.executeAsync(ambiance, stepParameters, inputPackage, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(message);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncServiceSweepingOutputNotPresent() {
    AsyncExecutableResponse response =
        step.executeAsync(ambiance, stepParameters, StepInputPackage.builder().build(), null);

    assertThat(response.getCallbackIdsCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimary() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<List<StepDelegateInfo>> stepDelegateInfosCaptor = ArgumentCaptor.forClass(List.class);

    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("docker").build());
    listEntityDetail.add(EntityDetail.builder().name("googleArtifactRegistry").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));

    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder()
                                             .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                             .spec(DockerHubArtifactConfig.builder()
                                                       .connectorRef(ParameterField.createValueField("connector"))
                                                       .tag(ParameterField.createValueField("latest"))
                                                       .imagePath(ParameterField.createValueField("nginx"))
                                                       .build())
                                             .build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());
    verify(delegateGrpcClientWrapper, times(1))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(ambiance, listEntityDetail, true);
    verify(serviceStepsHelper)
        .publishTaskIdsStepDetailsForServiceStep(eq(ambiance), stepDelegateInfosCaptor.capture(), eq("Artifact Step"));
    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(1);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("primary");
    assertThat(response.getCallbackIdsCount()).isEqualTo(1);

    DelegateTaskRequest taskRequest = delegateTaskRequestArgumentCaptor.getValue();
    verifyDockerArtifactRequest(taskRequest, "latest");
    verifyDelegateSelectors(taskRequest, CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    assertThat(stepDelegateInfosCaptor.getValue().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimaryCheckDelegateSelectorsFFDisabledWithStageDelegateSelector() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<List<StepDelegateInfo>> stepDelegateInfosCaptor = ArgumentCaptor.forClass(List.class);

    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("docker").build());
    listEntityDetail.add(EntityDetail.builder().name("googleArtifactRegistry").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));

    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder()
                                             .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                             .spec(DockerHubArtifactConfig.builder()
                                                       .connectorRef(ParameterField.createValueField("connector"))
                                                       .tag(ParameterField.createValueField("latest"))
                                                       .imagePath(ParameterField.createValueField("nginx"))
                                                       .build())
                                             .build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response =
        step.executeAsync(ambiance, stepParametersWithDelegateSelector, inputPackage, null);

    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());
    verify(delegateGrpcClientWrapper, times(1))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(ambiance, listEntityDetail, true);
    verify(serviceStepsHelper)
        .publishTaskIdsStepDetailsForServiceStep(eq(ambiance), stepDelegateInfosCaptor.capture(), eq("Artifact Step"));
    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(1);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("primary");
    assertThat(response.getCallbackIdsCount()).isEqualTo(1);

    DelegateTaskRequest taskRequest = delegateTaskRequestArgumentCaptor.getValue();
    verifyDockerArtifactRequest(taskRequest, "latest");
    verifyDelegateSelectors(taskRequest, CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    assertThat(stepDelegateInfosCaptor.getValue().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimaryCheckDelegateSelectorsFFEnabledWithConnectorDelegateSelector() {
    enableFF_CDS_DELEGATE_SELECTOR_PRECEDENCE();
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<List<StepDelegateInfo>> stepDelegateInfosCaptor = ArgumentCaptor.forClass(List.class);

    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("docker").build());
    listEntityDetail.add(EntityDetail.builder().name("googleArtifactRegistry").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), eq(CDS_ARTIFACTS_PRIMARY_IDENTIFIER));

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));

    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder()
                                             .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                             .spec(DockerHubArtifactConfig.builder()
                                                       .connectorRef(ParameterField.createValueField("connector"))
                                                       .tag(ParameterField.createValueField("latest"))
                                                       .imagePath(ParameterField.createValueField("nginx"))
                                                       .build())
                                             .build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());
    verify(delegateGrpcClientWrapper, times(1))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(ambiance, listEntityDetail, true);
    verify(serviceStepsHelper)
        .publishTaskIdsStepDetailsForServiceStep(eq(ambiance), stepDelegateInfosCaptor.capture(), eq("Artifact Step"));
    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(1);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("primary");
    assertThat(response.getCallbackIdsCount()).isEqualTo(1);

    DelegateTaskRequest taskRequest = delegateTaskRequestArgumentCaptor.getValue();
    verifyDockerArtifactRequest(taskRequest, "latest");
    verifyDelegateSelectors(taskRequest, CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    assertThat(stepDelegateInfosCaptor.getValue().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimaryCheckDelegateSelectorsFFEnabledWithStageDelegateSelector() {
    enableFF_CDS_DELEGATE_SELECTOR_PRECEDENCE();
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<List<StepDelegateInfo>> stepDelegateInfosCaptor = ArgumentCaptor.forClass(List.class);

    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("docker").build());
    listEntityDetail.add(EntityDetail.builder().name("googleArtifactRegistry").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), eq(CDS_ARTIFACTS_PRIMARY_IDENTIFIER));

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));

    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder()
                                             .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                             .spec(DockerHubArtifactConfig.builder()
                                                       .connectorRef(ParameterField.createValueField("connector"))
                                                       .tag(ParameterField.createValueField("latest"))
                                                       .imagePath(ParameterField.createValueField("nginx"))
                                                       .build())
                                             .build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response =
        step.executeAsync(ambiance, stepParametersWithDelegateSelector, inputPackage, null);

    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());
    verify(delegateGrpcClientWrapper, times(1))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(ambiance, listEntityDetail, true);
    verify(serviceStepsHelper)
        .publishTaskIdsStepDetailsForServiceStep(eq(ambiance), stepDelegateInfosCaptor.capture(), eq("Artifact Step"));
    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(1);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("primary");
    assertThat(response.getCallbackIdsCount()).isEqualTo(1);

    DelegateTaskRequest taskRequest = delegateTaskRequestArgumentCaptor.getValue();
    verifyDockerArtifactRequest(taskRequest, "latest");
    verifyDelegateSelectors(taskRequest, STAGE_DELEGATE, STAGE_ORIGIN);
    assertThat(stepDelegateInfosCaptor.getValue().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimaryNullCheck() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder().sourceType(null).spec(null).build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(expressionResolver, never()).updateExpressions(any(Ambiance.class), any());
    verify(delegateGrpcClientWrapper, never()).submitAsyncTaskV2(any(DelegateTaskRequest.class), any(Duration.class));

    assertThat(response.getCallbackIdsCount()).isZero();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncOnlyPrimaryNoDelegateTaskNeeded() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(
        getServiceYaml(
            ArtifactListConfig.builder()
                .primary(
                    PrimaryArtifact.builder()
                        .sourceType(ArtifactSourceType.CUSTOM_ARTIFACT)
                        .spec(CustomArtifactConfig.builder().version(ParameterField.createValueField("1.0")).build())
                        .build())
                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verifyNoInteractions(delegateGrpcClientWrapper);

    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(0);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(response.getCallbackIdsCount()).isEqualTo(0);

    assertThat(output.getArtifactConfigMapForNonDelegateTaskTypes()).hasSize(1);
    assertThat(
        ((CustomArtifactConfig) output.getArtifactConfigMapForNonDelegateTaskTypes().get(0)).getVersion().getValue())
        .isEqualTo("1.0");
    assertThat(output.getArtifactConfigMapForNonDelegateTaskTypes().get(0).isPrimaryArtifact()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncWithArtifactSources() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(DockerHubArtifactConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .tag(ParameterField.createValueField("latest"))
                                           .imagePath(ParameterField.createValueField("nginx"))
                                           .build())
                                 .build();
    ArtifactSource source2 = ArtifactSource.builder()
                                 .identifier("source2-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(GcrArtifactConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .tag(ParameterField.createValueField("latest-1"))
                                           .imagePath(ParameterField.createValueField("nginx"))
                                           .build())
                                 .build();
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .primary(PrimaryArtifact.builder()
                                  .sources(List.of(source1, source2))
                                  .primaryArtifactRef(ParameterField.createValueField(source1.getIdentifier()))
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-3"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(3))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(3);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("primary", "s1", "s2");
    assertThat(response.getCallbackIdsCount()).isEqualTo(3);

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(0), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-2");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(1), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(2), "latest-3");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(2), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testPrimaryArtifactRefNotResolved() {
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(DockerHubArtifactConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .tag(ParameterField.createValueField("latest"))
                                           .imagePath(ParameterField.createValueField("nginx"))
                                           .build())
                                 .build();

    ArtifactSource source2 = ArtifactSource.builder()
                                 .identifier("source2-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(DockerHubArtifactConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connector"))
                                           .tag(ParameterField.createValueField("latest"))
                                           .imagePath(ParameterField.createValueField("nginx"))
                                           .build())
                                 .build();
    doReturn(getServiceYaml(ArtifactListConfig.builder()
                                .primary(PrimaryArtifact.builder()
                                             .sources(List.of(source1, source2))
                                             .primaryArtifactRef(
                                                 ParameterField.createExpressionField(true, "<+input>", null, true))
                                             .build())
                                .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    Assertions.assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> step.executeAsync(ambiance, stepParameters, inputPackage, null))
        .withMessageContaining("Primary artifact ref cannot be runtime or expression inside service");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncPrimaryAndSidecars() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .primary(PrimaryArtifact.builder()
                                  .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                  .spec(DockerHubArtifactConfig.builder()
                                            .tag(ParameterField.createValueField("latest"))
                                            .connectorRef(ParameterField.createValueField("connector"))
                                            .imagePath(ParameterField.createValueField("nginx"))
                                            .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-1"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s3")
                                               .sourceType(ArtifactSourceType.CUSTOM_ARTIFACT)
                                               .spec(CustomArtifactConfig.builder()
                                                         .version(ParameterField.createValueField("1.0"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(3))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(3);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("s1", "s2", "primary");
    assertThat(response.getCallbackIdsCount()).isEqualTo(3);

    assertThat(output.getArtifactConfigMapForNonDelegateTaskTypes()).hasSize(1);
    assertThat(
        ((CustomArtifactConfig) output.getArtifactConfigMapForNonDelegateTaskTypes().get(0)).getVersion().getValue())
        .isEqualTo("1.0");
    assertThat(output.getArtifactConfigMapForNonDelegateTaskTypes().get(0).isPrimaryArtifact()).isFalse();

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(0), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-1");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(1), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(2), "latest-2");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(2), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncOnlySidecars() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-1"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(2))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(2);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("s1", "s2");
    assertThat(response.getCallbackIdsCount()).isEqualTo(2);

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest-1");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(0), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-2");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(1), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncOnlySidecarsCheckDelegateSelectorsFFDisabledWithStageDelegateSelector() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-1"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response =
        step.executeAsync(ambiance, stepParametersWithDelegateSelector, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(2))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(2);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("s1", "s2");
    assertThat(response.getCallbackIdsCount()).isEqualTo(2);

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest-1");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(0), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-2");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(1), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncOnlySidecarsCheckDelegateSelectorsFFEnabledWithConnectorDelegateSelector() {
    enableFF_CDS_DELEGATE_SELECTOR_PRECEDENCE();
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-1"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(2))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(2);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("s1", "s2");
    assertThat(response.getCallbackIdsCount()).isEqualTo(2);

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest-1");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(0), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-2");
    verifyDelegateSelectors(
        delegateTaskRequestArgumentCaptor.getAllValues().get(1), CONNECTOR_DELEGATE, DEFAULT_ORIGIN);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncOnlySidecarsCheckDelegateSelectorsFFEnabledWithStageDelegateSelector() {
    enableFF_CDS_DELEGATE_SELECTOR_PRECEDENCE();
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(getServiceYaml(
                 ArtifactListConfig.builder()
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s1")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-1"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .sidecar(SidecarArtifactWrapper.builder()
                                  .sidecar(SidecarArtifact.builder()
                                               .identifier("s2")
                                               .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                               .spec(DockerHubArtifactConfig.builder()
                                                         .connectorRef(ParameterField.createValueField("connector"))
                                                         .tag(ParameterField.createValueField("latest-2"))
                                                         .imagePath(ParameterField.createValueField("nginx"))
                                                         .build())
                                               .build())
                                  .build())
                     .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response =
        step.executeAsync(ambiance, stepParametersWithDelegateSelector, inputPackage, null);

    verify(delegateGrpcClientWrapper, times(2))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(2);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactly("s1", "s2");
    assertThat(response.getCallbackIdsCount()).isEqualTo(2);

    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(0), "latest-1");
    verifyDelegateSelectors(delegateTaskRequestArgumentCaptor.getAllValues().get(0), STAGE_DELEGATE, STAGE_ORIGIN);
    verifyDockerArtifactRequest(delegateTaskRequestArgumentCaptor.getAllValues().get(1), "latest-2");
    verifyDelegateSelectors(delegateTaskRequestArgumentCaptor.getAllValues().get(1), STAGE_DELEGATE, STAGE_ORIGIN);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeAsyncOnlySidecarsNullChecks() {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(), any(), any(), any()))
        .thenAnswer(i -> i.getArguments()[3]);
    doReturn(
        getServiceYaml(ArtifactListConfig.builder()
                           .sidecar(SidecarArtifactWrapper.builder().sidecar(SidecarArtifact.builder().build()).build())
                           .build()))
        .when(cdStepHelper)
        .fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    verify(delegateGrpcClientWrapper, never()).submitAsyncTaskV2(any(DelegateTaskRequest.class), any(Duration.class));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(0);
    assertThat(output.getPrimaryArtifactTaskId()).isNull();
    assertThat(response.getCallbackIdsCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponseEmpty() {
    StepResponse stepResponse = step.handleAsyncResponse(ambiance, stepParameters, new HashMap<>());

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponsePrimaryOnly() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(ArtifactsStepV2SweepingOutput.builder()
                             .primaryArtifactTaskId("taskId-1")
                             .artifactConfigMap(Map.of("taskId-1", sampleDockerConfig("image1")))
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2)));

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, stepParameters, Map.of("taskId-1", successResponse));

    final ArgumentCaptor<ArtifactsOutcome> captor = ArgumentCaptor.forClass(ArtifactsOutcome.class);
    verify(mockSweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("artifacts"), captor.capture(), eq("STAGE"));

    final ArtifactsOutcome outcome = captor.getValue();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.getPrimary()).isNotNull();
    assertThat(outcome.getSidecars()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncErrorNotifyResponse() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(ArtifactsStepV2SweepingOutput.builder()
                             .primaryArtifactTaskId("taskId-1")
                             .artifactConfigMap(Map.of("taskId-1", sampleDockerConfig("image1")))
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2)));

    try {
      step.handleAsyncResponse(
          ambiance, stepParameters, Map.of("taskId-1", errorNotifyResponse, "taskId-2", successResponse));
    } catch (ArtifactServerException ase) {
      assertThat(ase.getMessage()).contains("No Eligible Delegates");
      return;
    }
    fail("ArtifactServerException expected");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponsePrimaryAndSidecars() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(ArtifactsStepV2SweepingOutput.builder()
                             .primaryArtifactTaskId("taskId-1")
                             .artifactConfigMap(Map.of("taskId-1", sampleDockerConfig("image1"), "taskId-2",
                                 sampleDockerConfig("image2"), "taskId-3", sampleDockerConfig("image3")))
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2)));

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, stepParameters,
        Map.of("taskId-1", successResponse, "taskId-2", successResponse, "taskId-3", successResponse));

    final ArgumentCaptor<ArtifactsOutcome> captor = ArgumentCaptor.forClass(ArtifactsOutcome.class);
    verify(mockSweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("artifacts"), captor.capture(), eq("STAGE"));

    final ArtifactsOutcome outcome = captor.getValue();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.getPrimary()).isNotNull();
    assertThat(outcome.getSidecars()).hasSize(2);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleAsyncResponseSidecarsOnly() {
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(ArtifactsStepV2SweepingOutput.builder()
                             .artifactConfigMap(Map.of("taskId-1", sampleDockerConfig("image1"), "taskId-2",
                                 sampleDockerConfig("image2"), "taskId-3", sampleDockerConfig("image3")))
                             .build())
                 .build())
        .when(mockSweepingOutputService)
        .resolveOptional(any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(ARTIFACTS_STEP_V_2)));

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, stepParameters,
        Map.of("taskId-1", successResponse, "taskId-2", successResponse, "taskId-3", successResponse));

    final ArgumentCaptor<ArtifactsOutcome> captor = ArgumentCaptor.forClass(ArtifactsOutcome.class);
    verify(mockSweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("artifacts"), captor.capture(), eq("STAGE"));

    final ArtifactsOutcome outcome = captor.getValue();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.getPrimary()).isNull();
    assertThat(outcome.getSidecars()).hasSize(3);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testProcessServiceYamlWithPrimaryArtifactRef() {
    String serviceYamlFileName = "service-with-multiple-artifact-sources-template-ref.yaml";
    // merged service yaml
    String serviceYamlFromSweepingOutput = readFile(serviceYamlFileName).replace("$PRIMARY_ARTIFACT_REF", "fromtemp1");

    // primary artifact processed
    String actualServiceYaml =
        stepHelper.getArtifactProcessedServiceYaml(ambiance, serviceYamlFromSweepingOutput).getServiceYaml();
    String processedServiceYamlFileName = "service-with-processed-primaryartifact.yaml";
    String expectedServiceYaml = readFile(processedServiceYamlFileName);
    assertThat(actualServiceYaml).isEqualTo(expectedServiceYaml);
  }
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessServiceYamlWithPrimaryArtifactRefInputValidator() {
    String serviceYamlFileName = "service-with-multiple-artifact-sources-template-ref.yaml";
    // merged service yaml
    String serviceYamlFromSweepingOutput =
        readFile(serviceYamlFileName)
            .replace("$PRIMARY_ARTIFACT_REF", "fromtemp1.allowedValues(fromtemp1,fromtemp2,gcr)");

    // primary artifact processed
    String actualServiceYaml =
        stepHelper.getArtifactProcessedServiceYaml(ambiance, serviceYamlFromSweepingOutput).getServiceYaml();
    String processedServiceYamlFileName = "service-with-processed-primaryartifact.yaml";
    String expectedServiceYaml = readFile(processedServiceYamlFileName);
    assertThat(actualServiceYaml).isEqualTo(expectedServiceYaml);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testProcessServiceYamlWithPrimaryArtifactRefAsExpression() {
    String expression = "<+serviceVariables.paf>";
    doReturn("fromtemp1").when(expressionResolver).renderExpression(any(Ambiance.class), eq(expression));
    String serviceYamlFileName = "service-with-multiple-artifact-sources-template-ref.yaml";
    // merged service yaml
    String serviceYamlFromSweepingOutput = readFile(serviceYamlFileName).replace("$PRIMARY_ARTIFACT_REF", expression);

    // primary artifact processed
    String actualServiceYaml =
        stepHelper.getArtifactProcessedServiceYaml(ambiance, serviceYamlFromSweepingOutput).getServiceYaml();
    String processedServiceYamlFileName = "service-with-processed-primaryartifact.yaml";
    String expectedServiceYaml = readFile(processedServiceYamlFileName);
    assertThat(actualServiceYaml).isEqualTo(expectedServiceYaml);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testProcessServiceYamlWithSingleArtifactSource() {
    String serviceYamlFileName = "artifactsources/service-with-single-artifact-source.yaml";
    // merged service yaml
    String serviceYamlFromSweepingOutput = readFile(serviceYamlFileName);

    String asRuntime = serviceYamlFromSweepingOutput.replace("$PRIMARY_ARTIFACT_REF", "<+input>");
    String asExpression =
        serviceYamlFromSweepingOutput.replace("$PRIMARY_ARTIFACT_REF", "<+serviceVariables.my_variable>");

    // primary artifact processed
    for (String testString : List.of(asRuntime, asExpression)) {
      String actualServiceYaml = stepHelper.getArtifactProcessedServiceYaml(ambiance, testString).getServiceYaml();
      String processedServiceYamlFileName = "service-with-processed-primaryartifact.yaml";
      String expectedServiceYaml = readFile(processedServiceYamlFileName);
      assertThat(actualServiceYaml).isEqualTo(expectedServiceYaml);
    }
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void executeAsyncWithArtifactSources_MixedArtifactSources() throws IOException {
    ArgumentCaptor<ArtifactsStepV2SweepingOutput> captor = ArgumentCaptor.forClass(ArtifactsStepV2SweepingOutput.class);
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);

    String serviceYamlFileName = "service-with-multiple-artifact-sources-template-ref.yaml";
    String serviceYaml = readFile(serviceYamlFileName).replace("$PRIMARY_ARTIFACT_REF", "fromtemp1");
    //    when(serviceEntityService.resolveArtifactSourceTemplateRefs(any(),any(),any(),any())).thenAnswer(i ->
    //    i.getArguments()[3]);
    doReturn(serviceYaml).when(cdStepHelper).fetchServiceYamlFromSweepingOutput(Mockito.any(Ambiance.class));

    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    // processed service with template refs
    String processedServiceWithTemplateRefsFile = "service-with-processed-primaryartifact.yaml";
    String processedServiceYamlWithTemplateRefs = readFile(processedServiceWithTemplateRefsFile);

    // service with resolved template refs
    String resolvedTemplateRefFile = "service-with-resolved-template-ref.yaml";
    String resolvedServiceYaml = readFile(resolvedTemplateRefFile);
    doReturn(resolvedServiceYaml)
        .when(serviceEntityService)
        .resolveArtifactSourceTemplateRefs(any(), any(), any(), any());

    AsyncExecutableResponse response = step.executeAsync(ambiance, stepParameters, inputPackage, null);

    // 1 primary and 1 sidecar
    verify(delegateGrpcClientWrapper, times(2))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));
    verify(mockSweepingOutputService).consume(any(Ambiance.class), anyString(), captor.capture(), eq(""));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ArtifactsStepV2SweepingOutput output = captor.getValue();

    assertThat(output.getArtifactConfigMap()).hasSize(2);
    assertThat(output.getPrimaryArtifactTaskId()).isNotEmpty();
    assertThat(
        output.getArtifactConfigMap().values().stream().map(ArtifactConfig::getIdentifier).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("primary", "sidecar1");
    assertThat(response.getCallbackIdsCount()).isEqualTo(2);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private DockerHubArtifactConfig sampleDockerConfig(String imagePath) {
    return DockerHubArtifactConfig.builder()
        .identifier(UUIDGenerator.generateUuid())
        .connectorRef(ParameterField.createValueField("dockerhub"))
        .imagePath(ParameterField.createValueField(imagePath))
        .build();
  }

  private ArtifactTaskResponse sampleArtifactTaskResponse() {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(
            ArtifactTaskExecutionResponse.builder()
                .artifactDelegateResponse(DockerArtifactDelegateResponse.builder()
                                              .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                              .buildDetails(ArtifactBuildDetailsNG.builder().number("1").build())
                                              .build())
                .build())
        .build();
  }

  private ErrorNotifyResponseData sampleErrorNotifyResponse() {
    return ErrorNotifyResponseData.builder().errorMessage("No Eligible Delegates").build();
  }

  private String getServiceYaml(ArtifactListConfig artifactListConfig) {
    NGServiceV2InfoConfig config =
        NGServiceV2InfoConfig.builder()
            .identifier("service-id")
            .name("service-name")
            .serviceDefinition(ServiceDefinition.builder()
                                   .type(ServiceDefinitionType.KUBERNETES)
                                   .serviceSpec(KubernetesServiceSpec.builder().artifacts(artifactListConfig).build())
                                   .build())
            .build();
    return YamlUtils.writeYamlString(NGServiceConfig.builder().ngServiceV2InfoConfig(config).build());
  }

  private ArtifactListConfig artifactListConfigHelper(List<ArtifactSource> artifactSources, String primary) {
    return ArtifactListConfig.builder()
        .primary(PrimaryArtifact.builder()
                     .sources(artifactSources)
                     .primaryArtifactRef(ParameterField.createValueField(primary))
                     .build())
        .build();
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(generateUuid())
                   .setSetupId(generateUuid())
                   .setStepType(ArtifactsStepV2Constants.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(Map.of(SetupAbstractionKeys.accountId, ACCOUNT_ID, SetupAbstractionKeys.orgIdentifier,
            "orgId", SetupAbstractionKeys.projectIdentifier, "projectId"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }

  private void verifyDockerArtifactRequest(DelegateTaskRequest taskRequest, String tag) {
    assertThat(taskRequest.isParked()).isFalse();
    assertThat(taskRequest.getTaskSelectors().size()).isEqualTo(0);
    assertThat(taskRequest.getSerializationFormat()).isEqualTo(SerializationFormat.KRYO);
    assertThat(taskRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(taskRequest.getTaskType()).isEqualTo("DOCKER_ARTIFACT_TASK_NG");
    assertThat(taskRequest.getTaskSetupAbstractions()).hasSize(5);
    assertThat(taskRequest.getTaskParameters())
        .isEqualTo(
            ArtifactTaskParameters.builder()
                .accountId(ACCOUNT_ID)
                .attributes(
                    DockerArtifactDelegateRequest.builder()
                        .imagePath("nginx")
                        .connectorRef("connector")
                        .tag(tag)
                        .encryptedDataDetails(List.of())
                        .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                        .dockerConnectorDTO(
                            DockerConnectorDTO.builder()
                                .dockerRegistryUrl("https://index.docker.com/v1")
                                .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
                                .executeOnDelegate(true)
                                .delegateSelectors(Set.of(CONNECTOR_DELEGATE))
                                .build())
                        .build())
                .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                .build());
  }

  private void verifyDelegateSelectors(DelegateTaskRequest taskRequest, String selector, String origin) {
    assertThat(taskRequest.getSelectors().size()).isEqualTo(1);
    assertThat(taskRequest.getSelectors().get(0).getSelector()).isEqualTo(selector);
    assertThat(taskRequest.getSelectors().get(0).getOrigin()).isEqualTo(origin);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testGetSetupAbstractionsForArtifactSourceTasks() {
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(ACCOUNT_ID)
                                .orgIdentifier("orgId")
                                .projectIdentifier("projectId")
                                .build();

    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    assertThat(abstractions).hasSize(4);
    assertThat(abstractions.get(SetupAbstractionKeys.projectIdentifier)).isNotNull();
    assertThat(abstractions.get(SetupAbstractionKeys.owner)).isEqualTo("orgId/projectId");

    ngAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier("orgId").build();

    abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    assertThat(abstractions).hasSize(3);
    assertThat(abstractions.get(SetupAbstractionKeys.projectIdentifier)).isNull();
    assertThat(abstractions.get(SetupAbstractionKeys.owner)).isEqualTo("orgId");
  }

  @Test
  @Owner(developers = OwnerRule.SHIVAM)
  @Category(UnitTests.class)
  public void testForCustomDelegateRequest() {
    CustomArtifactConfig customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .version(ParameterField.createValueField("v1"))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .build();
    CustomArtifactDelegateRequest artifactSourceDelegateRequest =
        (CustomArtifactDelegateRequest) stepHelper.toSourceDelegateRequest(
            customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(artifactSourceDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.CUSTOM_ARTIFACT);
    assertThat(artifactSourceDelegateRequest.getExpressionFunctorToken()).isEqualTo(0);

    customArtifactConfig =
        CustomArtifactConfig.builder()
            .identifier("test")
            .primaryArtifact(true)
            .isFromTrigger(true)
            .version(ParameterField.createValueField(null))
            .versionRegex(ParameterField.createValueField("regex"))
            .scripts(CustomArtifactScripts.builder()
                         .fetchAllArtifacts(
                             FetchAllArtifacts.builder()
                                 .artifactsArrayPath(ParameterField.createValueField("results"))
                                 .versionPath(ParameterField.createValueField("version"))
                                 .shellScriptBaseStepInfo(
                                     CustomArtifactScriptInfo.builder()
                                         .source(CustomArtifactScriptSourceWrapper.builder()
                                                     .type("Inline")
                                                     .spec(CustomScriptInlineSource.builder()
                                                               .script(ParameterField.createValueField("echo test"))
                                                               .build())
                                                     .build())
                                         .build())
                                 .build())
                         .build())
            .build();
    artifactSourceDelegateRequest = (CustomArtifactDelegateRequest) stepHelper.toSourceDelegateRequest(
        customArtifactConfig, Ambiance.newBuilder().build());
    assertThat(artifactSourceDelegateRequest.getSourceType()).isEqualTo(ArtifactSourceType.CUSTOM_ARTIFACT);
    assertThat(artifactSourceDelegateRequest.getExpressionFunctorToken()).isNotEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void validateAmazonS3HubArtifactConfig_NullFilePath() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).bucketName(BUCKET_NAME).filePath(TAG_NULL).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);

    amazonS3ArtifactConfig.setFilePath(null);
    amazonS3ArtifactConfig.setFilePathRegex(TAG_NULL);

    source1.setSpec(amazonS3ArtifactConfig);

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void validateAmazonS3ArtifactConfig_InputFilePath() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).bucketName(BUCKET_NAME).filePath(TAG_INPUT).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);

    amazonS3ArtifactConfig.setFilePath(null);
    amazonS3ArtifactConfig.setFilePathRegex(TAG_INPUT);

    source1.setSpec(amazonS3ArtifactConfig);

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void validateAmazonS3ArtifactConfig_EmptyFilePath() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).bucketName(BUCKET_NAME).filePath(TAG_EMPTY).build();

    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);

    amazonS3ArtifactConfig.setFilePath(null);
    amazonS3ArtifactConfig.setFilePathRegex(TAG_EMPTY);

    source1.setSpec(amazonS3ArtifactConfig);

    checkResponse(source1, NULL_FILEPATH_FILEPATH_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAmazonS3HubArtifactConfig_NullBucketName() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).bucketName(TAG_NULL).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_BUCKET_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAmazonS3ArtifactConfig_InputBucketName() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).bucketName(TAG_INPUT).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_BUCKET_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAmazonS3ArtifactConfig_EmptyBucketName() {
    AmazonS3ArtifactConfig amazonS3ArtifactConfig =
        AmazonS3ArtifactConfig.builder().connectorRef(CONNECTOR).bucketName(TAG_EMPTY).build();

    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMAZONS3)
                                 .spec(amazonS3ArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_BUCKET_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAMIArtifactConfig_NullRegion() {
    AMIArtifactConfig amiArtifactConfig = AMIArtifactConfig.builder().connectorRef(CONNECTOR).region(TAG_NULL).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMI)
                                 .spec(amiArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_REGION_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAMIArtifactConfig_EmptyRegion() {
    AMIArtifactConfig amiArtifactConfig = AMIArtifactConfig.builder().connectorRef(CONNECTOR).region(TAG_EMPTY).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMI)
                                 .spec(amiArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_REGION_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAMIArtifactConfig_InputRegion() {
    AMIArtifactConfig amiArtifactConfig = AMIArtifactConfig.builder().connectorRef(CONNECTOR).region(TAG_INPUT).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMI)
                                 .spec(amiArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_REGION_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAMIArtifactConfig_NullVersion_VersionRegex() {
    AMIArtifactConfig amiArtifactConfig =
        AMIArtifactConfig.builder().connectorRef(CONNECTOR).region(REGION).version(TAG_NULL).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMI)
                                 .spec(amiArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
    amiArtifactConfig.setVersion(null);
    amiArtifactConfig.setVersionRegex(TAG_NULL);
    source1.setSpec(amiArtifactConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAMIArtifactConfig_EmptyVersion_VersionRegex() {
    AMIArtifactConfig amiArtifactConfig =
        AMIArtifactConfig.builder().connectorRef(CONNECTOR).region(REGION).version(TAG_EMPTY).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMI)
                                 .spec(amiArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
    amiArtifactConfig.setVersionRegex(TAG_EMPTY);
    source1.setSpec(amiArtifactConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAMIArtifactConfig_InputVersion_VersionRegex() {
    AMIArtifactConfig amiArtifactConfig =
        AMIArtifactConfig.builder().connectorRef(CONNECTOR).region(REGION).version(TAG_INPUT).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AMI)
                                 .spec(amiArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
    amiArtifactConfig.setVersionRegex(TAG_INPUT);
    source1.setSpec(amiArtifactConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateDockerHubArtifactConfig_NullImagePath() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).imagePath(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(dockerHubArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateDockerHubArtifactConfig_EmptyImagePath() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).imagePath(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(dockerHubArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateDockerHubArtifactConfig_InputImagePath() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).imagePath(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(dockerHubArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateDockerHubArtifactConfig_NullTag_TagRegex() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).imagePath(IMAGE_PATH).tag(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(dockerHubArtifactConfig)
                                 .build();

    source1.setSpec(dockerHubArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
    dockerHubArtifactConfig.setTag(null);
    dockerHubArtifactConfig.setTagRegex(TAG_NULL);
    source1.setSpec(dockerHubArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateDockerHubArtifactConfig_EmptyTag_TagRegex() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).imagePath(IMAGE_PATH).tag(TAG_EMPTY).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(dockerHubArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
    dockerHubArtifactConfig.setTag(null);
    dockerHubArtifactConfig.setTagRegex(TAG_EMPTY);
    source1.setSpec(dockerHubArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateDockerHubArtifactConfig_InputTag_TagRegex() {
    DockerHubArtifactConfig dockerHubArtifactConfig =
        DockerHubArtifactConfig.builder().connectorRef(CONNECTOR).imagePath(IMAGE_PATH).tag(TAG_INPUT).build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
                                 .spec(dockerHubArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
    dockerHubArtifactConfig.setTag(null);
    dockerHubArtifactConfig.setTagRegex(TAG_INPUT);
    source1.setSpec(dockerHubArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_NullPackageType() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig =
        GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).packageType(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_PACKAGE_TYPE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_EmptyPackageType() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig =
        GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).packageType(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_PACKAGE_TYPE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_InputPackageType() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig =
        GithubPackagesArtifactConfig.builder().connectorRef(CONNECTOR).packageType(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_PACKAGE_TYPE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_NullPackageName() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig = GithubPackagesArtifactConfig.builder()
                                                                    .connectorRef(CONNECTOR)
                                                                    .packageType(PACKAGE_TYPE)
                                                                    .packageName(TAG_NULL)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_EmptyPackageName() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig = GithubPackagesArtifactConfig.builder()
                                                                    .connectorRef(CONNECTOR)
                                                                    .packageType(PACKAGE_TYPE)
                                                                    .packageName(TAG_EMPTY)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_InputPackageName() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig = GithubPackagesArtifactConfig.builder()
                                                                    .connectorRef(CONNECTOR)
                                                                    .packageType(PACKAGE_TYPE)
                                                                    .packageName(TAG_INPUT)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_NullVersion() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig = GithubPackagesArtifactConfig.builder()
                                                                    .connectorRef(CONNECTOR)
                                                                    .packageType(PACKAGE_TYPE)
                                                                    .packageName(PACKAGE_NAME)
                                                                    .version(TAG_NULL)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    githubPackagesArtifactConfig.setVersion(null);
    githubPackagesArtifactConfig.setVersionRegex(TAG_NULL);

    source1.setSpec(githubPackagesArtifactConfig);

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_EmptyVersion() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig = GithubPackagesArtifactConfig.builder()
                                                                    .connectorRef(CONNECTOR)
                                                                    .packageType(PACKAGE_TYPE)
                                                                    .packageName(PACKAGE_NAME)
                                                                    .version(TAG_EMPTY)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    githubPackagesArtifactConfig.setVersion(null);
    githubPackagesArtifactConfig.setVersionRegex(TAG_EMPTY);

    source1.setSpec(githubPackagesArtifactConfig);

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGithubPackagesArtifactConfig_InputVersion() {
    GithubPackagesArtifactConfig githubPackagesArtifactConfig = GithubPackagesArtifactConfig.builder()
                                                                    .connectorRef(CONNECTOR)
                                                                    .packageType(PACKAGE_TYPE)
                                                                    .packageName(PACKAGE_NAME)
                                                                    .version(TAG_INPUT)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GITHUB_PACKAGES)
                                 .spec(githubPackagesArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    githubPackagesArtifactConfig.setVersion(null);
    githubPackagesArtifactConfig.setVersionRegex(TAG_INPUT);

    source1.setSpec(githubPackagesArtifactConfig);

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_NullSubscriptionId() {
    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().subscriptionId(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_SUBSCRIPTION_ID_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_EmptySubscriptionId() {
    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().subscriptionId(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_SUBSCRIPTION_ID_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_InputSubscriptionId() {
    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder().subscriptionId(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_SUBSCRIPTION_ID_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_NullRegistry() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder().subscriptionId(SUBSCRIPTION_ID).registry(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGISTRY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_EmptyRegistry() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder().subscriptionId(SUBSCRIPTION_ID).registry(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGISTRY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_InputRegistry() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder().subscriptionId(SUBSCRIPTION_ID).registry(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGISTRY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_NullRepository() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder().subscriptionId(SUBSCRIPTION_ID).registry(REGISTRY).repository(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_EmptyRepository() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder().subscriptionId(SUBSCRIPTION_ID).registry(REGISTRY).repository(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_InputRepository() {
    AcrArtifactConfig acrArtifactConfig =
        AcrArtifactConfig.builder().subscriptionId(SUBSCRIPTION_ID).registry(REGISTRY).repository(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_NullTag_TagRegex() {
    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder()
                                              .subscriptionId(SUBSCRIPTION_ID)
                                              .registry(REGISTRY)
                                              .repository(REPOSITORY)
                                              .tag(TAG_NULL)
                                              .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    acrArtifactConfig.setTag(null);
    acrArtifactConfig.setTagRegex(TAG_NULL);

    source1.setSpec(acrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_EmptyTag_TagRegex() {
    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder()
                                              .subscriptionId(SUBSCRIPTION_ID)
                                              .registry(REGISTRY)
                                              .repository(REPOSITORY)
                                              .tag(TAG_EMPTY)
                                              .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    acrArtifactConfig.setTag(null);
    acrArtifactConfig.setTagRegex(TAG_EMPTY);

    source1.setSpec(acrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateACRArtifactConfig_InputTag_TagRegex() {
    AcrArtifactConfig acrArtifactConfig = AcrArtifactConfig.builder()
                                              .subscriptionId(SUBSCRIPTION_ID)
                                              .registry(REGISTRY)
                                              .repository(REPOSITORY)
                                              .tag(TAG_INPUT)
                                              .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ACR)
                                 .spec(acrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    acrArtifactConfig.setTag(null);
    acrArtifactConfig.setTagRegex(TAG_INPUT);

    source1.setSpec(acrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_NullImagePath() {
    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder().registryHostname(REGISTRY_HOST_NAME).imagePath(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_EmptyImagePath() {
    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder().registryHostname(REGISTRY_HOST_NAME).imagePath(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_InputImagePath() {
    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder().registryHostname(REGISTRY_HOST_NAME).imagePath(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_NullRegistryHostName() {
    GcrArtifactConfig gcrArtifactConfig = GcrArtifactConfig.builder().registryHostname(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGISTRY_HOST_NAME_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_EmptyRegistryHostName() {
    GcrArtifactConfig gcrArtifactConfig = GcrArtifactConfig.builder().registryHostname(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGISTRY_HOST_NAME_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_InputRegistryHostName() {
    GcrArtifactConfig gcrArtifactConfig = GcrArtifactConfig.builder().registryHostname(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGISTRY_HOST_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_NullTag_TagRegex() {
    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder().imagePath(IMAGE_PATH).registryHostname(REGISTRY_HOST_NAME).tag(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    gcrArtifactConfig.setTag(null);
    gcrArtifactConfig.setTagRegex(TAG_NULL);

    source1.setSpec(gcrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_EmptyTag_TagRegex() {
    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder().imagePath(IMAGE_PATH).registryHostname(REGISTRY_HOST_NAME).tag(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    gcrArtifactConfig.setTag(null);
    gcrArtifactConfig.setTagRegex(TAG_EMPTY);

    source1.setSpec(gcrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGcrArtifactConfig_InputTag_TagRegex() {
    GcrArtifactConfig gcrArtifactConfig =
        GcrArtifactConfig.builder().imagePath(IMAGE_PATH).registryHostname(REGISTRY_HOST_NAME).tag(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GCR)
                                 .spec(gcrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    gcrArtifactConfig.setTag(null);
    gcrArtifactConfig.setTagRegex(TAG_INPUT);

    source1.setSpec(gcrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_NullImagePath() {
    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder().region(REGION).imagePath(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_EmptyImagePath() {
    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder().region(REGION).imagePath(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_IputImagePath() {
    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder().region(REGION).imagePath(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_IMAGE_PATH_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_NullRegion() {
    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder().region(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGION_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_EmptyRegion() {
    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder().region(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGION_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_InputRegion() {
    EcrArtifactConfig ecrArtifactConfig = EcrArtifactConfig.builder().region(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REGION_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_NullTag_TagRegex() {
    EcrArtifactConfig ecrArtifactConfig =
        EcrArtifactConfig.builder().imagePath(IMAGE_PATH).region(REGION).tag(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    ecrArtifactConfig.setTag(null);
    ecrArtifactConfig.setTagRegex(TAG_NULL);

    source1.setSpec(ecrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_EmptyTag_TagRegex() {
    EcrArtifactConfig ecrArtifactConfig =
        EcrArtifactConfig.builder().imagePath(IMAGE_PATH).region(REGION).tag(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    ecrArtifactConfig.setTag(null);
    ecrArtifactConfig.setTagRegex(TAG_EMPTY);

    source1.setSpec(ecrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateEcrArtifactConfig_InputTag_TagRegex() {
    EcrArtifactConfig ecrArtifactConfig =
        EcrArtifactConfig.builder().imagePath(IMAGE_PATH).region(REGION).tag(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.ECR)
                                 .spec(ecrArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    ecrArtifactConfig.setTag(null);
    ecrArtifactConfig.setTagRegex(TAG_INPUT);

    source1.setSpec(ecrArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_NullPackageName() {
    AzureArtifactsConfig azureArtifactsConfig =
        AzureArtifactsConfig.builder().feed(FEED).packageType(PACKAGE_TYPE).packageName(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_EmptyPackageName() {
    AzureArtifactsConfig azureArtifactsConfig =
        AzureArtifactsConfig.builder().feed(FEED).packageType(PACKAGE_TYPE).packageName(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_InputPackageName() {
    AzureArtifactsConfig azureArtifactsConfig =
        AzureArtifactsConfig.builder().feed(FEED).packageType(PACKAGE_TYPE).packageName(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_NullPackageType() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder().feed(FEED).packageType(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_TYPE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_EmptyPackageType() {
    AzureArtifactsConfig azureArtifactsConfig =
        AzureArtifactsConfig.builder().feed(FEED).packageType(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_TYPE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_InputPackageType() {
    AzureArtifactsConfig azureArtifactsConfig =
        AzureArtifactsConfig.builder().feed(FEED).packageType(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_TYPE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_NullFeed() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder().feed(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_FEED_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_EmptyFeed() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder().feed(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_FEED_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_InputFeed() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder().feed(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();
    checkResponse(source1, NULL_FEED_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_NullVersion_VersionRegex() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder()
                                                    .packageName(PACKAGE_NAME)
                                                    .packageType(PACKAGE_TYPE)
                                                    .feed(FEED)
                                                    .version(TAG_NULL)
                                                    .build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    azureArtifactsConfig.setVersion(null);
    azureArtifactsConfig.setVersionRegex(TAG_NULL);

    source1.setSpec(azureArtifactsConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_EmptyVersion_VersionRegex() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder()
                                                    .packageName(PACKAGE_NAME)
                                                    .packageType(PACKAGE_TYPE)
                                                    .feed(FEED)
                                                    .version(TAG_EMPTY)
                                                    .build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    azureArtifactsConfig.setVersion(null);
    azureArtifactsConfig.setVersionRegex(TAG_EMPTY);

    source1.setSpec(azureArtifactsConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateAzureArtifactConfig_InputVersion_VersionRegex() {
    AzureArtifactsConfig azureArtifactsConfig = AzureArtifactsConfig.builder()
                                                    .packageName(PACKAGE_NAME)
                                                    .packageType(PACKAGE_TYPE)
                                                    .feed(FEED)
                                                    .version(TAG_INPUT)
                                                    .build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
                                 .spec(azureArtifactsConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    azureArtifactsConfig.setVersion(null);
    azureArtifactsConfig.setVersionRegex(TAG_INPUT);

    source1.setSpec(azureArtifactsConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_NullRegion() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().project(PROJECT).region(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_REGION_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_EmptyRegion() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().project(PROJECT).region(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_REGION_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_InputRegion() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().project(PROJECT).region(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_REGION_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_NullProject() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().project(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_PROJECT_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_EmptyProject() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().project(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_PROJECT_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_InputProject() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().project(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_PROJECT_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_NullRepositoryName() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().region(REGION).project(PROJECT).repositoryName(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_EmptyRepositoryName() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().region(REGION).project(PROJECT).repositoryName(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_InputRepositoryName() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig =
        GoogleArtifactRegistryConfig.builder().region(REGION).project(PROJECT).repositoryName(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_NullPackage() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig = GoogleArtifactRegistryConfig.builder()
                                                                    .region(REGION)
                                                                    .project(PROJECT)
                                                                    .repositoryName(REPOSITORY)
                                                                    .pkg(TAG_NULL)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_EmptyPackage() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig = GoogleArtifactRegistryConfig.builder()
                                                                    .region(REGION)
                                                                    .project(PROJECT)
                                                                    .repositoryName(REPOSITORY)
                                                                    .pkg(TAG_EMPTY)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_InputPackage() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig = GoogleArtifactRegistryConfig.builder()
                                                                    .region(REGION)
                                                                    .project(PROJECT)
                                                                    .repositoryName(REPOSITORY)
                                                                    .pkg(TAG_INPUT)
                                                                    .build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();
    checkResponse(source1, NULL_PACKAGE_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_NullVersion_VersionRegex() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig = GoogleArtifactRegistryConfig.builder()
                                                                    .region(REGION)
                                                                    .project(PROJECT)
                                                                    .repositoryName(REPOSITORY)
                                                                    .pkg(PACKAGE)
                                                                    .version(TAG_NULL)
                                                                    .build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    googleArtifactRegistryConfig.setVersion(null);
    googleArtifactRegistryConfig.setVersionRegex(TAG_NULL);

    source1.setSpec(googleArtifactRegistryConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_EmptyVersion_VersionRegex() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig = GoogleArtifactRegistryConfig.builder()
                                                                    .region(REGION)
                                                                    .project(PROJECT)
                                                                    .repositoryName(REPOSITORY)
                                                                    .pkg(PACKAGE)
                                                                    .version(TAG_EMPTY)
                                                                    .build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    googleArtifactRegistryConfig.setVersion(null);
    googleArtifactRegistryConfig.setVersionRegex(TAG_EMPTY);

    source1.setSpec(googleArtifactRegistryConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateGoogleArtifactRegistryArtifactConfig_InputVersion_VersionRegex() {
    GoogleArtifactRegistryConfig googleArtifactRegistryConfig = GoogleArtifactRegistryConfig.builder()
                                                                    .region(REGION)
                                                                    .project(PROJECT)
                                                                    .repositoryName(REPOSITORY)
                                                                    .pkg(PACKAGE)
                                                                    .version(TAG_INPUT)
                                                                    .build();

    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
                                 .spec(googleArtifactRegistryConfig)
                                 .build();

    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);

    googleArtifactRegistryConfig.setVersion(null);
    googleArtifactRegistryConfig.setVersionRegex(TAG_INPUT);

    source1.setSpec(googleArtifactRegistryConfig);
    checkResponse(source1, NULL_VERSION_VERSION_REGEX_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateNexusRegistryArtifactConfig_NullRepository() {
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder().repository(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.NEXUS3_REGISTRY)
                                 .spec(nexusRegistryArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateNexusRegistryArtifactConfig_EmptyRepository() {
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder().repository(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.NEXUS3_REGISTRY)
                                 .spec(nexusRegistryArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }
  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateNexusRegistryArtifactConfig_InputRepository() {
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder().repository(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.NEXUS3_REGISTRY)
                                 .spec(nexusRegistryArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_REPOSITORY_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateNexusRegistryArtifactConfig_NullTag_TagRegex() {
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder().repository(REPOSITORY).tag(TAG_NULL).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.NEXUS3_REGISTRY)
                                 .spec(nexusRegistryArtifactConfig)
                                 .build();
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
    nexusRegistryArtifactConfig.setTag(null);
    nexusRegistryArtifactConfig.setTagRegex(TAG_NULL);

    source1.setSpec(nexusRegistryArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateNexusArtifactConfig_EmptyTag_TagRegex() {
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder().repository(REPOSITORY).tag(TAG_EMPTY).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.NEXUS3_REGISTRY)
                                 .spec(nexusRegistryArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    nexusRegistryArtifactConfig.setTag(null);
    nexusRegistryArtifactConfig.setTagRegex(TAG_EMPTY);

    source1.setSpec(nexusRegistryArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = OwnerRule.RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void validateNexusArtifactConfig_InputTag_TagRegex() {
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig =
        NexusRegistryArtifactConfig.builder().repository(REPOSITORY).tag(TAG_INPUT).build();
    // Prepare test data
    ArtifactSource source1 = ArtifactSource.builder()
                                 .identifier("source1-id")
                                 .sourceType(ArtifactSourceType.NEXUS3_REGISTRY)
                                 .spec(nexusRegistryArtifactConfig)
                                 .build();

    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);

    nexusRegistryArtifactConfig.setTag(null);
    nexusRegistryArtifactConfig.setTagRegex(TAG_INPUT);

    source1.setSpec(nexusRegistryArtifactConfig);
    checkResponse(source1, NULL_TAG_TAG_REGEX_MESSAGE);
  }
}
