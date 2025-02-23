/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.tasks.uniqueid;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.entities.Organization;

import com.google.inject.Inject;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PL)
public class AddUniqueIdToOrganizationMigration
    extends AddUniqueIdToNGEntitiesMigration<Organization> implements NGMigration {
  @Inject
  public AddUniqueIdToOrganizationMigration(MongoTemplate mongoTemplate) {
    super(mongoTemplate);
  }
}
