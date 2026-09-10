<script setup lang="ts">
import {
  useRoutesQuery,
  useExploreCalendarQuery,
  useRebuildDraftMutation,
  useDraftJobQuery,
  useActivateDraftMutation,
  useDiscardDraftMutation,
  useUndoDraftEditMutation,
  useRedoDraftEditMutation,
  useUpdateStopTimeMutation,
  useDraftEditsQuery,
} from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import { useDraftEditor } from '~/composables/useDraftEditor'
import { useDraftEdit } from '~/composables/useDraftEdit'
import AppPage from '~/components/AppPage.vue'
import EditorIdentityDialog from '~/components/draft/EditorIdentityDialog.vue'
import DraftGrid from '~/components/draft/DraftGrid.vue'
import DraftRail from '~/components/draft/DraftRail.vue'
import BulkShiftDialog from '~/components/draft/BulkShiftDialog.vue'
import AddTripDialog from '~/components/draft/AddTripDialog.vue'

definePageMeta({
  // Keep the same page instance (and the useDraftEditor lock) mounted while the
  // routeId param / ?dir / ?service change — only a real draft switch remounts.
  key: (route) => `draft-${route.params.draftId}`,
})

const route = useRoute()
const toast = useToast()
const { selectedFeedCode, feedPath } = useFeeds()

const draftId = computed(() => String(route.params.draftId))
const routeId = computed(() => String(route.params.routeId))
const directionId = computed<number | null>(() => {
  const d = route.query.dir
  if (d === '0') return 0
  if (d === '1') return 1
  return null
})
const serviceId = computed<string | null>(() => {
  const s = route.query.service
  return typeof s === 'string' && s ? s : null
})

const editor = useDraftEditor(draftId)
const {
  draft,
  loading: draftLoading,
  readOnly,
  identityMissing,
  lockBanner,
  canUndo,
  canRedo,
} = editor

// --- validation parsing --------------------------------------------------
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
  return value as ValidationSummary
}

const lastValidation = computed(() => parseValidation(draft.value?.lastValidation))

function formatTime(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString()
}

// --- route / service pickers -------------------------------------------
const { result: routesResult } = useRoutesQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const routeItems = computed(() =>
  [...(routesResult.value?.routes ?? [])]
    .sort((a, b) =>
      (a.routeShortName ?? a.routeId).localeCompare(b.routeShortName ?? b.routeId, undefined, {
        numeric: true,
      }),
    )
    .map((r) => ({
      label: `${r.routeShortName ?? r.routeId}${r.routeLongName ? ' — ' + r.routeLongName : ''}`,
      value: r.routeId,
    })),
)

const { result: calendarResult } = useExploreCalendarQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const serviceItems = computed(() => {
  const ids = new Set<string>()
  for (const c of calendarResult.value?.calendars ?? []) ids.add(c.serviceId)
  for (const d of calendarResult.value?.calendarDates ?? []) ids.add(d.serviceId)
  return [...ids]
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
    .map((id) => ({ label: id, value: id }))
})
const directionItems = [
  { label: 'All directions', value: '' },
  { label: 'Direction 0', value: '0' },
  { label: 'Direction 1', value: '1' },
]

function queryString(dir?: string, service?: string | null): string {
  const d = dir ?? (route.query.dir as string | undefined) ?? '0'
  const s = service ?? serviceId.value
  return `?dir=${encodeURIComponent(d)}` + (s ? `&service=${encodeURIComponent(s)}` : '')
}

const routeSel = ref(routeId.value)
watch(routeId, (v) => {
  routeSel.value = v
})
watch(routeSel, (v) => {
  if (v && v !== routeId.value) {
    navigateTo(feedPath('/drafts/' + draftId.value + '/' + encodeURIComponent(v)) + queryString())
  }
})

function setDirection(v: string) {
  navigateTo({ path: route.path, query: { ...route.query, dir: v || '' } }, { replace: true })
}

function setService(v: string) {
  navigateTo(
    { path: route.path, query: { ...route.query, service: v || undefined } },
    { replace: true },
  )
}

// --- rebuild & validate -----------------------------------------------
const { mutate: rebuildDraft } = useRebuildDraftMutation()
const rebuilding = ref(false)
const rebuildPhase = ref<string | null>(null)
const jobId = ref<string | null>(null)

const { result: jobResult } = useDraftJobQuery(
  () => ({ jobId: jobId.value ?? '' }),
  () => ({ enabled: !!jobId.value && rebuilding.value, pollInterval: 1000 }),
)

async function finishRebuild() {
  rebuilding.value = false
  jobId.value = null
  rebuildPhase.value = null
  const fresh = await editor.refetchDraft()
  const v = parseValidation(fresh?.lastValidation)
  toast.add({
    title: 'Rebuild complete',
    description: v ? `${v.errorCount ?? 0} errors · ${v.warningCount ?? 0} warnings` : undefined,
    color: (v?.errorCount ?? 0) > 0 ? 'error' : 'success',
    icon: 'i-lucide-hammer',
  })
}

watch(
  () => jobResult.value?.draftJob,
  (job) => {
    if (!rebuilding.value || !job) return
    rebuildPhase.value = job.phase
    if (job.state !== 'RUNNING') {
      if (job.error) {
        toast.add({ title: 'Rebuild failed', description: job.error, color: 'error' })
        rebuilding.value = false
        jobId.value = null
        rebuildPhase.value = null
        void editor.refetchDraft()
      } else {
        void finishRebuild()
      }
    }
  },
)

async function onRebuild() {
  if (rebuilding.value) return
  rebuilding.value = true
  try {
    const res = await rebuildDraft({ id: draftId.value })
    const job = res?.data?.rebuildDraft
    if (!job) {
      rebuilding.value = false
      return
    }
    rebuildPhase.value = job.phase
    if (job.state !== 'RUNNING') {
      if (job.error) {
        toast.add({ title: 'Rebuild failed', description: job.error, color: 'error' })
        rebuilding.value = false
        rebuildPhase.value = null
      } else {
        await finishRebuild()
      }
      return
    }
    jobId.value = job.id
  } catch (e) {
    toast.add({ title: 'Rebuild failed', description: (e as Error).message, color: 'error' })
    rebuilding.value = false
    rebuildPhase.value = null
  }
}

onBeforeUnmount(() => {
  rebuilding.value = false
  jobId.value = null
})

// --- export ----------------------------------------------------------
function onExport() {
  window.open('/api/revisions/' + draftId.value + '/gtfs.zip')
}

function isLockLost(e: unknown): boolean {
  const err = e as {
    graphQLErrors?: ReadonlyArray<{ extensions?: Record<string, unknown> | null }>
    cause?: { graphQLErrors?: ReadonlyArray<{ extensions?: Record<string, unknown> | null }> }
  }
  return (err?.graphQLErrors ?? err?.cause?.graphQLErrors)?.[0]?.extensions?.code === 'LOCK_LOST'
}

// --- activate --------------------------------------------------------
const { mutate: activateDraft, loading: activating } = useActivateDraftMutation()
const activateOpen = ref(false)
const force = ref(false)
const canActivate = computed(
  () =>
    (!draft.value?.derivationStale && (lastValidation.value?.errorCount ?? 1) === 0) || force.value,
)

async function confirmActivate() {
  try {
    await activateDraft({ id: draftId.value, editor: editor.me.value ?? '', force: force.value })
    toast.add({ title: 'Draft activated', color: 'success', icon: 'i-lucide-check' })
    activateOpen.value = false
    navigateTo(feedPath('/explore/feed'))
  } catch (e) {
    if (isLockLost(e)) {
      toast.add({
        title: 'Your editing session was taken over — reload to continue',
        color: 'error',
        icon: 'i-lucide-lock',
      })
    } else {
      toast.add({ title: 'Activation failed', description: (e as Error).message, color: 'error' })
    }
  }
}

// --- discard --------------------------------------------------------
const { mutate: discardDraft, loading: discarding } = useDiscardDraftMutation()
const discardOpen = ref(false)

async function confirmDiscard() {
  try {
    await discardDraft({ id: draftId.value, editor: editor.me.value ?? '' })
    toast.add({ title: 'Draft discarded', color: 'success', icon: 'i-lucide-trash-2' })
    discardOpen.value = false
    navigateTo(feedPath('/drafts'))
  } catch (e) {
    if (isLockLost(e)) {
      toast.add({
        title: 'Your editing session was taken over — reload to continue',
        color: 'error',
        icon: 'i-lucide-lock',
      })
    } else {
      toast.add({ title: 'Discard failed', description: (e as Error).message, color: 'error' })
    }
  }
}

// --- undo / redo ----------------------------------------------------
const { mutate: undoDraftEdit } = useUndoDraftEditMutation()
const { mutate: redoDraftEdit } = useRedoDraftEditMutation()

const { run: runEdit } = useDraftEdit(editor, {
  onChanged: () => gridRef.value?.refetch(),
})

async function onUndo() {
  await runEdit(
    (v) => undoDraftEdit({ id: v.draftId, editor: v.editor, expectedVersion: v.expectedVersion }),
    'undoDraftEdit',
  )
}

async function onRedo() {
  await runEdit(
    (v) => redoDraftEdit({ id: v.draftId, editor: v.editor, expectedVersion: v.expectedVersion }),
    'redoDraftEdit',
  )
}

// --- grid model + cell commit -------------------------------------
type GridCellVal = { arrivalSec: number | null; departureSec: number | null }
type DraftGridTrip = {
  tripId: string
  firstDepartureSec: number | null
  headsign: string | null
  blockId: string | null
  directionId: number | null
  serviceId: string | null
  cells: Array<{ stopSequence: number; arrivalSec: number | null; departureSec: number | null }>
}
const selectedTripId = ref<string | null>(null)
const activeCell = ref<{ tripId: string; stopSequence: number } | null>(null)
type DraftGridStop = {
  stopSequence: number
  stopId: string
  stopName: string | null
  timepoint: boolean
}
const gridRef = ref<{
  refetch: () => Promise<void>
  trips: DraftGridTrip[]
  stops: DraftGridStop[]
  cellAt: (tripId: string, stopSequence: number) => GridCellVal
} | null>(null)
const { mutate: updateStopTime } = useUpdateStopTimeMutation()

const selectedTrip = computed<DraftGridTrip | null>(
  () => gridRef.value?.trips?.find((t) => t.tripId === selectedTripId.value) ?? null,
)
const activeCellValue = computed<GridCellVal | null>(() => {
  const c = activeCell.value
  if (!c || !gridRef.value) return null
  return gridRef.value.cellAt(c.tripId, c.stopSequence)
})
const activeStopName = computed<string | null>(() => {
  const stop = gridRef.value?.stops?.find((s) => s.stopSequence === activeCell.value?.stopSequence)
  return stop?.stopName ?? stop?.stopId ?? null
})

// --- rail: undone (redo tail) + error counts --------------------
const { result: editsResult } = useDraftEditsQuery(
  () => ({ id: draftId.value, limit: 200 }),
  () => ({ enabled: !!draftId.value }),
)
const undoneCount = computed(
  () => (editsResult.value?.draftEdits ?? []).filter((e) => e.undone).length,
)
const errorCount = computed(() => lastValidation.value?.errorCount ?? 0)

async function onRailChanged() {
  await gridRef.value?.refetch()
  await editor.refetchDraft()
}

function onRailGoto(payload: { tripId: string; stopSequence: number }) {
  navigateTo({
    path: route.path,
    query: { ...route.query, trip: payload.tripId, stop: String(payload.stopSequence) },
  })
}

function onRailDeleted() {
  selectedTripId.value = null
}

async function onCommitCell(payload: {
  tripId: string
  stopSequence: number
  arrivalSec: number | null
  departureSec: number | null
}) {
  await runEdit(
    (v) =>
      updateStopTime({
        input: {
          draftId: v.draftId,
          editor: v.editor,
          expectedVersion: v.expectedVersion,
          tripId: payload.tripId,
          stopSequence: payload.stopSequence,
          arrivalSec: payload.arrivalSec,
          departureSec: payload.departureSec,
        },
      }),
    'updateStopTime',
  )
}

// --- dialogs wired in later tasks --------------------------------
const addTripOpen = ref(false) // AddTripDialog — task 8
const bulkShiftOpen = ref(false) // BulkShiftDialog — task 8
const duplicateSourceTripId = ref<string | null>(null)
const derivationStale = computed(() => draft.value?.derivationStale ?? false)

function openAddTrip() {
  duplicateSourceTripId.value = null
  addTripOpen.value = true
}

function openDuplicate(tripId: string) {
  duplicateSourceTripId.value = tripId
  addTripOpen.value = true
}
</script>

<template>
  <AppPage full-bleed>
    <template #leading>
      <span class="flex items-center gap-2 text-lg font-semibold text-highlighted">
        <span>{{ draft?.label ?? 'Draft' }}</span>
        <UBadge v-if="draft" color="neutral" variant="subtle" size="sm">
          v{{ draft.version }}
        </UBadge>
        <UBadge v-if="draft?.derivationStale" color="warning" variant="subtle" size="sm">
          derivation stale
        </UBadge>
        <UBadge v-if="draft?.lock?.editor" color="neutral" variant="soft" size="sm">
          🔒 {{ draft.lock.editor }}
        </UBadge>
      </span>
    </template>

    <template #actions>
      <UButton
        size="sm"
        color="neutral"
        variant="soft"
        icon="i-lucide-hammer"
        :loading="rebuilding"
        :disabled="rebuilding || !draft || readOnly"
        :label="rebuilding ? (rebuildPhase ?? 'Rebuilding…') : 'Rebuild & validate'"
        @click="onRebuild"
      />
      <UButton
        size="sm"
        color="neutral"
        variant="ghost"
        icon="i-lucide-download"
        label="Export"
        :disabled="!draft"
        @click="onExport"
      />
      <UButton
        size="sm"
        color="primary"
        variant="soft"
        icon="i-lucide-rocket"
        label="Activate"
        :disabled="!draft || readOnly"
        @click="activateOpen = true"
      />
      <UButton
        size="sm"
        color="error"
        variant="ghost"
        icon="i-lucide-trash-2"
        label="Discard"
        :disabled="!draft || readOnly"
        @click="discardOpen = true"
      />
    </template>

    <template #toolbar>
      <div class="flex flex-wrap items-center gap-2 py-2">
        <USelectMenu
          v-model="routeSel"
          :items="routeItems"
          value-key="value"
          placeholder="Route"
          icon="i-lucide-route"
          class="w-64"
        />
        <USelectMenu
          :model-value="(route.query.dir as string) || ''"
          :items="directionItems"
          value-key="value"
          class="w-44"
          @update:model-value="setDirection"
        />
        <USelectMenu
          :model-value="serviceId ?? ''"
          :items="serviceItems"
          value-key="value"
          placeholder="Service"
          icon="i-lucide-calendar"
          class="w-48"
          @update:model-value="setService"
        />
        <div class="ml-auto flex items-center gap-2">
          <UButton
            size="sm"
            color="neutral"
            variant="ghost"
            icon="i-lucide-undo-2"
            label="Undo"
            :disabled="!canUndo || readOnly"
            @click="onUndo"
          />
          <UButton
            size="sm"
            color="neutral"
            variant="ghost"
            icon="i-lucide-redo-2"
            label="Redo"
            :disabled="!canRedo || readOnly"
            @click="onRedo"
          />
          <UButton
            size="sm"
            color="neutral"
            variant="soft"
            icon="i-lucide-plus"
            label="Add trip"
            :disabled="readOnly"
            @click="openAddTrip"
          />
          <UButton
            size="sm"
            color="neutral"
            variant="soft"
            icon="i-lucide-move-horizontal"
            label="Bulk shift…"
            :disabled="readOnly"
            @click="bulkShiftOpen = true"
          />
        </div>
      </div>
    </template>

    <EditorIdentityDialog />

    <div
      v-if="!draft && draftLoading"
      class="flex items-center gap-3 py-16 justify-center text-muted"
    >
      <UIcon name="i-lucide-loader-circle" class="size-5 animate-spin" />
      <span class="text-sm">Loading editor…</span>
    </div>

    <div
      v-else-if="!draft"
      class="flex flex-col items-center gap-3 py-16 justify-center text-muted"
    >
      <UIcon name="i-lucide-file-x" class="size-6" />
      <p class="text-sm">This draft no longer exists.</p>
      <UButton
        size="sm"
        color="neutral"
        variant="soft"
        icon="i-lucide-arrow-left"
        label="Back to drafts"
        :to="feedPath('/drafts')"
      />
    </div>

    <template v-else>
      <UAlert
        v-if="identityMissing"
        color="warning"
        variant="subtle"
        icon="i-lucide-user-x"
        class="m-4"
        title="Not editing"
        description="Reload the page and enter your name to edit this draft."
      />
      <UAlert
        v-if="readOnly"
        color="warning"
        variant="subtle"
        icon="i-lucide-lock"
        class="m-4"
        :title="`Locked by ${lockBanner?.editor ?? 'another editor'}`"
        :description="lockBanner ? `Lease expires ${formatTime(lockBanner.expiresAt)}` : undefined"
      >
        <template #actions>
          <UButton
            size="xs"
            color="warning"
            variant="solid"
            label="Take over"
            @click="editor.takeOver().catch(() => {})"
          />
        </template>
      </UAlert>

      <div class="flex min-h-0 flex-1" :class="{ 'opacity-50': readOnly }">
        <div class="min-w-0 flex-1 overflow-hidden">
          <DraftGrid
            ref="gridRef"
            v-model:selected-trip-id="selectedTripId"
            v-model:active-cell="activeCell"
            :draft-id="draftId"
            :route-id="routeId"
            :direction-id="directionId"
            :service-id="serviceId"
            :read-only="readOnly"
            @commit-cell="onCommitCell"
            @add-trip="openAddTrip"
          />
        </div>
        <DraftRail
          :draft-id="draftId"
          :selected-trip="selectedTrip"
          :active-cell="activeCell"
          :active-cell-value="activeCellValue"
          :active-stop-name="activeStopName"
          :read-only="readOnly"
          :derivation-stale="draft?.derivationStale ?? false"
          :feed-code="selectedFeedCode ?? ''"
          :last-validation="draft?.lastValidation"
          :undone-count="undoneCount"
          :error-count="errorCount"
          :editor="editor"
          @changed="onRailChanged"
          @goto="onRailGoto"
          @deleted="onRailDeleted"
          @duplicate="openDuplicate"
        />
      </div>

      <BulkShiftDialog
        v-model:open="bulkShiftOpen"
        :editor="editor"
        :feed-code="selectedFeedCode ?? ''"
        :route-id="routeId"
        :service-id="serviceId"
        :derivation-stale="derivationStale"
        :trips="gridRef?.trips ?? []"
        :service-items="serviceItems"
        @changed="onRailChanged"
      />
      <AddTripDialog
        v-model:open="addTripOpen"
        :editor="editor"
        :feed-code="selectedFeedCode ?? ''"
        :route-id="routeId"
        :direction-id="directionId"
        :service-id="serviceId"
        :derivation-stale="derivationStale"
        :trips="gridRef?.trips ?? []"
        :duplicate-source-trip-id="duplicateSourceTripId"
        :service-items="serviceItems"
        @changed="onRailChanged"
      />
    </template>

    <UModal v-model:open="activateOpen" title="Activate draft?">
      <template #body>
        <div class="flex flex-col gap-4 text-sm text-muted">
          <p>Activating publishes this draft as the feed's active revision.</p>
          <div v-if="draft?.derivationStale" class="text-warning">
            Derivation is stale — rebuild before activating, or force.
          </div>
          <div v-if="(lastValidation?.errorCount ?? 0) > 0" class="text-error">
            {{ lastValidation?.errorCount }} validation error(s) outstanding.
          </div>
          <USwitch v-model="force" label="Force activation" />
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" label="Cancel" @click="activateOpen = false" />
          <UButton
            color="primary"
            :loading="activating"
            :disabled="!canActivate"
            label="Activate"
            @click="confirmActivate"
          />
        </div>
      </template>
    </UModal>

    <UModal v-model:open="discardOpen" title="Discard draft?">
      <template #body>
        <p class="text-sm text-muted">
          This permanently discards this draft and its edits. This cannot be undone.
        </p>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" label="Cancel" @click="discardOpen = false" />
          <UButton color="error" :loading="discarding" label="Discard" @click="confirmDiscard" />
        </div>
      </template>
    </UModal>
  </AppPage>
</template>
