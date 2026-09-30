{{- define "lens.fullname" -}}
{{ .Chart.Name }}
{{- end -}}

{{- define "lens.labels" -}}
app.kubernetes.io/name: {{ include "lens.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
