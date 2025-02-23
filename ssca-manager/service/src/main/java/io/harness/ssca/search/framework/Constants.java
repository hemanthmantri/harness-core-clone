/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.search.framework;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.SSCA)
public class Constants {
  public static final String ARTIFACT_ENTITY = "artifact";
  public static final String SBOM_COMPONENT_ENTITY = "component";
  public static final String REMEDIATION_TRACKER_RESOURCE = "SSCA_REMEDIATION_TRACKER";

  public static final String REMEDIATION_TRACKER_EDIT = "ssca_remediationtracker_edit";

  public static final String REMEDIATION_TRACKER_VIEW = "ssca_remediationtracker_view";

  public static final String REMEDIATION_TRACKER_CLOSE = "ssca_remediationtracker_close";
}
