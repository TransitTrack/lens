<script setup lang="ts">
import { useExploreQuery } from '~/composables/useExploreQuery'
import { TIME_PRESETS, formatHm, parseHm } from '~/utils/tripFilters'

const { t } = useI18n()
const { param } = useExploreQuery()
const from = param('from')
const to = param('to')

const fromSec = computed(() => parseHm(from.value))
const toSec = computed(() => parseHm(to.value))

const activeKey = computed(() => {
  const match = TIME_PRESETS.find(
    (p) =>
      (p.fromSec ?? null) === (fromSec.value ?? null) &&
      (p.toSec ?? null) === (toSec.value ?? null),
  )
  return match?.key ?? null
})

function applyPreset(key: string) {
  const p = TIME_PRESETS.find((x) => x.key === key)
  if (!p) return
  from.value = p.fromSec == null ? null : formatHm(p.fromSec)
  to.value = p.toSec == null ? null : formatHm(p.toSec)
}

// i18n-keys: filters.time.*
const presetLabel = (key: string) => t(`filters.time.${key}`)

const fromModel = computed({
  get: () => from.value ?? '',
  set: (v: string) => (from.value = v || null),
})
const toModel = computed({
  get: () => to.value ?? '',
  set: (v: string) => (to.value = v || null),
})
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <div class="flex flex-wrap gap-1">
      <UButton
        v-for="p in TIME_PRESETS"
        :key="p.key"
        size="xs"
        :color="activeKey === p.key ? 'primary' : 'neutral'"
        :variant="activeKey === p.key ? 'soft' : 'ghost'"
        :label="presetLabel(p.key)"
        @click="applyPreset(p.key)"
      />
    </div>
    <div class="flex items-center gap-1 text-xs text-dimmed">
      <UInput v-model="fromModel" type="time" size="xs" :aria-label="$t('filters.fromTime')" />
      <span>–</span>
      <UInput v-model="toModel" type="time" size="xs" :aria-label="$t('filters.toTime')" />
    </div>
  </div>
</template>
