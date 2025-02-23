/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.manifest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.InheritFromManifestStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.ngmigration.beans.ManifestProvidedEntitySpec;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class ValuesYamlFromHelmRepoManifestService implements NgManifestService {
  public static final String HELM_REPO_STORE = "helmRepoStore";

  @Override
  public List<ManifestConfigWrapper> getManifestConfigWrapper(ApplicationManifest applicationManifest,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      ManifestProvidedEntitySpec entitySpec, List<NGYamlFile> yamlFileList, CaseFormat identifierCaseFormat) {
    String helmValuesYamlFilePaths = applicationManifest.getHelmValuesYamlFilePaths();
    if (StringUtils.isEmpty(helmValuesYamlFilePaths)) {
      return new ArrayList<>();
    }
    String[] splitHelmValuesFilePaths = StringUtils.split(helmValuesYamlFilePaths, ',');

    List<String> valuesFilePathsList =
        Arrays.stream(splitHelmValuesFilePaths).map(String::trim).collect(Collectors.toList());

    ValuesManifest valuesManifest = ValuesManifest.builder()
                                        .store(ParameterField.createValueField(
                                            StoreConfigWrapper.builder()
                                                .spec(InheritFromManifestStoreConfig.builder()
                                                          .paths(ParameterField.createValueField(valuesFilePathsList))
                                                          .build())
                                                .type(StoreConfigType.InheritFromManifest)
                                                .build()))
                                        .build();
    return Collections.singletonList(
        ManifestConfigWrapper.builder()
            .manifest(
                ManifestConfig.builder()
                    .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid(), identifierCaseFormat)
                        + HELM_REPO_STORE)
                    .type(ManifestConfigType.VALUES)
                    .spec(valuesManifest)
                    .build())
            .build());
  }
}
