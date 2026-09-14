<script setup lang="ts">
import type { CompareRevisionsQuery } from '~~/generated/graphql'

type TripChange = CompareRevisionsQuery['compareRevisions']['tripChanges'][number]

const props = defineProps<{ changes: TripChange[]; totalCount?: number }>()

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

function kindColor(kind: string): 'success' | 'error' | 'neutral' {
  if (kind === 'ADDED') return 'success'
  if (kind === 'REMOVED') return 'error'
  return 'neutral'
}
</script>

<template>
  <div>
    <h2 class="mb-2 text-sm font-semibold text-highlighted">Trip changes</h2>
    <p v-if="truncated" class="mb-2 text-xs text-dimmed">
      Showing first {{ changes.length }} of {{ totalCount }} changed trips.
    </p>
    <div v-if="!changes.length" class="text-sm text-muted">No trip differences in this filter.</div>
    <div v-else class="divide-y divide-default rounded-lg border border-default">
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
            <UBadge size="xs" :color="kindColor(c.kind)" variant="subtle">{{ c.kind }}</UBadge>
          </div>
          <span
            v-if="c.runTimeDeltaSec != null"
            class="text-xs"
            :class="c.runTimeDeltaSec > 0 ? 'text-error' : c.runTimeDeltaSec < 0 ? 'text-success' : 'text-dimmed'"
          >
            {{ c.runTimeDeltaSec > 0 ? '+' : '' }}{{ c.runTimeDeltaSec }}s run time
          </span>
        </button>
        <div v-if="isExpandable(c) && expanded.has(c.tripId)" class="space-y-2 bg-elevated/50 px-6 py-3 text-xs">
          <div v-for="[field, v] in fieldEntries(c.fieldChanges)" :key="field">
            <span class="text-muted">{{ field }}:</span> {{ v.from ?? '—' }} → {{ v.to ?? '—' }}
          </div>
          <table v-if="c.stopChanges.length" class="w-full text-left">
            <thead class="text-dimmed">
              <tr>
                <th class="pr-3">Seq</th>
                <th class="pr-3">Kind</th>
                <th class="pr-3">Stop</th>
                <th class="pr-3">Arr Δ</th>
                <th>Dep Δ</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in c.stopChanges" :key="s.stopSequence">
                <td class="pr-3">{{ s.stopSequence }}</td>
                <td class="pr-3">{{ s.kind }}</td>
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
