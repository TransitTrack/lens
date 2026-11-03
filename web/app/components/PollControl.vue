<script setup lang="ts">
import { usePollControl } from '../composables/usePollControl'

const props = defineProps<{ updatedAt?: string | number | null }>()

const { t } = useI18n()
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
  if (s < 5) return t('poll.justNow')
  if (s < 60) return t('poll.secondsAgo', { count: s })
  return t('poll.minutesAgo', { count: Math.round(s / 60) })
})
</script>

<template>
  <UTooltip :text="paused ? $t('poll.resume') : $t('poll.pause')">
    <UButton
      :color="paused ? 'warning' : 'neutral'"
      variant="ghost"
      :icon="paused ? 'i-lucide-play' : 'i-lucide-pause'"
      @click="toggle"
    >
      <span class="flex items-center gap-1.5">
        <span
          class="size-2 rounded-full"
          :class="paused ? 'bg-warning' : 'animate-pulse bg-success'"
        />
        <span class="text-xs">
          {{ paused ? $t('poll.paused') : (ago ?? $t('poll.live')) }}
        </span>
      </span>
    </UButton>
  </UTooltip>
</template>
