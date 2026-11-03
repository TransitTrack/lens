<script setup lang="ts">
import { computed } from 'vue'

const { te, t } = useI18n()

const props = defineProps<{
  lastValidation: unknown
  derivationStale: boolean
}>()

// `goto` stays in the contract for a later task; unused until the backend
// enriches notices with tripId / stopSequence.
defineEmits<{ goto: [payload: { tripId: string; stopSequence: number }] }>()

interface Notice {
  severity: string
  code: string
  message: string
}
interface Parsed {
  errorCount: number
  warningCount: number
  notices: Notice[]
}

const parsed = computed<Parsed | null>(() => {
  let v: unknown = props.lastValidation
  if (v == null) return null
  if (typeof v === 'string') {
    try {
      v = JSON.parse(v)
    } catch {
      return null
    }
  }
  if (typeof v !== 'object' || v === null) return null
  const obj = v as Record<string, unknown>
  const rawNotices = Array.isArray(obj.notices) ? obj.notices : []
  const notices: Notice[] = rawNotices
    .filter((n): n is Record<string, unknown> => typeof n === 'object' && n !== null)
    .map((n) => {
      const sev = String(n.severity ?? 'INFO').toUpperCase()
      return {
        severity: sev === 'ERROR' || sev === 'WARNING' || sev === 'INFO' ? sev : 'INFO',
        code: String(n.code ?? ''),
        message: String(n.message ?? ''),
      }
    })
  return {
    errorCount: typeof obj.errorCount === 'number' ? obj.errorCount : 0,
    warningCount: typeof obj.warningCount === 'number' ? obj.warningCount : 0,
    notices,
  }
})

const groups = computed(() => {
  const g: Record<string, Notice[]> = { ERROR: [], WARNING: [], INFO: [] }
  for (const n of parsed.value?.notices ?? []) {
    ;(g[n.severity] ??= []).push(n)
  }
  return g
})

const order = ['ERROR', 'WARNING', 'INFO']

// i18n-keys: drafts.severity.*
function severityLabel(sev: string): string {
  const key = `drafts.severity.${sev}`
  return te(key) ? t(key) : sev
}
</script>

<template>
  <div class="flex flex-col gap-3 p-3 text-sm">
    <p v-if="derivationStale" class="text-xs text-muted">{{ $t('drafts.rebuildValidation') }}</p>

    <template v-else-if="parsed">
      <div class="flex gap-2 text-xs">
        <UBadge color="error" variant="subtle">{{ $t('drafts.errors', { count: parsed.errorCount }, parsed.errorCount) }}</UBadge>
        <UBadge color="warning" variant="subtle">{{ $t('drafts.warnings', { count: parsed.warningCount }, parsed.warningCount) }}</UBadge>
      </div>

      <p v-if="parsed.notices.length === 0" class="text-xs text-muted">{{ $t('drafts.noNotices') }}</p>

      <!-- FOLLOW-UP: per-notice go-to once backend enriches notices with tripId/stopSequence -->
      <div v-for="sev in order" :key="sev">
        <div v-if="groups[sev]?.length" class="mb-1 text-xs font-semibold uppercase text-muted">
          {{ severityLabel(sev) }}
        </div>
        <ul class="flex flex-col gap-1">
          <li v-for="(n, i) in groups[sev] ?? []" :key="sev + i" class="text-xs">
            <span class="font-semibold">{{ n.code }}</span>
            <span class="text-muted"> — {{ n.message }}</span>
          </li>
        </ul>
      </div>
    </template>

    <p v-else class="text-xs text-muted">{{ $t('drafts.noValidation') }}</p>
  </div>
</template>
