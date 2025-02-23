/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.TelemetryConstants.CLOUD_PROVIDER;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_EVALUATION_ADHOC_ENQUEUED;
import static io.harness.ccm.TelemetryConstants.GOVERNANCE_EVALUATION_ENQUEUED;
import static io.harness.ccm.TelemetryConstants.MODULE;
import static io.harness.ccm.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.TelemetryConstants.RESOURCE_TYPE;
import static io.harness.ccm.views.helper.RuleCloudProviderType.AWS;
import static io.harness.ccm.views.helper.RuleCloudProviderType.AZURE;
import static io.harness.ccm.views.helper.RuleCloudProviderType.GCP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.dto.GovernanceJobEnqueueDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.helper.GovernanceJobDetailsAWS;
import io.harness.ccm.views.helper.GovernanceJobDetailsAzure;
import io.harness.ccm.views.helper.GovernanceJobDetailsGCP;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleExecutionStatusType;
import io.harness.ccm.views.helper.RuleExecutionType;
import io.harness.ccm.views.helper.RuleList;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpccm.GcpCloudCostConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.faktory.FaktoryProducer;
import io.harness.filter.FilterType;
import io.harness.ng.beans.PageResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.reinert.jjschema.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.yaml.snakeyaml.Yaml;
import org.zeroturnaround.exec.ProcessExecutor;

@Slf4j
@Singleton
@OwnedBy(CE)
public class GovernanceRuleServiceImpl implements GovernanceRuleService {
  @Inject private RuleDAO ruleDAO;

  @Inject @Named("governance-schema") private String ruleSchema;
  @Inject private YamlSchemaValidator yamlSchemaValidator;
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject private RuleExecutionService ruleExecutionService;
  @Inject private TelemetryReporter telemetryReporter;
  @Nullable @Inject @Named("governanceConfig") io.harness.remote.GovernanceConfig governanceConfig;

  @Override
  public boolean save(Rule rules) {
    return ruleDAO.save(rules);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return ruleDAO.delete(accountId, uuid);
  }

  @Override
  public Rule update(Rule rules, String accountId) {
    return ruleDAO.update(rules, accountId);
  }

  @Override
  public RuleList list(GovernanceRuleFilter governancePolicyFilter) {
    return ruleDAO.list(governancePolicyFilter);
  }

  @Override
  public List<Rule> list(String accountId, List<String> uuid) {
    return ruleDAO.check(accountId, uuid);
  }

  @Override
  public Rule fetchByName(String accountId, String name, boolean create) {
    return ruleDAO.fetchByName(accountId, name, create);
  }

  @Override
  public Rule fetchById(String accountId, String uuid, boolean create) {
    return ruleDAO.fetchById(accountId, uuid, create);
  }

  @Override
  public void check(String accountId, List<String> rulesIdentifiers) {
    List<Rule> rules = ruleDAO.check(accountId, rulesIdentifiers);
    if (rules.size() != rulesIdentifiers.size()) {
      for (Rule it : rules) {
        log.info("{} {} ", it, it.getUuid());
        rulesIdentifiers.remove(it.getUuid());
      }
      if (!rulesIdentifiers.isEmpty()) {
        throw new InvalidRequestException("No such rules exist:" + rulesIdentifiers);
      }
    }
  }

  @Override
  public void customRuleLimit(String accountId) {
    GovernanceRuleFilter governancePolicyFilter = GovernanceRuleFilter.builder().build();
    governancePolicyFilter.setAccountId(accountId);
    governancePolicyFilter.setIsOOTB(false);
    if (list(governancePolicyFilter).getRules().size() >= 300) {
      throw new InvalidRequestException("You have exceeded the limit for rules creation");
    }
  }

  @Override
  public void custodianValidate(Rule rule) {
    validateSchema(rule);
    try {
      yamlSchemaValidator.validate(rule.getRulesYaml(), getSchema());
    } catch (InvalidYamlException yamlException) {
      throw new InvalidRequestException(getYAMLError(yamlException));
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid input", e);
    }
  }

  private String getYAMLError(final InvalidYamlException yamlException) {
    String defaultMessage = "Policy YAML is malformed";
    if (yamlException == null || yamlException.getMetadata() == null) {
      return defaultMessage;
    }
    if (!(yamlException.getMetadata() instanceof YamlSchemaErrorWrapperDTO)) {
      return defaultMessage;
    }
    YamlSchemaErrorWrapperDTO errorWrapperDTO = (YamlSchemaErrorWrapperDTO) yamlException.getMetadata();
    if (CollectionUtils.isEmpty(errorWrapperDTO.getSchemaErrors())) {
      return defaultMessage;
    }
    return errorWrapperDTO.getSchemaErrors().get(0).getMessage();
  }

  @Override
  public void validateSchema(Rule rule) {
    log.info("yaml: {}", rule.getRulesYaml());
    try {
      YamlUtils.readTree(rule.getRulesYaml());
      Set<String> ValidateMsg = yamlSchemaValidator.validate(rule.getRulesYaml(), EntityType.CCM_GOVERNANCE_RULE_AWS);
      if (ValidateMsg.size() > 0) {
        log.info(ValidateMsg.toString());
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Policy YAML is malformed");
    }
  }

  @Override
  public Set<ConnectorInfoDTO> getConnectorResponse(
      String accountId, Set<String> targets, RuleCloudProviderType cloudProvider) {
    Set<ConnectorInfoDTO> responseDTO = new HashSet<>();
    List<String> finalTargets = new ArrayList<>();
    for (String target : targets) {
      final CacheKey cacheKey = new CacheKey(accountId, target);
      ConnectorInfoDTO connectorInfoDTO = connectorCache.getIfPresent(cacheKey);
      if (connectorInfoDTO != null) {
        log.info("cache hit for key: {} value: {}", cacheKey, connectorInfoDTO);
        responseDTO.add(connectorInfoDTO);
      } else {
        finalTargets.add(target);
        log.info("cache miss for key: {}", cacheKey);
      }
    }
    if (!finalTargets.isEmpty()) {
      log.info("targets not cached: {}", finalTargets);
      List<ConnectorResponseDTO> nextGenConnectorResponses =
          getConnectorsWithTargets(new ArrayList<>(targets), accountId, cloudProvider);
      for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
        ConnectorInfoDTO connectorInfo = connector.getConnector();
        final CacheKey cacheKey;
        if (cloudProvider == AZURE) {
          CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connectorInfo.getConnectorConfig();
          cacheKey = new CacheKey(accountId, ceAzureConnectorDTO.getSubscriptionId());
        } else if (cloudProvider == AWS) {
          CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
          cacheKey = new CacheKey(accountId, ceAwsConnectorDTO.getAwsAccountId());
        } else {
          GcpCloudCostConnectorDTO gcpCloudCostConnectorDTO =
              (GcpCloudCostConnectorDTO) connectorInfo.getConnectorConfig();
          cacheKey = new CacheKey(accountId, gcpCloudCostConnectorDTO.getProjectId());
        }
        responseDTO.add(connectorInfo);
        connectorCache.put(cacheKey, connectorInfo);
      }
    }
    return responseDTO;
  }

  @Override
  public List<ConnectorResponseDTO> getConnectorsWithTargets(
      List<String> targets, String accountId, RuleCloudProviderType cloudProvider) {
    List<ConnectorResponseDTO> nextGenConnectorResponses = new ArrayList<>();
    List<ConnectorResponseDTO> connectorsForGivenTargets = new ArrayList<>();
    PageResponse<ConnectorResponseDTO> response = null;
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        getConnectorFilterPropertiesDTOBasedOnCloudProvider(cloudProvider, targets);
    int page = 0;
    int size = 1000;
    do {
      response = NGRestUtils.getResponse(connectorResourceClient.listConnectors(
          accountId, null, null, page, size, connectorFilterPropertiesDTO, false));
      if (response != null && isNotEmpty(response.getContent())) {
        nextGenConnectorResponses.addAll(response.getContent());
      }
      page++;
    } while (response != null && isNotEmpty(response.getContent()));

    if (cloudProvider == AWS) {
      return nextGenConnectorResponses;
    }

    for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
      if (cloudProvider == AZURE) {
        CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connector.getConnector().getConnectorConfig();
        if (targets.contains(ceAzureConnectorDTO.getSubscriptionId())) {
          connectorsForGivenTargets.add(connector);
        }
      } else {
        GcpCloudCostConnectorDTO gcpCloudCostConnectorDTO =
            (GcpCloudCostConnectorDTO) connector.getConnector().getConnectorConfig();
        if (targets.contains(gcpCloudCostConnectorDTO.getProjectId())) {
          connectorsForGivenTargets.add(connector);
        }
      }
    }

    return connectorsForGivenTargets;
  }

  private ConnectorFilterPropertiesDTO getConnectorFilterPropertiesDTOBasedOnCloudProvider(
      RuleCloudProviderType cloudProvider, List<String> targets) {
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO;
    if (cloudProvider == AZURE) {
      connectorFilterPropertiesDTO =
          ConnectorFilterPropertiesDTO.builder()
              .types(List.of(ConnectorType.CE_AZURE))
              .ccmConnectorFilter(CcmConnectorFilter.builder().featuresEnabled(List.of(CEFeatures.GOVERNANCE)).build())
              .build();
    } else if (cloudProvider == AWS) {
      connectorFilterPropertiesDTO = ConnectorFilterPropertiesDTO.builder()
                                         .types(Arrays.asList(ConnectorType.CE_AWS))
                                         .ccmConnectorFilter(CcmConnectorFilter.builder()
                                                                 .featuresEnabled(Arrays.asList(CEFeatures.GOVERNANCE))
                                                                 .awsAccountIds(targets)
                                                                 .build())
                                         .build();
    } else {
      connectorFilterPropertiesDTO =
          ConnectorFilterPropertiesDTO.builder()
              .types(List.of(ConnectorType.GCP_CLOUD_COST))
              .ccmConnectorFilter(CcmConnectorFilter.builder().featuresEnabled(List.of(CEFeatures.GOVERNANCE)).build())
              .build();
    }
    connectorFilterPropertiesDTO.setFilterType(FilterType.CONNECTOR);
    return connectorFilterPropertiesDTO;
  }

  ProcessExecutor getProcessExecutor() {
    return new ProcessExecutor();
  }

  private final Cache<CacheKey, ConnectorInfoDTO> connectorCache =
      Caffeine.newBuilder().maximumSize(2000).expireAfterWrite(15, TimeUnit.MINUTES).build();

  @Override
  public String enqueueAdhoc(String accountId, GovernanceJobEnqueueDTO governanceJobEnqueueDTO) {
    // TO DO: Refactor and make this method smaller
    // Step-1 Fetch from mongo
    String ruleEnforcementUuid = governanceJobEnqueueDTO.getRuleEnforcementId();
    String response = null;
    // Call is from UI for adhoc evaluation. Directly enqueue in this case
    // TO DO: See if UI adhoc requests can be sent to higher priority queue. This should also change in worker.
    log.info("enqueuing for ad-hoc request");
    if (isEmpty(accountId)) {
      throw new InvalidRequestException("Missing accountId");
    }
    List<Rule> rulesList = list(accountId, Arrays.asList(governanceJobEnqueueDTO.getRuleId()));
    if (rulesList == null) {
      log.error("For rule id {}: no rules exists in mongo. Nothing to enqueue", governanceJobEnqueueDTO.getRuleId());
      return null;
    }
    try {
      String jid;
      if (governanceJobEnqueueDTO.getRuleCloudProviderType() == AZURE) {
        jid = enqueueAdhocAzure(accountId, governanceJobEnqueueDTO);
      } else if (governanceJobEnqueueDTO.getRuleCloudProviderType() == AWS) {
        jid = enqueueAdhocAws(accountId, governanceJobEnqueueDTO);
      } else {
        jid = enqueueAdhocGcp(accountId, governanceJobEnqueueDTO);
      }

      // Make a record in Mongo
      RuleExecution ruleExecution =
          RuleExecution.builder()
              .accountId(accountId)
              .jobId(jid)
              .cloudProvider(governanceJobEnqueueDTO.getRuleCloudProviderType())
              .executionLogPath("") // Updated by worker when execution finishes
              .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
              .ruleEnforcementIdentifier(ruleEnforcementUuid)
              .executionCompletedAt(null) // Updated by worker when execution finishes
              .ruleIdentifier(governanceJobEnqueueDTO.getRuleId())
              .targetAccount(governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo())
              .targetRegions(governanceJobEnqueueDTO.getRuleCloudProviderType() == GCP
                      ? null
                      : Arrays.asList(governanceJobEnqueueDTO.getTargetRegion()))
              .executionLogBucketType("")
              .executionType(governanceJobEnqueueDTO.getExecutionType())
              .resourceCount(0)
              .ruleName(rulesList.get(0).getName())
              .OOTB(rulesList.get(0).getIsOOTB())
              .executionStatus(RuleExecutionStatusType.ENQUEUED)
              .build();
      response = ruleExecutionService.save(ruleExecution);
      HashMap<String, Object> properties = new HashMap<>();
      properties.put(MODULE, MODULE_NAME);
      properties.put(CLOUD_PROVIDER, governanceJobEnqueueDTO.getRuleCloudProviderType());
      properties.put(RESOURCE_TYPE, rulesList.get(0).getResourceType());
      telemetryReporter.sendTrackEvent(GOVERNANCE_EVALUATION_ADHOC_ENQUEUED, null, accountId, properties,
          Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    } catch (Exception e) {
      log.warn("Exception enqueueing job for ruleEnforcementUuid: {} for targetAccount: {} for targetRegions: {}, {}",
          ruleEnforcementUuid, governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo(),
          governanceJobEnqueueDTO.getTargetRegion(), e);
    }
    return response;
  }

  private String enqueueAdhocGcp(String accountId, GovernanceJobEnqueueDTO governanceJobEnqueueDTO) throws IOException {
    GovernanceJobDetailsGCP governanceJobDetailsGCP =
        GovernanceJobDetailsGCP.builder()
            .accountId(accountId)
            .projectId(governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo())
            .serviceAccountEmail(governanceJobEnqueueDTO.getTargetAccountDetails().getRoleId())
            .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
            .policyId(governanceJobEnqueueDTO.getRuleId())
            .policyEnforcementId("") // This is adhoc run
            .policy(governanceJobEnqueueDTO.getPolicy())
            .isOOTB(governanceJobEnqueueDTO.getIsOOTB())
            .executionType(governanceJobEnqueueDTO.getExecutionType())
            .cloudConnectorID(governanceJobEnqueueDTO.getTargetAccountDetails().getCloudConnectorId())
            .build();

    Gson gson = new GsonBuilder().create();
    String json = gson.toJson(governanceJobDetailsGCP);
    log.info("Enqueuing Gcp job in Faktory {}", json);
    // jobType, jobQueue, json
    String jid =
        FaktoryProducer.push(governanceConfig.getGcpFaktoryJobType(), governanceConfig.getGcpFaktoryQueueName(), json);
    log.info("Pushed Gcp job in Faktory: {}", jid);
    return jid;
  }

  private String enqueueAdhocAzure(String accountId, GovernanceJobEnqueueDTO governanceJobEnqueueDTO)
      throws IOException {
    GovernanceJobDetailsAzure governanceJobDetailsAzure =
        GovernanceJobDetailsAzure.builder()
            .accountId(accountId)
            .subscriptionId(governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo())
            .tenantId(governanceJobEnqueueDTO.getTargetAccountDetails().getTenantInfo())
            .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
            .policyId(governanceJobEnqueueDTO.getRuleId())
            .region(governanceJobEnqueueDTO.getTargetRegion())
            .policyEnforcementId("") // This is adhoc run
            .policy(governanceJobEnqueueDTO.getPolicy())
            .isOOTB(governanceJobEnqueueDTO.getIsOOTB())
            .executionType(governanceJobEnqueueDTO.getExecutionType())
            .cloudConnectorID(governanceJobEnqueueDTO.getTargetAccountDetails().getCloudConnectorId())
            .build();

    Gson gson = new GsonBuilder().create();
    String json = gson.toJson(governanceJobDetailsAzure);
    log.info("Enqueuing Azure job in Faktory {}", json);
    // jobType, jobQueue, json
    String jid = FaktoryProducer.push(
        governanceConfig.getAzureFaktoryJobType(), governanceConfig.getAzureFaktoryQueueName(), json);
    log.info("Pushed Azure job in Faktory: {}", jid);
    return jid;
  }

  private String enqueueAdhocAws(String accountId, GovernanceJobEnqueueDTO governanceJobEnqueueDTO) throws IOException {
    GovernanceJobDetailsAWS governanceJobDetailsAWS =
        GovernanceJobDetailsAWS.builder()
            .accountId(accountId)
            .awsAccountId(governanceJobEnqueueDTO.getTargetAccountDetails().getTargetInfo())
            .externalId(governanceJobEnqueueDTO.getTargetAccountDetails().getRoleId())
            .roleArn(governanceJobEnqueueDTO.getTargetAccountDetails().getRoleInfo())
            .isDryRun(governanceJobEnqueueDTO.getIsDryRun())
            .ruleId(governanceJobEnqueueDTO.getRuleId())
            .region(governanceJobEnqueueDTO.getTargetRegion())
            .ruleEnforcementId("") // This is adhoc run
            .policy(governanceJobEnqueueDTO.getPolicy())
            .isOOTB(governanceJobEnqueueDTO.getIsOOTB())
            .executionType(governanceJobEnqueueDTO.getExecutionType())
            .cloudConnectorID(governanceJobEnqueueDTO.getTargetAccountDetails().getCloudConnectorId())
            .build();

    Gson gson = new GsonBuilder().create();
    String json = gson.toJson(governanceJobDetailsAWS);
    log.info("Enqueuing Aws job in Faktory {}", json);
    // jobType, jobQueue, json
    String jid =
        FaktoryProducer.push(governanceConfig.getAwsFaktoryJobType(), governanceConfig.getAwsFaktoryQueueName(), json);
    log.info("Pushed Aws job in Faktory: {}", jid);
    return jid;
  }

  @Override
  public List<RuleExecution> enqueue(String accountId, RuleEnforcement ruleEnforcement, List<Rule> rulesList,
      ConnectorConfigDTO connectorConfig, String cloudConnectorId, String faktoryJobType, String faktoryQueueName) {
    List<RuleExecution> ruleExecutions = new ArrayList<>();
    String targetAccount;
    List<String> targetRegions = ruleEnforcement.getTargetRegions();
    if (ruleEnforcement.getCloudProvider() == AWS) {
      targetAccount = ((CEAwsConnectorDTO) connectorConfig).getAwsAccountId();
    } else if (ruleEnforcement.getCloudProvider() == AZURE) {
      targetAccount = ((CEAzureConnectorDTO) connectorConfig).getSubscriptionId();
    } else {
      targetAccount = ((GcpCloudCostConnectorDTO) connectorConfig).getProjectId();
      // In case of GCP the targetRegions would be empty
      // So to use same loop making one dummy entry in the list
      targetRegions = new ArrayList<>();
      targetRegions.add("DummyGcpRegion");
    }
    for (String region : targetRegions) {
      for (Rule rule : rulesList) {
        try {
          String json =
              getGovernanceJobDetails(accountId, ruleEnforcement, connectorConfig, cloudConnectorId, region, rule);
          log.info("For rule enforcement setting {}: Enqueuing {} job in Faktory {}", ruleEnforcement.getUuid(),
              ruleEnforcement.getCloudProvider(), json);
          // Bulk enqueue in faktory can lead to difficulties in error handling and retry.
          // order: jobType, jobQueue, json
          String jid = FaktoryProducer.push(faktoryJobType, faktoryQueueName, json);
          log.info("For rule enforcement setting {}: Pushed {} job in Faktory: {}", ruleEnforcement.getUuid(),
              ruleEnforcement.getCloudProvider(), jid);
          // Make a record in Mongo
          // TO DO: We can bulk insert in mongo for all successful faktory job pushes
          ruleExecutions.add(
              RuleExecution.builder()
                  .accountId(accountId)
                  .jobId(jid)
                  .cloudProvider(ruleEnforcement.getCloudProvider())
                  .executionLogPath("") // Updated by worker when execution finishes
                  .isDryRun(ruleEnforcement.getIsDryRun())
                  .ruleEnforcementIdentifier(ruleEnforcement.getUuid())
                  .ruleEnforcementName(ruleEnforcement.getName())
                  .executionCompletedAt(null) // Updated by worker when execution finishes
                  .ruleIdentifier(rule.getUuid())
                  .targetAccount(targetAccount)
                  .targetRegions(ruleEnforcement.getCloudProvider() == GCP ? null : Arrays.asList(region))
                  .executionLogBucketType("")
                  .ruleName(rule.getName())
                  .OOTB(rule.getIsOOTB())
                  .executionStatus(RuleExecutionStatusType.ENQUEUED)
                  .executionType(RuleExecutionType.EXTERNAL)
                  .build());
          HashMap<String, Object> properties = new HashMap<>();
          properties.put(MODULE, MODULE_NAME);
          properties.put(CLOUD_PROVIDER, rule.getCloudProvider());
          properties.put(RESOURCE_TYPE, rule.getResourceType());
          telemetryReporter.sendTrackEvent(GOVERNANCE_EVALUATION_ENQUEUED, null, accountId, properties,
              Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
        } catch (Exception e) {
          String regionLog = ruleEnforcement.getCloudProvider() == GCP ? "" : " for targetRegions: " + region + ",";
          log.warn("Exception enqueueing {} job for ruleEnforcementUuid: {} for targetAccount: {}{} {}",
              ruleEnforcement.getCloudProvider(), ruleEnforcement.getUuid(), targetAccount, regionLog, e);
        }
      }
    }
    return ruleExecutions;
  }

  @Override
  public String getResourceType(String ruleYaml) {
    Yaml yaml = new Yaml();
    Map<String, Object> ruleYamlMap = yaml.load(ruleYaml);
    ArrayList<Object> policies = (ArrayList<Object>) ruleYamlMap.get("policies");
    if (policies != null && policies.size() >= 1) {
      Map<String, Object> policyMap = (Map) policies.get(0);
      return (String) policyMap.get("resource");
    }
    return null;
  }

  private String getGovernanceJobDetails(String accountId, RuleEnforcement ruleEnforcement,
      ConnectorConfigDTO connectorConfig, String cloudConnectorId, String region, Rule rule) {
    Gson gson = new GsonBuilder().create();
    if (ruleEnforcement.getCloudProvider() == AWS) {
      CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorConfig;
      GovernanceJobDetailsAWS governanceJobDetailsAWS =
          GovernanceJobDetailsAWS.builder()
              .accountId(accountId)
              .awsAccountId(ceAwsConnectorDTO.getAwsAccountId())
              .externalId(ceAwsConnectorDTO.getCrossAccountAccess().getExternalId())
              .roleArn(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn())
              .isDryRun(ruleEnforcement.getIsDryRun())
              .ruleId(rule.getUuid())
              .region(region)
              .ruleEnforcementId(ruleEnforcement.getUuid())
              .policy(rule.getRulesYaml())
              .executionType(RuleExecutionType.EXTERNAL)
              .build();
      return gson.toJson(governanceJobDetailsAWS);
    } else if (ruleEnforcement.getCloudProvider() == AZURE) {
      CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connectorConfig;
      GovernanceJobDetailsAzure governanceJobDetailsAzure = GovernanceJobDetailsAzure.builder()
                                                                .accountId(accountId)
                                                                .subscriptionId(ceAzureConnectorDTO.getSubscriptionId())
                                                                .tenantId(ceAzureConnectorDTO.getTenantId())
                                                                .isDryRun(ruleEnforcement.getIsDryRun())
                                                                .policyId(rule.getUuid())
                                                                .region(region)
                                                                .policyEnforcementId(ruleEnforcement.getUuid())
                                                                .policy(rule.getRulesYaml())
                                                                .executionType(RuleExecutionType.EXTERNAL)
                                                                .cloudConnectorID(cloudConnectorId)
                                                                .build();
      return gson.toJson(governanceJobDetailsAzure);
    } else {
      GcpCloudCostConnectorDTO gcpCloudCostConnectorDTO = (GcpCloudCostConnectorDTO) connectorConfig;
      GovernanceJobDetailsGCP governanceJobDetailsGCP =
          GovernanceJobDetailsGCP.builder()
              .accountId(accountId)
              .projectId(gcpCloudCostConnectorDTO.getProjectId())
              .serviceAccountEmail(gcpCloudCostConnectorDTO.getServiceAccountEmail())
              .isDryRun(ruleEnforcement.getIsDryRun())
              .policyId(rule.getUuid())
              .policyEnforcementId(ruleEnforcement.getUuid())
              .policy(rule.getRulesYaml())
              .isOOTB(rule.getIsOOTB())
              .executionType(RuleExecutionType.EXTERNAL)
              .cloudConnectorID(cloudConnectorId)
              .build();
      return gson.toJson(governanceJobDetailsGCP);
    }
  }

  @Value
  private static class CacheKey {
    String accountId;
    String targetAccount;
  }

  @Override
  public String getSchema() {
    return ruleSchema;
  }
}