/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.template;

import static io.harness.cvng.core.services.CVNextGenConstants.ACCOUNT_IDENTIFIER_PREFIX;
import static io.harness.cvng.core.services.CVNextGenConstants.ORG_IDENTIFIER_PREFIX;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

@Singleton
public class TemplateFacade {
  private static final String TEMPLATE_KEY = "template";

  @Inject private TemplateResourceClient templateResourceClient;

  public String resolveYaml(ProjectParams projectParams, String yaml) {
    TemplateMergeResponseDTO templateMergeResponseDTO =
        NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), null, null, null, null, null, null,
            null, null, BOOLEAN_FALSE_VALUE,
            TemplateApplyRequestDTO.builder().originalEntityYaml(yaml).checkForAccess(false).build(), false));
    String resolvedYaml = templateMergeResponseDTO.getMergedPipelineYaml();
    return getResolvedYamlWithInputTemplateMerged(resolvedYaml, yaml);
  }

  private String getResolvedYamlWithInputTemplateMerged(String resolvedYaml, String inputYaml) {
    Yaml yamlObject = new Yaml();
    Map<String, Object> data = yamlObject.load(resolvedYaml);
    Map<String, Object> monitoredServiceData =
        (Map<String, Object>) data.get(CVNextGenConstants.MONITORED_SERVICE_YAML_ROOT);
    Map<String, Object> inputData = yamlObject.load(inputYaml);
    Map<String, Object> inputMonitoredServiceData =
        (Map<String, Object>) inputData.get(CVNextGenConstants.MONITORED_SERVICE_YAML_ROOT);
    if (inputMonitoredServiceData.containsKey(TEMPLATE_KEY)) {
      monitoredServiceData.put(TEMPLATE_KEY, inputMonitoredServiceData.get(TEMPLATE_KEY));
    }
    return yamlObject.dump(data);
  }

  public Long getTemplateVersionNumber(ProjectParams projectParams, String templateRef, String versionLabel) {
    projectParams = getTemplateProjectParams(projectParams, templateRef);
    String templateRefExtracted = extractTemplateRef(templateRef);
    return NGRestUtils
        .getResponse(templateResourceClient.get(templateRefExtracted, projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), versionLabel, false))
        .getVersion();
  }

  public String getTemplateInputs(ProjectParams projectParams, String templateRef, String versionLabel) {
    projectParams = getTemplateProjectParams(projectParams, templateRef);
    String templateRefExtracted = extractTemplateRef(templateRef);
    return NGRestUtils.getResponse(
        templateResourceClient.getTemplateInputsYaml(templateRefExtracted, projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), versionLabel, false));
  }

  private ProjectParams getTemplateProjectParams(ProjectParams projectParams, String templateRef) {
    String accountIdentifier = projectParams.getAccountIdentifier();
    String orgIdentifier = projectParams.getOrgIdentifier();
    String projectIdentifier = projectParams.getProjectIdentifier();
    if (templateRef.contains(ACCOUNT_IDENTIFIER_PREFIX)) {
      orgIdentifier = null;
      projectIdentifier = null;
    } else if (templateRef.contains(ORG_IDENTIFIER_PREFIX)) {
      projectIdentifier = null;
    }
    return ProjectParams.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private String extractTemplateRef(String templateRef) {
    if (templateRef.contains(ACCOUNT_IDENTIFIER_PREFIX)) {
      templateRef = templateRef.replace(ACCOUNT_IDENTIFIER_PREFIX, "");
    } else if (templateRef.contains(ORG_IDENTIFIER_PREFIX)) {
      templateRef = templateRef.replace(ORG_IDENTIFIER_PREFIX, "");
    }
    return templateRef;
  }
}
