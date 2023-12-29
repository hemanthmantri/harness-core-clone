/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.serviceoverridesv2.custom;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SCM_BAD_REQUEST;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.ScmException;
import io.harness.exception.WingsException;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.utils.GitXUtils;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class ServiceOverrideRepositoryCustomV2Impl implements ServiceOverrideRepositoryCustomV2 {
  private final MongoTemplate mongoTemplate;
  private final GitAwareEntityHelper gitAwareEntityHelper;

  @Override
  public NGServiceOverridesEntity update(Criteria criteria, NGServiceOverridesEntity serviceOverridesEntity) {
    Query query = new Query(criteria);
    Update updateOperations =
        ServiceOverrideRepositoryHelper.getUpdateOperationsForServiceOverrideV2(serviceOverridesEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed updating Service Override Entity; attempt: {}",
        "[Failed]: Failed updating Service Override Entity; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(query, updateOperations, new FindAndModifyOptions().returnNew(true),
                     NGServiceOverridesEntity.class));
  }

  @Override
  public DeleteResult delete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed deleting Service Override; attempt: {}",
        "[Failed]: Failed deleting Service Override; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, NGServiceOverridesEntity.class));
  }

  @Override
  public Page<NGServiceOverridesEntity> findAll(Criteria criteria, Pageable pageRequest) {
    Query query = new Query(criteria).with(pageRequest);
    List<NGServiceOverridesEntity> overridesEntities = mongoTemplate.find(query, NGServiceOverridesEntity.class);
    return PageableExecutionUtils.getPage(overridesEntities, pageRequest,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NGServiceOverridesEntity.class));
  }

  @Override
  public List<NGServiceOverridesEntity> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, NGServiceOverridesEntity.class);
  }

  @Override
  public NGServiceOverridesEntity saveGitAware(NGServiceOverridesEntity overrideToSave) {
    if (GitAwareContextHelper.isRemoteEntity()) {
      createOverridesOnGit(overrideToSave);
    }

    // save in db
    return mongoTemplate.save(overrideToSave);
  }

  @Override
  public NGServiceOverridesEntity getRemoteOverridesWithYaml(
      NGServiceOverridesEntity savedEntity, boolean loadFromCache, boolean loadFromFallbackBranch) {
    try {
      String branchName = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepo());
      if (loadFromFallbackBranch) {
        savedEntity = fetchRemoteEntityWithFallBackBranch(savedEntity.getAccountId(), savedEntity.getOrgIdentifier(),
            savedEntity.getProjectIdentifier(), savedEntity, branchName, loadFromCache);
      } else {
        savedEntity = fetchRemoteEntity(savedEntity.getAccountId(), savedEntity.getOrgIdentifier(),
            savedEntity.getProjectIdentifier(), savedEntity, branchName, loadFromCache);
      }

      return savedEntity;
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while retrieving overrides YAML: [%s]", savedEntity.getIdentifier()), e);
      throw e;
    } catch (Exception e) {
      log.error(
          String.format("Unexpected error occurred while retrieving overrides YAML: [%s]", savedEntity.getIdentifier()),
          e);
      throw new InternalServerErrorException(String.format(
          "Unexpected error occurred while retrieving overrides YAML: [%s]", savedEntity.getIdentifier()));
    }
  }

  private NGServiceOverridesEntity fetchRemoteEntityWithFallBackBranch(String accountId, String orgIdentifier,
      String projectIdentifier, NGServiceOverridesEntity savedEntity, String branch, boolean loadFromCache) {
    try {
      savedEntity = fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, branch, loadFromCache);
    } catch (WingsException ex) {
      log.warn(String.format("Error occurred while retrieving overrides YAML: [%s] from branch: [%s]",
                   savedEntity.getIdentifier(), branch),
          ex);
      String fallBackBranch = savedEntity.getFallBackBranch();
      GitAwareContextHelper.setIsDefaultBranchInGitEntityInfoWithParameter(savedEntity.getFallBackBranch());
      if (shouldRetryWithFallBackBranch(GitXUtils.getScmExceptionIfExists(ex), branch, fallBackBranch)) {
        log.info(String.format(
            "Retrieving overrides: [%s] from fall back branch [%s] ", savedEntity.getIdentifier(), fallBackBranch));
        savedEntity =
            fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, fallBackBranch, loadFromCache);
      } else {
        throw ex;
      }
    }
    return savedEntity;
  }

  private NGServiceOverridesEntity fetchRemoteEntity(String accountId, String orgIdentifier, String projectIdentifier,
      NGServiceOverridesEntity savedEntity, String branchName, boolean loadFromCache) {
    return (NGServiceOverridesEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
        Scope.of(accountId, orgIdentifier, projectIdentifier),
        GitContextRequestParams.builder()
            .branchName(branchName)
            .connectorRef(savedEntity.getConnectorRef())
            .filePath(savedEntity.getFilePath())
            .repoName(savedEntity.getRepo())
            .entityType(EntityType.OVERRIDES)
            .loadFromCache(loadFromCache)
            .build(),
        Collections.emptyMap());
  }

  boolean shouldRetryWithFallBackBranch(ScmException scmException, String branchTried, String overridesFallbackBranch) {
    return scmException != null && SCM_BAD_REQUEST.equals(scmException.getCode())
        && (isNotEmpty(overridesFallbackBranch) && !branchTried.equals(overridesFallbackBranch));
  }

  private void createOverridesOnGit(NGServiceOverridesEntity overrideToSave) {
    Scope scope = Scope.of(
        overrideToSave.getAccountId(), overrideToSave.getOrgIdentifier(), overrideToSave.getProjectIdentifier());
    String yamlToPush = overrideToSave.getYamlV2();
    gitAwareEntityHelper.createEntityOnGit(overrideToSave, yamlToPush, scope);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }
}
