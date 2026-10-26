<script setup lang="ts">
defineProps<{ collapsed?: boolean }>()

const currentTime = ref(new Date())
let clockTimer: ReturnType<typeof setInterval> | undefined

const formattedClock = computed(() => {
  const date = currentTime.value
  const twoDigits = (value: number) => String(value).padStart(2, '0')
  return `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}:${twoDigits(date.getSeconds())}`
})

const formattedCalendarDate = computed(() => {
  const date = currentTime.value
  const weekday = new Intl.DateTimeFormat('en-GB', { weekday: 'short' }).format(date)
  const twoDigits = (value: number) => String(value).padStart(2, '0')
  return `${weekday}, ${twoDigits(date.getDate())}.${twoDigits(date.getMonth() + 1)}.${date.getFullYear()}`
})

onMounted(() => {
  currentTime.value = new Date()
  clockTimer = setInterval(() => {
    currentTime.value = new Date()
  }, 1000)
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<template>
  <div
    v-if="!collapsed"
    class="flex flex-col w-full items-baseline border rounded shadow border-default bg-elevated/50 px-4 py-2.5"
  >
    <time
      :datetime="currentTime.toISOString()"
      class="min-w-0 leading-tight"
      aria-label="Current local time"
    >
      <span
        class="block text-3xl tracking-tighter leading-tight font-semibold tabular-nums text-highlighted"
        >{{ formattedClock }}</span
      >
    </time>
    <span class="tracking-tighter text-[11px] leading-tight text-muted">{{
      formattedCalendarDate
    }}</span>
  </div>
</template>
