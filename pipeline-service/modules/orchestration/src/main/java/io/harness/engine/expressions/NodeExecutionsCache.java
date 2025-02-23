/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(CDC)
@Value
public class NodeExecutionsCache {
  private static final String NULL_PARENT_ID = "__NULL_PARENT_ID__";

  NodeExecutionService nodeExecutionService;
  PlanService planService;
  Ambiance ambiance;
  Map<String, NodeExecution> map;
  Map<String, List<String>> childrenMap;
  Map<String, Node> nodeMap;
  Map<String, Ambiance> ambianceMap;

  @Builder
  public NodeExecutionsCache(NodeExecutionService nodeExecutionService, PlanService planService, Ambiance ambiance) {
    this.nodeExecutionService = nodeExecutionService;
    this.planService = planService;
    this.ambiance = ambiance;
    this.nodeMap = new HashMap<>();
    this.map = new HashMap<>();
    this.childrenMap = new HashMap<>();
    this.ambianceMap = new HashMap<>();
  }

  public synchronized NodeExecution fetch(String nodeExecutionId) {
    if (nodeExecutionId == null) {
      return null;
    }
    if (map.containsKey(nodeExecutionId)) {
      return map.get(nodeExecutionId);
    }

    NodeExecution nodeExecution;
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_USE_AMBIANCE_IN_EXPRESSION_ENGINE.name())) {
      nodeExecution = nodeExecutionService.getWithFieldsIncluded(
          nodeExecutionId, NodeProjectionUtils.fieldsForExpressionEngineWithAmbiance);
    } else {
      nodeExecution =
          nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForExpressionEngine);
    }
    map.put(nodeExecutionId, nodeExecution);
    return nodeExecution;
  }

  /**
   * Fetches a list of children for a particular parent Id.
   *
   * If parentId found in cache {@link NodeExecutionsCache#childrenMap} return list of nodes by
   * querying the {@link NodeExecutionsCache#map}
   *
   * Adds all the children to the {@link NodeExecutionsCache#map} and populates
   * {@link NodeExecutionsCache#childrenMap} with parentId => List#childIds
   *
   */
  public synchronized List<NodeExecution> fetchChildren(String parentId) {
    String childrenMapKey = parentId == null ? NULL_PARENT_ID : parentId;
    if (childrenMap.containsKey(childrenMapKey)) {
      List<String> ids = childrenMap.get(childrenMapKey);
      if (EmptyPredicate.isEmpty(ids)) {
        return Collections.emptyList();
      }

      return ids.stream().map(map::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    List<NodeExecution> childExecutions = new LinkedList<>();
    Set<String> fieldsForExpressionEngine;
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_USE_AMBIANCE_IN_EXPRESSION_ENGINE.name())) {
      fieldsForExpressionEngine = NodeProjectionUtils.fieldsForExpressionEngineWithAmbiance;
    } else {
      fieldsForExpressionEngine = NodeProjectionUtils.fieldsForExpressionEngine;
    }
    try (CloseableIterator<NodeExecution> iterator = nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             ambiance.getPlanExecutionId(), parentId, fieldsForExpressionEngine)) {
      while (iterator.hasNext()) {
        childExecutions.add(iterator.next());
      }
    }
    if (EmptyPredicate.isEmpty(childExecutions)) {
      childrenMap.put(parentId, Collections.emptyList());
      return Collections.emptyList();
    }

    childExecutions.forEach(childExecution -> map.put(childExecution.getUuid(), childExecution));
    childrenMap.put(parentId, childExecutions.stream().map(NodeExecution::getUuid).collect(Collectors.toList()));
    return childExecutions;
  }

  // Should not change the fields to be included as its only used by NodeExecutionMap, if you change it may not use
  // index of NodeExecution collection
  public List<Status> findAllTerminalChildrenStatusOnly(String parentId, boolean includeChildrenOfStrategy) {
    List<NodeExecution> nodeExecutions = nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetries(
        ambiance.getPlanExecutionId(), parentId, null, false, Collections.emptySet(), includeChildrenOfStrategy);
    return nodeExecutions.stream()
        .map(NodeExecution::getStatus)
        .filter(status -> StatusUtils.finalStatuses().contains(status))
        .collect(Collectors.toList());
  }

  public synchronized Node fetchNode(String nodeId) {
    if (nodeId == null) {
      return null;
    }
    if (nodeMap.containsKey(nodeId)) {
      return nodeMap.get(nodeId);
    }

    Node node = planService.fetchNode(ambiance.getPlanId(), nodeId);
    nodeMap.put(nodeId, node);
    return node;
  }

  public synchronized Ambiance getAmbiance(String nodeExecutionId) {
    if (nodeExecutionId == null) {
      return null;
    }
    if (ambianceMap.containsKey(nodeExecutionId)) {
      return ambianceMap.get(nodeExecutionId);
    }
    Ambiance ambiance =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbiance).getAmbiance();
    ambianceMap.put(nodeExecutionId, ambiance);
    return ambiance;
  }
}
