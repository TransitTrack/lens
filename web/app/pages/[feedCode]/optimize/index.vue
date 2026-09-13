<script setup lang="ts">
import type {TableColumn} from '@nuxt/ui'
import {useOptimizationRunsQuery, type OptimizationRunsQuery} from '~~/generated/graphql'
import {useFeeds} from '~/composables/useFeeds'
import AppPage from '~/components/AppPage.vue'
import NewOptimizationRunDialog from '~/components/optimize/NewOptimizationRunDialog.vue'
import {gtfsDate} from '~/utils/gtfs'

const {selectedFeedCode, feedPath} = useFeeds()
const newOpen = ref(false)

const {result, loading, error} = useOptimizationRunsQuery(
  () => ({feedCode: selectedFeedCode.value ?? '', limit: 50, offset: 0}),
  () => ({enabled: !!selectedFeedCode.value, pollInterval: 5000}),
)

type Run = OptimizationRunsQuery['optimizationRuns'][number]
const runs = computed<Run[]>(() => result.value?.optimizationRuns ?? [])

const columns: TableColumn<Run>[] = [
  {accessorKey: 'id', header: 'Run'},
  {accessorKey: 'state', header: 'State'},
  {id: 'created', header: 'Created'},
  {id: 'completed', header: 'Completed'},
]

const STATE_COLOR: Record<string, 'neutral' | 'info' | 'success' | 'error'> = {
  QUEUED: 'neutral',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
}

function openRun(row: Run) {
  navigateTo(feedPath('/optimize/' + row.id))
}
</script>

<template>
  <AppPage title="Optimize">
    <template #actions>
      <UButton
        icon="i-lucide-plus"
        color="neutral"
        variant="soft"
        label="New run"
        @click="newOpen = true"
      />
    </template>

    <div v-if="loading && !runs.length" class="flex flex-col gap-4">
      <USkeleton class="h-10 w-full" />
      <USkeleton class="h-40 w-full" />
    </div>

    <UAlert
      v-else-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Runs unavailable"
      :description="error.message"
    />

    <div v-else-if="!runs.length" class="flex flex-col items-center gap-3 py-16 text-center">
      <UIcon name="i-lucide-sparkles" class="size-8 text-dimmed" />
      <p class="text-sm text-muted">No optimization runs yet for this feed.</p>
      <UButton icon="i-lucide-plus" label="New run" @click="newOpen = true" />
    </div>

    <UTable v-else :data="runs" :columns="columns" @select="(_e, row) => openRun(row.original)">
      <template #id-cell="{ row }">#{{ row.original.id }}</template>
      <template #state-cell="{ row }">
        <UBadge :color="STATE_COLOR[row.original.state] ?? 'neutral'" variant="subtle">
          {{ row.original.state }}
        </UBadge>
      </template>
      <template #created-cell="{ row }">{{ gtfsDate(row.original.createdAt) }}</template>
      <template #completed-cell="{ row }">
        {{ row.original.completedAt ? gtfsDate(row.original.completedAt) : '—' }}
      </template>
    </UTable>

    <NewOptimizationRunDialog v-model:open="newOpen" />
  </AppPage>
</template>
