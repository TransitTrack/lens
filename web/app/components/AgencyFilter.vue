<script setup lang="ts">
import { useAgencyFilter } from '~/composables/useAgencyFilter'

const { agencies, hasMultiple, agencyId, setAgency } = useAgencyFilter()

const items = computed(() => [
  { label: 'All agencies', value: null },
  ...agencies.value.map((a) => ({ label: a.agencyName ?? a.agencyId ?? '—', value: a.agencyId })),
])
</script>

<template>
  <USelectMenu
    v-if="hasMultiple"
    :model-value="agencyId"
    :items="items"
    value-key="value"
    icon="i-lucide-building-2"
    size="sm"
    class="w-52"
    @update:model-value="setAgency"
  />
</template>
