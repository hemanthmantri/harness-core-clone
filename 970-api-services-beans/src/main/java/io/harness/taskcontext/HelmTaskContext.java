/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.taskcontext;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.taskcontext.infra.InfraContext;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;

@Builder
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class HelmTaskContext implements TaskContext {
  Set<String> hints;
  InfraContext infraContext;

  @Override
  public Optional<String> getConnectorInfo() {
    if (infraContext != null) {
      return infraContext.getConnectorInfo();
    }
    return Optional.empty();
  }

  @Override
  public void addHint(String hint) {
    if (hints == null) {
      hints = new HashSet<>();
    }
    hints.add(hint);
  }

  @Override
  public void addHints(List<String> hints) {
    if (this.hints == null) {
      this.hints = new HashSet<>();
    }
    this.hints.addAll(hints);
  }

  @Override
  public Set<String> getHints() {
    if (hints == null) {
      hints = new HashSet<>();
    }
    return hints;
  }
}
