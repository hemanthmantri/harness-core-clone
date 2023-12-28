/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.SSCA)
public class NgSettingsUtils {
  @Inject private NGSettingsClient settingsClient;

  public boolean getBaseEncodingEnabled(String accountIdentifer, String orgIdentifier, String projectIdentifier) {
    boolean useBase64SecretForAttestation = false;
    try {
      useBase64SecretForAttestation = Boolean.parseBoolean(
          NGRestUtils
              .getResponse(settingsClient.getSetting(SettingIdentifiers.USE_BASE64_ENCODED_SECRETS_FOR_ATTESTATION,
                  accountIdentifer, orgIdentifier, projectIdentifier))
              .getValue());
    } catch (Exception e) {
      log.error(
          String.format("Could not fetch the default config for base64 secret encoding, reverting to default", e));
    }
    return useBase64SecretForAttestation;
  }

  public boolean getAirgapEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    boolean enabledAirgap = false;
    try {
      enabledAirgap =
          Boolean.parseBoolean(NGRestUtils
                                   .getResponse(settingsClient.getSetting(SettingIdentifiers.ENABLE_SSCA_AIRGAP,
                                       accountIdentifier, orgIdentifier, projectIdentifier))
                                   .getValue());
    } catch (Exception e) {
      log.error(String.format("Could not fetch the default config for SSCA airgap, reverting to default", e));
    }
    return enabledAirgap;
  }
}
