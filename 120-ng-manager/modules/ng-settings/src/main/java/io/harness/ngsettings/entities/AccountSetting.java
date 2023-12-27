/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.entities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingValueType;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PL)
@Getter
@FieldNameConstants(innerTypeName = "AccountSettingKeys")
@Persistent
@TypeAlias("NGSetting")
@EqualsAndHashCode(callSuper = true)
public class AccountSetting extends Setting {
  @NotNull Boolean allowOverrides;
  @NotNull SettingValueType valueType;
  @NotNull String value;

  @Builder
  public AccountSetting(String id, String identifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SettingCategory category, String groupIdentifier, Long lastModifiedAt,
      Boolean allowOverrides, SettingValueType valueType, String value) {
    super(
        id, identifier, accountIdentifier, orgIdentifier, projectIdentifier, category, groupIdentifier, lastModifiedAt);
    this.allowOverrides = allowOverrides;
    this.valueType = valueType;
    this.value = value;
  }
}