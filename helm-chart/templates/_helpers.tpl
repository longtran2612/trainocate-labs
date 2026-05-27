{{/*
Chart name
*/}}
{{- define "money-transfer.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "money-transfer.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}

{{/*
Selector labels for a service
*/}}
{{- define "money-transfer.selectorLabels" -}}
app: {{ .name }}
{{- end }}
