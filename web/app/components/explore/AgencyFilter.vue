<script setup lang="ts">
import type { Agency } from '~/api'
import {useAgencyFilter} from '~/composables/useAgencyFilter'

const props = defineProps<{ agencies: Agency[] | undefined }>()

const { t } = useI18n()
const {agencies, hasMultiple, agencyId, setAgency} = useAgencyFilter(toRef(props, 'agencies'))

const items = computed(() => [
  {label: t('filters.allAgencies'), value: null},
  ...agencies.value.map((a) => ({label: a.agencyName ?? a.agencyId ?? '—', value: a.agencyId})),
])
</script>

<template>
  <USelectMenu
    v-if="hasMultiple"
    :model-value="agencyId"
    :items="items"
    value-key="value"
    icon="i-lucide-building-2"
    class="w-52"
    @update:model-value="setAgency"
  />
</template>
