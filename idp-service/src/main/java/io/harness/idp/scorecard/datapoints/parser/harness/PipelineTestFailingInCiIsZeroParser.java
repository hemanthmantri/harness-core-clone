/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class PipelineTestFailingInCiIsZeroParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    return Map.of(dataFetchDTO.getRuleIdentifier(),
        ((Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier()))
            .get(dataFetchDTO.getDataPoint().getIdentifier()));
  }
}
