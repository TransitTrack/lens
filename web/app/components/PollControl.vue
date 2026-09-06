<script setup lang="ts">
import { usePollControl } from '../composables/usePollControl'

const props = defineProps<{ updatedAt?: string | number | null }>()

const { paused, toggle } = usePollControl()

const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => (now.value = Date.now()), 5000)
})
onBeforeUnmount(() => clearInterval(timer))

const ago = computed(() => {
  if (props.updatedAt == null) return null
  const ms = now.value - new Date(props.updatedAt).getTime()
  if (!Number.isFinite(ms)) return null
  const s = Math.max(0, Math.round(ms / 1000))
  if (s < 5) return 'just now'
  if (s < 60) return `${s}s ago`
  return `${Math.round(s / 60)}m ago`
})
</script>

<template>
  <UTooltip :text="paused ? 'Resume live updates' : 'Pause live updates'">
    <UButton
      :color="paused ? 'warning' : 'neutral'"
      variant="ghost"
      size="sm"
      :icon="paused ? 'i-lucide-play' : 'i-lucide-pause'"
      @click="toggle"
    >
      <span class="flex items-center gap-1.5">
        <span
          class="size-2 rounded-full"
          :class="paused ? 'bg-warning' : 'animate-pulse bg-success'"
        />
        <span class="text-xs">
          {{ paused ? 'Paused' : ago ?? 'Live' }}
        </span>
      </span>
    </UButton>
  </UTooltip>
</template>
