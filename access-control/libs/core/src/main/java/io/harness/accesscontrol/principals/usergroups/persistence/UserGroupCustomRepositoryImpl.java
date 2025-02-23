/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.usergroups.persistence;

import io.harness.annotation.HarnessRepo;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@HarnessRepo
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class UserGroupCustomRepositoryImpl implements UserGroupCustomRepository {
  private MongoTemplate mongoTemplate;
  @Override
  public List<UserGroupDBO> find(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, UserGroupDBO.class);
  }
}
