/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;
import static io.harness.agent.AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY;
import static io.harness.agent.AgentGatewayUtils.isAgentConnectedUsingMtls;
import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifact.ArtifactCollectionResponseHandler;
import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateTaskEventsResponse;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateCapacity;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateConfiguration.Action;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateUnregisterRequest;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.delegate.heartbeat.polling.DelegatePollingHeartbeatService;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.delegate.task.validation.DelegateConnectionResultDetail;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ipallowlist.IPAllowListClient;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.common.AccessTokenBean;
import io.harness.managerclient.AccountPreference;
import io.harness.managerclient.AccountPreferenceQuery;
import io.harness.managerclient.DelegateVersions;
import io.harness.managerclient.DelegateVersionsQuery;
import io.harness.managerclient.GetDelegatePropertiesRequest;
import io.harness.managerclient.GetDelegatePropertiesResponse;
import io.harness.managerclient.HttpsCertRequirement;
import io.harness.managerclient.HttpsCertRequirementQuery;
import io.harness.manifest.ManifestCollectionResponseHandler;
import io.harness.network.SafeHttpCall;
import io.harness.perpetualtask.PerpetualTaskLogContext;
import io.harness.perpetualtask.connector.ConnectorHearbeatPublisher;
import io.harness.perpetualtask.instancesync.InstanceSyncResponsePublisher;
import io.harness.perpetualtask.instancesync.InstanceSyncResponseV2;
import io.harness.persistence.HPersistence;
import io.harness.polling.client.PollingResourceClient;
import io.harness.queueservice.infc.DelegateCapacityManagementService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateRingService;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigValidateResponse;

import software.wings.beans.Account;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.manifest.ManifestCollectionExecutionResponse;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.LoggingTokenCache;
import software.wings.service.impl.instance.InstanceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Api("/agent/delegates")
@Path("/agent/delegates")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
@BreakDependencyOn("software.wings.service.impl.instance.InstanceHelper")
public class DelegateAgentResource {
  private static final String PT_LOG_ERROR_TEMPLATE = "Failed to process results for perpetual task: [{}] due to [{}]";
  private final DelegateService delegateService;
  private final AccountService accountService;
  private final HPersistence persistence;
  private final SubdomainUrlHelperIntfc subdomainUrlHelper;
  private final ArtifactCollectionResponseHandler artifactCollectionResponseHandler;
  private final InstanceHelper instanceHelper;
  private final ManifestCollectionResponseHandler manifestCollectionResponseHandler;
  private final ConnectorHearbeatPublisher connectorHearbeatPublisher;
  private final KryoSerializer kryoSerializer;
  private final ConfigurationController configurationController;
  private final DelegateTaskServiceClassic delegateTaskServiceClassic;
  private final InstanceSyncResponsePublisher instanceSyncResponsePublisher;
  private final PollingResourceClient pollingResourceClient;
  private final DelegatePollingHeartbeatService delegatePollingHeartbeatService;
  private final DelegateCapacityManagementService delegateCapacityManagementService;
  private final DelegateRingService delegateRingService;
  private final LoggingTokenCache loggingTokenCache;

  private final IPAllowListClient ipAllowListClient;

  @Inject
  public DelegateAgentResource(DelegateService delegateService, AccountService accountService, HPersistence persistence,
      SubdomainUrlHelperIntfc subdomainUrlHelper, ArtifactCollectionResponseHandler artifactCollectionResponseHandler,
      InstanceHelper instanceHelper, ManifestCollectionResponseHandler manifestCollectionResponseHandler,
      ConnectorHearbeatPublisher connectorHearbeatPublisher, KryoSerializer kryoSerializer,
      ConfigurationController configurationController, DelegateTaskServiceClassic delegateTaskServiceClassic,
      PollingResourceClient pollingResourceClient, InstanceSyncResponsePublisher instanceSyncResponsePublisher,
      DelegatePollingHeartbeatService delegatePollingHeartbeatService,
      DelegateCapacityManagementService delegateCapacityManagementService, DelegateRingService delegateRingService,
      final LoggingTokenCache loggingTokenCache, @Named("PRIVILEGED") IPAllowListClient ipAllowListClient) {
    this.instanceHelper = instanceHelper;
    this.delegateService = delegateService;
    this.accountService = accountService;
    this.persistence = persistence;
    this.subdomainUrlHelper = subdomainUrlHelper;
    this.artifactCollectionResponseHandler = artifactCollectionResponseHandler;
    this.manifestCollectionResponseHandler = manifestCollectionResponseHandler;
    this.connectorHearbeatPublisher = connectorHearbeatPublisher;
    this.kryoSerializer = kryoSerializer;
    this.configurationController = configurationController;
    this.delegateTaskServiceClassic = delegateTaskServiceClassic;
    this.pollingResourceClient = pollingResourceClient;
    this.instanceSyncResponsePublisher = instanceSyncResponsePublisher;
    this.delegatePollingHeartbeatService = delegatePollingHeartbeatService;
    this.delegateCapacityManagementService = delegateCapacityManagementService;
    this.delegateRingService = delegateRingService;
    this.loggingTokenCache = loggingTokenCache;
    this.ipAllowListClient = ipAllowListClient;
  }

  @DelegateAuth
  @GET
  @Path("configuration")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateConfiguration> getDelegateConfiguration(
      @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      DelegateConfiguration configuration = accountService.getDelegateConfiguration(accountId);
      String primaryDelegateVersion = configurationController.getPrimaryVersion();
      // Adding primary delegate to the last element of delegate versions.
      if (isNotEmpty(configuration.getDelegateVersions())
          && configuration.getDelegateVersions().remove(primaryDelegateVersion)) {
        configuration.getDelegateVersions().add(primaryDelegateVersion);
      }
      return new RestResponse<>(configuration);
    } catch (InvalidRequestException ex) {
      if (isNotBlank(ex.getMessage()) && ex.getMessage().startsWith("Deleted AccountId")) {
        return new RestResponse<>(DelegateConfiguration.builder().action(Action.SELF_DESTRUCT).build());
      }

      return null;
    }
  }

  @DelegateAuth
  @GET
  @Path("jreVersion")
  @Timed
  @ExceptionMetered
  public RestResponse<String> getJREVersion(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("isDelegate") boolean isDelegate) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String jreVersion = delegateRingService.getJREVersion(accountId, isDelegate);
      if (isNotEmpty(jreVersion)) {
        return new RestResponse<>(jreVersion);
      }
    } catch (Exception ex) {
      log.error("Unable to fetch jre version from ring for accountId {}", accountId, ex);
    }
    return null;
  }

  @DelegateAuth
  @POST
  @Path("properties")
  @Timed
  @ExceptionMetered
  public RestResponse<String> getDelegateProperties(@QueryParam("accountId") String accountId, byte[] request)
      throws InvalidProtocolBufferException {
    GetDelegatePropertiesRequest requestProto = GetDelegatePropertiesRequest.parseFrom(request);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      GetDelegatePropertiesResponse response =
          GetDelegatePropertiesResponse.newBuilder()
              .addAllResponseEntry(
                  requestProto.getRequestEntryList()
                      .stream()
                      .map(requestEntry -> {
                        if (requestEntry.is(DelegateVersionsQuery.class)) {
                          return Any.pack(
                              DelegateVersions.newBuilder()
                                  .addAllDelegateVersion(
                                      accountService.getDelegateConfiguration(accountId).getDelegateVersions())
                                  .build());
                        } else if (requestEntry.is(HttpsCertRequirementQuery.class)) {
                          return Any.pack(
                              HttpsCertRequirement.newBuilder()
                                  .setCertRequirement(accountService.getHttpsCertificateRequirement(accountId))
                                  .build());
                        } else if (requestEntry.is(AccountPreferenceQuery.class)) {
                          Account account = accountService.get(accountId);
                          if (account.getAccountPreferences() != null
                              && account.getAccountPreferences().getDelegateSecretsCacheTTLInHours() != null) {
                            return Any.pack(AccountPreference.newBuilder()
                                                .setDelegateSecretsCacheTTLInHours(
                                                    account.getAccountPreferences().getDelegateSecretsCacheTTLInHours())
                                                .build());
                          }
                          return Any.newBuilder().build();
                        } else {
                          return Any.newBuilder().build();
                        }
                      })
                      .collect(toList()))
              .build();
      return new RestResponse<>(response.toString());

    } catch (Exception e) {
      log.error("Encountered an exception while parsing proto", e);
      return null;
    }
  }

  @DelegateAuth
  @POST
  @Path("register")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateRegisterResponse> register(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty final String accountId, final DelegateParams delegateParams,
      @HeaderParam(HEADER_AGENT_MTLS_AUTHORITY) @Nullable String agentMtlsAuthority) {
    // We want to enforce delegate registration allowlist only for next gen immutable delegates.
    if (accountService.isNextGenEnabled(accountId)
        && accountService.isFeatureFlagEnabled("PL_ENFORCE_DELEGATE_REGISTRATION_ALLOWLIST", accountId)
        && delegateParams.isImmutable()) {
      // Retrieve source IP from the request
      String sourceIp = request.getRemoteAddr();
      log.info("Delegate registration is originating from IP {}, and delegate host ip is {}", sourceIp,
          delegateParams.getIp());
      IPAllowlistConfigValidateResponse response;
      try {
        response = NGRestUtils.getGeneralResponse(ipAllowListClient.allowedIpAddress(accountId, sourceIp));
      } catch (Exception e) {
        log.error("Exception while validating delegate ip: ", e);
        return null;
      }
      if (response == null || !response.isAllowedForApi()) {
        log.info("Delegate IP {} is not allowed, failing registration", sourceIp);
        return null;
      }
    }
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      final long startTime = System.currentTimeMillis();
      final boolean isConnectedUsingMtls = isAgentConnectedUsingMtls(agentMtlsAuthority);
      final DelegateRegisterResponse registerResponse = delegateService.register(delegateParams, isConnectedUsingMtls);
      log.info("Delegate registration took {} in ms", System.currentTimeMillis() - startTime);
      return new RestResponse<>(registerResponse);
    }
  }

  @DelegateAuth
  @POST
  @Path("unregister")
  @Timed
  @ExceptionMetered
  public RestResponse<Void> unregister(
      @QueryParam("accountId") @NotEmpty final String accountId, final DelegateUnregisterRequest request) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegateService.unregister(accountId, request);
      return new RestResponse<>();
    }
  }

  @DelegateAuth
  @GET
  @Path("{delegateId}/profile")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateProfileParams> checkForProfile(@QueryParam("accountId") @NotEmpty String accountId,
      @PathParam("delegateId") String delegateId, @QueryParam("profileId") String profileId,
      @QueryParam("lastUpdatedAt") Long lastUpdatedAt) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      DelegateProfileParams profileParams =
          delegateService.checkForProfile(accountId, delegateId, profileId, lastUpdatedAt);
      return new RestResponse<>(profileParams);
    }
  }

  @DelegateAuth
  @POST
  @Path("connectionHeartbeat/{delegateId}")
  @Timed
  @ExceptionMetered
  public void connectionHeartbeat(@QueryParam("accountId") @NotEmpty String accountId,
      @PathParam("delegateId") String delegateId, DelegateConnectionHeartbeat connectionHeartbeat) {
    // do nothing : remove this 3 months after release, keeping for older version compatibility
  }

  @POST
  public RestResponse<Delegate> add(@QueryParam("accountId") @NotEmpty String accountId, Delegate delegate) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      delegate.setAccountId(accountId);
      return new RestResponse<>(delegateService.add(delegate));
    }
  }

  @DelegateAuth
  @PUT
  @Produces("application/x-kryo")
  @Path("{delegateId}/tasks/{taskId}/acquire")
  @Timed
  @ExceptionMetered
  public DelegateTaskPackage acquireDelegateTask(@PathParam("delegateId") String delegateId,
      @PathParam("taskId") String taskId, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateInstanceId") String delegateInstanceId) {
    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new DelegateLogContext(accountId, delegateId, delegateInstanceId, OVERRIDE_ERROR)) {
      return delegateTaskServiceClassic.acquireDelegateTask(accountId, delegateId, taskId, delegateInstanceId);
    }
  }

  @DelegateAuth
  @PUT
  @Produces("application/x-kryo-v2")
  @Path("{delegateId}/tasks/{taskId}/acquire/v2")
  @Timed
  @ExceptionMetered
  public DelegateTaskPackage acquireDelegateTaskV2(@PathParam("delegateId") String delegateId,
      @PathParam("taskId") String taskId, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateInstanceId") String delegateInstanceId) {
    return acquireDelegateTask(delegateId, taskId, accountId, delegateInstanceId);
  }

  @DelegateAuth
  @POST
  @Produces("application/x-kryo")
  @Path("{delegateId}/tasks/{taskId}/report")
  @Timed
  @ExceptionMetered
  public DelegateTaskPackage reportConnectionResults(@PathParam("delegateId") String delegateId,
      @PathParam("taskId") String taskId, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateInstanceId") String delegateInstanceId, List<DelegateConnectionResultDetail> results) {
    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new DelegateLogContext(accountId, delegateId, delegateInstanceId, OVERRIDE_ERROR)) {
      return delegateTaskServiceClassic.reportConnectionResults(
          accountId, delegateId, taskId, delegateInstanceId, getDelegateConnectionResults(results));
    }
  }

  @DelegateAuth
  @POST
  @Produces("application/x-kryo-v2")
  @Path("{delegateId}/tasks/{taskId}/report/v2")
  @Timed
  @ExceptionMetered
  public DelegateTaskPackage reportConnectionResultsV2(@PathParam("delegateId") String delegateId,
      @PathParam("taskId") String taskId, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateInstanceId") String delegateInstanceId, List<DelegateConnectionResultDetail> results) {
    return reportConnectionResults(delegateId, taskId, accountId, delegateInstanceId, results);
  }

  @NotNull
  private List<DelegateConnectionResult> getDelegateConnectionResults(List<DelegateConnectionResultDetail> results) {
    List<DelegateConnectionResult> delegateConnectionResult = new ArrayList<>();
    for (DelegateConnectionResultDetail source : results) {
      DelegateConnectionResult target = DelegateConnectionResult.builder().build();
      target.setAccountId(source.getAccountId());
      target.setCriteria(source.getCriteria());
      target.setDelegateId(source.getDelegateId());
      target.setDuration(source.getDuration());
      target.setLastUpdatedAt(source.getLastUpdatedAt());
      target.setUuid(source.getUuid());
      target.setValidated(source.isValidated());
      target.setValidUntil(source.getValidUntil());
      delegateConnectionResult.add(target);
    }
    return delegateConnectionResult;
  }

  @DelegateAuth
  @GET
  @Path("logging-token")
  @Produces(javax.ws.rs.core.MediaType.APPLICATION_JSON)
  @Timed
  @ExceptionMetered
  public RestResponse<AccessTokenBean> getLoggingToken(@QueryParam("accountId") @NotEmpty final String accountId) {
    try {
      return new RestResponse<>(loggingTokenCache.getToken(accountId));
    } catch (ExecutionException e) {
      throw new InvalidRequestException("Please ensure log service is running.");
    }
  }

  @DelegateAuth
  @GET
  @Path("{delegateId}/upgrade")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateScripts> checkForUpgrade(@Context HttpServletRequest request,
      @HeaderParam("Version") String version, @PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("delegateName") String delegateName)
      throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateService.getDelegateScripts(accountId, version,
          subdomainUrlHelper.getManagerUrl(request, accountId), getVerificationUrl(request), delegateName));
    }
  }

  @DelegateAuth
  @GET
  @Path("delegateScriptsNg")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateScripts> getDelegateScriptsNg(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateVersion") @NotEmpty String delegateVersion, @QueryParam("patchVersion") String patchVersion,
      @QueryParam("delegateType") String delegateType) throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String fullVersion = isNotEmpty(patchVersion) ? delegateVersion + "-" + patchVersion : delegateVersion;
      return new RestResponse<>(delegateService.getDelegateScriptsNg(accountId, fullVersion,
          subdomainUrlHelper.getManagerUrl(request, accountId), getVerificationUrl(request), delegateType));
    }
  }

  @DelegateAuth
  @GET
  @Path("delegateScripts")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateScripts> getDelegateScripts(@Context HttpServletRequest request,
      @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateVersion") @NotEmpty String delegateVersion, @QueryParam("patchVersion") String patchVersion,
      @QueryParam("delegateName") String delegateName) throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String fullVersion = isNotEmpty(patchVersion) ? delegateVersion + "-" + patchVersion : delegateVersion;
      return new RestResponse<>(delegateService.getDelegateScripts(accountId, fullVersion,
          subdomainUrlHelper.getManagerUrl(request, accountId), getVerificationUrl(request), delegateName));
    }
  }

  @DelegateAuth
  @GET
  @Path("{delegateId}/task-events")
  @Timed
  @ExceptionMetered
  public DelegateTaskEventsResponse getDelegateTaskEvents(@PathParam("delegateId") @NotEmpty String delegateId,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("syncOnly") boolean syncOnly) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      List<DelegateTaskEvent> delegateTaskEvents =
          delegateTaskServiceClassic.getDelegateTaskEvents(accountId, delegateId, syncOnly);
      return DelegateTaskEventsResponse.builder().delegateTaskEvents(delegateTaskEvents).build();
    }
  }

  @DelegateAuth
  @POST
  @Path("heartbeat-with-polling")
  @Timed
  @ExceptionMetered
  public RestResponse<DelegateHeartbeatResponse> updateDelegateHB(@QueryParam("accountId") @NotEmpty String accountId,
      @HeaderParam(HEADER_AGENT_MTLS_AUTHORITY) @Nullable String agentMtlsAuthority, DelegateParams delegateParams) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateParams.getDelegateId(), OVERRIDE_ERROR)) {
      final boolean isConnectedUsingMtls = isAgentConnectedUsingMtls(agentMtlsAuthority);
      // delegate.isPollingModeEnabled() will be true here.
      if ("ECS".equals(delegateParams.getDelegateType())) {
        Delegate registeredDelegate = delegateService.handleEcsDelegateRequest(
            Delegate.getDelegateFromParams(delegateParams, isConnectedUsingMtls));

        return new RestResponse<>(buildDelegateHBResponse(registeredDelegate));
      } else {
        return new RestResponse<>(delegatePollingHeartbeatService.process(delegateParams));
      }
    }
  }

  @DelegateAuth
  @POST
  @Path("{delegateId}/state-executions")
  @Timed
  @ExceptionMetered
  public void saveApiCallLogs(
      @PathParam("delegateId") String delegateId, @QueryParam("accountId") String accountId, byte[] logsBlob) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      log.debug("About to convert logsBlob byte array into ThirdPartyApiCallLog.");
      List<ThirdPartyApiCallLog> logs = (List<ThirdPartyApiCallLog>) kryoSerializer.asObject(logsBlob);
      log.debug("LogsBlob byte array converted successfully into ThirdPartyApiCallLog.");

      persistence.save(
          logs.stream().map(software.wings.service.impl.ThirdPartyApiCallLog::fromDto).collect(Collectors.toList()));
    }
  }

  private String getVerificationUrl(HttpServletRequest request) {
    return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
  }

  @DelegateAuth
  @POST
  @Path("artifact-collection/{perpetualTaskId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> processArtifactCollectionResult(
      @PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, byte[] response) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      BuildSourceExecutionResponse executionResponse = (BuildSourceExecutionResponse) kryoSerializer.asObject(response);

      if (executionResponse.getBuildSourceResponse() != null) {
        log.debug("Received artifact collection {}", executionResponse.getBuildSourceResponse().getBuildDetails());
      }
      artifactCollectionResponseHandler.processArtifactCollectionResult(accountId, perpetualTaskId, executionResponse);
    }
    return new RestResponse<>(true);
  }

  @DelegateAuth
  @POST
  @Path("instance-sync/{perpetualTaskId}")
  public RestResponse<Boolean> processInstanceSyncResult(@PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateResponseData response) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      instanceHelper.processInstanceSyncResponseFromPerpetualTask(perpetualTaskId.replaceAll("[\r\n]", ""), response);
    } catch (Exception e) {
      log.warn(PT_LOG_ERROR_TEMPLATE, perpetualTaskId.replaceAll("[\r\n]", ""), ExceptionUtils.getMessage(e));
    }
    return new RestResponse<>(true);
  }

  @DelegateAuth
  @POST
  @Path("instance-sync-ng/{perpetualTaskId}")
  public RestResponse<Boolean> processInstanceSyncNGResult(
      @PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateResponseData response) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      instanceSyncResponsePublisher.publishInstanceSyncResponseToNG(
          accountId, perpetualTaskId.replaceAll("[\r\n]", ""), response);
    } catch (Exception e) {
      log.warn(PT_LOG_ERROR_TEMPLATE, perpetualTaskId.replaceAll("[\r\n]", ""), ExceptionUtils.getMessage(e));
    }
    return new RestResponse<>(true);
  }

  @DelegateAuth
  @POST
  @Path("instance-sync-ng-v2/{perpetualTaskId}")
  public RestResponse<Boolean> processInstanceSyncNGResultV2(
      @PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, InstanceSyncResponseV2 instanceSyncResponseV2) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      instanceSyncResponsePublisher.publishInstanceSyncResponseV2ToNG(
          accountId, perpetualTaskId.replaceAll("[\r\n]", ""), instanceSyncResponseV2);
    } catch (Exception e) {
      log.warn(PT_LOG_ERROR_TEMPLATE, perpetualTaskId.replaceAll("[\r\n]", ""), ExceptionUtils.getMessage(e));
    }
    return new RestResponse<>(true);
  }

  @DelegateAuth
  @POST
  @Path("manifest-collection/{perpetualTaskId}")
  public RestResponse<Boolean> processManifestCollectionResult(
      @PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, byte[] serializedExecutionResponse) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      ManifestCollectionExecutionResponse executionResponse =
          (ManifestCollectionExecutionResponse) kryoSerializer.asObject(serializedExecutionResponse);

      if (executionResponse.getManifestCollectionResponse() != null) {
        log.debug("Received manifest collection {}", executionResponse.getManifestCollectionResponse().getHelmCharts());
      }
      manifestCollectionResponseHandler.handleManifestCollectionResponse(accountId, perpetualTaskId, executionResponse);
    }
    return new RestResponse<>(Boolean.TRUE);
  }

  @DelegateAuth
  @POST
  @Path("connectors/{perpetualTaskId}")
  public RestResponse<Boolean> publishNGConnectorHeartbeatResult(
      @PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, ConnectorHeartbeatDelegateResponse validationResult) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      connectorHearbeatPublisher.pushConnectivityCheckActivity(accountId, validationResult);
    }
    return new RestResponse<>(true);
  }

  @DelegateAuth
  @POST
  @Path("polling/{perpetualTaskId}")
  public RestResponse<Boolean> processPollingResultNg(@PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, byte[] serializedExecutionResponse) throws IOException {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      SafeHttpCall.executeWithExceptions(pollingResourceClient.processPolledResult(perpetualTaskId, accountId,
          RequestBody.create(MediaType.parse("application/octet-stream"), serializedExecutionResponse)));
    }
    return new RestResponse<>(Boolean.TRUE);
  }

  @DelegateAuth
  @POST
  @Path("register-delegate-capacity/{delegateId}")
  @Timed
  @ExceptionMetered
  public void registerDelegateCapacity(@QueryParam("accountId") @NotEmpty String accountId,
      @PathParam("delegateId") String delegateId, DelegateCapacity delegateCapacity) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      delegateCapacityManagementService.registerDelegateCapacity(accountId, delegateId, delegateCapacity);
    }
  }

  private DelegateHeartbeatResponse buildDelegateHBResponse(Delegate delegate) {
    return DelegateHeartbeatResponse.builder()
        .delegateId(delegate.getUuid())
        .delegateRandomToken(delegate.getDelegateRandomToken())
        .jreVersion(delegate.getUseJreVersion())
        .sequenceNumber(delegate.getSequenceNum())
        .status(delegate.getStatus().toString())
        .useCdn(delegate.isUseCdn())
        .build();
  }
}
