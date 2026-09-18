{{- define "transittrack.fullname" -}}
{{ .Chart.Name }}
{{- end -}}

{{- define "transittrack.labels" -}}
app.kubernetes.io/name: {{ include "transittrack.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
