<script setup lang="ts">
import type { CompareRevisionsQuery } from '~~/generated/graphql'

type TripChange = CompareRevisionsQuery['compareRevisions']['tripChanges'][number]

const props = defineProps<{ changes: TripChange[]; totalCount?: number }>()
const { t, te } = useI18n()

const expanded = ref<Set<string>>(new Set())
function toggle(tripId: string) {
  if (expanded.value.has(tripId)) expanded.value.delete(tripId)
  else expanded.value.add(tripId)
}

function isExpandable(c: TripChange): boolean {
  return c.kind === 'MODIFIED'
}

const truncated = computed(
  () => props.totalCount != null && props.totalCount > props.changes.length,
)

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
    <h2 class="mb-2 text-sm font-semibold text-highlighted">{{ $t('compare.tripChanges') }}</h2>
    <p v-if="truncated" class="mb-2 text-xs text-dimmed">
      {{ $t('compare.showingFirst', { shown: changes.length, count: totalCount }, totalCount ?? 0) }}
    </p>
    <div v-if="!changes.length" class="text-sm text-muted">{{ $t('compare.noTripDiff') }}</div>
    <div v-else class="divide-y divide-default rounded-md border border-default">
      <div v-for="c in changes" :key="c.tripId">
        <button
          type="button"
          class="flex w-full items-center justify-between gap-2 px-3 py-2 text-left"
          :class="isExpandable(c) ? 'hover:bg-elevated' : 'cursor-default'"
          @click="isExpandable(c) && toggle(c.tripId)"
        >
          <div class="flex items-center gap-2">
            <UIcon
              v-if="isExpandable(c)"
              :name="expanded.has(c.tripId) ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'"
              class="size-4 text-dimmed"
            />
            <span class="font-mono text-sm">{{ c.tripId }}</span>
            <UBadge size="xs" :color="kindColor(c.kind)" variant="subtle">{{ kindLabel(c.kind) }}</UBadge>
          </div>
          <span
            v-if="c.runTimeDeltaSec != null"
            class="text-xs"
            :class="c.runTimeDeltaSec > 0 ? 'text-error' : c.runTimeDeltaSec < 0 ? 'text-success' : 'text-dimmed'"
          >
            {{ $t('compare.runTimeDelta', { delta: (c.runTimeDeltaSec > 0 ? '+' : '') + c.runTimeDeltaSec }) }}
          </span>
        </button>
        <div v-if="isExpandable(c) && expanded.has(c.tripId)" class="space-y-2 bg-elevated/50 px-6 py-3 text-xs">
          <div v-for="[field, v] in fieldEntries(c.fieldChanges)" :key="field">
            <span class="text-muted">{{ $t('compare.fieldLabel', { field }) }}</span> {{ v.from ?? '—' }} → {{ v.to ?? '—' }}
          </div>
          <table v-if="c.stopChanges.length" class="w-full text-left">
            <thead class="text-dimmed">
              <tr>
                <th class="pr-3">{{ $t('compare.seq') }}</th>
                <th class="pr-3">{{ $t('compare.kindCol') }}</th>
                <th class="pr-3">{{ $t('explore.col.stop') }}</th>
                <th class="pr-3">{{ $t('compare.arrDelta') }}</th>
                <th>{{ $t('compare.depDelta') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in c.stopChanges" :key="s.stopSequence">
                <td class="pr-3">{{ s.stopSequence }}</td>
                <td class="pr-3">{{ kindLabel(s.kind) }}</td>
                <td class="pr-3">{{ s.stopId ?? '—' }}</td>
                <td class="pr-3">{{ s.arrivalDeltaSec ?? '—' }}</td>
                <td>{{ s.departureDeltaSec ?? '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
