<script setup lang="ts">
import type { Route } from '~/api'
import {useRouteTypeFilter} from '~/composables/useRouteTypeFilter'

const props = defineProps<{ routes: Route[] | undefined }>()

const { t } = useI18n()
const {types, hasMultiple, typeId, setType} = useRouteTypeFilter(toRef(props, 'routes'))

const items = computed(() => [
  {label: t('filters.allTypes'), value: null},
  ...types.value.map((rt) => ({label: `${rt.label} (${rt.count})`, value: String(rt.type)})),
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
