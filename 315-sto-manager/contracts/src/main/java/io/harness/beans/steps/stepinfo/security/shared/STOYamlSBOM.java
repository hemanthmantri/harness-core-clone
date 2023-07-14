/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security.shared;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.sto.variables.STOYamlSBOMFormat;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(STO)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class STOYamlSBOM {
  @YamlSchemaTypes(value = {expression})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  protected ParameterField<Boolean> generate;

  @YamlSchemaTypes(value = {expression})
  @ApiModelProperty(dataType = "io.harness.yaml.sto.variables.STOYamlSBOMFormat")
  protected ParameterField<STOYamlSBOMFormat> format;
}