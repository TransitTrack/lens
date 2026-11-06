<script setup lang="ts">
import type { TableColumn, DropdownMenuItem } from '@nuxt/ui'
import { useDraftsQuery, useDiscardDraftMutation, type DraftsQuery } from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import AppPage from '~/components/AppPage.vue'
import PageState from '~/components/PageState.vue'
import NewDraftDialog from '~/components/draft/NewDraftDialog.vue'
import { gtfsDate } from '~/utils/gtfs'
import { useEditorIdentity } from '~/composables/useEditorIdentity'

const { selectedFeedCode, feedPath } = useFeeds()
const { name: editorName } = useEditorIdentity()
const toast = useToast()
const { t, locale } = useI18n()

const newOpen = ref(false)

const { result, loading, error, refetch } = useDraftsQuery(() => ({
  variables: { feedCode: selectedFeedCode.value },
  enabled: !!selectedFeedCode.value,
}))

type Draft = DraftsQuery['drafts'][number]
const drafts = computed<Draft[]>(() => result.value?.drafts ?? [])

interface ValidationSummary {
  errorCount?: number
  warningCount?: number
}

function parseValidation(raw: unknown): ValidationSummary | null {
  if (raw == null) return null
  let value: unknown = raw
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value)
    } catch {
      return null
    }
  }
  if (typeof value !== 'object' || value === null) return null
  const v = value as ValidationSummary
  if (typeof v.errorCount !== 'number' && typeof v.warningCount !== 'number') return null
  return v
}

function validationLabel(raw: unknown): string {
  const v = parseValidation(raw)
  if (!v) return '—'
  return t('drafts.validationShort', { errors: v.errorCount ?? 0, warnings: v.warningCount ?? 0 })
}

const columns = computed<TableColumn<Draft>[]>(() => [
  { accessorKey: 'label', header: t('drafts.col.label') },
  { accessorKey: 'baseRevisionId', header: t('drafts.col.baseRev') },
  { id: 'version', header: t('drafts.col.version') },
  { id: 'stale', header: t('drafts.col.stale') },
  { id: 'validation', header: t('drafts.col.validation') },
  { id: 'lock', header: t('drafts.col.lock') },
  { id: 'created', header: t('drafts.col.created') },
  { id: 'actions', header: '' },
])

function onSelect(_e: Event, row: { original: Draft }) {
  navigateTo(feedPath('/drafts/' + row.original.id))
}

function openDraft(row: Draft) {
  navigateTo(feedPath('/drafts/' + row.id))
}

function exportDraft(row: Draft) {
  window.open('/api/revisions/' + row.id + '/gtfs.zip')
}

function compareDraft(row: Draft) {
  if (!row.baseRevisionId) return
  navigateTo(feedPath(`/compare?from=${row.baseRevisionId}&to=${row.id}`))
}

// --- discard confirm -----------------------------------------------------
const discardTarget = ref<Draft | null>(null)
const discardOpen = computed({
  get: () => discardTarget.value !== null,
  set: (v: boolean) => {
    if (!v) discardTarget.value = null
  },
})
const { mutate: discard, loading: discarding } = useDiscardDraftMutation()

async function confirmDiscard() {
  const target = discardTarget.value
  if (!target) return
  try {
    await discard({ variables: { id: target.id, editor: editorName.value ?? '' } })
    toast.add({
      title: t('drafts.discardedNamed', { name: target.label ?? target.id }),
      color: 'success',
      icon: 'i-lucide-trash-2',
    })
    discardTarget.value = null
    await refetch()
  } catch (e) {
    const code = graphQlErrorsOf(e)[0]?.extensions?.code
    if (code === 'LOCK_LOST') {
      toast.add({
        title: t('drafts.lockLost'),
        color: 'error',
        icon: 'i-lucide-lock',
      })
    } else {
      toast.add({ title: t('drafts.discardFailed'), description: (e as Error).message, color: 'error' })
    }
  }
}

function rowActions(row: Draft): DropdownMenuItem[][] {
  return [
    [
      { label: t('drafts.open'), icon: 'i-lucide-square-pen', onSelect: () => openDraft(row) },
      { label: t('drafts.export'), icon: 'i-lucide-download', onSelect: () => exportDraft(row) },
      {
        label: t('drafts.compare'),
        icon: 'i-lucide-git-compare',
        disabled: !row.baseRevisionId,
        onSelect: () => compareDraft(row),
      },
    ],
    [
      {
        label: t('drafts.discard'),
        icon: 'i-lucide-trash-2',
        color: 'error',
        onSelect: () => (discardTarget.value = row),
      },
    ],
  ]
}
</script>

<template>
  <AppPage :title="$t('nav.drafts')" :description="$t('drafts.description')">
    <template #actions>
      <UButton
        icon="i-lucide-plus"
        color="neutral"
        variant="soft"
        :label="$t('drafts.newDraft')"
        @click="newOpen = true"
      />
    </template>

    <PageState v-if="loading && !drafts.length" state="loading" :title="$t('drafts.loading')" />

    <PageState
      v-else-if="error"
      state="error"
      :title="$t('drafts.unavailable')"
      :description="error.message"
    />

    <PageState
      v-else-if="!drafts.length"
      state="empty"
      :title="$t('drafts.emptyTitle')"
      :description="$t('drafts.emptyBody')"
      icon="i-lucide-file-pen-line"
    >
      <template #action>
        <UButton icon="i-lucide-plus" :label="$t('drafts.newDraft')" @click="newOpen = true" />
      </template>
    </PageState>

    <section v-else class="app-frame">
      <UTable :data="drafts" :columns="columns" @select="onSelect">
        <template #label-cell="{ row }">{{ row.original.label ?? $t('drafts.untitled') }}</template>
        <template #baseRevisionId-cell="{ row }">{{ row.original.baseRevisionId ?? '—' }}</template>
        <template #version-cell="{ row }">v{{ row.original.version }}</template>
        <template #stale-cell="{ row }">
          <UBadge v-if="row.original.derivationStale" color="warning" variant="subtle">
            {{ $t('drafts.staleBadge') }}
          </UBadge>
          <span v-else class="text-dimmed">—</span>
        </template>
        <template #validation-cell="{ row }">
          <span class="text-xs text-muted">{{ validationLabel(row.original.lastValidation) }}</span>
        </template>
        <template #lock-cell="{ row }">{{ row.original.lock?.editor ?? '—' }}</template>
        <template #created-cell="{ row }">
          <div class="text-xs">
            <div class="text-highlighted">{{ row.original.createdBy ?? '—' }}</div>
            <div class="text-dimmed">{{ gtfsDate(row.original.createdAt, locale) }}</div>
          </div>
        </template>
        <template #actions-cell="{ row }">
          <div class="flex justify-end">
            <UDropdownMenu :items="rowActions(row.original)">
              <UButton
                icon="i-lucide-ellipsis-vertical"
                color="neutral"
                variant="ghost"
                size="xs"
                @click.stop
              />
            </UDropdownMenu>
          </div>
        </template>
      </UTable>
    </section>

    <UModal v-model:open="discardOpen" :title="$t('drafts.discardTitle')">
      <template #body>
        <i18n-t keypath="drafts.discardNamed" tag="p" class="text-sm text-muted">
          <template #name>
            <span class="font-medium text-highlighted">{{
              discardTarget?.label ?? discardTarget?.id
            }}</span>
          </template>
        </i18n-t>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="discardTarget = null" />
          <UButton color="error" :loading="discarding" :label="$t('drafts.discard')" @click="confirmDiscard" />
        </div>
      </template>
    </UModal>

    <NewDraftDialog v-model:open="newOpen" />
  </AppPage>
</template>
