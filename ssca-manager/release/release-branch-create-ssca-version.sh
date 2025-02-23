#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
set -ex

export FIX_SSCA_VERSION="SSCA-""$VERSION"
RELDATE=$(date +'%Y-%m-%d')
IFS="-" read -ra PROJNUM <<< "$KEY"
PROJ="SSCA"
response=$(curl -X POST https://harness.atlassian.net/rest/api/2/version/ --write-out '%{http_code}' --output /dev/null --silent --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
  "project":"'$PROJ'",
  "name": "'"$FIX_SSCA_VERSION"'",
  "releaseDate": "'"$RELDATE"'",
  "archived": false,
  "released": true }')


if  [[ "$response" -ne 201 ]] ; then
  echo "Failed to create version $FIX_SSCA_VERSION in $PROJ - Response code: $response"
  if [[ "$response" -eq 404 ]] ; then
    echo "404 response indicates that user $JIRA_USERNAME does not have permissions to create versions in project $PROJ"
  fi
else
  echo "Successfully created version $FIX_SSCA_VERSION in $PROJ"
fi

# make sure fix version is available
FIX_SSCA_VERSION_ID=$(curl -X GET -H "Content-Type: application/json" \
      https://harness.atlassian.net/rest/api/2/project/SSCA/versions \
      --user $JIRA_USERNAME:$JIRA_PASSWORD \
      | jq -c '.[] | select( .name == "'$FIX_SSCA_VERSION'" )' | jq '.id' | tr -d '"')

if [[ -z "$FIX_SSCA_VERSION_ID" ]]; then
  echo "fix version not found - aborting script"
  exit -1
else
  echo "FIX_SSCA_VERSION_ID=$FIX_SSCA_VERSION_ID"
fi