<script setup lang="ts">
import {useRouteTypeFilter} from '~/composables/useRouteTypeFilter'

const {types, hasMultiple, typeId, setType} = useRouteTypeFilter()

const items = computed(() => [
  {label: 'All types', value: null},
  ...types.value.map((t) => ({label: `${t.label} (${t.count})`, value: String(t.type)})),
])
</script>

<template>
  <USelectMenu
    v-if="hasMultiple"
    :model-value="typeId"
    :items="items"
    value-key="value"
    icon="i-lucide-shapes"
    class="w-44"
    @update:model-value="setType"
  />
</template>
