<script setup lang="ts">
import type {TableColumn} from '@nuxt/ui'
import {useOptimizationRecommendationsQuery, type OptimizationRecommendationsQuery} from '~~/generated/graphql'

const props = defineProps<{ runId: string }>()
const selected = defineModel<string[]>('selected', {default: () => []})

const {result, loading, error, refetch} = useOptimizationRecommendationsQuery(
  () => ({runId: props.runId, status: 'PENDING', offset: 0, limit: 100}),
)

defineExpose({refetch})

type Recommendation = OptimizationRecommendationsQuery['optimizationRecommendations'][number]
const recommendations = computed<Recommendation[]>(
  () => result.value?.optimizationRecommendations ?? [],
)

const conflictIds = defineModel<string[]>('conflictIds', {default: () => []})

const columns: TableColumn<Recommendation>[] = [
  {id: 'select', header: ''},
  {accessorKey: 'kind', header: 'Kind'},
  {accessorKey: 'deltaSec', header: 'Delta (s)'},
  {accessorKey: 'sampleCount', header: 'Samples'},
  {accessorKey: 'reason', header: 'Reason'},
]

function toggle(id: string, checked: boolean) {
  if (checked) {
    if (!selected.value.includes(id)) selected.value = [...selected.value, id]
  } else {
    selected.value = selected.value.filter((x) => x !== id)
  }
}

function target(row: Recommendation): string {
  const proposed = row.proposedValue as { targets?: Array<Record<string, unknown>> }
  const targets = proposed?.targets ?? []
  if (!targets.length) return '—'
  return targets
    .map((t) => (t.stopSequence != null ? `${t.tripId}#${t.stopSequence}` : String(t.tripId)))
    .join(', ')
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div v-if="loading && !recommendations.length" class="flex flex-col gap-4">
      <USkeleton class="h-40 w-full" />
    </div>

    <UAlert
      v-else-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Recommendations unavailable"
      :description="error.message"
    />

    <div v-else-if="!recommendations.length" class="flex flex-col items-center gap-3 py-16 text-center">
      <UIcon name="i-lucide-inbox" class="size-8 text-dimmed" />
      <p class="text-sm text-muted">
        No recommendations met the configured thresholds for this selection.
      </p>
    </div>

    <UTable v-else :data="recommendations" :columns="columns">
      <template #select-cell="{ row }">
        <UCheckbox
          :model-value="selected.includes(row.original.id)"
          @update:model-value="(v: boolean) => toggle(row.original.id, v)"
        />
      </template>
      <template #kind-cell="{ row }">
        <UBadge variant="subtle">{{ row.original.kind }}</UBadge>
      </template>
      <template #deltaSec-cell="{ row }">
        <span :class="row.original.deltaSec >= 0 ? 'text-success' : 'text-error'">
          {{ row.original.deltaSec >= 0 ? '+' : '' }}{{ row.original.deltaSec }}
        </span>
      </template>
      <template #reason-cell="{ row }">
        <div>
          <p class="text-sm">{{ row.original.reason }}</p>
          <p class="text-xs text-dimmed">{{ target(row.original) }}</p>
          <UBadge
            v-if="conflictIds.includes(row.original.id)"
            color="error"
            variant="subtle"
            class="mt-1"
          >
            conflicts with another selection
          </UBadge>
        </div>
      </template>
    </UTable>
  </div>
</template>
