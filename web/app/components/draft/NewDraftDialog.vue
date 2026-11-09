<script setup lang="ts">
import type { Revision } from '~/api'
import { useEditorIdentity } from '~/composables/useEditorIdentity'
import { gtfsDate } from '~/utils/gtfs'

const open = defineModel<boolean>('open', { default: false })

const props = defineProps<{
  /** The feed's ACTIVE revision — the base every new draft is forked from. */
  activeRevision: Revision | null
  forking: boolean
}>()

const emit = defineEmits<{
  submit: [input: { label: string | null, editor: string }]
}>()

const { t, locale } = useI18n()
const { name: editorName } = useEditorIdentity()

const label = ref('')
const editor = ref('')

// ITEM-5 stopgap: forking is restricted to the feed's ACTIVE revision. Picking a
// non-active base offers routes/services the draft doesn't contain and lets
// AddTripDialog write a dangling service_id. Draft-scoped `draftRoutes` /
// `draftCalendar` queries are the proper fix (deferred). The page always sends
// `baseRevisionId: null` so the backend uses ACTIVE.
const activeRevisionLabel = computed(() => {
  const active = props.activeRevision
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

function submit() {
  const name = editor.value.trim()
  if (!name) return
  emit('submit', { label: label.value.trim() || null, editor: name })
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
