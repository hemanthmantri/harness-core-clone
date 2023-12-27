/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.entities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.licensing.Edition;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingPlanConfig;
import io.harness.ngsettings.SettingValueType;

import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PL)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "AccountSettingConfigurationKeys")
@Persistent
@TypeAlias("NGSettingConfiguration")
@EqualsAndHashCode(callSuper = true)
public class AccountSettingConfiguration extends SettingConfiguration {
  String defaultValue;
  @NotNull SettingValueType valueType;
  Set<String> allowedValues;
  Boolean allowOverrides;

  @Builder
  public AccountSettingConfiguration(String id, String identifier, String name, SettingCategory category,
      String groupIdentifier, String defaultValue, SettingValueType valueType, Set<String> allowedValues,
      Boolean allowOverrides, Set<ScopeLevel> allowedScopes, Map<Edition, SettingPlanConfig> allowedPlans) {
    super(id, identifier, name, category, groupIdentifier, allowedScopes, allowedPlans);
    this.defaultValue = defaultValue;
    this.valueType = valueType;
    this.allowedValues = allowedValues;
    this.allowOverrides = allowOverrides;
  }
}