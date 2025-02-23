# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import os
import json
import datetime
import requests
from typing import Any, List
from calendar import monthrange
from google.cloud import functions_v2
from google.cloud import secretmanager
from google.cloud import bigquery
from google.cloud import pubsub_v1
from billing_helper import BillingHelper
from google.oauth2 import service_account
from google.auth import impersonated_credentials, default
from google.cloud.bigquery.table import TableReference
from util import create_dataset, print_, if_tbl_exists, createTable, run_batch_query, COSTAGGREGATED, UNIFIED, \
    CEINTERNALDATASET, update_connector_data_sync_status, CURRENCYCONVERSIONFACTORUSERINPUT, \
    add_currency_preferences_columns_to_schema, CURRENCY_LIST, BACKUP_CURRENCY_FX_RATES, send_event, \
    flatten_label_keys_in_table, run_bq_query_with_retries

class BillingBigQueryHelper(BillingHelper):
    KEY = "CCM_GCP_CREDENTIALS"
    PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
    COSTCATEGORIESUPDATETOPIC = os.environ.get('COSTCATEGORIESUPDATETOPIC', 'ccm-bigquery-batch-update')
    # https://cloud.google.com/billing/docs/how-to/export-data-bigquery-tables/detailed-usage
    GCP_STANDARD_EXPORT_COLUMNS = ["billing_account_id", "usage_start_time", "usage_end_time", "export_time",
                                "cost", "currency", "currency_conversion_rate", "cost_type", "labels",
                                "system_labels", "credits", "usage", "invoice", "adjustment_info",
                                "service", "sku", "project", "location", "cost_at_list"]
    GCP_DETAILED_EXPORT_COLUMNS = GCP_STANDARD_EXPORT_COLUMNS + ["resource", "price"]

    def __init__(self):
        self.client = bigquery.Client(self.PROJECTID)
        self.publisher = pubsub_v1.PublisherClient()

    def __get_cf_v2_uri(self, cf_name: str) -> Any:
        functions_v2_client = functions_v2.FunctionServiceClient()
        request = functions_v2.GetFunctionRequest(
            name=cf_name
        )
        response = functions_v2_client.get_function(request=request)
        return response.service_config.uri
    
    def __get_secret_key(self, project_id: str) -> str:
        client = secretmanager.SecretManagerServiceClient()
        request = {"name": f"projects/{project_id}/secrets/{self.KEY}/versions/latest"}
        response = client.access_secret_version(request)
        secret_string = response.payload.data.decode("UTF-8")
        return secret_string
    
    def __alter_raw_table(self, jsonData: Any) -> bool:
        print_("Altering raw gcp_billing_export Table")
        query = "ALTER TABLE `%s.%s.%s` \
            ADD COLUMN IF NOT EXISTS cost_at_list FLOAT64, \
            ADD COLUMN IF NOT EXISTS price STRUCT<effective_price NUMERIC, tier_start_amount NUMERIC, unit STRING, pricing_unit_quantity NUMERIC>, \
            ADD COLUMN IF NOT EXISTS resource STRUCT<name STRING, global_name STRING>;" % (self.PROJECTID, jsonData["datasetName"], jsonData["tableName"])
        try:
            print_(query)
            query_job = self.client.query(query)
            query_job.result()
        except Exception as e:
            # Error Running Alter Query
            print_(e)
        else:
            print_("Finished Altering %s Table" % jsonData["tableName"])

    def __prepare_select_query(self, jsonData: Any, columns_list: List[str]) -> str:
        if jsonData["ccmPreferredCurrency"]:
            select_query = ""
            for column in columns_list:
                if select_query:
                    select_query += ", "
                if column == "cost":
                    select_query += "(cost * fxRateSrcToDest) as cost"
                elif column == "credits":
                    select_query += "ARRAY (SELECT as struct credit.name as name, " \
                                    "(credit.amount * fxRateSrcToDest) as amount, " \
                                    "credit.full_name as full_name, credit.id as id, " \
                                    "credit.type as type FROM UNNEST(credits) AS credit) as credits"
                else:
                    select_query += f"{column} as {column}"
            return select_query
        else:
            return ", ".join(f"{w}" for w in columns_list)
        
    def __fetch_ingestion_filters(self, jsonData: Any) -> None:
        return """ DATE(startTime) >= DATE_SUB(CURRENT_DATE() , INTERVAL %s DAY) AND cloudProvider = "GCP" 
                AND gcpBillingAccountId IN (%s) """ % (jsonData["interval"], jsonData["billingAccountIds"])
    
    def ingest_data_to_costagg(self, jsonData: Any) -> None:
        ds = "%s.%s" % (self.PROJECTID, jsonData["datasetName"])
        table_name = "%s.%s.%s" % (self.PROJECTID, CEINTERNALDATASET, COSTAGGREGATED)
        source_table = "%s.%s" % (ds, UNIFIED)
        print_("Loading into %s table..." % table_name)
        query = """DELETE FROM `%s` WHERE DATE(day) >= DATE_SUB(@run_date , INTERVAL %s DAY) AND cloudProvider = 'GCP' AND accountId = '%s';
                INSERT INTO `%s` (day, cost, cloudProvider, accountId)
                    SELECT TIMESTAMP_TRUNC(startTime, DAY) AS day, SUM(cost) AS cost, "GCP" AS cloudProvider, '%s' as accountId
                    FROM `%s`  
                    WHERE DATE(startTime) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY) and cloudProvider = "GCP" 
                    GROUP BY day;
        """ % (
            table_name, jsonData["interval"], jsonData.get("accountId"), table_name, jsonData.get("accountId"),
            source_table,
            jsonData["interval"])

        job_config = bigquery.QueryJobConfig(
            priority=bigquery.QueryPriority.BATCH,
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )

        run_batch_query(self.client, query, job_config, timeout=180)

    def create_dataset(self, datasetName: Any, accountId: str = "") -> None:
        create_dataset(self.client, datasetName, accountId)

    def if_tbl_exists(self, table_ref: TableReference) -> bool:
        if_tbl_exists(self.client, table_ref)

    def createTable(self, table_ref: TableReference) -> Any:
        createTable(self.client, table_ref)

    def alter_unified_table(self, jsonData: Any) -> None:
        print_("Altering unifiedTable Table")
        ds = "%s.%s" % (self.PROJECTID, jsonData["datasetName"])
        query = "ALTER TABLE `%s.unifiedTable` \
            ADD COLUMN IF NOT EXISTS costCategory ARRAY<STRUCT<costCategoryName STRING, costBucketName STRING>>, \
            ADD COLUMN IF NOT EXISTS gcpResource STRUCT<name STRING, global_name STRING>, \
            ADD COLUMN IF NOT EXISTS gcpSystemLabels ARRAY<STRUCT<key STRING, value STRING>>, \
            ADD COLUMN IF NOT EXISTS gcpCostAtList FLOAT64, \
            ADD COLUMN IF NOT EXISTS gcpProjectNumber STRING, \
            ADD COLUMN IF NOT EXISTS gcpProjectName STRING, \
            ADD COLUMN IF NOT EXISTS gcpPrice STRUCT<effective_price NUMERIC, tier_start_amount NUMERIC, unit STRING, pricing_unit_quantity NUMERIC>, \
            ADD COLUMN IF NOT EXISTS gcpUsage STRUCT<amount FLOAT64, unit STRING, amount_in_pricing_units FLOAT64, pricing_unit STRING>, \
            ADD COLUMN IF NOT EXISTS gcpCredits ARRAY<STRUCT<name STRING, amount FLOAT64, full_name STRING, id STRING, type STRING>>;" % ds

        try:
            print_(query)
            query_job = self.client.query(query)
            query_job.result()
        except Exception as e:
            # Error Running Alter Query
            print_(e)
        else:
            print_("Finished Altering unifiedTable Table")

    def add_currency_preferences_columns_to_schema(self, table_ids: Any) -> None:
        add_currency_preferences_columns_to_schema(self.client, table_ids)

    def get_preferred_currency(self, jsonData: Any) -> None:
        ds = "%s.%s" % (self.PROJECTID, jsonData["datasetName"])
        jsonData["ccmPreferredCurrency"] = None
        query = """SELECT destinationCurrency
                    FROM `%s.%s` where destinationCurrency is NOT NULL LIMIT 1;
                    """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT)
        try:
            print_(query)
            query_job = self.client.query(query)
            results = query_job.result()  # wait for job to complete
            for row in results:
                jsonData["ccmPreferredCurrency"] = row.destinationCurrency.upper()
                print_("Found preferred-currency for account: %s" % (jsonData["ccmPreferredCurrency"]))
                break
        except Exception as e:
            print_(e)
            print_("Failed to fetch preferred currency for account", "WARN")

        if not jsonData["ccmPreferredCurrency"]:
            print_("No preferred-currency found for account")
    
    def trigger_historical_cost_update_in_preferred_currency(self, jsonData: Any) -> None:
        current_timestamp = datetime.datetime.utcnow()
        currentMonth = f"{current_timestamp.month:02d}"
        currentYear = current_timestamp.year
        if "disableHistoricalUpdateForMonths" not in jsonData or not jsonData["disableHistoricalUpdateForMonths"]:
            jsonData["disableHistoricalUpdateForMonths"] = [f"{currentYear}-{currentMonth}-01"]

        # get months for which historical update needs to be triggered, and custom conversion factors
        ds = "%s.%s" % (self.PROJECTID, jsonData["datasetName"])
        query = """SELECT month, conversionType, sourceCurrency, destinationCurrency, conversionFactor
                    FROM `%s.%s`
                    WHERE accountId="%s" AND destinationCurrency is NOT NULL AND isHistoricalUpdateRequired = TRUE
                    AND cloudServiceProvider = "GCP"
                    AND month NOT IN (%s);
                    """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT, jsonData.get("accountId"),
                        ", ".join(f"DATE('{month}')" for month in jsonData["disableHistoricalUpdateForMonths"]))
        print_(query)
        historical_update_months = set()
        custom_factors_dict = {}
        try:
            query_job = self.client.query(query)
            results = query_job.result()  # wait for job to complete
            for row in results:
                historical_update_months.add(str(row.month))
                if row.conversionType == "CUSTOM":
                    custom_factors_dict[row.sourceCurrency] = float(row.conversionFactor)
        except Exception as e:
            print_(e)
            print_("Failed to fetch historical-update months for account", "WARN")

        # unset historicalUpdate flag for months in disableHistoricalUpdateForMonths
        query = """UPDATE `%s.%s` 
                    SET isHistoricalUpdateRequired = FALSE
                    WHERE cloudServiceProvider = "GCP" 
                    AND month in (%s)
                    AND accountId = '%s';
                    """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                        ", ".join(f"DATE('{month}')" for month in jsonData["disableHistoricalUpdateForMonths"]),
                        jsonData.get("accountId"))
        print_(query)
        try:
            query_job = self.client.query(query)
            query_job.result()  # wait for job to complete
        except Exception as e:
            print_(e)
            print_("Failed to unset isHistoricalUpdateRequired flags in CURRENCYCONVERSIONFACTORUSERINPUT table", "WARN")
            # updates on table are disallowed after streaming insert from currency APIs. retry in next run.
            return

        # trigger historical update CF if required
        if list(historical_update_months):
            trigger_payload = {
                "accountId": jsonData.get("accountId"),
                "cloudServiceProvider": "GCP",
                "months": list(historical_update_months),
                "userInputFxRates": custom_factors_dict
            }
            url = self.__get_cf_v2_uri(
                f"projects/{self.PROJECTID}/locations/us-central1/functions/ce-gcp-historical-currency-update-bq-terraform")
            try:
                # Set up metadata server request
                # See https://cloud.google.com/compute/docs/instances/verifying-instance-identity#request_signature
                metadata_server_token_url = 'http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience='
                token_request_url = metadata_server_token_url + url
                token_request_headers = {'Metadata-Flavor': 'Google'}

                # Fetch the token
                token_response = requests.get(token_request_url, headers=token_request_headers)
                jwt = token_response.content.decode("utf-8")

                # Provide the token in the request to the receiving function
                receiving_function_headers = {'Authorization': f'bearer {jwt}'}
                r = requests.post(url, json=trigger_payload, timeout=30, headers=receiving_function_headers)
            except Exception as e:
                print_("Post-request timeout reached when triggering historical update CF.")
                pass
    
    def get_impersonated_credentials(self, jsonData: Any) -> None:
        # Get source credentials
        
        target_scopes = [
            'https://www.googleapis.com/auth/cloud-platform']
        if jsonData["useWorkloadIdentity"] == "True":
            source_credentials, project = default()
            # Google ADC
        else:
            json_acct_info = json.loads(self.__get_secret_key(self.PROJECTID))
            credentials = service_account.Credentials.from_service_account_info(json_acct_info)
            source_credentials = credentials.with_scopes(target_scopes)

        # Impersonate to target credentials
        target_credentials = impersonated_credentials.Credentials(
            source_credentials=source_credentials,
            target_principal=jsonData["serviceAccount"],
            target_scopes=target_scopes,
            lifetime=500)
        jsonData["credentials"] = target_credentials
        print_("source: %s, target: %s" % (target_credentials._source_credentials.service_account_email,
                                        target_credentials.service_account_email))            
        
    def send_cost_category_update_event(self, jsonData: Any) -> None:
        if "interval" in jsonData:
            send_event(self.publisher.topic_path(self.PROJECTID, self.COSTCATEGORIESUPDATETOPIC), {
                "eventType": "COST_CATEGORY_UPDATE",
                "message": {
                    "accountId": jsonData["accountId"],
                    "startDate": "%s" % (datetime.datetime.today() - datetime.timedelta(days=int(jsonData["interval"]))).date(),
                    "endDate": "%s" % datetime.datetime.today().date(),
                    "cloudProvider": "GCP",
                    "cloudProviderAccountIds": jsonData["billingAccountIdsList"]
                }
            })

    def isFreshSync(self, jsonData: Any) -> Any:
        print_("Determining if we need to do fresh sync")
        if jsonData.get("dataSourceId"):
            # Check in preaggregated table for non US regions
            query = """  SELECT count(*) as count from %s.%s.preAggregated
                    WHERE starttime >= DATETIME_SUB(CURRENT_TIMESTAMP, INTERVAL 180 DAY) AND cloudProvider = "GCP" AND gcpBillingAccountId IN (%s) ;
                    """ % (self.PROJECTID, jsonData["datasetName"], jsonData["billingAccountIds"])
        else:
            # Only applicable for US regions
            query = """  SELECT count(*) as count from %s.%s.%s
                    WHERE DATE(%s) >= DATE_SUB(CURRENT_DATE(), INTERVAL 187 DAY) AND usage_start_time >= DATETIME_SUB(CURRENT_TIMESTAMP, INTERVAL 180 DAY);
                    """ % (self.PROJECTID, jsonData["datasetName"], jsonData["tableName"],
                            jsonData["gcpBillingExportTablePartitionColumnName"])
        print_(query)
        try:
            query_job = self.client.query(query)
            results = query_job.result()  # wait for job to complete
            for row in results:
                print_("  Number of GCP records existing on our side : %s" % (row["count"]))
                if row["count"] > 0:
                    return False
                else:
                    return True
        except Exception as e:
            # Table does not exist
            print_(e)
            print_("  Fresh sync is needed")
            return True
        
    def compute_sync_interval(self, jsonData: Any) -> None:
        if jsonData.get("isFreshSync"):
            jsonData["interval"] = '180'
            print_("Sync Interval: %s days" % jsonData["interval"])
            return

        # find last_synced_export_date in our side of gcp_cost_export table
        # (not using gcp_billing_export table on our side since it will have currently synced data in case of cross-region)
        last_synced_export_date = ''
        intermediary_table_name = jsonData["tableName"].replace("gcp_billing_export", "gcp_cost_export", 1) if jsonData[
            "tableName"].startswith("gcp_billing_export") else f"gcp_cost_export_{jsonData['tableName']}"
        gcp_cost_export_table_name = "%s.%s.%s" % (self.PROJECTID, jsonData["datasetName"], intermediary_table_name)
        query = """
            SELECT DATE_SUB(DATE(MAX(export_time)), INTERVAL 1 DAY) as last_synced_export_date 
            FROM `%s` 
            where usage_start_time >= TIMESTAMP_SUB(current_timestamp(), INTERVAL 180 DAY);
        """ % gcp_cost_export_table_name
        results = run_bq_query_with_retries(self.client, query)
        for row in results:
            last_synced_export_date = str(row.last_synced_export_date)

        # find syncInterval based on minimum usage_start_time at source for data after last_synced_export_date
        # for querying source table, assuming that date(_PARTITIONTIME) is same as date(export_time) - using the former.
        sync_interval = ""
        query = """
            SELECT DATE_DIFF(CURRENT_DATE(), DATE(MIN(usage_start_time)), DAY) as sync_interval
            FROM `%s.%s.%s` 
            WHERE DATE(_PARTITIONTIME) >= DATE('%s')
        """ % (jsonData["sourceGcpProjectId"], jsonData["sourceDataSetId"], jsonData["sourceGcpTableName"],
            last_synced_export_date)

        if jsonData.get("dataSourceId") == "cross_region_copy":
            imclient = self.client
        elif jsonData["deployMode"] == "ONPREM":
            # Uses Google ADC
            imclient = bigquery.Client(project=self.PROJECTID)
        else:
            # for SAAS
            imclient = bigquery.Client(credentials=jsonData["credentials"], project=self.PROJECTID)
        results = run_bq_query_with_retries(imclient, query)
        for row in results:
            sync_interval = row.sync_interval

        # setting the sync-interval in jsonData. Will not sync more than 180 days data in any case.
        if jsonData["ccmPreferredCurrency"]:
            jsonData["interval"] = str(min(max(datetime.datetime.utcnow().date().day - 1, sync_interval), 180))
        else:
            jsonData["interval"] = str(min(sync_interval, 180))
        print_("Sync Interval: %s days" % jsonData["interval"])

    def check_if_billing_export_is_detailed(self, jsonData: Any) -> bool:
        print_("Checking if raw billing export (%s) is detailed / has resource column at source" % jsonData["tableName"])
        query = """  SELECT column_name FROM `%s.%s.INFORMATION_SCHEMA.COLUMNS` 
                WHERE table_name = '%s' and column_name = "resource";
                """ % (jsonData["sourceGcpProjectId"], jsonData["sourceDataSetId"], jsonData["sourceGcpTableName"])
        # TODO: Remove run_date query parameter if not needed
        # Configure the query job.
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        try:
            print_(query)
            imclient = bigquery.Client(credentials=jsonData["credentials"], project=self.PROJECTID)
            query_job = imclient.query(query, job_config=job_config)
            results = query_job.result()  # wait for job to complete
            for row in results:
                if row.column_name == "resource":
                    return True
        except Exception as e:
            print_(e)
            print_(query)
            print_("  Failed to retrieve columns from the ingested billing_export table", "WARN")
        return False

    def ingest_into_gcp_billing_export_table(self, destination: str, jsonData: Any) -> None:
        if jsonData["isFreshSync"]:
            # Fresh sync. Sync only for 180 days.
            query = """  SELECT * FROM `%s.%s.%s` WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL %s DAY);
            """ % (jsonData["sourceGcpProjectId"], jsonData["sourceDataSetId"], jsonData["sourceGcpTableName"],
                str(int(jsonData["interval"]) + 7), str(int(jsonData["interval"]) + 7))
            # Configure the query job.
            print_(" Destination :%s" % destination)
            if jsonData["gcpBillingExportTablePartitionColumnName"] == "usage_start_time":
                job_config = bigquery.QueryJobConfig(
                    destination=destination,
                    write_disposition=bigquery.job.WriteDisposition.WRITE_TRUNCATE,
                    time_partitioning=bigquery.table.TimePartitioning(
                        field=jsonData["gcpBillingExportTablePartitionColumnName"]),
                    query_parameters=[
                        bigquery.ScalarQueryParameter(
                            "run_date",
                            "DATE",
                            datetime.datetime.utcnow().date(),
                        )
                    ]
                )
            else:
                job_config = bigquery.QueryJobConfig(
                    destination=destination,
                    write_disposition=bigquery.job.WriteDisposition.WRITE_TRUNCATE,
                    time_partitioning=bigquery.table.TimePartitioning(),
                    query_parameters=[
                        bigquery.ScalarQueryParameter(
                            "run_date",
                            "DATE",
                            datetime.datetime.utcnow().date(),
                        )
                    ]
                )
        else:
            # Alter raw table if it's existing on our side.
            self.__alter_raw_table(jsonData)
            # keeping this 3 days for currency customers also
            # only tables other than gcp_billing_export require to be updated with current month currency factors
            # Sync past 3 days only. Specify columns here explicitely.

            # check whether the raw billing table is standard_export or detailed_export
            jsonData["isBillingExportDetailed"] = self.check_if_billing_export_is_detailed(jsonData)
            query = """  DELETE FROM `%s` 
                    WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) and DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL %s DAY); 
                INSERT INTO `%s` (billing_account_id, %s service, %s sku,usage_start_time,usage_end_time,project,labels,system_labels,location,export_time,cost,currency,currency_conversion_rate,usage,credits,invoice,cost_type,adjustment_info, cost_at_list)
                    SELECT billing_account_id, %s service,%s sku,usage_start_time,usage_end_time,project,labels,system_labels,location,export_time,cost,currency,currency_conversion_rate,usage,credits,invoice,cost_type,adjustment_info, cost_at_list 
                    FROM `%s.%s.%s`
                    WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL %s DAY);
            """ % (destination, jsonData["gcpBillingExportTablePartitionColumnName"],
                str(int(jsonData["interval"]) + 7), jsonData["interval"],
                destination,
                "resource," if jsonData["isBillingExportDetailed"] else "", "price," if jsonData["isBillingExportDetailed"] else "",
                "resource," if jsonData["isBillingExportDetailed"] else "", "price," if jsonData["isBillingExportDetailed"] else "",
                jsonData["sourceGcpProjectId"], jsonData["sourceDataSetId"], jsonData["sourceGcpTableName"],
                str(int(jsonData["interval"]) + 7), jsonData["interval"])

            # Configure the query job.
            job_config = bigquery.QueryJobConfig(
                query_parameters=[
                    bigquery.ScalarQueryParameter(
                        "run_date",
                        "DATE",
                        datetime.datetime.utcnow().date(),
                    )
                ]
            )

        if jsonData["deployMode"] == "ONPREM":
            # Uses Google ADC
            imclient = bigquery.Client(project=self.PROJECTID)
        else:
            # for SAAS
            imclient = bigquery.Client(credentials=jsonData["credentials"], project=self.PROJECTID)
        query_job = imclient.query(query, job_config=job_config)
        print_(query)
        print_(query_job.job_id)
        query_job.result()
        print_("  Loaded in %s" % jsonData["tableName"])

    def get_unique_billingaccount_id(self, jsonData: Any) -> None:
        # Get unique billingAccountIds from main gcp table
        print_("Getting unique billingAccountIds from %s" % jsonData["tableName"])
        query = """  SELECT DISTINCT(billing_account_id) as billing_account_id FROM `%s.%s.%s` 
                WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY);
                """ % (self.PROJECTID, jsonData["datasetName"], jsonData["tableName"],
                    jsonData["gcpBillingExportTablePartitionColumnName"], str(int(jsonData.get("interval", 180)) + 7))
        # Configure the query job.
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        try:
            print_(query)
            query_job = self.client.query(query, job_config=job_config)
            results = query_job.result()  # wait for job to complete
            billingAccountIds = []
            for row in results:
                billingAccountIds.append(row.billing_account_id)
            jsonData["billingAccountIds"] = ", ".join(f"'{w}'" for w in billingAccountIds)
            jsonData["billingAccountIdsList"] = billingAccountIds
        except Exception as e:
            print_(query)
            print_("  Failed to retrieve distinct billingAccountIds", "WARN")
            jsonData["billingAccountIds"] = ""
            jsonData["billingAccountIdsList"] = []
            raise e
        print_("  Found unique billingAccountIds %s" % jsonData.get("billingAccountIds"))

    def insert_currencies_with_unit_conversion_factors_in_bq(self, jsonData: Any) -> None:
        # we are inserting these rows for showing active month's source_currencies to user
        current_timestamp = datetime.datetime.utcnow()
        currentMonth = f"{current_timestamp.month:02d}"
        currentYear = current_timestamp.year

        # update 1.0 rows in currencyConversionFactorDefault table only for current month
        date_start = "%s-%s-01" % (currentYear, currentMonth)
        date_end = "%s-%s-%s" % (currentYear, currentMonth, monthrange(int(currentYear), int(currentMonth))[1])

        query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                    WHERE accountId = '%s' AND cloudServiceProvider = "GCP" AND sourceCurrency = destinationCurrency 
                    AND conversionSource = "BILLING_EXPORT_SRC_CCY" AND month < DATE('%s');
                INSERT INTO `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                (accountId,cloudServiceProvider,sourceCurrency,destinationCurrency,
                conversionFactor,month,conversionSource,createdAt,updatedAt)
                    SELECT distinct 
                    '%s' as accountId,
                    "GCP" as cloudServiceProvider,
                    currency as sourceCurrency,
                    currency as destinationCurrency,
                    1.0 as conversionFactor,
                    DATE('%s') as month,
                    "BILLING_EXPORT_SRC_CCY" as conversionSource,
                    TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
                    FROM `%s.%s.%s`
                    WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY)  
                    AND DATE(usage_start_time) >= '%s' and DATE(usage_start_time) <= '%s'
                    AND currency not in (select distinct sourceCurrency FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                    WHERE accountId = '%s' AND cloudServiceProvider = "GCP" AND sourceCurrency = destinationCurrency 
                    AND conversionSource = "BILLING_EXPORT_SRC_CCY" AND month = DATE('%s'));
        """ % (self.PROJECTID,
            jsonData.get("accountId"),
            date_start,
            self.PROJECTID,
            jsonData.get("accountId"),
            date_start,
            current_timestamp, current_timestamp,
            self.PROJECTID, jsonData["datasetName"], jsonData["tableName"],
            jsonData["gcpBillingExportTablePartitionColumnName"],
            str(datetime.datetime.utcnow().date().day - 1),
            date_start, date_end,
            self.PROJECTID,
            jsonData.get("accountId"),
            date_start)
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        print_(query)
        query_job = self.client.query(query, job_config=job_config)
        try:
            query_job.result()
        except Exception as e:
            print_(e)
            print_(f"Failed to execute query: {query}", "WARN")
            # raise e

    def initialize_fx_rates_dict(self, jsonData: Any) -> None:
        if not jsonData["ccmPreferredCurrency"]:
            return
        jsonData["fx_rates_srcCcy_to_destCcy"] = {}

        query = """SELECT distinct DATE_TRUNC(DATE(usage_start_time), month) as billing_month, currency
                    FROM `%s.%s.%s` 
                    WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND 
                    DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY);
                    """ % (self.PROJECTID, jsonData["datasetName"], jsonData["tableName"],
                        jsonData["gcpBillingExportTablePartitionColumnName"],
                        str(int(jsonData["interval"]) + 7),
                        jsonData["interval"])
        try:
            job_config = bigquery.QueryJobConfig(
                query_parameters=[
                    bigquery.ScalarQueryParameter(
                        "run_date",
                        "DATE",
                        datetime.datetime.utcnow().date(),
                    )
                ]
            )
            print_(query)
            query_job = self.client.query(query, job_config=job_config)
            results = query_job.result()  # wait for job to complete
            for row in results:
                if str(row.billing_month) not in jsonData["fx_rates_srcCcy_to_destCcy"]:
                    jsonData["fx_rates_srcCcy_to_destCcy"][str(row.billing_month)] = {row.currency.upper(): None}
                else:
                    jsonData["fx_rates_srcCcy_to_destCcy"][str(row.billing_month)][row.currency.upper()] = None
        except Exception as e:
            print_(e)
            print_("Failed to list distinct GCP source-currencies for account", "WARN")

    def fetch_default_conversion_factors_from_API(self, jsonData: Any) -> None:
        if not jsonData["ccmPreferredCurrency"]:
            return

        current_timestamp = datetime.datetime.utcnow()
        currentMonth = f"{current_timestamp.month:02d}"
        currentYear = current_timestamp.year
        date_start = "%s-%s-01" % (currentYear, currentMonth)
        date_end = "%s-%s-%s" % (currentYear, currentMonth, monthrange(int(currentYear), int(currentMonth))[1])
        current_fx_rates_from_api = None

        for billing_month in jsonData["fx_rates_srcCcy_to_destCcy"]:
            # fetch conversion factors from the API for all sourceCurrencies vs USD and destCcy vs USD for that DATE.
            # cdn.jsdelivr.net api returns currency-codes in lowercase
            print_(f"Hitting fxRate API for the month: {billing_month}")
            try:
                response = requests.get(
                    f"https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/{billing_month}/currencies/usd.json")
                fx_rates_from_api = response.json()
                if billing_month == date_start:
                    current_fx_rates_from_api = response.json()
            except Exception as e:
                print_(e)
                print_("fxRate API failed. Using backup fx_rates.", "WARN")
                fx_rates_from_api = BACKUP_CURRENCY_FX_RATES[date_start]
                if billing_month == date_start:
                    current_fx_rates_from_api = BACKUP_CURRENCY_FX_RATES[date_start]

            for srcCurrency in fx_rates_from_api["usd"]:
                if srcCurrency.upper() not in CURRENCY_LIST:
                    continue
                # ensure precision of fx rates while performing operations
                try:
                    # 1 usd = x src
                    # 1 usd = y dest
                    # 1 src = (y/x) dest
                    if srcCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"][billing_month]:
                        jsonData["fx_rates_srcCcy_to_destCcy"][billing_month][srcCurrency.upper()] = \
                            fx_rates_from_api["usd"][jsonData["ccmPreferredCurrency"].lower()] / fx_rates_from_api["usd"][
                                srcCurrency]
                except Exception as e:
                    print_(e, "WARN")
                    print_(f"fxRate for {srcCurrency} to {jsonData['ccmPreferredCurrency']} was not found in API response.")

        # update currencyConversionFactorDefault table for current month's currencies
        currency_pairs_from_api = ", ".join(
            [f"'USD_{srcCurrency.upper()}'" for srcCurrency in current_fx_rates_from_api["usd"]])
        select_query = ""
        for currency in current_fx_rates_from_api["usd"]:
            if currency.upper() not in CURRENCY_LIST:
                continue
            if select_query:
                select_query += " UNION ALL "
            select_query += """
            SELECT cast(null as string) as accountId, cast(null as string) as cloudServiceProvider,
            'USD' as sourceCurrency, '%s' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
            "API" as conversionSource,
            TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
            """ % (currency.upper(), current_fx_rates_from_api["usd"][currency], date_start,
                current_timestamp, current_timestamp)

            # flip source and destination
            if currency.upper() != "USD":
                select_query += " UNION ALL "
                select_query += """
                SELECT cast(null as string) as accountId, cast(null as string) as cloudServiceProvider,
                '%s' as sourceCurrency, 'USD' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
                "API" as conversionSource,
                TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
                """ % (currency.upper(), 1.0 / current_fx_rates_from_api["usd"][currency], date_start,
                    current_timestamp, current_timestamp)

        query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                    WHERE accountId IS NULL AND conversionSource = "API" 
                    AND (CONCAT(sourceCurrency,'_',destinationCurrency) in (%s) OR 
                        CONCAT(destinationCurrency,'_',sourceCurrency) in (%s));
                        
                INSERT INTO `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                (accountId,cloudServiceProvider,sourceCurrency,destinationCurrency,
                conversionFactor,month,conversionSource,createdAt,updatedAt)
                    (%s)
        """ % (self.PROJECTID,
            currency_pairs_from_api,
            currency_pairs_from_api,
            self.PROJECTID,
            select_query)
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        print_(query)
        query_job = self.client.query(query, job_config=job_config)
        try:
            query_job.result()
        except Exception as e:
            print_(e)
            print_(query)
            # raise e

    def fetch_default_conversion_factors_from_billing_export(self, jsonData: Any) -> None:
        if jsonData["ccmPreferredCurrency"] is None:
            return

        fx_rates_from_billing_export = []
        query = """SELECT distinct DATE_TRUNC(DATE(usage_start_time), month) as billing_month, currency, currency_conversion_rate 
                    from `%s.%s.%s` 
                    WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND 
                    DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY);
                    """ % (self.PROJECTID, jsonData["datasetName"], jsonData["tableName"],
                        jsonData["gcpBillingExportTablePartitionColumnName"],
                        str(int(jsonData["interval"]) + 7),
                        jsonData["interval"])
        try:
            job_config = bigquery.QueryJobConfig(
                query_parameters=[
                    bigquery.ScalarQueryParameter(
                        "run_date",
                        "DATE",
                        datetime.datetime.utcnow().date(),
                    )
                ]
            )
            print_(query)
            query_job = self.client.query(query, job_config=job_config)
            results = query_job.result()  # wait for job to complete
            for row in results:
                fx_rates_from_billing_export.append({
                    "sourceCurrency": row.currency.upper(),
                    "destinationCurrency": "USD",
                    "fxRate": 1.0 / float(row.currency_conversion_rate),
                    "billing_month": row.billing_month
                })
                if row.currency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"] and jsonData[
                    "ccmPreferredCurrency"] == "USD":
                    jsonData["fx_rates_srcCcy_to_destCcy"][row.billing_month][row.currency.upper()] = 1.0 / float(
                        row.currency_conversion_rate)
        except Exception as e:
            print_(e)
            print_("Failed to fetch conversion-factors from the BILLING_EXPORT", "WARN")

        # update currencyConversionFactorDefault table with the conversion factors obtained from billing export
        current_timestamp = datetime.datetime.utcnow()

        currency_pairs_from_billing_export = ", ".join(
            [f"'{row['billing_month']}_{row['sourceCurrency']}_{row['destinationCurrency']}'" for row in
            fx_rates_from_billing_export])
        select_query = ""
        for row in fx_rates_from_billing_export:
            if select_query:
                select_query += " UNION ALL "
            select_query += """
            SELECT '%s' as accountId, 'GCP' as cloudServiceProvider,
            '%s' as sourceCurrency, '%s' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
            "BILLING_EXPORT" as conversionSource,
            TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
            """ % (jsonData.get("accountId"), row['sourceCurrency'], row['destinationCurrency'],
                row['fxRate'], row['billing_month'], current_timestamp, current_timestamp)

        query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                    WHERE accountId = '%s' AND conversionSource = "BILLING_EXPORT" 
                    AND cloudServiceProvider = 'GCP' 
                    AND CONCAT(month,'_',sourceCurrency,'_',destinationCurrency) in (%s);
                        
                INSERT INTO `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                (accountId,cloudServiceProvider,sourceCurrency,destinationCurrency,
                conversionFactor,month,conversionSource,createdAt,updatedAt)
                    (%s)
        """ % (self.PROJECTID,
            jsonData.get("accountId"), currency_pairs_from_billing_export,
            self.PROJECTID,
            select_query)
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        print_(query)
        query_job = self.client.query(query, job_config=job_config)
        try:
            query_job.result()
        except Exception as e:
            print_(e)
            print_(query)
            # raise e

    def fetch_custom_conversion_factors(self, jsonData: Any) -> None:
        if not jsonData["ccmPreferredCurrency"]:
            return

        # using latest entry in CURRENCYCONVERSIONFACTORUSERINPUT table for each {src, dest, month}
        # last user entry for a currency-pair might be several months before reportMonth

        for billing_month_start in jsonData["fx_rates_srcCcy_to_destCcy"]:
            ds = "%s.%s" % (self.PROJECTID, jsonData["datasetName"])
            year, month = billing_month_start.split('-')[0], billing_month_start.split('-')[1]
            billing_month_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
            query = """WITH latest_custom_rate_rows as 
                        (SELECT sourceCurrency, destinationCurrency, max(updatedAt) as latestUpdatedAt
                        FROM `%s.%s` 
                        WHERE accountId="%s" 
                        and cloudServiceProvider="GCP" 
                        and destinationCurrency='%s' 
                        and month <= '%s'
                        group by sourceCurrency, destinationCurrency)
                        
                        SELECT customFxRates.sourceCurrency as sourceCurrency, 
                        customFxRates.conversionFactor as fx_rate, 
                        customFxRates.conversionType as conversion_type 
                        FROM `%s.%s` customFxRates
                        left join latest_custom_rate_rows 
                        on (customFxRates.sourceCurrency=latest_custom_rate_rows.sourceCurrency 
                        and customFxRates.destinationCurrency=latest_custom_rate_rows.destinationCurrency 
                        and customFxRates.updatedAt=latest_custom_rate_rows.latestUpdatedAt) 
                        WHERE latest_custom_rate_rows.latestUpdatedAt is not null 
                        and customFxRates.accountId="%s" 
                        and customFxRates.cloudServiceProvider="GCP" 
                        and customFxRates.destinationCurrency='%s' 
                        and customFxRates.month <= '%s';
                        """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                            jsonData.get("accountId"),
                            jsonData["ccmPreferredCurrency"],
                            billing_month_end,
                            ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                            jsonData.get("accountId"),
                            jsonData["ccmPreferredCurrency"],
                            billing_month_end)
            try:
                print_(query)
                query_job = self.client.query(query)
                results = query_job.result()  # wait for job to complete
                for row in results:
                    if row.sourceCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"][
                        billing_month_start] and row.conversion_type == "CUSTOM":
                        jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start][row.sourceCurrency.upper()] = float(
                            row.fx_rate)
            except Exception as e:
                print_(e)
                print_("Failed to fetch custom conversion-factors for account", "WARN")
    
    def verify_existence_of_required_conversion_factors(self, jsonData: Any) -> None:
        # preferred currency should've been obtained at this point if it was set by customer
        if not jsonData["ccmPreferredCurrency"]:
            return

        for billing_month_start in jsonData["fx_rates_srcCcy_to_destCcy"]:
            if not all(jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start].values()):
                print_(jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start])
                print_("Required fx rate not found for at least one currency pair", "ERROR")
                # throw error here. CF execution can't proceed from here

    def update_fx_rate_column_in_raw_table(self, jsonData: Any) -> None:
        if not jsonData["ccmPreferredCurrency"]:
            return

        # add fxRateSrcToDest column if not exists
        print_("Altering raw gcp_billing_export Table - adding fxRateSrcToDest column")
        query = "ALTER TABLE `%s.%s.%s` \
            ADD COLUMN IF NOT EXISTS fxRateSrcToDest FLOAT64;" % (self.PROJECTID, jsonData["datasetName"], jsonData["tableName"])
        try:
            print_(query)
            query_job = self.client.query(query)
            query_job.result()
        except Exception as e:
            # Error Running Alter Query
            print_(e)
        else:
            print_("Finished Altering gcp_billing_export Table")

        # update value of fxRateSrcToDest column using dict
        fx_rate_case_when_query = "CASE "
        for billing_month_start in jsonData["fx_rates_srcCcy_to_destCcy"]:
            for sourceCurrency in jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start]:
                fx_rate_case_when_query += f" WHEN ( DATE_TRUNC(DATE(usage_start_time), month) = '{billing_month_start}' and currency = '{sourceCurrency}' ) THEN CAST({jsonData['fx_rates_srcCcy_to_destCcy'][billing_month_start][sourceCurrency]} AS FLOAT64) "
        fx_rate_case_when_query += f" ELSE CAST(1.0 AS FLOAT64) END"

        query = """UPDATE `%s.%s.%s` 
                    SET fxRateSrcToDest = (%s) 
                    WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND 
                    DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY) 
                    AND currency IS NOT NULL;
                    """ % (self.PROJECTID, jsonData["datasetName"], jsonData["tableName"], fx_rate_case_when_query,
                        jsonData["gcpBillingExportTablePartitionColumnName"],
                        str(int(jsonData["interval"]) + 7),
                        jsonData["interval"])
        try:
            job_config = bigquery.QueryJobConfig(
                query_parameters=[
                    bigquery.ScalarQueryParameter(
                        "run_date",
                        "DATE",
                        datetime.datetime.utcnow().date(),
                    )
                ]
            )
            print_(query)
            query_job = self.client.query(query, job_config=job_config)
            query_job.result()  # wait for job to complete
        except Exception as e:
            print_(e)
            print_("Failed to update fxRateSrcToDest column in raw table %s.%s.%s" % (
                self.PROJECTID, jsonData["datasetName"], jsonData["tableName"]), "WARN")
            
    def alter_cost_export_table(self, jsonData: Any) -> None:
        print_("Altering %s Table" % jsonData["gcpCostExportTableTableName"])
        query = "ALTER TABLE `%s` \
            ADD COLUMN IF NOT EXISTS cost_at_list FLOAT64, \
            ADD COLUMN IF NOT EXISTS price STRUCT<effective_price NUMERIC, tier_start_amount NUMERIC, unit STRING, pricing_unit_quantity NUMERIC>, \
            ADD COLUMN IF NOT EXISTS resource STRUCT<name STRING, global_name STRING>;" % (jsonData["gcpCostExportTableTableName"])
        try:
            print_(query)
            query_job = self.client.query(query)
            query_job.result()
        except Exception as e:
            # Error Running Alter Query
            print_(e)
        else:
            print_("Finished Altering %s Table" % jsonData["gcpCostExportTableTableName"])
    
    def ingest_into_gcp_cost_export_table(self, gcp_cost_export_table_name: str, jsonData: Any) -> None:
        print_("Loading into %s table..." % gcp_cost_export_table_name)
        billing_export_columns = self.GCP_DETAILED_EXPORT_COLUMNS if jsonData["isBillingExportDetailed"] else self.GCP_STANDARD_EXPORT_COLUMNS
        insert_columns_query = ", ".join(f"{w}" for w in billing_export_columns)
        select_columns_query = self.__prepare_select_query(jsonData, billing_export_columns)
        query = """  DELETE FROM `%s` WHERE DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL %s DAY)  
                AND billing_account_id IN (%s);
           INSERT INTO `%s` (%s, fxRateSrcToDest, ccmPreferredCurrency)
                SELECT %s, %s as fxRateSrcToDest, %s as ccmPreferredCurrency  
                FROM `%s.%s.%s`
                WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND 
                DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY);
        """ % (gcp_cost_export_table_name, jsonData["interval"], jsonData["billingAccountIds"],
               gcp_cost_export_table_name, insert_columns_query,
               select_columns_query,
               ("fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
               (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData[
                   "ccmPreferredCurrency"] else "cast(null as string)"),
               self.PROJECTID, jsonData["datasetName"], jsonData["tableName"],
               jsonData["gcpBillingExportTablePartitionColumnName"],
               str(int(jsonData["interval"]) + 7),
               jsonData["interval"])

        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        query_job = self.client.query(query, job_config=job_config)
        print_(query)
        try:
            print_(query_job.job_id)
            query_job.result()
        except Exception as e:
            print_(query)
            raise e
        print_("  Loaded into intermediary gcp_cost_export table.")

    def ingest_into_preaggregated(self, jsonData: Any) -> None:
        print_("Loading into preaggregated table...")
        fx_rate_multiplier_query = "*fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else ""
        query = """  DELETE FROM `%s.preAggregated` WHERE DATE(startTime) >= DATE_SUB(@run_date , INTERVAL %s DAY) AND cloudProvider = "GCP"
                    AND gcpBillingAccountId IN (%s);
            INSERT INTO `%s.preAggregated` (cost, gcpProduct,gcpSkuId,gcpSkuDescription,
                startTime,gcpProjectId,region,zone,gcpBillingAccountId,cloudProvider, discount, fxRateSrcToDest, ccmPreferredCurrency) 
                SELECT SUM(cost %s) AS cost, service.description AS gcpProduct,
                sku.id AS gcpSkuId, sku.description AS gcpSkuDescription, TIMESTAMP_TRUNC(usage_start_time, DAY) as startTime, project.id AS gcpProjectId,
                location.region AS region, location.zone AS zone, billing_account_id AS gcpBillingAccountId, "GCP" AS cloudProvider, SUM(IFNULL((SELECT SUM(c.amount %s) FROM UNNEST(credits) c), 0)) as discount,
                %s as fxRateSrcToDest, %s as ccmPreferredCurrency 
            FROM `%s.%s`
            WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND
                DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY)
            GROUP BY service.description, sku.id, sku.description, startTime, project.id, location.region, location.zone, billing_account_id;
            """ % (jsonData["datasetName"], jsonData["interval"], jsonData["billingAccountIds"], jsonData["datasetName"],
                fx_rate_multiplier_query, fx_rate_multiplier_query,
                ("max(fxRateSrcToDest)" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
                (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData[
                    "ccmPreferredCurrency"] else "cast(null as string)"),
                jsonData["datasetName"], jsonData["tableName"], jsonData["gcpBillingExportTablePartitionColumnName"],
                str(int(jsonData["interval"]) + 7), jsonData["interval"])

        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        query_job = self.client.query(query, job_config=job_config)
        print_(query)
        try:
            print_(query_job.job_id)
            query_job.result()
        except Exception as e:
            print_(query)
            raise e
        print_("  Loaded into preAggregated table.")

    def ingest_into_unified(self, jsonData: Any) -> None:
        print_("Loading into unifiedTable table...")
        fx_rate_multiplier_query = "*fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else ""


        insert_columns = """product, cost, gcpProduct,gcpSkuId,gcpSkuDescription, startTime, endTime, gcpProjectId,
                    gcpProjectName, gcpProjectNumber,
                    region,zone,gcpBillingAccountId,cloudProvider, discount, labels, fxRateSrcToDest, ccmPreferredCurrency,
                    gcpInvoiceMonth, gcpCostType, gcpCredits, gcpUsage, gcpSystemLabels, gcpCostAtList"""

        select_columns = """service.description AS product, (cost %s) AS cost, service.description AS gcpProduct, sku.id AS gcpSkuId,
                        sku.description AS gcpSkuDescription, TIMESTAMP_TRUNC(usage_start_time, DAY) as startTime, TIMESTAMP_TRUNC(usage_end_time, DAY) as endTime, project.id AS gcpProjectId, project.name AS gcpProjectName, project.number AS gcpProjectNumber,
                        location.region AS region, location.zone AS zone, billing_account_id AS gcpBillingAccountId, "GCP" AS cloudProvider, (SELECT SUM(c.amount %s) FROM UNNEST(credits) c) as discount, labels AS labels,
                        %s as fxRateSrcToDest, %s as ccmPreferredCurrency, 
                        invoice.month as gcpInvoiceMonth, cost_type as gcpCostType, credits as gcpCredits, usage as gcpUsage, system_labels as gcpSystemLabels, cost_at_list as gcpCostAtList""" % (
                        fx_rate_multiplier_query, fx_rate_multiplier_query,
                        ("fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
                        (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData["ccmPreferredCurrency"] else "cast(null as string)"))

        # supporting additional fields in unifiedTable for Elevance
        if jsonData.get("isBillingExportDetailed", False):
            for additionalColumn in ["resource", "price"]:
                insert_columns = insert_columns + ", gcp%s" % (additionalColumn)
                select_columns = select_columns + ", %s as gcp%s" % (additionalColumn, additionalColumn)

        query = """  DELETE FROM `%s.unifiedTable` WHERE DATE(startTime) >= DATE_SUB(@run_date , INTERVAL %s DAY) AND cloudProvider = "GCP" 
                    AND gcpBillingAccountId IN (%s);
                INSERT INTO `%s.unifiedTable` (%s)
                        SELECT %s 
                        FROM `%s.%s`
                        WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND
                            DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY) ;
            """ % (jsonData["datasetName"], jsonData["interval"], jsonData["billingAccountIds"],
                jsonData["datasetName"], insert_columns,
                select_columns,
                jsonData["datasetName"], jsonData["tableName"],
                jsonData["gcpBillingExportTablePartitionColumnName"], str(int(jsonData["interval"]) + 7),
                jsonData["interval"])

        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        try:
            run_bq_query_with_retries(self.client, query, max_retry_count=3, job_config=job_config)
            flatten_label_keys_in_table(self.client, jsonData.get("accountId"), self.PROJECTID, jsonData["datasetName"], UNIFIED,
                                        "labels", self.__fetch_ingestion_filters(jsonData))
        except Exception as e:
            print_(query)
            raise e
        print_("  Loaded into unifiedTable table.")

    def update_connector_data_sync_status(self, jsonData: Any) -> None:
        update_connector_data_sync_status(jsonData, self.PROJECTID, self.client)

    def ingest_data_to_costagg(self, jsonData: Any) -> None:
        pass