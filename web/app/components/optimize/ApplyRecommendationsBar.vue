<script setup lang="ts">
import {useApplyOptimizationRecommendationsMutation} from '~~/generated/graphql'
import {useEditorIdentity} from '~/composables/useEditorIdentity'
import {useFeeds} from '~/composables/useFeeds'

const props = defineProps<{ runId: string; selectedIds: string[] }>()
const emit = defineEmits<{
  applied: []
  conflict: [ids: string[]]
}>()

const {feedPath} = useFeeds()
const {name: editorName} = useEditorIdentity()
const toast = useToast()
const {t} = useI18n()

const label = ref('')
const editor = ref('')

watch(
  () => editorName.value,
  (v) => (editor.value = v ?? editor.value),
  {immediate: true},
)

const {mutate: apply, loading: applying} = useApplyOptimizationRecommendationsMutation()

interface GraphQlErrorLike {
  extensions?: { code?: string; conflictingRecommendationIds?: string[] } | null
}
function extractGraphQlErrors(e: unknown): GraphQlErrorLike[] {
  const err = e as {
    graphQLErrors?: ReadonlyArray<GraphQlErrorLike>
    cause?: { graphQLErrors?: ReadonlyArray<GraphQlErrorLike> }
  }
  return [...(err?.graphQLErrors ?? err?.cause?.graphQLErrors ?? [])]
}

async function submit() {
  const name = editor.value.trim()
  if (!name || !props.selectedIds.length) return
  editorName.value = name
  try {
    const res = await apply({
      runId: props.runId,
      recommendationIds: props.selectedIds,
      label: label.value.trim() || null,
      editor: name,
    })
    const draft = res?.data?.applyOptimizationRecommendations
    toast.add({
      title: t('optimize.applied'),
      description: draft ? t('optimize.createdDraft', {id: draft.id}) : undefined,
      color: 'success',
      icon: 'i-lucide-check',
      actions: draft
        ? [{label: t('optimize.viewDraft'), onClick: () => navigateTo(feedPath('/drafts/' + draft.id))}]
        : undefined,
    })
    emit('applied')
  } catch (e) {
    const first = extractGraphQlErrors(e)[0]
    if (first?.extensions?.code === 'RECOMMENDATION_CONFLICT') {
      const ids = (first.extensions.conflictingRecommendationIds ?? []).map(String)
      emit('conflict', ids)
      toast.add({
        title: t('optimize.conflictTitle'),
        description: t('optimize.conflictBody'),
        color: 'error',
      })
    } else {
      toast.add({title: t('optimize.applyFailed'), description: (e as Error).message, color: 'error'})
    }
  }
}
</script>

<template>
  <aside class="sticky bottom-4 z-10 rounded-md border border-primary/30 bg-default/95 p-4 shadow-lg backdrop-blur sm:p-5">
    <div class="flex flex-col gap-4 lg:flex-row lg:items-center">
      <div class="flex min-w-44 items-center gap-3">
        <div class="grid size-10 place-items-center rounded-full bg-primary/10 text-primary">
          <UIcon name="i-lucide-list-checks" class="size-5" />
        </div>
        <div>
          <p class="text-sm font-semibold text-highlighted">{{ $t('optimize.ready') }}</p>
          <p class="text-sm text-muted">{{ $t('optimize.changesSelected', {count: selectedIds.length}, selectedIds.length) }}</p>
        </div>
      </div>
      <div class="grid flex-1 gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(10rem,1fr)_12rem_auto]">
        <UInput v-model="label" :placeholder="$t('optimize.labelPlaceholder')" />
        <UInput v-model="editor" :placeholder="$t('drafts.yourName')" />
        <UButton
          :loading="applying"
          :disabled="!selectedIds.length || !editor.trim()"
          :label="$t('optimize.applyToDraft')"
          icon="i-lucide-arrow-right"
          class="justify-center"
          @click="submit"
        />
      </div>
    </div>
  </aside>
</template>
