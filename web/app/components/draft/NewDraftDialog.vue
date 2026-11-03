<script setup lang="ts">
import { useFeedDetailQuery, useForkDraftMutation } from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import { useEditorIdentity } from '~/composables/useEditorIdentity'
import { gtfsDate } from '~/utils/gtfs'

const open = defineModel<boolean>('open', { default: false })

const { selectedFeedCode, feedPath } = useFeeds()
const toast = useToast()
const { t, locale } = useI18n()
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
  return active
    ? t('drafts.revisionLabel', { id: active.id, date: gtfsDate(active.createdAt, locale.value) })
    : t('feed.overview.activeRevision')
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
  toast.add({ title: t('drafts.forking'), description: t('drafts.copyingRows'), color: 'info', icon: 'i-lucide-git-fork' })
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
    toast.add({ title: t('drafts.forkFailed'), description: (e as Error).message, color: 'error' })
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="$t('drafts.newDraft')">
    <template #body>
      <div class="flex flex-col gap-3">
        <UFormField :label="$t('drafts.col.label')" :hint="$t('drafts.optional')">
          <UInput v-model="label" :placeholder="$t('drafts.labelPlaceholder')" class="w-full" />
        </UFormField>
        <UFormField :label="$t('drafts.baseRevision')" :hint="$t('drafts.activeRevisionHint')">
          <UInput :model-value="activeRevisionLabel" readonly disabled class="w-full" />
        </UFormField>
        <UFormField :label="$t('drafts.yourName')">
          <UInput v-model="editor" :placeholder="$t('drafts.yourName')" class="w-full" />
        </UFormField>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="open = false" />
        <UButton :loading="forking" :disabled="!editor.trim()" :label="$t('drafts.createDraft')" @click="submit" />
      </div>
    </template>
  </UModal>
</template>
