<script setup lang="ts">
import type {TableColumn} from '@nuxt/ui'
import {gtfsDate} from '~/utils/gtfs'
import type {FeedDetailQuery, RevisionStatus} from '~~/generated/graphql'

type Rev = NonNullable<FeedDetailQuery['feed']>['revisions'][number]

const { t, te, locale } = useI18n()

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

const columns = computed<TableColumn<Rev>[]>(() => [
  {accessorKey: 'createdAt', header: t('feed.dataset.date')},
  {id: 'service', header: t('feed.dataset.serviceRange')},
  {accessorKey: 'status', header: t('feed.dataset.status')},
  {id: 'size', header: t('feed.dataset.size')},
  {id: 'validation', header: t('feed.dataset.validation')},
  {id: 'actions', header: ''},
])

// i18n-keys: feed.dataset.statusValue.*
function statusLabel(status: string): string {
  const key = `feed.dataset.statusValue.${status}`
  return te(key) ? t(key) : status
}
</script>

<template>
  <div class="flex flex-col gap-2 p-4">
    <div class="text-sm font-medium text-muted">{{ $t('feed.dataset.title') }}</div>
    <UTable :data="revisions" :columns="columns">
      <template #createdAt-cell="{ row }">
        <div class="font-medium text-highlighted">{{ gtfsDate(row.original.createdAt, locale) }}</div>
        <div class="text-xs text-dimmed">#{{ row.original.id }}</div>
      </template>
      <template #service-cell="{ row }">
        <span class="text-sm text-muted">
          {{ gtfsDate(row.original.feedStartDate, locale) }} → {{ gtfsDate(row.original.feedEndDate, locale) }}
        </span>
      </template>
      <template #status-cell="{ row }">
        <UBadge :color="statusColor(row.original.status)" variant="subtle">
          {{ statusLabel(row.original.status) }}
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
          {{
            $t('feed.dataset.validationShort', {
              errors: row.original.validationSummary.errorCount,
              warnings: row.original.validationSummary.warningCount,
            })
          }}
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
            :label="$t('feed.dataset.activate')"
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
