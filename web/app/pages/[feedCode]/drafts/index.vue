<script setup lang="ts">
import type { TableColumn, DropdownMenuItem } from '@nuxt/ui'
import { useDraftsQuery, useDiscardDraftMutation, type DraftsQuery } from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import NavbarActions from '~/components/NavbarActions.vue'
import NewDraftDialog from '~/components/draft/NewDraftDialog.vue'
import { gtfsDate } from '~/utils/gtfs'
import { useEditorIdentity } from '~/composables/useEditorIdentity'

const { selectedFeedCode, feedPath } = useFeeds()
const { name: editorName } = useEditorIdentity()
const toast = useToast()

const newOpen = ref(false)

const { result, loading, error, refetch } = useDraftsQuery(
  () => ({ feedCode: selectedFeedCode.value }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

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
  return `E${v.errorCount ?? 0}/W${v.warningCount ?? 0}`
}

const columns: TableColumn<Draft>[] = [
  { accessorKey: 'label', header: 'Label' },
  { accessorKey: 'baseRevisionId', header: 'Base rev' },
  { id: 'version', header: 'Version' },
  { id: 'stale', header: 'Stale' },
  { id: 'validation', header: 'Validation' },
  { id: 'lock', header: 'Lock' },
  { id: 'created', header: 'Created' },
  { id: 'actions', header: '' },
]

function onSelect(_e: Event, row: { original: Draft }) {
  navigateTo(feedPath('/drafts/' + row.original.id))
}

function openDraft(row: Draft) {
  navigateTo(feedPath('/drafts/' + row.id))
}

function exportDraft(row: Draft) {
  window.open('/api/revisions/' + row.id + '/gtfs.zip')
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
    await discard({ id: target.id, editor: editorName.value ?? '' })
    toast.add({ title: `Draft ${target.label ?? target.id} discarded`, color: 'success', icon: 'i-lucide-trash-2' })
    discardTarget.value = null
    await refetch()
  } catch (e) {
    const err = e as {
      graphQLErrors?: ReadonlyArray<{ extensions?: Record<string, unknown> | null }>
      cause?: { graphQLErrors?: ReadonlyArray<{ extensions?: Record<string, unknown> | null }> }
    }
    const code = (err?.graphQLErrors ?? err?.cause?.graphQLErrors)?.[0]?.extensions?.code
    if (code === 'LOCK_LOST') {
      toast.add({
        title: 'Someone is editing this draft — ask them to discard it',
        color: 'error',
        icon: 'i-lucide-lock',
      })
    } else {
      toast.add({ title: 'Discard failed', description: (e as Error).message, color: 'error' })
    }
  }
}

function rowActions(row: Draft): DropdownMenuItem[][] {
  return [
    [
      { label: 'Open', icon: 'i-lucide-square-pen', onSelect: () => openDraft(row) },
      { label: 'Export', icon: 'i-lucide-download', onSelect: () => exportDraft(row) },
    ],
    [
      { label: 'Discard', icon: 'i-lucide-trash-2', color: 'error', onSelect: () => (discardTarget.value = row) },
    ],
  ]
}
</script>

<template>
  <UDashboardPanel id="drafts">
    <template #header>
      <UDashboardNavbar title="Drafts" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            icon="i-lucide-plus"
            size="sm"
            color="neutral"
            variant="soft"
            label="New draft"
            @click="newOpen = true"
          />
          <NavbarActions />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div v-if="loading && !drafts.length" class="flex flex-col gap-4">
        <USkeleton class="h-10 w-full" />
        <USkeleton class="h-40 w-full" />
      </div>

      <UAlert
        v-else-if="error"
        color="error"
        variant="soft"
        icon="i-lucide-alert-triangle"
        title="Drafts unavailable"
        :description="error.message"
      />

      <div
        v-else-if="!drafts.length"
        class="flex flex-col items-center gap-3 py-16 text-center"
      >
        <UIcon name="i-lucide-file-pen-line" class="size-8 text-dimmed" />
        <p class="text-sm text-muted">No drafts yet for this feed.</p>
        <UButton icon="i-lucide-plus" label="New draft" @click="newOpen = true" />
      </div>

      <UTable v-else :data="drafts" :columns="columns" @select="onSelect">
        <template #label-cell="{ row }">{{ row.original.label ?? '(untitled)' }}</template>
        <template #baseRevisionId-cell="{ row }">{{ row.original.baseRevisionId ?? '—' }}</template>
        <template #version-cell="{ row }">v{{ row.original.version }}</template>
        <template #stale-cell="{ row }">
          <UBadge v-if="row.original.derivationStale" color="warning" variant="subtle" size="sm">
            stale
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
            <div class="text-dimmed">{{ gtfsDate(row.original.createdAt) }}</div>
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

      <UModal v-model:open="discardOpen" title="Discard draft?">
        <template #body>
          <p class="text-sm text-muted">
            This permanently discards
            <span class="font-medium text-highlighted">{{ discardTarget?.label ?? discardTarget?.id }}</span>
            and its edits. This cannot be undone.
          </p>
        </template>
        <template #footer>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" label="Cancel" @click="discardTarget = null" />
            <UButton color="error" :loading="discarding" label="Discard" @click="confirmDiscard" />
          </div>
        </template>
      </UModal>

      <NewDraftDialog v-model:open="newOpen" />
    </template>
  </UDashboardPanel>
</template>
