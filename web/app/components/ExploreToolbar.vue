<script setup lang="ts">
import ExploreTabs from './ExploreTabs.vue'
import AgencyFilter from './AgencyFilter.vue'

withDefaults(defineProps<{ showAgency?: boolean }>(), { showAgency: true })
</script>

<template>
  <UDashboardToolbar>
    <ExploreTabs />
    <template #right>
      <AgencyFilter v-if="showAgency" />
    </template>
  </UDashboardToolbar>
</template>
