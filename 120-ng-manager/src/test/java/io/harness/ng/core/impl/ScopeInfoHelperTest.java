/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.beans.ScopeLevel.ACCOUNT;
import static io.harness.beans.ScopeLevel.ORGANIZATION;
import static io.harness.beans.ScopeLevel.PROJECT;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ScopeInfoHelperTest {
  ScopeInfoHelper scopeInfoHelper = new ScopeInfoHelper();
  String accountIdentifier = randomAlphabetic(10);
  String orgUniqueId = randomAlphabetic(10);
  String orgIdentifier = randomAlphabetic(10);
  String projectUniqueId = randomAlphabetic(10);
  String projectIdentifier = randomAlphabetic(10);

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPopulateScopeInfo() {
    // Account scope
    ScopeInfo scopeInfo = scopeInfoHelper.populateScopeInfo(ACCOUNT, accountIdentifier, accountIdentifier, null, null);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ACCOUNT);
    assertThat(scopeInfo.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getOrgIdentifier()).isNull();
    assertThat(scopeInfo.getProjectIdentifier()).isNull();

    scopeInfo = scopeInfoHelper.populateScopeInfo(ACCOUNT, accountIdentifier, accountIdentifier, orgIdentifier, null);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ACCOUNT);
    assertThat(scopeInfo.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getOrgIdentifier()).isNull();
    assertThat(scopeInfo.getProjectIdentifier()).isNull();

    scopeInfo = scopeInfoHelper.populateScopeInfo(
        ACCOUNT, accountIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ACCOUNT);
    assertThat(scopeInfo.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getOrgIdentifier()).isNull();
    assertThat(scopeInfo.getProjectIdentifier()).isNull();

    scopeInfo =
        scopeInfoHelper.populateScopeInfo(ACCOUNT, accountIdentifier, accountIdentifier, null, projectIdentifier);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ACCOUNT);
    assertThat(scopeInfo.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getOrgIdentifier()).isNull();
    assertThat(scopeInfo.getProjectIdentifier()).isNull();

    // Organization scope
    scopeInfo = scopeInfoHelper.populateScopeInfo(ORGANIZATION, orgUniqueId, accountIdentifier, orgIdentifier, null);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ORGANIZATION);
    assertThat(scopeInfo.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(orgUniqueId);
    assertThat(scopeInfo.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(scopeInfo.getProjectIdentifier()).isNull();

    scopeInfo = scopeInfoHelper.populateScopeInfo(
        ORGANIZATION, orgUniqueId, accountIdentifier, orgIdentifier, projectIdentifier);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ORGANIZATION);
    assertThat(scopeInfo.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(orgUniqueId);
    assertThat(scopeInfo.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(scopeInfo.getProjectIdentifier()).isNull();

    // Project scope
    scopeInfo = scopeInfoHelper.populateScopeInfo(
        PROJECT, projectUniqueId, accountIdentifier, orgIdentifier, projectIdentifier);

    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(PROJECT);
    assertThat(scopeInfo.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(projectUniqueId);
    assertThat(scopeInfo.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(scopeInfo.getProjectIdentifier()).isEqualTo(projectIdentifier);
  }
}
