<script setup lang="ts">
import type { CompareRevisionsQuery } from '~~/generated/graphql'

type CalendarChange = CompareRevisionsQuery['compareRevisions']['calendarChanges'][number]

defineProps<{ changes: CalendarChange[] }>()

function fieldEntries(
  fieldChanges: unknown,
): [string, { from?: string | null; to?: string | null }][] {
  if (!fieldChanges || typeof fieldChanges !== 'object') return []
  return Object.entries(fieldChanges as Record<string, { from?: string | null; to?: string | null }>)
}

function kindColor(kind: string): 'success' | 'error' | 'neutral' {
  if (kind === 'ADDED') return 'success'
  if (kind === 'REMOVED') return 'error'
  return 'neutral'
}
</script>

<template>
  <div>
    <h2 class="mb-2 text-sm font-semibold text-highlighted">Calendar changes</h2>
    <div v-if="!changes.length" class="text-sm text-muted">No calendar differences in this filter.</div>
    <div v-else class="divide-y divide-default rounded-lg border border-default">
      <div v-for="c in changes" :key="c.serviceId" class="px-3 py-2 text-xs">
        <div class="flex items-center gap-2">
          <span class="font-mono text-sm">{{ c.serviceId }}</span>
          <UBadge size="xs" :color="kindColor(c.kind)" variant="subtle">{{ c.kind }}</UBadge>
        </div>
        <div v-for="[field, v] in fieldEntries(c.fieldChanges)" :key="field" class="mt-1 text-muted">
          {{ field }}: {{ v.from ?? '—' }} → {{ v.to ?? '—' }}
        </div>
        <div v-if="c.exceptionChanges.length" class="mt-1">
          <div v-for="e in c.exceptionChanges" :key="e.date" class="text-muted">
            {{ e.date }}: {{ e.kind }} ({{ e.fromType ?? '—' }} → {{ e.toType ?? '—' }})
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
