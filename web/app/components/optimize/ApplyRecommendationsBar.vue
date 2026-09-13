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
      title: 'Applied',
      description: draft ? `Created draft #${draft.id}` : undefined,
      color: 'success',
      icon: 'i-lucide-check',
      actions: draft
        ? [{label: 'View draft', onClick: () => navigateTo(feedPath('/drafts/' + draft.id))}]
        : undefined,
    })
    emit('applied')
  } catch (e) {
    const first = extractGraphQlErrors(e)[0]
    if (first?.extensions?.code === 'RECOMMENDATION_CONFLICT') {
      const ids = (first.extensions.conflictingRecommendationIds ?? []).map(String)
      emit('conflict', ids)
      toast.add({
        title: 'Selection conflicts',
        description: 'Two selected recommendations propose different values for the same target.',
        color: 'error',
      })
    } else {
      toast.add({title: 'Apply failed', description: (e as Error).message, color: 'error'})
    }
  }
}
</script>

<template>
  <div class="flex items-center gap-3 border-t border-default pt-4">
    <span class="text-sm text-muted">{{ selectedIds.length }} selected</span>
    <UInput v-model="label" placeholder="Label (optional)" class="w-64" />
    <UInput v-model="editor" placeholder="Your name" class="w-48" />
    <UButton
      :loading="applying"
      :disabled="!selectedIds.length || !editor.trim()"
      label="Apply selected"
      icon="i-lucide-check"
      @click="submit"
    />
  </div>
</template>
