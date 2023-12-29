{{/*
Expand the name of the chart.
*/}}
{{- define "debezium-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "debezium-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "debezium-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "debezium-service.labels" -}}
helm.sh/chart: {{ include "debezium-service.chart" . }}
{{ include "debezium-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "debezium-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "debezium-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "debezium-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "debezium-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}


{{/*
Helper function for pullSecrets at chart level or global level.
*/}}
{{- define "debezium-service.pullSecrets" -}}
{{ include "common.images.pullSecrets" (dict "images" (list .Values.image .Values.waitForInitContainer.image) "global" .Values.global ) }}
{{- end -}}

{{/* 
Generates comma separated list of Mongo Protocol
*/}}
{{- define "debezium-service.mongoProtocol" }}
    {{- $ := .ctx }}
    {{- $database := .database }}
    {{- if empty $database }}
        {{- fail "ERROR: missing input argument - database" }}
    {{- end }}
    {{- $instanceName := include "harnesscommon.dbv3.generateInstanceName" (dict "database" $database) }}
    {{- if empty $instanceName }}
        {{- fail "ERROR: invalid instanceName value" }}
    {{- end }}
    {{- $localDBCtx := get $.Values.database.mongo $instanceName }}
    {{- $globalDBCtx := $.Values.global.database.mongo }}
    {{- if and $ $localDBCtx $globalDBCtx }}
        {{- $localEnabled := dig "enabled" false $localDBCtx }}
        {{- $installed := dig "installed" true $globalDBCtx }}
        {{- $protocol := "" }}
        {{- if $localEnabled }}
            {{- $protocol = $localDBCtx.protocol }}
        {{- else if $installed }}
            {{- $protocol = "mongodb://" }}
        {{- else }}
            {{- $protocol = $globalDBCtx.protocol }}
        {{- end }}
{{- printf "%s://" $protocol | quote }}
    {{- else }}
        {{- fail (printf "ERROR: invalid contexts") }}
    {{- end }}
{{- end }}

{{/* 
Generates comma separated list of Mongo Host names based off environment 
*/}}
{{- define "debezium-service.mongohosts" }}
    {{- $ := .ctx }}
    {{- $database := .database }}
    {{- if empty $database }}
        {{- fail "ERROR: missing input argument - database" }}
    {{- end }}
    {{- $instanceName := include "harnesscommon.dbv3.generateInstanceName" (dict "database" $database) }}
    {{- if empty $instanceName }}
        {{- fail "ERROR: invalid instanceName value" }}
    {{- end }}
    {{- $localDBCtx := get $.Values.database.mongo $instanceName }}
    {{- $globalDBCtx := $.Values.global.database.mongo }}
    {{- if and $ $localDBCtx $globalDBCtx }}
        {{- $localEnabled := dig "enabled" false $localDBCtx }}
        {{- $installed := dig "installed" true $globalDBCtx }}
        {{- $mongoHosts := "" }}
        {{- if $localEnabled }}
            {{- $hosts := $.Values.mongoHosts}}
            {{- $mongoHosts = (join "," $hosts ) }}
        {{- else if $installed }}
            {{- $namespace := $.Release.Namespace }}
            {{- if $.Values.global.ha }}
                {{- $mongoHosts = printf "'mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-1.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-2.mongodb-replicaset-chart.%s.svc:27017'" $namespace $namespace $namespace }}
            {{- else }}
                {{- $mongoHosts = printf "'mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc'" $namespace }}
            {{- end }}
        {{- else }}
            {{- $hosts := $.Values.mongoHosts}}
            {{- $mongoHosts = (join "," $hosts ) }}
        {{- end }}
{{- printf "%s" $mongoHosts }}
    {{- else }}
        {{- fail (printf "ERROR: invalid contexts") }}
    {{- end }}
{{- end }}

{{/* Generates Mongo Connection string
{{ include "debezium-service.mongoConnectionUrl" (dict "database" "foo" "context" $) }}
*/}}
{{- define "debezium-service.mongoConnectionUrl" }}
    {{- $ := .ctx }}
    {{- $database := .database }}
    {{- if empty $database }}
        {{- fail "ERROR: missing input argument - database" }}
    {{- end }}
    {{- $instanceName := include "harnesscommon.dbv3.generateInstanceName" (dict "database" $database) }}
    {{- if empty $instanceName }}
        {{- fail "ERROR: invalid instanceName value" }}
    {{- end }}
    {{- $localDBCtx := get $.Values.database.mongo $instanceName }}
    {{- $globalDBCtx := $.Values.global.database.mongo }}
    {{- if and $ $localDBCtx $globalDBCtx }}
        {{- $localEnabled := dig "enabled" false $localDBCtx }}
        {{- $installed := dig "installed" true $globalDBCtx }}
        {{- if $localEnabled }}
            {{- $hosts := $localDBCtx.hosts }}
            {{- $extraArgs := $localDBCtx.extraArgs }}
            {{- $args := (printf "/%s?%s" $database $extraArgs ) }}
            {{- $finalhost := (index $hosts  0) }}
            {{- range $host := (rest $hosts ) }}
                {{- $finalhost = printf "%s,%s" $finalhost $host }}
            {{- end }}
            {{- $connectionString := (printf "%s%s" $finalhost $args) }}
            {{- printf "%s" $connectionString }}
        {{- else if $installed }}
            {{- $namespace := $.Release.Namespace }}
            {{- if $.Values.global.ha }}
            {{- printf "'mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-1.mongodb-replicaset-chart.%s.svc,mongodb-replicaset-chart-2.mongodb-replicaset-chart.%s.svc:27017/%s?replicaSet=rs0&authSource=admin'" $namespace $namespace $namespace .database }}
            {{- else }}
                {{- printf "'mongodb-replicaset-chart-0.mongodb-replicaset-chart.%s.svc/%s?authSource=admin'" $namespace .database }}
            {{- end }}
        {{- else }}
            {{- $hosts := $globalDBCtx.hosts }}
            {{- $extraArgs := $globalDBCtx.extraArgs }}
            {{- $args := (printf "/%s?%s" $database $extraArgs ) }}
            {{- $finalhost := (index $hosts  0) }}
            {{- range $host := (rest $hosts ) }}
                {{- $finalhost = printf "%s,%s" $finalhost $host }}
            {{- end }}
            {{- $connectionString := (printf "%s%s" $finalhost $args) }}
            {{- printf "%s" $connectionString }}
        {{- end }}
    {{- else }}
        {{- fail (printf "ERROR: invalid contexts") }}
    {{- end }}
{{- end }}
