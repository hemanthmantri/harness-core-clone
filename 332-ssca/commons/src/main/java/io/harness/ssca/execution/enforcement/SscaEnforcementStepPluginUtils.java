/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.enforcement.EnforcementStepEnvVariables;
import io.harness.ssca.beans.enforcement.EnforcementStepSecretVariables;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
public class SscaEnforcementStepPluginUtils {
  public static final String PLUGIN_SBOMSOURCE = "PLUGIN_SBOMSOURCE";
  public static final String PLUGIN_TYPE = "PLUGIN_TYPE";
  public static final String STEP_EXECUTION_ID = "STEP_EXECUTION_ID";
  public static final String POLICY_FILE_IDENTIFIER = "POLICY_FILE_IDENTIFIER";
  public static final String SSCA_MANAGER_ENABLED = "SSCA_MANAGER_ENABLED";

  public static final String COSIGN_PUBLIC_KEY = "COSIGN_PUBLIC_KEY";
  public static final String POLICY_SET_REF = "POLICY_SET_REF";
  public static final String PLUGIN_BASE64_SECRET = "PLUGIN_BASE64_SECRET";
  public static final String ENABLE_SSCA_AIRGAP = "ENABLE_SSCA_AIRGAP";

  public Map<String, String> getSscaEnforcementStepEnvVariables(EnforcementStepEnvVariables envVariables) {
    Map<String, String> envMap = new HashMap<>();
    envMap.put(STEP_EXECUTION_ID, envVariables.getStepExecutionId());
    envMap.put(PLUGIN_SBOMSOURCE, envVariables.getSbomSource());
    envMap.put(PLUGIN_TYPE, "Enforce");
    envMap.put(SSCA_MANAGER_ENABLED, String.valueOf(envVariables.isSscaManagerEnabled()));
    if (envVariables.getHarnessPolicyFileId() != null) {
      envMap.put(POLICY_FILE_IDENTIFIER, envVariables.getHarnessPolicyFileId());
    }
    if (envVariables.getPolicySetRef() != null) {
      envMap.put(POLICY_SET_REF, envVariables.getPolicySetRef());
    }
    envMap.put(PLUGIN_BASE64_SECRET, String.valueOf(envVariables.isBase64SecretAttestation()));
    envMap.put(ENABLE_SSCA_AIRGAP, String.valueOf(envVariables.isAirgapEnabled()));
    return envMap;
  }

  public Map<String, SecretNGVariable> getSscaEnforcementSecretVars(EnforcementStepSecretVariables secretVariables) {
    Map<String, SecretNGVariable> secretNGVariableMap = new HashMap<>();
    if (secretVariables == null) {
      return secretNGVariableMap;
    }
    if (secretVariables.getCosignPublicKey() != null) {
      SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretVariables.getCosignPublicKey());
      secretNGVariableMap.put(COSIGN_PUBLIC_KEY,
          SecretNGVariable.builder()
              .type(NGVariableType.SECRET)
              .value(ParameterField.createValueField(secretRefData))
              .name(COSIGN_PUBLIC_KEY)
              .build());
    }
    return secretNGVariableMap;
  }
}
