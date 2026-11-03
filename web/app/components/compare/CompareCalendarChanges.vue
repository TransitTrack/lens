<script setup lang="ts">
import type { CompareRevisionsQuery } from '~~/generated/graphql'

type CalendarChange = CompareRevisionsQuery['compareRevisions']['calendarChanges'][number]

defineProps<{ changes: CalendarChange[] }>()
const { t, te } = useI18n()

function fieldEntries(
  fieldChanges: unknown,
): [string, { from?: string | null; to?: string | null }][] {
  if (!fieldChanges || typeof fieldChanges !== 'object') return []
  return Object.entries(fieldChanges as Record<string, { from?: string | null; to?: string | null }>)
}

// i18n-keys: compare.kind.*
function kindLabel(kind: string): string {
  const key = `compare.kind.${kind}`
  return te(key) ? t(key) : kind
}

function kindColor(kind: string): 'success' | 'error' | 'neutral' {
  if (kind === 'ADDED') return 'success'
  if (kind === 'REMOVED') return 'error'
  return 'neutral'
}
</script>

<template>
  <div>
    <h2 class="mb-2 text-sm font-semibold text-highlighted">{{ $t('compare.calendarChanges') }}</h2>
    <div v-if="!changes.length" class="text-sm text-muted">{{ $t('compare.noCalendarDiff') }}</div>
    <div v-else class="divide-y divide-default rounded-md border border-default">
      <div v-for="c in changes" :key="c.serviceId" class="px-3 py-2 text-xs">
        <div class="flex items-center gap-2">
          <span class="font-mono text-sm">{{ c.serviceId }}</span>
          <UBadge size="xs" :color="kindColor(c.kind)" variant="subtle">{{ kindLabel(c.kind) }}</UBadge>
        </div>
        <div v-for="[field, v] in fieldEntries(c.fieldChanges)" :key="field" class="mt-1 text-muted">
          {{ $t('compare.fieldChange', { field, from: v.from ?? '—', to: v.to ?? '—' }) }}
        </div>
        <div v-if="c.exceptionChanges.length" class="mt-1">
          <div v-for="e in c.exceptionChanges" :key="e.date" class="text-muted">
            {{ $t('compare.exception', { date: e.date, kind: kindLabel(e.kind), from: e.fromType ?? '—', to: e.toType ?? '—' }) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
