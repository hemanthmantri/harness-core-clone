/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.scm.bitbucket;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DEFAULT;
import static io.harness.idp.common.Constants.DEFAULT_BRANCH_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_BRANCH_NAME_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.NO_PULL_REQUESTS_FOUND;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.DateUtils;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class BitbucketMeanTimeToMergeParser implements DataPointParser {
  private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointData = new HashMap<>();
    List<InputValue> inputValues = dataFetchDTO.getInputValues();
    if (inputValues.size() != 1) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, INVALID_BRANCH_NAME_ERROR));
      return dataPointData;
    }
    String inputValue = inputValues.get(0).getValue();
    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(constructDataPointInfo(
          dataFetchDTO, null, !isEmpty(errorMessage) ? errorMessage : INVALID_BRANCH_NAME_ERROR));
      return dataPointData;
    }

    List<Map<String, Object>> values = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "values");
    if (isEmpty(values)) {
      inputValue = inputValue.replace("\"", "");
      String branchName = inputValue.equals(DEFAULT_BRANCH_KEY) ? DEFAULT : inputValue;
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, format(NO_PULL_REQUESTS_FOUND, branchName)));
      return dataPointData;
    }

    int numberOfPullRequests = values.size();
    long totalTimeToMerge = 0;
    for (Map<String, Object> value : values) {
      long createdAtMillis = DateUtils.parseTimestamp((String) value.get("created_on"), DATE_FORMAT);
      long mergedAtMillis = DateUtils.parseTimestamp((String) value.get("updated_on"), DATE_FORMAT);
      long timeToMergeMillis = mergedAtMillis - createdAtMillis;
      totalTimeToMerge += timeToMergeMillis;
    }

    double meanTimeToMergeMillis = (double) totalTimeToMerge / numberOfPullRequests;
    long value = (long) (meanTimeToMergeMillis / (60 * 60 * 1000));
    dataPointData.putAll(constructDataPointInfo(dataFetchDTO, value, null));
    return dataPointData;
  }
}
