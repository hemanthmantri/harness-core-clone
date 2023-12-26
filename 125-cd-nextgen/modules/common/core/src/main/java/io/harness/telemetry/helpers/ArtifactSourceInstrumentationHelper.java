/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.telemetry.helpers.InstrumentationConstants.ACCOUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.API_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.ARTIFACT_IDENTIFIER;
import static io.harness.telemetry.helpers.InstrumentationConstants.ARTIFACT_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.COUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.DEPLOYMENT_TYPE;
import static io.harness.telemetry.helpers.InstrumentationConstants.IS_ARTIFACT_PRIMARY;
import static io.harness.telemetry.helpers.InstrumentationConstants.IS_SERVICE_REMOTE;
import static io.harness.telemetry.helpers.InstrumentationConstants.IS_SERVICE_V2;
import static io.harness.telemetry.helpers.InstrumentationConstants.ORG;
import static io.harness.telemetry.helpers.InstrumentationConstants.PROJECT;
import static io.harness.telemetry.helpers.InstrumentationConstants.TIME_TAKEN;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ArtifactSourceInstrumentationHelper extends InstrumentationHelper {
  private static final String LAST_PUBLISHED_TAG_EVENT_NAME = "last_published_tag";
  private static final String ARTIFACTS_SOURCE_API_USAGE_EVENT_NAME = "artifacts_source_api_usage";
  private static final String ARTIFACT_DEPLOYMENT_EVENT_NAME = "artifact_deployment";

  private CompletableFuture<Void> publishArtifactInfo(
      ArtifactConfig artifactConfig, String accountId, String orgId, String projectId, String eventName) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ACCOUNT, accountId);
    eventPropertiesMap.put(ORG, orgId);
    eventPropertiesMap.put(ARTIFACT_TYPE, artifactConfig.getSourceType());
    eventPropertiesMap.put(ARTIFACT_IDENTIFIER, artifactConfig.getIdentifier());
    eventPropertiesMap.put(PROJECT, projectId);
    eventPropertiesMap.put(IS_ARTIFACT_PRIMARY, artifactConfig.isPrimaryArtifact());
    return sendEvent(eventName, accountId, eventPropertiesMap);
  }

  public CompletableFuture<Void> sendLastPublishedTagExpressionEvent(
      ArtifactConfig artifactConfig, String accountId, String orgId, String projectId) {
    return publishArtifactInfo(artifactConfig, accountId, orgId, projectId, LAST_PUBLISHED_TAG_EVENT_NAME);
  }

  private CompletableFuture<Void> publishArtifactApiInfo(ArtifactSourceType artifactSourceType, String accountId,
      String orgId, String projectId, String apiType, long timeTaken, int count, Boolean isServiceV2,
      Boolean isServiceRemote, String eventName) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ACCOUNT, accountId);
    eventPropertiesMap.put(PROJECT, projectId);
    eventPropertiesMap.put(ORG, orgId);
    eventPropertiesMap.put(ARTIFACT_TYPE, artifactSourceType);
    eventPropertiesMap.put(API_TYPE, apiType);
    eventPropertiesMap.put(IS_SERVICE_V2, isServiceV2);
    eventPropertiesMap.put(IS_SERVICE_REMOTE, isServiceRemote);
    eventPropertiesMap.put(TIME_TAKEN, timeTaken);
    eventPropertiesMap.put(COUNT, count);

    return sendEvent(eventName, accountId, eventPropertiesMap);
  }

  public CompletableFuture<Void> sendArtifactApiEvent(ArtifactSourceType artifactSourceType, String accountId,
      String orgId, String projectId, String apiType, long timeTaken, int count, Boolean isServiceV2,
      Boolean isServiceRemote) {
    return publishArtifactApiInfo(artifactSourceType, accountId, orgId, projectId, apiType, timeTaken, count,
        isServiceV2, isServiceRemote, ARTIFACTS_SOURCE_API_USAGE_EVENT_NAME);
  }

  private CompletableFuture<Void> publishArtifactDeploymentInfo(ArtifactConfig artifactConfig, String accountId,
      String orgId, String projectId, String eventName, String deploymentType, Boolean isServiceV2) {
    HashMap<String, Object> eventPropertiesMap = new HashMap<>();
    eventPropertiesMap.put(ACCOUNT, accountId);
    eventPropertiesMap.put(ORG, orgId);
    eventPropertiesMap.put(ARTIFACT_TYPE, artifactConfig.getSourceType());
    eventPropertiesMap.put(ARTIFACT_IDENTIFIER, artifactConfig.getIdentifier());
    eventPropertiesMap.put(PROJECT, projectId);
    eventPropertiesMap.put(IS_ARTIFACT_PRIMARY, artifactConfig.isPrimaryArtifact());
    eventPropertiesMap.put(DEPLOYMENT_TYPE, deploymentType);
    eventPropertiesMap.put(IS_SERVICE_V2, isServiceV2);
    return sendEvent(eventName, accountId, eventPropertiesMap);
  }

  public CompletableFuture<Void> sendArtifactDeploymentEvent(ArtifactConfig artifactConfig, String accountId,
      String orgId, String projectId, String deploymentType, Boolean isServiceV2) {
    return publishArtifactDeploymentInfo(
        artifactConfig, accountId, orgId, projectId, ARTIFACT_DEPLOYMENT_EVENT_NAME, deploymentType, isServiceV2);
  }
}
