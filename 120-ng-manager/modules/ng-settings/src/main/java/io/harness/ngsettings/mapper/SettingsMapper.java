/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.mapper;

import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.entities.AccountSetting;
import io.harness.ngsettings.entities.AccountSettingConfiguration;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.utils.SettingUtils;

public class SettingsMapper {
  public SettingDTO writeSettingDTO(AccountSetting accountSetting,
      AccountSettingConfiguration accountSettingConfiguration, Boolean isSettingEditable, String defaultValue) {
    return SettingDTO.builder()
        .identifier(accountSetting.getIdentifier())
        .name(accountSettingConfiguration.getName())
        .orgIdentifier(accountSetting.getOrgIdentifier())
        .projectIdentifier(accountSetting.getProjectIdentifier())
        .allowedValues(accountSettingConfiguration.getAllowedValues())
        .allowOverrides(accountSetting.getAllowOverrides())
        .category(accountSettingConfiguration.getCategory())
        .groupIdentifier(accountSettingConfiguration.getGroupIdentifier())
        .valueType(accountSettingConfiguration.getValueType())
        .defaultValue(defaultValue)
        .value(accountSetting.getValue())
        .settingSource(SettingUtils.getSettingSource(accountSetting))
        .isSettingEditable(isSettingEditable)
        .allowedScopes(accountSettingConfiguration.getAllowedScopes())
        .build();
  }

  public SettingDTO writeSettingDTO(
      AccountSetting setting, AccountSettingConfiguration accountSettingConfiguration, Boolean isSettingEditable) {
    return SettingDTO.builder()
        .identifier(setting.getIdentifier())
        .name(accountSettingConfiguration.getName())
        .orgIdentifier(setting.getOrgIdentifier())
        .projectIdentifier(setting.getProjectIdentifier())
        .allowedValues(accountSettingConfiguration.getAllowedValues())
        .allowOverrides(setting.getAllowOverrides())
        .category(accountSettingConfiguration.getCategory())
        .groupIdentifier(accountSettingConfiguration.getGroupIdentifier())
        .valueType(accountSettingConfiguration.getValueType())
        .defaultValue(setting.getValue())
        .value(setting.getValue())
        .settingSource(SettingUtils.getSettingSource(setting))
        .isSettingEditable(isSettingEditable)
        .allowedScopes(accountSettingConfiguration.getAllowedScopes())
        .build();
  }

  public SettingDTO writeSettingDTO(
      AccountSettingConfiguration accountSettingConfiguration, Boolean isSettingEditable, String defaultValue) {
    return SettingDTO.builder()
        .identifier(accountSettingConfiguration.getIdentifier())
        .name(accountSettingConfiguration.getName())
        .category(accountSettingConfiguration.getCategory())
        .groupIdentifier(accountSettingConfiguration.getGroupIdentifier())
        .valueType(accountSettingConfiguration.getValueType())
        .defaultValue(defaultValue)
        .value(defaultValue)
        .allowedValues(accountSettingConfiguration.getAllowedValues())
        .allowOverrides(accountSettingConfiguration.getAllowOverrides())
        .settingSource(SettingSource.DEFAULT)
        .isSettingEditable(isSettingEditable)
        .allowedScopes(accountSettingConfiguration.getAllowedScopes())
        .build();
  }

  public SettingResponseDTO writeSettingResponseDTO(AccountSetting accountSetting,
      SettingConfiguration settingConfiguration, Boolean isSettingEditable, String defaultValue) {
    return SettingResponseDTO.builder()
        .setting(writeSettingDTO(
            accountSetting, (AccountSettingConfiguration) settingConfiguration, isSettingEditable, defaultValue))
        .lastModifiedAt(accountSetting.getLastModifiedAt())
        .build();
  }

  public SettingResponseDTO writeSettingResponseDTO(
      Setting setting, SettingConfiguration settingConfiguration, Boolean isSettingEditable) {
    return SettingResponseDTO.builder()
        .setting(writeSettingDTO(
            (AccountSetting) setting, (AccountSettingConfiguration) settingConfiguration, isSettingEditable))
        .lastModifiedAt(setting.getLastModifiedAt())
        .build();
  }

  public SettingResponseDTO writeSettingResponseDTO(
      SettingConfiguration settingConfiguration, Boolean isSettingEditable, String defaultValue) {
    return SettingResponseDTO.builder()
        .setting(writeSettingDTO((AccountSettingConfiguration) settingConfiguration, isSettingEditable, defaultValue))
        .build();
  }

  public SettingDTO writeNewDTO(AccountSetting setting, SettingRequestDTO settingRequestDTO,
      AccountSettingConfiguration accountSettingConfiguration, Boolean isSettingEditable, String defaultValue) {
    return SettingDTO.builder()
        .identifier(setting.getIdentifier())
        .name(accountSettingConfiguration.getName())
        .orgIdentifier(setting.getOrgIdentifier())
        .projectIdentifier(setting.getProjectIdentifier())
        .allowedValues(accountSettingConfiguration.getAllowedValues())
        .allowOverrides(settingRequestDTO.getAllowOverrides())
        .category(accountSettingConfiguration.getCategory())
        .groupIdentifier(accountSettingConfiguration.getGroupIdentifier())
        .value(settingRequestDTO.getValue())
        .valueType(accountSettingConfiguration.getValueType())
        .defaultValue(defaultValue)
        .isSettingEditable(isSettingEditable)
        .settingSource(SettingUtils.getSettingSource(setting))
        .allowedScopes(accountSettingConfiguration.getAllowedScopes())
        .build();
  }

  public SettingDTO writeNewDTO(String orgIdentifier, String projectIdentifier, SettingRequestDTO settingRequestDTO,
      AccountSettingConfiguration settingConfiguration, Boolean isSettingEditable, String defaultValue) {
    return SettingDTO.builder()
        .identifier(settingConfiguration.getIdentifier())
        .name(settingConfiguration.getName())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .allowedValues(settingConfiguration.getAllowedValues())
        .allowOverrides(settingRequestDTO.getAllowOverrides())
        .category(settingConfiguration.getCategory())
        .groupIdentifier(settingConfiguration.getGroupIdentifier())
        .value(settingRequestDTO.getValue())
        .valueType(settingConfiguration.getValueType())
        .defaultValue(defaultValue)
        .isSettingEditable(isSettingEditable)
        .settingSource(SettingUtils.getSettingSourceFromOrgAndProject(orgIdentifier, projectIdentifier))
        .allowedScopes(settingConfiguration.getAllowedScopes())
        .build();
  }

  public SettingUpdateResponseDTO writeBatchResponseDTO(SettingResponseDTO responseDTO) {
    return SettingUpdateResponseDTO.builder()
        .updateStatus(true)
        .identifier(responseDTO.getSetting().getIdentifier())
        .setting(responseDTO.getSetting())
        .lastModifiedAt(responseDTO.getLastModifiedAt())
        .build();
  }

  public SettingUpdateResponseDTO writeBatchResponseDTO(String identifier, Exception exception) {
    return SettingUpdateResponseDTO.builder()
        .updateStatus(false)
        .identifier(identifier)
        .errorMessage(exception.getMessage())
        .build();
  }

  public AccountSetting toSetting(String accountIdentifier, SettingDTO settingDTO) {
    return AccountSetting.builder()
        .identifier(settingDTO.getIdentifier())
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(settingDTO.getOrgIdentifier())
        .projectIdentifier(settingDTO.getProjectIdentifier())
        .category(settingDTO.getCategory())
        .groupIdentifier(settingDTO.getGroupIdentifier())
        .allowOverrides(settingDTO.getAllowOverrides())
        .value(settingDTO.getValue())
        .valueType(settingDTO.getValueType())
        .build();
  }
}