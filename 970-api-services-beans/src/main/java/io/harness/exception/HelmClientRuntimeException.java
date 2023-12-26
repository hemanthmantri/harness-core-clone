/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.taskcontext.TaskContext;

import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class HelmClientRuntimeException extends RuntimeException {
  @Getter final HelmClientException helmClientException;
  @Getter ExceptionType type;
  @Setter @Getter TaskContext taskContext;

  public HelmClientRuntimeException(@NotNull HelmClientException helmClientException) {
    super(ExceptionUtils.getMessage(helmClientException));
    this.helmClientException = helmClientException;
  }

  public HelmClientRuntimeException(@NotNull HelmClientException helmClientException, ExceptionType type) {
    this(helmClientException);
    this.type = type;
  }

  public HelmClientRuntimeException(
      @NotNull HelmClientException helmClientException, ExceptionType type, TaskContext taskContext) {
    this(helmClientException, type);
    this.taskContext = taskContext;
  }

  public HelmClientRuntimeException(@NotNull HelmClientException helmClientException, TaskContext taskContext) {
    this(helmClientException);
    this.taskContext = taskContext;
  }

  public enum ExceptionType { INTERRUPT }
}
