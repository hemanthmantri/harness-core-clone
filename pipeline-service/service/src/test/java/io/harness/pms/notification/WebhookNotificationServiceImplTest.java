/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdstage.remote.CDNGStageSummaryResourceClient;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionMetadataServiceImpl;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.PipelineEventType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class WebhookNotificationServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";
  private static String ORG_ID = "orgId";
  private static String PROJECT_ID = "projectId";

  private static String PLAN_EXECUTION_ID = "planExecutionId";

  CDNGStageSummaryResourceClient cdngStageSummaryResourceClient;
  WebhookNotificationService webhookNotificationService;
  PlanExecutionMetadataService planExecutionMetadataService;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    cdngStageSummaryResourceClient = mock(CDNGStageSummaryResourceClient.class, RETURNS_DEEP_STUBS);
    planExecutionMetadataService = mock(PlanExecutionMetadataServiceImpl.class, RETURNS_DEEP_STUBS);
    webhookNotificationService =
        new WebhookNotificationServiceImpl(cdngStageSummaryResourceClient, planExecutionMetadataService);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetModuleInfo() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder().setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE)))
            .build();
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.PIPELINE_START);
    assertThat(moduleInfo).isNotNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetModuleInfoForStageLevelStageSuccess() throws IOException {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String runtimeID = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeID).setStepType(
                                StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                            .build();
    Response<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> restResponse = Response.success(
        ResponseDTO.newResponse(Map.of(runtimeID, CDStageSummaryResponseDTO.builder().service("s1").build())));
    Call<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(restResponse);
    Mockito
        .when(cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, Lists.newArrayList(runtimeID)))
        .thenReturn(responseDTOCall);
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STAGE_SUCCESS);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("s1"));
    Mockito.verify(cdngStageSummaryResourceClient)
        .listStageExecutionFormattedSummary(ACCOUNT_ID, ORG_ID, PROJECT_ID, Lists.newArrayList(runtimeID));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetModuleInfoForStageLevelStageStart() throws IOException {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                                .accountId(ACCOUNT_ID)
                                                                .projectIdentifier(PROJECT_ID)
                                                                .orgIdentifier(ORG_ID)
                                                                .build();
    String runtimeID = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setIdentifier("stage1").setRuntimeId(runtimeID).setStepType(
                                StepType.newBuilder().setStepCategory(StepCategory.STAGE)))
                            .build();
    Response<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> restResponse = Response.success(
        ResponseDTO.newResponse(Map.of("stage1", CDStageSummaryResponseDTO.builder().service("s1").build())));
    Call<ResponseDTO<Map<String, CDStageSummaryResponseDTO>>> responseDTOCall = mock(Call.class);
    when(responseDTOCall.execute()).thenReturn(restResponse);
    Mockito
        .when(cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID, Lists.newArrayList("stage1")))
        .thenReturn(responseDTOCall);
    ModuleInfo moduleInfo =
        webhookNotificationService.getModuleInfo(ambiance, executionSummaryEntity, PipelineEventType.STAGE_START);
    assertThat(moduleInfo.getServices()).isEqualTo(Lists.newArrayList("s1"));
    Mockito.verify(cdngStageSummaryResourceClient)
        .listStagePlanCreationFormattedSummary(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, PLAN_EXECUTION_ID, Lists.newArrayList("stage1"));
  }
}
