<script setup lang="ts">
import type {TableColumn} from '@nuxt/ui'
import {gtfsDate} from '~/utils/gtfs'
import type {FeedDetailQuery, RevisionStatus} from '~~/generated/graphql'

type Rev = NonNullable<FeedDetailQuery['feed']>['revisions'][number]

defineProps<{ revisions: Rev[] }>()
const emit = defineEmits<{
  (e: 'activate' | 'delete', id: string): void
}>()

function statusColor(s: RevisionStatus): 'success' | 'error' | 'warning' | 'neutral' {
  if (s === 'ACTIVE' || s === 'READY') return 'success'
  if (s === 'FAILED') return 'error'
  if (s === 'SUPERSEDED' || s === 'UNCHANGED') return 'neutral'
  return 'warning'
}

function mb(bytes: number | null | undefined): string {
  return bytes ? `${(bytes / 1e6).toFixed(1)} MB` : '—'
}

const columns: TableColumn<Rev>[] = [
  {accessorKey: 'createdAt', header: 'Date'},
  {id: 'service', header: 'Service range'},
  {accessorKey: 'status', header: 'Status'},
  {id: 'size', header: 'Size'},
  {id: 'validation', header: 'Validation'},
  {id: 'actions', header: ''},
]
</script>

<template>
  <div class="flex flex-col gap-2">
    <div class="text-sm font-medium text-muted">Datasets</div>
    <UTable :data="revisions" :columns="columns">
      <template #createdAt-cell="{ row }">
        <div class="font-medium text-highlighted">{{ gtfsDate(row.original.createdAt) }}</div>
        <div class="text-xs text-dimmed">#{{ row.original.id }}</div>
      </template>
      <template #service-cell="{ row }">
        <span class="text-sm text-muted">
          {{ gtfsDate(row.original.feedStartDate) }} → {{ gtfsDate(row.original.feedEndDate) }}
        </span>
      </template>
      <template #status-cell="{ row }">
        <UBadge :color="statusColor(row.original.status)" variant="subtle">
          {{ row.original.status }}
        </UBadge>
      </template>
      <template #size-cell="{ row }">
        <span class="text-sm text-muted">{{ mb(row.original.byteSize) }}</span>
      </template>
      <template #validation-cell="{ row }">
        <UBadge
          v-if="row.original.validationSummary"
          :color="
            row.original.validationSummary.errorCount
              ? 'error'
              : row.original.validationSummary.warningCount
                ? 'warning'
                : 'success'
          "
          variant="subtle"
        >
          {{ row.original.validationSummary.errorCount }}e /
          {{ row.original.validationSummary.warningCount }}w
        </UBadge>
        <span v-else-if="row.original.errorMessage" class="text-xs text-error">
          {{ row.original.errorMessage }}
        </span>
        <span v-else class="text-xs text-dimmed">—</span>
      </template>
      <template #actions-cell="{ row }">
        <div class="flex justify-end gap-1">
          <UButton
            v-if="row.original.status !== 'ACTIVE' && row.original.status !== 'FAILED'"
            size="xs"
            color="neutral"
            variant="ghost"
            icon="i-lucide-circle-check"
            label="Activate"
            @click="emit('activate', row.original.id)"
          />
          <UButton
            v-if="row.original.status !== 'ACTIVE'"
            size="xs"
            color="error"
            variant="ghost"
            icon="i-lucide-trash-2"
            @click="emit('delete', row.original.id)"
          />
        </div>
      </template>
    </UTable>
  </div>
</template>
