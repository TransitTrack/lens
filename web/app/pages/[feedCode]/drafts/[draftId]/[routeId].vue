<script setup lang="ts">
import { useMutation, useQuery, useQueryCache } from '@pinia/colada'
import type {SplitterItem} from '@nuxt/ui'
import type { DraftEditResult } from '~/api'
import { draftQuery, draftEditsQuery, draftGridQuery, draftJobQuery } from '~/composables/api/queries/drafts'
import { routesQuery, calendarsQuery, calendarDatesQuery, tripQuery } from '~/composables/api/queries/gtfs'
import { tripPatternQuery, tripPatternsQuery, stopPathsQuery } from '~/composables/api/queries/schedule'
import { claimDraftEditorMutation, renewDraftEditorMutation, releaseDraftEditorMutation, updateStopTimeMutation, setStopDwellMutation, shiftTripMutation, deleteTripMutation, addTripMutation, duplicateTripMutation, bulkShiftTripsMutation, undoDraftEditMutation, undoDraftEditStepMutation, cancelDraftReads, invalidateDraft, redoDraftEditMutation, rebuildDraftMutation, activateDraftMutation, discardDraftMutation } from '~/composables/api/mutations/drafts'
import type { DraftEditCommand, DraftTripDetail } from '~/types/draftEdits'
import { groupStopPaths, patternsWithStopPaths } from '~/utils/joins/stopPaths'
import SplitterGrip from '~/components/shell/SplitterGrip.vue'

import {useFeeds} from '~/composables/useFeeds'
import {useDraftEditor, type MutateVars} from '~/composables/useDraftEditor'
import {useDraftEdit} from '~/composables/useDraftEdit'
import {formatClock} from '~/utils/clock'
import AppPage from '~/components/shell/AppPage.vue'
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
const {t, locale} = useI18n()
const {selectedFeedCode, feedPath} = useFeeds()
const queryCache = useQueryCache()

const draftId = computed(() => String(route.params.draftId))
const routeId = computed(() => String(route.params.routeId))
const feedCode = computed(() => selectedFeedCode.value ?? '')
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

// --- draft session: detail, edit history (undo/redo seed), editor lock ----
const {
  data: draftData,
  isLoading: draftLoading,
  error: draftError,
  refetch: refetchDraftQuery,
} = useQuery(() => ({
  ...draftQuery({ id: draftId.value }),
  enabled: !!draftId.value,
}))
watch(draftError, (err) => {
  if (err) toast.add({ title: t('drafts.loadFailed'), description: err.message, color: 'error' })
})

const { data: editorEdits } = useQuery(() => ({
  ...draftEditsQuery({ id: draftId.value, limit: 500 }),
  enabled: !!draftId.value,
}))

const { mutateAsync: claimEditor } = useMutation(claimDraftEditorMutation())
const { mutateAsync: renewEditor } = useMutation(renewDraftEditorMutation())
const { mutateAsync: releaseEditor } = useMutation(releaseDraftEditorMutation())

const editor = useDraftEditor(draftId, {
  draft: draftData,
  edits: editorEdits,
  // `refetch`, not `refresh`: after a claim or a STALE_DRAFT conflict the draft must be read
  // anew — `refresh` would join a read already in flight from before the write.
  refreshDraft: async () => (await refetchDraftQuery()).data ?? null,
  claim: claimEditor,
  renew: renewEditor,
  release: releaseEditor,
})
const {
  draft,
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
  return formatClock(iso, { seconds: true, locale: locale.value })
}

// --- route / service pickers (the feed's active revision, as before) -----
const {data: routesData} = useQuery(() => ({
  ...routesQuery({ feedCode: feedCode.value }),
  enabled: !!selectedFeedCode.value,
}))
const routeItems = computed(() =>
  [...(routesData.value ?? [])]
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

const {data: calendars} = useQuery(() => ({
  ...calendarsQuery({ feedCode: feedCode.value }),
  enabled: !!selectedFeedCode.value,
}))
const {data: calendarDates} = useQuery(() => ({
  ...calendarDatesQuery({ feedCode: feedCode.value }),
  enabled: !!selectedFeedCode.value,
}))
const serviceItems = computed(() => {
  const ids = new Set<string>()
  for (const c of calendars.value ?? []) ids.add(c.serviceId)
  for (const d of calendarDates.value ?? []) ids.add(d.serviceId)
  return [...ids]
    .sort((a, b) => a.localeCompare(b, undefined, {numeric: true}))
    .map((id) => ({label: id, value: id}))
})
const directionItems = computed(() => [
  {label: t('drafts.allDirections'), value: ''},
  {label: t('explore.trips.direction', {dir: 0}), value: '0'},
  {label: t('explore.trips.direction', {dir: 1}), value: '1'},
])

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
  navigateTo({path: route.path, query: {...route.query, dir: v || ''}}, {replace: true})
}

function setService(v: string) {
  navigateTo(
    {path: route.path, query: {...route.query, service: v || undefined}},
    {replace: true},
  )
}

// --- rebuild & validate -----------------------------------------------
const {mutateAsync: rebuildDraft} = useMutation(rebuildDraftMutation(queryCache))
const rebuilding = ref(false)
const rebuildPhase = ref<string | null>(null)
const jobId = ref<string | null>(null)

const {data: jobData} = useQuery(() => ({
  ...draftJobQuery({ jobId: jobId.value ?? '' }),
  enabled: !!jobId.value && rebuilding.value,
  autoRefetch: () => (rebuilding.value ? 1000 : false),
}))

async function finishRebuild() {
  rebuilding.value = false
  jobId.value = null
  rebuildPhase.value = null
  const fresh = await editor.refreshDraft()
  const v = parseValidation(fresh?.lastValidation)
  toast.add({
    title: t('drafts.rebuildComplete'),
    description: v ? t('drafts.rebuildSummary', {errors: v.errorCount ?? 0, warnings: v.warningCount ?? 0}) : undefined,
    color: (v?.errorCount ?? 0) > 0 ? 'error' : 'success',
    icon: 'i-lucide-hammer',
  })
}

watch(
  jobData,
  (job) => {
    // `data` keeps the previous key's job as a placeholder: only act on the job being polled.
    if (!rebuilding.value || !job || job.id !== jobId.value) return
    rebuildPhase.value = job.phase
    if (job.state !== 'RUNNING') {
      if (job.error) {
        toast.add({title: t('drafts.rebuildFailed'), description: job.error, color: 'error'})
        rebuilding.value = false
        jobId.value = null
        rebuildPhase.value = null
        void editor.refreshDraft()
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
    const job = await rebuildDraft({ path: {id: draftId.value} })
    if (!job) {
      rebuilding.value = false
      return
    }
    rebuildPhase.value = job.phase
    if (job.state !== 'RUNNING') {
      if (job.error) {
        toast.add({title: t('drafts.rebuildFailed'), description: job.error, color: 'error'})
        rebuilding.value = false
        rebuildPhase.value = null
      } else {
        await finishRebuild()
      }
      return
    }
    jobId.value = job.id
  } catch (e) {
    toast.add({title: t('drafts.rebuildFailed'), description: (e as Error).message, color: 'error'})
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
  return restProblemOf(e)?.code === 'LOCK_LOST'
}

/**
 * The draft no longer exists as a draft (activated / discarded): stop the refetches its
 * mutation's invalidation started — their 404s would flash "draft gone" — then leave.
 */
function leaveDraft(path: string) {
  cancelDraftReads(queryCache, draftId.value)
  navigateTo(feedPath(path))
}

// --- activate --------------------------------------------------------
const {mutateAsync: activateDraft, isLoading: activating} = useMutation(activateDraftMutation(queryCache))
const activateOpen = ref(false)
const force = ref(false)
const canActivate = computed(
  () =>
    (!draft.value?.derivationStale && (lastValidation.value?.errorCount ?? 1) === 0) || force.value,
)

async function confirmActivate() {
  try {
    await activateDraft({ path: {id: draftId.value}, body: {editor: editor.me.value ?? '', force: force.value} })
    toast.add({title: t('drafts.activated'), color: 'success', icon: 'i-lucide-check'})
    activateOpen.value = false
    leaveDraft('/explore/feed')
  } catch (e) {
    if (isLockLost(e)) {
      toast.add({
        title: t('drafts.sessionTakenOver'),
        color: 'error',
        icon: 'i-lucide-lock',
      })
    } else {
      toast.add({title: t('drafts.activationFailed'), description: (e as Error).message, color: 'error'})
    }
  }
}

// --- discard --------------------------------------------------------
const {mutateAsync: discardDraft, isLoading: discarding} = useMutation(discardDraftMutation(queryCache))
const discardOpen = ref(false)

async function confirmDiscard() {
  try {
    await discardDraft({ path: {id: draftId.value}, body: {editor: editor.me.value ?? ''} })
    toast.add({title: t('drafts.discarded'), color: 'success', icon: 'i-lucide-trash-2'})
    discardOpen.value = false
    leaveDraft('/drafts')
  } catch (e) {
    if (isLockLost(e)) {
      toast.add({
        title: t('drafts.sessionTakenOver'),
        color: 'error',
        icon: 'i-lucide-lock',
      })
    } else {
      toast.add({title: t('drafts.discardFailed'), description: (e as Error).message, color: 'error'})
    }
  }
}

// --- grid model ---------------------------------------------------
const selectedTripId = ref<string | null>(null)
const activeCell = ref<{ tripId: string; stopSequence: number } | null>(null)

const {data: grid, isLoading: gridLoading, error: gridError} = useQuery(() => ({
  ...draftGridQuery({
    draftId: draftId.value,
    routeId: routeId.value,
    directionId: directionId.value ?? undefined,
    serviceId: serviceId.value ?? undefined,
  }),
  enabled: !!draftId.value && !!routeId.value,
}))
const gridTrips = computed(() => grid.value?.trips ?? [])

const selectedTrip = computed(
  () => gridTrips.value.find((t) => t.tripId === selectedTripId.value) ?? null,
)
const activeCellValue = computed<{ arrivalSec: number | null; departureSec: number | null } | null>(() => {
  const c = activeCell.value
  if (!c || !grid.value) return null
  const cell = gridTrips.value.find((t) => t.tripId === c.tripId)?.cells.find((x) => x.stopSequence === c.stopSequence)
  return { arrivalSec: cell?.arrivalSec ?? null, departureSec: cell?.departureSec ?? null }
})
const activeStopName = computed<string | null>(() => {
  const stop = grid.value?.stops.find((s) => s.stopSequence === activeCell.value?.stopSequence)
  return stop?.stopName ?? stop?.stopId ?? null
})

// --- selected trip's derived detail in the draft --------------------
const derivationStale = computed(() => draft.value?.derivationStale ?? false)
const tripDetailEnabled = computed(
  () => !!selectedFeedCode.value && !!selectedTripId.value && !derivationStale.value,
)
const {data: selectedTripRow} = useQuery(() => ({
  ...tripQuery({ feedCode: feedCode.value, draftId: draftId.value, tripId: selectedTripId.value ?? '' }),
  enabled: tripDetailEnabled.value,
}))
const selectedPatternKey = computed(() =>
  selectedTripRow.value?.tripId === selectedTripId.value ? selectedTripRow.value?.patternKey ?? null : null,
)
const {data: selectedTripPattern} = useQuery(() => ({
  ...tripPatternQuery({ feedCode: feedCode.value, draftId: draftId.value, patternKey: selectedPatternKey.value ?? '' }),
  enabled: tripDetailEnabled.value && !!selectedPatternKey.value,
}))
const tripDetail = computed<DraftTripDetail | null>(() => {
  const trip = selectedTripRow.value
  // `data` holds the previous trip as a placeholder while the next one loads: never mix the two.
  if (!trip || trip.tripId !== selectedTripId.value) return null
  const pattern = selectedTripPattern.value
  return { ...trip, pattern: pattern && pattern.patternKey === trip.patternKey ? pattern : null }
})

// --- rail: history (latest 200 edits), undone (redo tail) + error counts ----
const {data: recentEdits, refresh: refreshRecentEdits} = useQuery(() => ({
  ...draftEditsQuery({ id: draftId.value, limit: 200 }),
  enabled: !!draftId.value,
}))
// Another editor's change reaches us as a new draft version (after a refresh of the draft):
// reload the history then. Our own edits already refresh it through the mutation's
// invalidation, and `refresh` joins that pending request instead of issuing a second one.
// An undo burst bumps the version once per step and reloads the history once at its end.
let undoBurst = false
watch(
  () => draft.value?.version,
  (version, previous) => {
    if (!undoBurst && previous != null && version !== previous) void refreshRecentEdits()
  },
)
const undoneCount = computed(
  () => (recentEdits.value ?? []).filter((e) => e.undone).length,
)
const errorCount = computed(() => lastValidation.value?.errorCount ?? 0)

function onRailGoto(payload: { tripId: string; stopSequence: number }) {
  navigateTo({
    path: route.path,
    query: {...route.query, trip: payload.tripId, stop: String(payload.stopSequence)},
  })
}

// --- dialogs -------------------------------------------------------
const addTripOpen = ref(false)
const addTripMode = ref<'new' | 'duplicate'>('duplicate')
const bulkShiftOpen = ref(false)
const duplicateSourceTripId = ref<string | null>(null)
const dialogSubmitting = ref(false)

function openAddTrip() {
  duplicateSourceTripId.value = null
  addTripOpen.value = true
}

function openDuplicate(tripId: string) {
  duplicateSourceTripId.value = tripId
  addTripOpen.value = true
}

// The route's patterns in the draft — read only while a dialog needs them (as before).
const patternsAllowed = computed(
  () => !!selectedFeedCode.value && !!routeId.value && !!draft.value?.id && !derivationStale.value,
)
const addTripNeedsPatterns = computed(() => addTripOpen.value && addTripMode.value === 'new')
const {data: patternRows} = useQuery(() => ({
  ...tripPatternsQuery({ feedCode: feedCode.value, draftId: draftId.value, routeId: routeId.value }),
  enabled: patternsAllowed.value && (addTripNeedsPatterns.value || bulkShiftOpen.value),
}))
const {data: patternStopPaths} = useQuery(() => ({
  ...stopPathsQuery({ feedCode: feedCode.value, draftId: draftId.value, routeId: routeId.value }),
  enabled: patternsAllowed.value && addTripNeedsPatterns.value,
}))
const draftPatterns = computed(() =>
  patternsWithStopPaths(patternRows.value ?? [], groupStopPaths(patternStopPaths.value ?? [], new Map())),
)

// --- edits: every command runs here, through the editor session -----
const {mutateAsync: updateStopTime} = useMutation(updateStopTimeMutation(queryCache))
const {mutateAsync: setStopDwell} = useMutation(setStopDwellMutation(queryCache))
const {mutateAsync: shiftTrip} = useMutation(shiftTripMutation(queryCache))
const {mutateAsync: deleteTrip} = useMutation(deleteTripMutation(queryCache))
const {mutateAsync: addTrip} = useMutation(addTripMutation(queryCache))
const {mutateAsync: duplicateTrip} = useMutation(duplicateTripMutation(queryCache))
const {mutateAsync: bulkShiftTrips} = useMutation(bulkShiftTripsMutation(queryCache))
const {mutateAsync: undoDraftEdit} = useMutation(undoDraftEditMutation(queryCache))
const {mutateAsync: redoDraftEdit} = useMutation(redoDraftEditMutation(queryCache))
// One step of an "undo to here" burst: no per-step invalidation — the burst refreshes the
// draft's reads once at the end instead.
const {mutateAsync: undoBurstStep} = useMutation(undoDraftEditStepMutation())

const {run: runEdit} = useDraftEdit(editor)

function editRequest(command: DraftEditCommand, v: MutateVars): Promise<DraftEditResult> {
  const session = { draftId: v.draftId, editor: v.editor, expectedVersion: v.expectedVersion }
  switch (command.op) {
    case 'updateStopTime': return updateStopTime({ body: { ...command.body, ...session } })
    case 'setStopDwell': return setStopDwell({ body: { ...command.body, ...session } })
    case 'shiftTrip': return shiftTrip({ body: { ...command.body, ...session } })
    case 'deleteTrip': return deleteTrip({ body: { ...command.body, ...session } })
    case 'addTrip': return addTrip({ body: { ...command.body, ...session } })
    case 'duplicateTrip': return duplicateTrip({ body: { ...command.body, ...session } })
    case 'bulkShiftTrips': return bulkShiftTrips({ body: { ...command.body, ...session } })
    case 'undoDraftEdit': return undoDraftEdit({ path: { id: v.draftId }, body: { editor: v.editor, expectedVersion: v.expectedVersion } })
    case 'redoDraftEdit': return redoDraftEdit({ path: { id: v.draftId }, body: { editor: v.editor, expectedVersion: v.expectedVersion } })
  }
}

async function onEdit(command: DraftEditCommand) {
  const fromDialog = command.op === 'addTrip' || command.op === 'duplicateTrip' || command.op === 'bulkShiftTrips'
  if (fromDialog) dialogSubmitting.value = true
  try {
    const result = await runEdit((v) => editRequest(command, v))
    if (!result) return
    if (command.op === 'deleteTrip') selectedTripId.value = null
    if (command.op === 'addTrip' || command.op === 'duplicateTrip') addTripOpen.value = false
    if (command.op === 'bulkShiftTrips') bulkShiftOpen.value = false
  } finally {
    if (fromDialog) dialogSubmitting.value = false
  }
}

function onUndo() {
  return onEdit({ op: 'undoDraftEdit' })
}

function onRedo() {
  return onEdit({ op: 'redoDraftEdit' })
}

function onCommitCell(payload: {
  tripId: string
  stopSequence: number
  arrivalSec: number | null
  departureSec: number | null
}) {
  return onEdit({ op: 'updateStopTime', body: payload })
}

/** "Undo to here": apply each undo locally, then refresh the draft's queries once. */
async function onUndoSteps(count: number) {
  let applied = 0
  undoBurst = true
  try {
    for (let i = 0; i < count; i++) {
      try {
        const result = await editor.mutate((v) =>
          undoBurstStep({ path: { id: v.draftId }, body: { editor: v.editor, expectedVersion: v.expectedVersion } }),
        )
        if (!result) break
        editor.applyResult(result)
        applied++
      } catch {
        // editor.mutate already toasts
        break
      }
    }
    // let the version watcher see the last step while the burst is still on
    await nextTick()
  } finally {
    undoBurst = false
  }
  if (applied > 0) void invalidateDraft(queryCache, draftId.value)
}

// --- grid / rail split --------------------------------------------
const draftSplitterItems: SplitterItem[] = [
  {id: 'grid', slot: 'grid', class: 'flex min-h-0 flex-col overflow-hidden'},
  {
    id: 'rail',
    slot: 'rail',
    sizeUnit: 'px',
    defaultSize: 280,
    minSize: 220,
    maxSize: 480,
    collapsible: true,
    collapsedSize: 34,
    class: 'flex min-h-0 flex-col overflow-hidden border-l border-default',
  },
]
</script>

<template>
  <AppPage full-width>
    <template #leading>
      <span class="flex items-center gap-2 text-lg font-semibold text-highlighted">
        <span>{{ draft?.label ?? $t('drafts.draftFallback') }}</span>
        <UBadge v-if="draft" color="neutral" variant="subtle">
          v{{ draft.version }}
        </UBadge>
        <UBadge v-if="draft?.derivationStale" color="warning" variant="subtle">
          {{ $t('drafts.derivationStale') }}
        </UBadge>
        <UBadge v-if="draft?.lock?.editor" color="neutral" variant="soft">
          🔒 {{ draft.lock.editor }}
        </UBadge>
      </span>
    </template>

    <template #actions>
      <UButton
        color="neutral"
        variant="soft"
        icon="i-lucide-hammer"
        :loading="rebuilding"
        :disabled="rebuilding || !draft || readOnly"
        :label="rebuilding ? (rebuildPhase ?? $t('drafts.rebuilding')) : $t('drafts.rebuild')"
        @click="onRebuild"
      />
      <UButton
        color="neutral"
        variant="ghost"
        icon="i-lucide-download"
        :label="$t('drafts.export')"
        :disabled="!draft"
        @click="onExport"
      />
      <UButton
        color="primary"
        variant="soft"
        icon="i-lucide-rocket"
        :label="$t('drafts.activate')"
        :disabled="!draft || readOnly"
        @click="activateOpen = true"
      />
      <UButton
        color="error"
        variant="ghost"
        icon="i-lucide-trash-2"
        :label="$t('drafts.discard')"
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
          :placeholder="$t('explore.col.route')"
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
          :placeholder="$t('explore.col.service')"
          icon="i-lucide-calendar"
          class="w-48"
          @update:model-value="setService"
        />
        <div class="ml-auto flex items-center gap-2">
          <UButton
            color="neutral"
            variant="ghost"
            icon="i-lucide-undo-2"
            :label="$t('drafts.undo')"
            :disabled="!canUndo || readOnly"
            @click="onUndo"
          />
          <UButton
            color="neutral"
            variant="ghost"
            icon="i-lucide-redo-2"
            :label="$t('drafts.redo')"
            :disabled="!canRedo || readOnly"
            @click="onRedo"
          />
          <UButton
            color="neutral"
            variant="soft"
            icon="i-lucide-plus"
            :label="$t('drafts.addTrip')"
            :disabled="readOnly"
            @click="openAddTrip"
          />
          <UButton
            color="neutral"
            variant="soft"
            icon="i-lucide-move-horizontal"
            :label="$t('drafts.bulkShift')"
            :disabled="readOnly"
            @click="bulkShiftOpen = true"
          />
        </div>
      </div>
    </template>

    <EditorIdentityDialog/>

    <div
      v-if="!draft && draftLoading"
      class="flex items-center gap-3 py-16 justify-center text-muted"
    >
      <UIcon name="i-lucide-loader-circle" class="size-5 animate-spin"/>
      <span class="text-sm">{{ $t('drafts.loadingEditorInline') }}</span>
    </div>

    <div
      v-else-if="!draft"
      class="flex flex-col items-center gap-3 py-16 justify-center text-muted"
    >
      <UIcon name="i-lucide-file-x" class="size-6"/>
      <p class="text-sm">{{ $t('drafts.gone') }}</p>
      <UButton
        color="neutral"
        variant="soft"
        icon="i-lucide-arrow-left"
        :label="$t('drafts.backToDrafts')"
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
        :title="$t('drafts.notEditing')"
        :description="$t('drafts.notEditingBody')"
      />
      <UAlert
        v-if="readOnly"
        color="warning"
        variant="subtle"
        icon="i-lucide-lock"
        class="m-4"
        :title="lockBanner?.editor ? $t('drafts.lockedBy', {editor: lockBanner.editor}) : $t('drafts.lockedByAnother')"
        :description="lockBanner ? $t('drafts.leaseExpires', {time: formatTime(lockBanner.expiresAt)}) : undefined"
      >
        <template #actions>
          <UButton
            size="xs"
            color="warning"
            variant="solid"
            :label="$t('drafts.takeOver')"
            @click="editor.takeOver().catch(() => {})"
          />
        </template>
      </UAlert>

      <USplitter
        id="draft-editor-splitter"
        :items="draftSplitterItems"
        class="min-h-0 flex-1"
        :class="{ 'opacity-50': readOnly }"
      >
        <template #grid>
          <DraftGrid
            v-model:selected-trip-id="selectedTripId"
            v-model:active-cell="activeCell"
            :grid="grid"
            :loading="gridLoading"
            :error="gridError"
            :read-only="readOnly"
            @commit-cell="onCommitCell"
            @add-trip="openAddTrip"
          />
        </template>

        <template #rail="{ collapsed, collapse, expand }">
          <DraftRail
            :selected-trip="selectedTrip"
            :trip-detail="tripDetail"
            :active-cell="activeCell"
            :active-cell-value="activeCellValue"
            :active-stop-name="activeStopName"
            :read-only="readOnly"
            :derivation-stale="draft?.derivationStale ?? false"
            :last-validation="draft?.lastValidation"
            :undone-count="undoneCount"
            :error-count="errorCount"
            :edits="recentEdits ?? []"
            :can-undo="canUndo"
            :can-redo="canRedo"
            :collapsed="collapsed"
            @edit="onEdit"
            @undo-steps="onUndoSteps"
            @goto="onRailGoto"
            @duplicate="openDuplicate"
            @expand="expand"
            @collapse="collapse"
          />
        </template>

        <template #resize-handle>
          <SplitterGrip />
        </template>
      </USplitter>

      <BulkShiftDialog
        v-model:open="bulkShiftOpen"
        :route-id="routeId"
        :service-id="serviceId"
        :derivation-stale="derivationStale"
        :trips="gridTrips"
        :service-items="serviceItems"
        :patterns="patternRows ?? []"
        :submitting="dialogSubmitting"
        @edit="onEdit"
      />
      <AddTripDialog
        v-model:open="addTripOpen"
        v-model:mode="addTripMode"
        :route-id="routeId"
        :direction-id="directionId"
        :service-id="serviceId"
        :derivation-stale="derivationStale"
        :trips="gridTrips"
        :duplicate-source-trip-id="duplicateSourceTripId"
        :service-items="serviceItems"
        :patterns="draftPatterns"
        :submitting="dialogSubmitting"
        @edit="onEdit"
      />
    </template>

    <UModal v-model:open="activateOpen" :title="$t('drafts.activateTitle')">
      <template #body>
        <div class="flex flex-col gap-4 text-sm text-muted">
          <p>{{ $t('drafts.activateBody') }}</p>
          <div v-if="draft?.derivationStale" class="text-warning">
            {{ $t('drafts.staleWarning') }}
          </div>
          <div v-if="(lastValidation?.errorCount ?? 0) > 0" class="text-error">
            {{ $t('drafts.outstandingErrors', {count: lastValidation?.errorCount ?? 0}, lastValidation?.errorCount ?? 0) }}
          </div>
          <USwitch v-model="force" :label="$t('drafts.force')"/>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="activateOpen = false"/>
          <UButton
            color="primary"
            :loading="activating"
            :disabled="!canActivate"
            :label="$t('drafts.activate')"
            @click="confirmActivate"
          />
        </div>
      </template>
    </UModal>

    <UModal v-model:open="discardOpen" :title="$t('drafts.discardTitle')">
      <template #body>
        <p class="text-sm text-muted">
          {{ $t('drafts.discardBody') }}
        </p>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="discardOpen = false"/>
          <UButton color="error" :loading="discarding" :label="$t('drafts.discard')" @click="confirmDiscard"/>
        </div>
      </template>
    </UModal>
  </AppPage>
</template>
