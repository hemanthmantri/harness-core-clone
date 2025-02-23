/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.supporter.async;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

@Getter
@OwnedBy(PIPELINE)
public class TestAsyncStep implements AsyncExecutable<TestStepParameters> {
  public static int timeout = 100;
  public static final StepType ASYNC_STEP_TYPE =
      StepType.newBuilder().setType("TEST_STATE_PLAN_ASYNC").setStepCategory(StepCategory.STEP).build();

  public static AtomicInteger ABORT_COUNTER = new AtomicInteger(0);
  public static AtomicInteger FAIL_COUNTER = new AtomicInteger(0);

  private String message;

  public TestAsyncStep(String message) {
    this.message = message;
  }

  @Override
  public Class<TestStepParameters> getStepParametersClass() {
    return TestStepParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, TestStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    String resumeId = generateUuid();
    return AsyncExecutableResponse.newBuilder().setTimeout(timeout).addCallbackIds(resumeId).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, TestStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public void handleFailure(Ambiance ambiance, TestStepParameters stepParameters, AsyncExecutableResponse response,
      Map<String, String> metadata) {
    FAIL_COUNTER.incrementAndGet();
  }

  @Override
  public void handleExpire(
      Ambiance ambiance, TestStepParameters stepParameters, AsyncExecutableResponse executableResponse) {
    AsyncExecutable.super.handleExpire(ambiance, stepParameters, executableResponse);
  }

  @Override
  public void handleAbort(Ambiance ambiance, TestStepParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    ABORT_COUNTER.incrementAndGet();
  }

  @Override
  public ProgressData handleProgressAsync(
      Ambiance ambiance, TestStepParameters stepParameters, ProgressData progressData) {
    return AsyncExecutable.super.handleProgressAsync(ambiance, stepParameters, progressData);
  }

  @Override
  public void handleForCallbackId(Ambiance ambiance, TestStepParameters stepParameters, List<String> allCallbackIds,
      String callbackId, ResponseData responseData) {
    message = callbackId;
  }
}
