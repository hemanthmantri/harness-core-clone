/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatsEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.idp.scorecard.checks.events.CheckCreateEvent;
import io.harness.idp.scorecard.checks.events.CheckDeleteEvent;
import io.harness.idp.scorecard.checks.events.CheckUpdateEvent;
import io.harness.idp.scorecard.checks.mappers.CheckDetailsMapper;
import io.harness.idp.scorecard.checks.mappers.CheckStatsMapper;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.idp.scorecard.checks.repositories.CheckStatsRepository;
import io.harness.idp.scorecard.checks.repositories.CheckStatusEntityByIdentifier;
import io.harness.idp.scorecard.checks.repositories.CheckStatusRepository;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckGraph;
import io.harness.spec.server.idp.v1.model.CheckStatsResponse;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.InputDetails;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.Rule;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jooq.tools.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class CheckServiceImpl implements CheckService {
  private final CheckRepository checkRepository;
  private final CheckStatusRepository checkStatusRepository;
  private final CheckStatsRepository checkStatsRepository;
  private final ScorecardService scorecardService;
  private final NGSettingsClient settingsClient;
  private final DataPointService dataPointService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private final OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject
  public CheckServiceImpl(CheckRepository checkRepository, CheckStatusRepository checkStatusRepository,
      CheckStatsRepository checkStatsRepository, ScorecardService scorecardService, NGSettingsClient settingsClient,
      DataPointService dataPointService, TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.checkRepository = checkRepository;
    this.checkStatusRepository = checkStatusRepository;
    this.checkStatsRepository = checkStatsRepository;
    this.scorecardService = scorecardService;
    this.settingsClient = settingsClient;
    this.dataPointService = dataPointService;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public void createCheck(CheckDetails checkDetails, String accountIdentifier) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      generateRuleIdentifiersIfNotPresent(checkDetails);
      validateCheckSaveRequest(checkDetails, accountIdentifier);

      if (isCheckAlreadyDeleted(accountIdentifier, checkDetails.getIdentifier())) {
        checkDetails.setIdentifier(checkDetails.getIdentifier() + new Random().nextInt(9999));
      }
      CheckEntity savedCheckEntity = checkRepository.save(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
      outboxService.save(new CheckCreateEvent(accountIdentifier, CheckDetailsMapper.toDTO(savedCheckEntity, null)));
      return true;
    }));
  }

  @Override
  public void updateCheck(CheckDetails checkDetails, String accountIdentifier) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      generateRuleIdentifiersIfNotPresent(checkDetails);
      validateCheckSaveRequest(checkDetails, accountIdentifier);

      CheckEntity oldCheckEntity =
          checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, checkDetails.getIdentifier());
      CheckEntity updatedCheckEntity =
          checkRepository.update(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
      if (updatedCheckEntity == null) {
        throw new InvalidRequestException("Default checks cannot be updated");
      }
      outboxService.save(new CheckUpdateEvent(accountIdentifier, CheckDetailsMapper.toDTO(updatedCheckEntity, null),
          CheckDetailsMapper.toDTO(oldCheckEntity, null)));
      return true;
    }));
  }

  @Override
  public Page<CheckEntity> getChecksByAccountId(
      Boolean custom, String accountIdentifier, Pageable pageRequest, String searchTerm) {
    Criteria criteria = buildCriteriaForChecksList(accountIdentifier, custom, searchTerm);
    return checkRepository.findAll(criteria, pageRequest);
  }

  @Override
  public List<CheckEntity> getActiveChecks(String accountIdentifier, List<String> checkIdentifiers) {
    return checkRepository.findByAccountIdentifierInAndIsDeletedAndIdentifierIn(
        addGlobalAccountIdentifierAlong(accountIdentifier), false, checkIdentifiers);
  }

  @Override
  public CheckDetails getCheckDetails(String accountIdentifier, String identifier, Boolean custom) {
    CheckEntity checkEntity;
    if (Boolean.TRUE.equals(custom)) {
      checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    } else {
      checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, identifier);
    }
    if (checkEntity == null) {
      throw new InvalidRequestException(String.format("Check details not found for checkId [%s]", identifier));
    }
    CheckStatusEntity checkStatusEntity =
        getCheckStatusByAccountIdAndIdentifiers(accountIdentifier, List.of(identifier))
            .get((custom ? accountIdentifier : GLOBAL_ACCOUNT_ID) + DOT_SEPARATOR + identifier);
    return CheckDetailsMapper.toDTO(checkEntity, checkStatusEntity);
  }

  @Override
  public List<CheckEntity> getChecksByAccountIdAndIdentifiers(String accountIdentifier, Set<String> identifiers) {
    return checkRepository.findByAccountIdentifierInAndIdentifierIn(
        addGlobalAccountIdentifierAlong(accountIdentifier), identifiers);
  }

  @Override
  public CheckStatsResponse getCheckStats(String accountIdentifier, String identifier, Boolean custom) {
    CheckEntity checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(
        custom ? accountIdentifier : GLOBAL_ACCOUNT_ID, identifier);
    if (checkEntity == null) {
      throw new InvalidRequestException(String.format("Check stats not found for checkId [%s]", identifier));
    }
    List<CheckStatsEntity> checkStatsEntities =
        checkStatsRepository.findByAccountIdentifierAndCheckIdentifierAndIsCustom(
            accountIdentifier, identifier, custom);
    return CheckStatsMapper.toDTO(checkStatsEntities, checkEntity.getName());
  }

  @Override
  public List<CheckGraph> getCheckGraph(String accountIdentifier, String identifier, Boolean custom) {
    CheckEntity checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(
        custom ? accountIdentifier : GLOBAL_ACCOUNT_ID, identifier);
    if (checkEntity == null) {
      throw new InvalidRequestException(String.format("Check graph not found for checkId [%s]", identifier));
    }
    return CheckStatsMapper.toDTO(
        checkStatusRepository.findByAccountIdentifierAndIdentifierAndIsCustom(accountIdentifier, identifier, custom));
  }

  @Override
  public Map<String, CheckStatusEntity> getCheckStatusByAccountIdAndIdentifiers(
      String accountIdentifier, List<String> identifiers) {
    List<CheckStatusEntityByIdentifier> lastComputedCheckStatus =
        checkStatusRepository.findByAccountIdentifierAndIdentifierIn(accountIdentifier, identifiers);
    return lastComputedCheckStatus.stream().collect(Collectors.toMap(checkStatusEntityByIdentifier
        -> (checkStatusEntityByIdentifier.isCustom() ? accountIdentifier : GLOBAL_ACCOUNT_ID) + DOT_SEPARATOR
            + checkStatusEntityByIdentifier.getIdentifier(),
        CheckStatusEntityByIdentifier::getCheckStatusEntity));
  }

  @Override
  public void deleteCustomCheck(String accountIdentifier, String identifier, boolean forceDelete) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      if (forceDelete && !isForceDeleteSettingEnabled(accountIdentifier)) {
        throw new InvalidRequestException(
            format("Parameter forceDelete cannot be true. Force deletion of check is not enabled for this account [%s]",
                accountIdentifier));
      }
      if (!forceDelete) {
        validateCheckUsage(accountIdentifier, identifier);
      }

      CheckEntity oldCheckEntity = checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
      outboxService.save(new CheckDeleteEvent(accountIdentifier, CheckDetailsMapper.toDTO(oldCheckEntity, null)));

      UpdateResult updateResult = checkRepository.updateDeleted(accountIdentifier, identifier);
      if (updateResult.getModifiedCount() == 0) {
        throw new InvalidRequestException("Default checks cannot be deleted");
      }
      return true;
    }));
  }

  private void validateCheckUsage(String accountIdentifier, String checkIdentifier) {
    List<String> scorecardIdentifiers =
        scorecardService.getScorecardIdentifiers(accountIdentifier, checkIdentifier, true);
    if (!isEmpty(scorecardIdentifiers)) {
      throw new ReferencedEntityException(
          format("Could not delete the check [%s] as it is referenced by other scorecards", checkIdentifier));
    }
  }

  protected boolean isForceDeleteSettingEnabled(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }

  private Criteria buildCriteriaForChecksList(String accountIdentifier, Boolean custom, String searchTerm) {
    Criteria criteria = new Criteria();
    if (custom == null) {
      criteria.and(CheckEntity.CheckKeys.accountIdentifier).in(addGlobalAccountIdentifierAlong(accountIdentifier));
    } else {
      String accountId = custom ? accountIdentifier : GLOBAL_ACCOUNT_ID;
      criteria.and(CheckEntity.CheckKeys.accountIdentifier)
          .is(accountId)
          .and(CheckEntity.CheckKeys.isCustom)
          .is(custom);
    }

    if (isNotEmpty(searchTerm)) {
      criteria.andOperator(buildSearchCriteria(searchTerm));
    }

    criteria.and(CheckEntity.CheckKeys.isDeleted).is(false);
    return criteria;
  }

  private Criteria buildSearchCriteria(String searchTerm) {
    return new Criteria().orOperator(
        where(CheckEntity.CheckKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        where(CheckEntity.CheckKeys.identifier)
            .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        where(CheckEntity.CheckKeys.tags).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
  }

  private void validateCheckSaveRequest(CheckDetails checkDetails, String accountIdentifier) {
    Map<String, DataPoint> dataPointMap = dataPointService.getDataPointsMap(accountIdentifier);
    for (Rule rule : checkDetails.getRules()) {
      String key = rule.getDataSourceIdentifier() + DOT_SEPARATOR + rule.getDataPointIdentifier();
      if (!dataPointMap.containsKey(key)) {
        throw new InvalidRequestException(format("Data point not found for dataSource: %s, dataPoint: %s",
            rule.getDataSourceIdentifier(), rule.getDataPointIdentifier()));
      }

      // TODO: Remove this condition once UI sends in new format
      if (rule.getInputValues() != null) {
        DataPoint dataPoint = dataPointMap.get(key);
        List<InputValue> inputValues = rule.getInputValues();
        for (InputValue inputValue : inputValues) {
          Optional<InputDetails> inputDetailsOpt =
              dataPoint.getInputDetails()
                  .stream()
                  .filter(inputDetails -> inputDetails.getKey().equals(inputValue.getKey()))
                  .findFirst();
          if (inputDetailsOpt.isEmpty()) {
            throw new InvalidRequestException(String.format(
                "Conditional input value for key %s does not match any data point input details", inputValue.getKey()));
          }
          String value = inputValue.getValue();
          if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
          }
          if (inputDetailsOpt.get().isRequired() && isEmpty(value)) {
            throw new InvalidRequestException(
                String.format("Conditional input value for key %s is required", inputValue.getKey()));
          }
        }
      }
    }
  }

  private boolean isCheckAlreadyDeleted(String accountIdentifier, String checkId) {
    CheckEntity checkEntity =
        checkRepository.findByAccountIdentifierAndIdentifierAndIsDeleted(accountIdentifier, checkId, true);
    return checkEntity != null;
  }

  private void generateRuleIdentifiersIfNotPresent(CheckDetails checkDetails) {
    for (Rule rule : checkDetails.getRules()) {
      if (StringUtils.isBlank(rule.getIdentifier())) {
        rule.setIdentifier(UUID.randomUUID().toString());
      }
    }
  }
}
