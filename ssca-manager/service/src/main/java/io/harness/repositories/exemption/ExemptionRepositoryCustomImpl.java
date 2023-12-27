/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.exemption;

import io.harness.ssca.entities.exemption.Exemption;
import io.harness.ssca.entities.exemption.Exemption.ExemptionKeys;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class ExemptionRepositoryCustomImpl implements ExemptionRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  @Override
  public Page<Exemption> findExemptions(Criteria criteria, Pageable pageable) {
    Query query = getQueryWithDefaultSorting(criteria).with(pageable);
    List<Exemption> exemptions = mongoTemplate.find(query, Exemption.class);
    return PageableExecutionUtils.getPage(
        exemptions, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Exemption.class));
  }

  @Override
  public List<Exemption> findExemptions(Criteria criteria) {
    return mongoTemplate.find(getQueryWithDefaultSorting(criteria), Exemption.class);
  }

  @Override
  public Exemption createExemption(Exemption exemptionRequest) {
    if (StringUtils.isBlank(exemptionRequest.getUuid())) {
      exemptionRequest.setUuid(UUID.randomUUID().toString());
    }
    if (Objects.isNull(exemptionRequest.getCreatedAt())) {
      exemptionRequest.setCreatedAt(System.currentTimeMillis());
    }
    return mongoTemplate.save(exemptionRequest);
  }

  private static Query getQueryWithDefaultSorting(Criteria criteria) {
    return new Query().addCriteria(criteria).with(Sort.by(ExemptionKeys.createdAt));
  }
}
