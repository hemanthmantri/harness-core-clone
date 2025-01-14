-- Copyright 2022 Harness Inc. All rights reserved.
-- Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
-- that can be found in the licenses directory at the root of this repository, also available at
-- https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

-- drop the existing UNIQUE constraint
ALTER TABLE SLO_HISTORY
DROP CONSTRAINT IF EXISTS SLO_HISTORY_UNIQUE_RECORD_INDEX;

-- Next, create a new UNIQUE constraint with the updated columns
BEGIN;
ALTER TABLE SLO_HISTORY
ADD CONSTRAINT SLO_HISTORY_UNIQUE_RECORD_INDEX UNIQUE(ACCOUNTID, ORGID, PROJECTID, SLOID, STARTTIME, ENDTIME);
COMMIT;
