/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.K8sContainer;
import io.harness.ssca.beans.instance.K8sInstanceInfoDTO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
@Slf4j
public class K8sInstanceInfoDTOMapper {
  public K8sInstanceInfoDTO mapToK8sInstanceInfo(Document instanceInfo, MongoTemplate mongoTemplate) {
    // Older debezium service converts list to map whereas newer debezium service directly sends map.
    // Handling both the cases here for backward compatability.
    Object containerList = instanceInfo.get("containerList");
    if (containerList == null) {
      return null;
    }

    try {
      if (containerList instanceof List) {
        return mongoTemplate.getConverter().read(K8sInstanceInfoDTO.class, instanceInfo);
      } else if (containerList instanceof Map) {
        K8sInstanceInfoDTO k8sInstanceInfoDTO = K8sInstanceInfoDTO.builder().build();
        Document containerMap = (Document) instanceInfo.get("containerList");
        if (containerMap != null) {
          Collection<Object> k8sContainerList = containerMap.values();
          List<K8sContainer> containers = new ArrayList<>();
          for (Object object : k8sContainerList) {
            Document document = (Document) object;
            String image = getFieldStringValueOrNull(document, "image");
            String name = getFieldStringValueOrNull(document, "name");
            String containerId = getFieldStringValueOrNull(document, "containerId");
            containers.add(K8sContainer.builder().image(image).name(name).containerId(containerId).build());
          }
          k8sInstanceInfoDTO.setContainerList(containers);
        }
        return k8sInstanceInfoDTO;
      }
    } catch (Exception e) {
      log.error("Exception while mapping to k8sInstanceInfoDto", e);
    }

    return null;
  }

  private String getFieldStringValueOrNull(Document document, String field) {
    if (document.get(field) == null) {
      return null;
    }
    return document.get(field).toString();
  }
}
