/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.normalize;

import io.harness.ssca.beans.SettingsDTO;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import java.text.ParseException;
import java.util.List;

public interface Normalizer<T> {
  List<NormalizedSBOMComponentEntity> normaliseSBOM(T sbomDTO, SettingsDTO settings) throws ParseException;
}