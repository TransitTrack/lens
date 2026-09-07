<script setup lang="ts">
import { useFeedDetailQuery, useForkDraftMutation } from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import { useEditorIdentity } from '~/composables/useEditorIdentity'
import { gtfsDate } from '~/utils/gtfs'

const open = defineModel<boolean>('open', { default: false })

const { selectedFeedCode, feedPath } = useFeeds()
const toast = useToast()
const { name: editorName } = useEditorIdentity()

const label = ref('')
const editor = ref('')

const { result: feedResult } = useFeedDetailQuery(
  () => ({ code: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value && open.value }),
)

// ITEM-5 stopgap: forking is restricted to the feed's ACTIVE revision. Picking a
// non-active base offers routes/services the draft doesn't contain and lets
// AddTripDialog write a dangling service_id. Draft-scoped `draftRoutes` /
// `draftCalendar` queries are the proper fix (deferred). We always send
// `baseRevisionId: null` so the backend uses ACTIVE.
const activeRevisionLabel = computed(() => {
  const active = feedResult.value?.feed?.activeRevision
  return active ? `#${active.id} · ${gtfsDate(active.createdAt)}` : 'Active revision'
})

watch(open, (isOpen) => {
  if (isOpen) {
    label.value = ''
    editor.value = editorName.value ?? ''
  }
})

const { mutate: fork, loading: forking } = useForkDraftMutation()

async function submit() {
  const name = editor.value.trim()
  if (!name) return
  editorName.value = name
  toast.add({ title: 'Forking draft…', description: 'Copying rows', color: 'info', icon: 'i-lucide-git-fork' })
  try {
    const res = await fork({
      input: {
        feedCode: selectedFeedCode.value ?? '',
        baseRevisionId: null,
        label: label.value.trim() || null,
        editor: name,
      },
    })
    const id = res?.data?.forkDraft.id
    open.value = false
    if (id) await navigateTo(feedPath('/drafts/' + id))
  } catch (e) {
    toast.add({ title: 'Fork failed', description: (e as Error).message, color: 'error' })
  }
}
</script>

<template>
  <UModal v-model:open="open" title="New draft">
    <template #body>
      <div class="flex flex-col gap-3">
        <UFormField label="Label" hint="optional">
          <UInput v-model="label" placeholder="e.g. Summer 2026 timetable" class="w-full" />
        </UFormField>
        <UFormField label="Base revision" hint="active revision">
          <UInput :model-value="activeRevisionLabel" readonly disabled class="w-full" />
        </UFormField>
        <UFormField label="Your name">
          <UInput v-model="editor" placeholder="Your name" class="w-full" />
        </UFormField>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" label="Cancel" @click="open = false" />
        <UButton :loading="forking" :disabled="!editor.trim()" label="Create draft" @click="submit" />
      </div>
    </template>
  </UModal>
</template>
