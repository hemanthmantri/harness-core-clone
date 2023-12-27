/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class AccountOrgProjectValidator {
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final AccountClient accountClient;

  public boolean isPresent(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    boolean isProjectIdentifierPresent = isNotEmpty(projectIdentifier);
    boolean isOrgIdentifierEmpty = isEmpty(orgIdentifier);
    if (isProjectIdentifierPresent && isOrgIdentifierEmpty) {
      orgIdentifier = DEFAULT_ORG_IDENTIFIER;
    }

    if (isEmpty(accountIdentifier) || isEmpty(orgIdentifier)) {
      return true;
    } else if (isEmpty(projectIdentifier)) {
      return organizationService.get(accountIdentifier, orgIdentifier).isPresent();
    } else {
      return projectService.get(accountIdentifier, orgIdentifier, projectIdentifier).isPresent();
    }
  }
}
