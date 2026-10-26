<script setup lang="ts">
import ExploreTabs from './ExploreTabs.vue'
import AgencyFilter from './AgencyFilter.vue'
import RouteTypeFilter from './RouteTypeFilter.vue'

withDefaults(defineProps<{ showAgency?: boolean; showRouteType?: boolean }>(), {
  showAgency: true,
  showRouteType: false,
})
</script>

<template>
  <UDashboardToolbar>
    <template #left>
      <ExploreTabs/>
    </template>
    <template #right>
      <RouteTypeFilter v-if="showRouteType"/>
      <AgencyFilter v-if="showAgency"/>
    </template>
  </UDashboardToolbar>
</template>
