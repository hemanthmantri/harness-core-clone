/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services.exemption;

import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.OperatorEntity;
import io.harness.ssca.entities.exemption.Exemption;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class ExemptionHelper {
  private static final String COMPONENT_KEY_DELIMITER = ",";
  public String getUniqueComponentKeyFromEnforcementResultEntity(EnforcementResultEntity entity) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(entity.getName());
    if (StringUtils.isNotBlank(entity.getVersion())) {
      stringBuilder.append(COMPONENT_KEY_DELIMITER).append(entity.getVersion());
    }
    return stringBuilder.toString();
  }

  public Map<String, String> getExemptedComponents(Set<String> uniqueComponents, List<Exemption> exemptions) {
    Map<String, String> exemptedComponents = new HashMap<>();
    for (String uniqueComponent : uniqueComponents) {
      String[] componentKeyAttributes = uniqueComponent.split(COMPONENT_KEY_DELIMITER);
      String componentName = componentKeyAttributes[0];
      String componentVersion = null;
      if (componentKeyAttributes.length == 2) {
        componentVersion = componentKeyAttributes[1];
      }
      for (Exemption exemption : exemptions) {
        if (isComponentExempted(componentName, componentVersion, exemption)) {
          exemptedComponents.put(uniqueComponent, exemption.getUuid());
          break;
        }
      }
    }
    return exemptedComponents;
  }

  private static boolean isComponentExempted(String componentName, String componentVersion, Exemption exemption) {
    return exemption.getComponentName().equals(componentName)
        && isVersionExempted(componentVersion, exemption.getComponentVersion(), exemption.getVersionOperator());
  }

  private static boolean isVersionExempted(
      String componentVersion, String exemptionVersion, OperatorEntity operatorEntity) {
    if (StringUtils.isBlank(exemptionVersion)) {
      return true;
    }
    if (StringUtils.isBlank(componentVersion)) {
      return false;
    }
    // TODO: Add logic to compare semantic and non-semantic versions using operatorEntity
    return false;
  }
}