/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.k8s.model.K8sContainer;
import io.harness.ssca.beans.instance.InstanceDTO;
import io.harness.ssca.beans.instance.K8sInstanceInfoDTO;
import io.harness.ssca.entities.artifact.ArtifactEntity;
import io.harness.ssca.services.ArtifactService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.SSCA)
@Slf4j
public class CdInstanceSummaryServiceHelper {
  @Inject ArtifactService artifactService;

  public boolean isK8sInstanceInfo(InstanceDTO instance) {
    return instance.getInstanceInfo() != null && (instance.getInstanceInfo() instanceof K8sInstanceInfoDTO);
  }
  public Set<ArtifactEntity> findCorrelatedArtifactsForK8sInstance(InstanceDTO instance) {
    // keeping it as set in case multiple containers are using the same image, then we update the artifact only once.
    Set<ArtifactEntity> correlatedArtifacts = new HashSet<>();
    K8sInstanceInfoDTO k8sInstanceInfo = (K8sInstanceInfoDTO) instance.getInstanceInfo();
    List<K8sContainer> containers = k8sInstanceInfo.getContainerList();
    if (EmptyPredicate.isEmpty(containers)) {
      return correlatedArtifacts;
    }
    for (K8sContainer container : containers) {
      String image = container.getImage();
      if (EmptyPredicate.isEmpty(image)) {
        continue;
      }
      // First trying with fully qualified path
      ArtifactEntity artifact = artifactService.getArtifactByCorrelationId(
          instance.getAccountIdentifier(), instance.getOrgIdentifier(), instance.getProjectIdentifier(), image);
      if (!Objects.isNull(artifact)) {
        correlatedArtifacts.add(artifact);
        continue;
      }
      // If not able to find with fully qualified path, trying the loose image search.
      String[] colonSplit = image.split(":");
      if (colonSplit.length == 2) {
        String fullyQualifiedImage = colonSplit[0];
        String tag = colonSplit[1];
        String imageName = fullyQualifiedImage;
        int lastSlashIndex = fullyQualifiedImage.lastIndexOf("/");
        if (lastSlashIndex != -1) {
          imageName = fullyQualifiedImage.substring(lastSlashIndex + 1);
        }
        artifact = artifactService.getLatestArtifactByImageNameAndTag(instance.getAccountIdentifier(),
            instance.getOrgIdentifier(), instance.getProjectIdentifier(), imageName, tag);
        if (!Objects.isNull(artifact)) {
          correlatedArtifacts.add(artifact);
        }
      }
    }
    log.info(String.format("Correlating %s images for instance %s", correlatedArtifacts.size(), instance.getId()));
    return correlatedArtifacts;
  }
}
