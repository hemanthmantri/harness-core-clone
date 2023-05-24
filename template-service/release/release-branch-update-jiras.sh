#!/bin/bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex
KEYS=$(git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -o -iE '('CDS')-[0-9]+' | sort | uniq)

echo $KEYS

#FIX_TEMPLATE_VERSION value to be same as used in release-branch-create-pie-version.sh
FIX_TEMPLATE_VERSION="TEMPLATE-""$VERSION"
FINAL_KEYS=()
for KEY in ${KEYS[@]}
  do
    response=$(curl -X GET -H "Content-Type: application/json" \
          https://harness.atlassian.net/rest/api/2/issue/${KEY}/?components \
          --user $JIRA_USERNAME:$JIRA_PASSWORD)

    components=$(echo "${response}" | jq -r '.fields.components')

    if name=$(echo "${components}" | jq -r '.[] | select(.name == "Pipeline") | .name'); test -n "${name}"; then
      FINAL_KEYS+=( "$KEY" )
    fi
  done

echo ${FINAL_KEYS[@]}

for KEY in ${FINAL_KEYS[@]}
  do
    echo "$KEY"
    EXCLUDE_PROJECTS=","
    # Extract Jira project from Jira key
    IFS="-" read -ra PROJNUM <<< "$KEY"
    PROJ="${PROJNUM[0]}"
    # If it is in the exclude projects list, then do not attempt to set the fix version
    if [[ $EXCLUDE_PROJECTS == *",$PROJ,"* ]]; then
      echo "Skipping $KEY - project is archived or not relevant to versions."
    else
      response=$(curl -q -X PUT https://harness.atlassian.net/rest/api/2/issue/${KEY} --write-out '%{http_code}' --user ${JIRA_USERNAME}:${JIRA_PASSWORD} -H "Content-Type: application/json" -d '{
        "update": {
          "fixVersions": [
            {"add":
              {"name": "'"$FIX_TEMPLATE_VERSION"'" }
            }
          ]
        }
      }')
      if [[ "$response" -eq 204 ]] ; then
        echo "$KEY fixVersion set to $FIX_TEMPLATE_VERSION"
      elif [[ "$response" -eq 400 ]] ; then
        echo "Could not set fixVersion on $KEY - field hidden for the issue type"
      fi
    fi
  done