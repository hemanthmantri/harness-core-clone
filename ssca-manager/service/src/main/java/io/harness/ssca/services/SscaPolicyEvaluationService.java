/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.ssca.v1.model.EnforceSbomRequestBody;
import io.harness.ssca.beans.PolicyEvaluationResult;
import io.harness.ssca.beans.RuleDTO;
import io.harness.ssca.enforcement.ExecutorRegistry;
import io.harness.ssca.enforcement.constants.RuleExecutorType;
import io.harness.ssca.enforcement.rule.Engine;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.artifact.ArtifactEntity;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.SSCA)
@Slf4j
public class SscaPolicyEvaluationService implements PolicyEvaluationService {
  @Inject ExecutorRegistry executorRegistry;
  @Inject RuleEngineService ruleEngineService;
  @Override
  public PolicyEvaluationResult evaluatePolicy(String accountId, String orgIdentifier, String projectIdentifier,
      EnforceSbomRequestBody body, ArtifactEntity artifactEntity) {
    if (StringUtils.isBlank(body.getPolicyFileId())) {
      throw new BadRequestException("policy_file_id must not be blank");
    }
    log.info("Evaluating policy violations using ssca policies for accountId: {} enforcementId: {}", accountId,
        body.getEnforcementId());
    RuleDTO ruleDTO = ruleEngineService.getRules(accountId, orgIdentifier, projectIdentifier, body.getPolicyFileId());

    Engine engine = Engine.builder()
                        .artifact(artifactEntity)
                        .enforcementId(body.getEnforcementId())
                        .executorRegistry(executorRegistry)
                        .executorType(RuleExecutorType.MONGO_EXECUTOR)
                        .rules(ruleDTO.getDenyList())
                        .build();

    List<EnforcementResultEntity> denyListResult = engine.executeRules();

    engine.setRules(ruleDTO.getAllowList());
    List<EnforcementResultEntity> allowListResult = engine.executeRules();
    return PolicyEvaluationResult.builder()
        .denyListViolations(denyListResult)
        .allowListViolations(allowListResult)
        .build();
  }
}
