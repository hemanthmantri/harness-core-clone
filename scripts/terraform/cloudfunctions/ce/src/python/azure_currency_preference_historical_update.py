# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import json
import base64
import os
import re
import datetime
import util
import requests

from util import create_dataset, if_tbl_exists, createTable, print_, run_batch_query, COSTAGGREGATED, UNIFIED, \
    PREAGGREGATED, CEINTERNALDATASET, CURRENCYCONVERSIONFACTORUSERINPUT, update_connector_data_sync_status, \
    add_currency_preferences_columns_to_schema, CURRENCY_LIST, BACKUP_CURRENCY_FX_RATES
from calendar import monthrange
from google.cloud import bigquery
from google.cloud import storage

"""
{
	"accountId": "nvsv7gjbtzya3cgsgxnocg",
	"cloudServiceProvider": "AZURE",
	"months": ["2022-12-01", "2022-11-01"], # pass only months for which there won't be any concurrent access with regular CFs
	"userInputfxRates": {
        "INR": 82.48
    } 
    # if fxRate not found in this list, use default rates from CUR / API
	# For Azure:
	# will update azurecost_2022_12_*, unified, preagg, costagg
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
storage_client = storage.Client(PROJECTID)


def main(request):
    # update costs only for those months in which historicalUpdateRequired is true in conversion_factor_userinput table
    try:
        print(request)
        jsonData = request.get_json(force=True)
        print(jsonData)

        # Set accountid for GCP logging
        util.ACCOUNTID_LOG = jsonData.get("accountId")

        jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
        jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]

        # fetch fxRates from billing_export & API/Default_table if userInput of CF doesn't have fxRate for ccy pair
        get_preferred_currency(jsonData)
        if not jsonData["ccmPreferredCurrency"]:
            return

        jsonData["fx_rates_srcCcy_to_destCcy"] = {}
        jsonData["source_currency_mapping"] = {}

        tables = client.list_tables(jsonData["datasetName"])
        for table in tables:
            if "azureBilling_" not in table.table_id:
                continue
            print_(f"Fetching from table: {table.table_id}")
            azure_column_mapping = setAvailableColumns(table.table_id, jsonData)
            initialize_fx_rates_dict(table.table_id, azure_column_mapping, jsonData)
        fetch_default_conversion_factors_from_API(jsonData)
        fetch_custom_conversion_factors(jsonData)
        verify_existence_of_required_conversion_factors(jsonData)

        tables = client.list_tables(jsonData["datasetName"])
        for table in tables:
            if "azurecost_" not in table.table_id:
                continue
            print_(f"Processing table: {table.table_id}")
            azurecost_tableid = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], table.table_id)
            add_currency_preferences_columns_to_schema(client, [azurecost_tableid])

            create_fx_rate_query(jsonData, "azureSubscriptionGuid", "startTime")
            update_costs_in_azurecost_table(azurecost_tableid, jsonData)

        ds = f"{PROJECTID}.{jsonData['datasetName']}"
        table_ids = ["%s.%s" % (ds, "unifiedTable"),
                     "%s.%s" % (ds, "preAggregated")]
        add_currency_preferences_columns_to_schema(client, table_ids)

        create_fx_rate_query(jsonData, "azureSubscriptionGuid", "startTime")
        update_costs_in_unified_table("%s.%s" % (ds, "unifiedTable"), jsonData)

        create_fx_rate_query(jsonData, "azureSubscriptionGuid", "startTime")
        update_costs_in_preagg_table("%s.%s" % (ds, "preAggregated"), jsonData)

        # ingest_data_to_costagg(jsonData)

        unset_historical_update_flag_in_bq(jsonData)

        return "Historical cost successfully converted in preferred currency."

    except Exception as e:
        print_(e)
        return "Failed to convert historical cost data in preferred currency."


def setAvailableColumns(table_name, jsonData):
    # Ref: https://docs.microsoft.com/en-us/azure/cost-management-billing/understand/mca-understand-your-usage
    # BQ column names are case insensitive.
    azure_column_mapping = {
        "startTime": "",
        "azureResourceRate": "",
        "cost": "",
        "region": "",
        "azureSubscriptionGuid": "",
        "azureInstanceId": "",
        "currency": "",
    }

    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = """SELECT column_name, data_type
            FROM %s.INFORMATION_SCHEMA.COLUMNS
            WHERE table_name="%s";
            """ % (ds, table_name)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        columns = set()
        for row in results:
            columns.add(row.column_name.lower())
        jsonData["columns"] = columns
        print_("Retrieved available columns: %s" % columns)
    except Exception as e:
        print_("Failed to retrieve available columns", "WARN")
        jsonData["columns"] = None
        raise e

    # startTime
    if "date" in columns:  # This is O(1) for python sets
        azure_column_mapping["startTime"] = "date"
    elif "usagedatetime" in columns:
        azure_column_mapping["startTime"] = "usagedatetime"
    else:
        raise Exception("No mapping found for startTime column")

    # azureResourceRate
    if "effectiveprice" in columns:
        azure_column_mapping["azureResourceRate"] = "effectiveprice"
    elif "resourcerate" in columns:
        azure_column_mapping["azureResourceRate"] = "resourcerate"
    else:
        raise Exception("No mapping found for azureResourceRate column")

    # currency
    if "billingcurrency" in columns:
        azure_column_mapping["currency"] = "billingcurrency"
    elif "currency" in columns:
        azure_column_mapping["currency"] = "currency"
    else:
        raise Exception("No mapping found for currency column")

    # cost
    if "costinbillingcurrency" in columns:
        azure_column_mapping["cost"] = "costinbillingcurrency"
    elif "pretaxcost" in columns:
        azure_column_mapping["cost"] = "pretaxcost"
    elif "cost" in columns:
        azure_column_mapping["cost"] = "cost"
    else:
        raise Exception("No mapping found for cost column")

    # azureSubscriptionGuid
    if "subscriptionid" in columns:
        azure_column_mapping["azureSubscriptionGuid"] = "subscriptionid"
    elif "subscriptionguid" in columns:
        azure_column_mapping["azureSubscriptionGuid"] = "subscriptionguid"
    else:
        raise Exception("No mapping found for azureSubscriptionGuid column")

    # azureInstanceId
    if "resourceid" in columns:
        azure_column_mapping["azureInstanceId"] = "resourceid"
    elif "instanceid" in columns:
        azure_column_mapping["azureInstanceId"] = "instanceid"
    else:
        raise Exception("No mapping found for azureInstanceId column")

    # azureResourceGroup
    if "resourcegroup" in columns:
        azure_column_mapping["azureResourceGroup"] = "resourcegroup"
    elif "resourcegroupname" in columns:
        azure_column_mapping["azureResourceGroup"] = "resourcegroupname"
    else:
        raise Exception("No mapping found for azureResourceGroup column")

    print_("azure_column_mapping: %s" % azure_column_mapping)
    return azure_column_mapping


def unset_historical_update_flag_in_bq(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = """UPDATE `%s.%s` 
                SET isHistoricalUpdateRequired = FALSE
                WHERE cloudServiceProvider = "AZURE" 
                AND month in (%s)
                AND accountId = '%s';
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                       ", ".join(f"DATE('{month}')" for month in jsonData["months"]),
                       jsonData.get("accountId"))
    print_(query)
    try:
        query_job = client.query(query)
        query_job.result()  # wait for job to complete
    except Exception as e:
        print_(e)
        print_("Failed to unset isHistoricalUpdateRequired flags in CURRENCYCONVERSIONFACTORUSERINPUT table", "WARN")


def ingest_data_to_costagg(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    table_name = "%s.%s.%s" % (PROJECTID, CEINTERNALDATASET, COSTAGGREGATED)
    source_table = "%s.%s" % (ds, UNIFIED)
    print_("Loading into %s table..." % table_name)
    query = """DELETE FROM `%s` WHERE DATE_TRUNC( DATE(day), month ) in (%s) AND cloudProvider = "AZURE" AND accountId = '%s';
               INSERT INTO `%s` (day, cost, cloudProvider, accountId) 
                SELECT TIMESTAMP_TRUNC(startTime, DAY) AS day, SUM(cost) AS cost, "AZURE" AS cloudProvider, '%s' as accountId 
                FROM `%s`  
                WHERE DATE_TRUNC( DATE(startTime), month ) in (%s) AND cloudProvider = "AZURE" 
                GROUP BY day;
     """ % (table_name, ", ".join(f"DATE('{month}')" for month in jsonData["months"]), jsonData.get("accountId"),
            table_name,
            jsonData.get("accountId"),
            source_table,
            ", ".join(f"DATE('{month}')" for month in jsonData["months"]))
    print_(query)

    job_config = bigquery.QueryJobConfig(
        priority=bigquery.QueryPriority.BATCH
    )
    run_batch_query(client, query, job_config, timeout=180)


def update_costs_in_preagg_table(table_id, jsonData):
    query = """UPDATE `%s` 
                SET fxRateSrcToDest = (%s), 
                ccmPreferredCurrency = '%s',
                azureResourceRate = (azureResourceRate * (%s)), 
                cost = (cost * (%s))
                WHERE DATE_TRUNC( DATE(startTime), month ) in (%s) AND cloudProvider = "AZURE"
                AND ccmPreferredCurrency is NULL;
                """ % (table_id, jsonData["fx_rate_query"], jsonData["ccmPreferredCurrency"],
                       jsonData["fx_rate_query"], jsonData["fx_rate_query"],
                       ", ".join(f"DATE('{month}')" for month in jsonData["months"]))
    try:
        job_config = bigquery.QueryJobConfig(
            priority=bigquery.QueryPriority.BATCH
        )
        run_batch_query(client, query, job_config, timeout=1800)
    except Exception as e:
        print_(e)
        print_("Failed to update cost columns in pre-aggregated table %s" % table_id, "WARN")


def update_costs_in_unified_table(table_id, jsonData):
    query = """UPDATE `%s` 
                SET fxRateSrcToDest = (%s), 
                ccmPreferredCurrency = '%s',
                cost = (cost * (%s)), 
                azureResourceRate = (azureResourceRate * (%s))
                WHERE DATE_TRUNC( DATE(startTime), month ) in (%s) AND cloudProvider = "AZURE" 
                AND ccmPreferredCurrency is NULL;
                """ % (table_id, jsonData["fx_rate_query"], jsonData["ccmPreferredCurrency"],
                       jsonData["fx_rate_query"], jsonData["fx_rate_query"],
                       ", ".join(f"DATE('{month}')" for month in jsonData["months"]))
    try:
        job_config = bigquery.QueryJobConfig(
            priority=bigquery.QueryPriority.BATCH
        )
        run_batch_query(client, query, job_config, timeout=1800)
    except Exception as e:
        print_(e)
        print_("Failed to update cost columns in unified table %s" % table_id, "WARN")


def update_costs_in_azurecost_table(azurecost_tableid, jsonData):
    query = """UPDATE `%s` 
                SET fxRateSrcToDest = (%s), 
                ccmPreferredCurrency = '%s',
                azureResourceRate = (azureResourceRate * (%s)), 
                cost = (cost * (%s))
                WHERE ccmPreferredCurrency is NULL 
                AND DATE_TRUNC( DATE(startTime), month ) in (%s);
                """ % (azurecost_tableid, jsonData["fx_rate_query"], jsonData["ccmPreferredCurrency"],
                       jsonData["fx_rate_query"], jsonData["fx_rate_query"],
                       ", ".join(f"DATE('{month}')" for month in jsonData["months"]))
    try:
        job_config = bigquery.QueryJobConfig(
            priority=bigquery.QueryPriority.BATCH
        )
        run_batch_query(client, query, job_config, timeout=1800)
    except Exception as e:
        print_(e)
        print_("Failed to update cost columns in azurecost table %s" % azurecost_tableid, "WARN")


def create_fx_rate_query(jsonData, subscriptionid_column_name, usagestartdate_column_name):
    jsonData["fx_rate_query"] = "CASE "
    for subscriptionid_usagemonth in jsonData["source_currency_mapping"]:
        billing_month = subscriptionid_usagemonth.split('_')[1]
        jsonData["fx_rate_query"] += "WHEN CONCAT( %s, '_', DATE_TRUNC(DATE(%s), month) ) = '%s' " \
                                     "THEN CAST(%s AS FLOAT64) " \
                                     % (subscriptionid_column_name, usagestartdate_column_name, subscriptionid_usagemonth,
                                        jsonData['fx_rates_srcCcy_to_destCcy'][billing_month][jsonData["source_currency_mapping"][subscriptionid_usagemonth]])
    jsonData["fx_rate_query"] += " WHEN 1=0 THEN CAST(1.0 AS FLOAT64) "
    jsonData["fx_rate_query"] += " ELSE CAST(1.0 AS FLOAT64) END"


def get_preferred_currency(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    jsonData["ccmPreferredCurrency"] = None
    query = """SELECT destinationCurrency
                FROM %s.%s
                WHERE accountId="%s" and destinationCurrency is NOT NULL LIMIT 1;
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT, jsonData.get("accountId"))
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["ccmPreferredCurrency"] = row.destinationCurrency.upper()
            print_("Found preferred-currency for account %s: %s" % (jsonData.get("accountId"), jsonData["ccmPreferredCurrency"]))
            break
    except Exception as e:
        print_(e)
        print_("Failed to fetch preferred currency for account", "WARN")

    if jsonData["ccmPreferredCurrency"] is None:
        print_("No preferred-currency found for account %s" % jsonData.get("accountId"))


def initialize_fx_rates_dict(table_name, azure_column_mapping, jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return
    # jsonData["months"] = set()
    billing_export_fx_rate_query = ", max(pricingCurrency) as destinationCcy, 1.0 / max(exchangeRatePricingToBilling) as fxRate" if "exchangeratepricingtobilling" in jsonData["columns"] else ""
    query = """SELECT %s as subscriptionId, DATE_TRUNC(DATE(%s), month) as usagemonth, max(%s) as srcCcy %s
                FROM `%s.%s.%s`
                WHERE DATE_TRUNC( DATE(%s), month ) in (%s)
                GROUP BY %s, %s;
                """ % (azure_column_mapping["azureSubscriptionGuid"], azure_column_mapping["startTime"],
                       azure_column_mapping["currency"], billing_export_fx_rate_query, PROJECTID, jsonData["datasetName"], table_name,
                       azure_column_mapping["startTime"], ", ".join(f"DATE('{month}')" for month in jsonData["months"]),
                       azure_column_mapping["azureSubscriptionGuid"], azure_column_mapping["startTime"])
    print_(query)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["source_currency_mapping"][str(row.subscriptionId) + "_" + str(row.usagemonth)] = str(row.srcCcy)
            # jsonData["months"].add(str(row.usagemonth))
            fx_rate = row.fxRate if "exchangeratepricingtobilling" in jsonData["columns"] and row.destinationCcy == jsonData["ccmPreferredCurrency"] else None
            if str(row.usagemonth) not in jsonData["fx_rates_srcCcy_to_destCcy"]:
                jsonData["fx_rates_srcCcy_to_destCcy"][str(row.usagemonth)] = {row.srcCcy.upper(): fx_rate}
            else:
                jsonData["fx_rates_srcCcy_to_destCcy"][str(row.usagemonth)][row.srcCcy.upper()] = fx_rate
    except Exception as e:
        print_("Failed to list distinct AZURE source-currencies for account", "WARN")


def fetch_default_conversion_factors_from_API(jsonData):
    # preferred currency should've been obtained at this point if it was set by customer
    if jsonData["ccmPreferredCurrency"] is None:
        return

    print_("Fetching default conversion factors from API..")
    for billing_month in jsonData["months"]:
        # fetch conversion factors from the API for all sourceCurrencies vs USD and destCcy vs USD for that DATE.
        # cdn.jsdelivr.net api returns currency-codes in lowercase
        try:
            response = requests.get(f"https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/{billing_month}/currencies/usd.json")
            fx_rates_from_api = response.json()
        except Exception as e:
            print_(e)
            fx_rates_from_api = BACKUP_CURRENCY_FX_RATES[billing_month]

        for srcCurrency in fx_rates_from_api["usd"]:
            if srcCurrency.upper() not in CURRENCY_LIST:
                continue
            # ensure precision of fx rates while performing operations
            try:
                # 1 usd = x src
                # 1 usd = y dest
                # 1 src = (y/x) dest
                if srcCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"][billing_month] and jsonData["fx_rates_srcCcy_to_destCcy"][billing_month][srcCurrency.upper()] is None:
                    jsonData["fx_rates_srcCcy_to_destCcy"][billing_month][srcCurrency.upper()] = \
                        fx_rates_from_api["usd"][jsonData["ccmPreferredCurrency"].lower()] / fx_rates_from_api["usd"][srcCurrency]
            except Exception as e:
                print_(e, "WARN")
                print_(f"fxRate for {srcCurrency} to {jsonData['ccmPreferredCurrency']} was not found in API response.")


def fetch_custom_conversion_factors(jsonData):
    if jsonData["ccmPreferredCurrency"] is None:
        return
    print_("Fetching custom conversion factors..")
    for billing_month in jsonData["fx_rates_srcCcy_to_destCcy"]:
        for source_currency in jsonData["userInputFxRates"]:
            if source_currency in jsonData["fx_rates_srcCcy_to_destCcy"][billing_month]:
                jsonData["fx_rates_srcCcy_to_destCcy"][billing_month][source_currency] = float(jsonData["userInputFxRates"][source_currency])


def verify_existence_of_required_conversion_factors(jsonData):
    # preferred currency should've been obtained at this point if it was set by customer
    if not jsonData["ccmPreferredCurrency"]:
        return

    for billing_month_start in jsonData["fx_rates_srcCcy_to_destCcy"]:
        if not all(jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start].values()):
            print_(jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start])
            print_("Required fx rate not found for at least one currency pair", "ERROR")
            # throw error here. CF execution can't proceed from here