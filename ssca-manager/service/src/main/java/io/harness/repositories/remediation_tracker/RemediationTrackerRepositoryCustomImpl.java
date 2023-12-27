/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.remediation_tracker;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.entities.remediation_tracker.RemediationTrackerEntity;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(HarnessTeam.SSCA)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class RemediationTrackerRepositoryCustomImpl implements RemediationTrackerRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public RemediationTrackerEntity update(Query query, Update update) {
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true), RemediationTrackerEntity.class);
  }

  @Override
  public Page<RemediationTrackerEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<RemediationTrackerEntity> artifactEntities = mongoTemplate.find(query, RemediationTrackerEntity.class);
    return PageableExecutionUtils.getPage(artifactEntities, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), RemediationTrackerEntity.class));
  }
}
