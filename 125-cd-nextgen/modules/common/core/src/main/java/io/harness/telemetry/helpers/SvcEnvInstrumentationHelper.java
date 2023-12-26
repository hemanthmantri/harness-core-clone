/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.telemetry.helpers.InstrumentationConstants.ACCOUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.DEPLOYMENT_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.ORG;
import static io.harness.telemetry.helpers.InstrumentationConstants.PIPELINE_ID;
import static io.harness.telemetry.helpers.InstrumentationConstants.PROJECT;
import static io.harness.telemetry.helpers.InstrumentationConstants.STAGE_IDENTIFIER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.telemetry.entity.SvcEnvTelemetryInfo;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class SvcEnvInstrumentationHelper extends InstrumentationHelper {
  private static final String SERVICE_COUNT = "service_count";
  private static final String ENVIRONMENT_COUNT = "environment_count";
  private static final String INFRASTRUCTURE_COUNT = "infrastructure_count";
  private static final String MULTI_SERVICE_DEPLOYMENT = "multi_service_deployment";
  private static final String MULTI_ENVIRONMENT_DEPLOYMENT = "multi_environment_deployment";
  private static final String ENVIRONMENT_GROUP_PRESENT = "environment_group_present";
  private static final String ENVIRONMENT_FILTERS_PRESENT = "environment_filters_present";
  private static final String SVC_ENV_EVENT = "svc_env";

  public CompletableFuture<Void> sendSvcEnvEvent(SvcEnvTelemetryInfo svcEnvTelemetryInfo) {
    return publishSvcEnvInfo(svcEnvTelemetryInfo, SVC_ENV_EVENT);
  }

  private CompletableFuture<Void> publishSvcEnvInfo(SvcEnvTelemetryInfo svcEnvTelemetryInfo, String eventName) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ACCOUNT, svcEnvTelemetryInfo.getAccountIdentifier());
    eventPropertiesMap.put(ORG, svcEnvTelemetryInfo.getOrgIdentifier());
    eventPropertiesMap.put(PROJECT, svcEnvTelemetryInfo.getProjectIdentifier());
    eventPropertiesMap.put(PIPELINE_ID, svcEnvTelemetryInfo.getPipelineIdentifier());
    eventPropertiesMap.put(STAGE_IDENTIFIER, svcEnvTelemetryInfo.getStageIdentifier());
    eventPropertiesMap.put(SERVICE_COUNT, svcEnvTelemetryInfo.getServiceCount());
    eventPropertiesMap.put(ENVIRONMENT_COUNT, svcEnvTelemetryInfo.getEnvironmentCount());
    eventPropertiesMap.put(INFRASTRUCTURE_COUNT, svcEnvTelemetryInfo.getInfrastructureCount());
    eventPropertiesMap.put(DEPLOYMENT_TYPE, svcEnvTelemetryInfo.getDeploymentType());
    eventPropertiesMap.put(MULTI_SERVICE_DEPLOYMENT, svcEnvTelemetryInfo.getMultiServiceDeployment());
    eventPropertiesMap.put(MULTI_ENVIRONMENT_DEPLOYMENT, svcEnvTelemetryInfo.getMultiEnvironmentDeployment());
    eventPropertiesMap.put(ENVIRONMENT_GROUP_PRESENT, svcEnvTelemetryInfo.getEnvironmentGroupPresent());
    eventPropertiesMap.put(ENVIRONMENT_FILTERS_PRESENT, svcEnvTelemetryInfo.getEnvironmentFilterPresent());
    return sendEvent(eventName, svcEnvTelemetryInfo.getAccountIdentifier(), eventPropertiesMap);
  }
}
