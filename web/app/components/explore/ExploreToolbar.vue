<script setup lang="ts">
import type { Agency, Route } from '~/api'
import ExploreTabs from '~/components/explore/ExploreTabs.vue'
import AgencyFilter from '~/components/explore/AgencyFilter.vue'
import RouteTypeFilter from '~/components/explore/RouteTypeFilter.vue'

withDefaults(defineProps<{
  showAgency?: boolean
  showRouteType?: boolean
  agencies?: Agency[]
  routes?: Route[]
}>(), {
  showAgency: true,
  showRouteType: false,
  agencies: undefined,
  routes: undefined,
})
</script>

<template>
  <UDashboardToolbar>
    <template #left>
      <ExploreTabs/>
    </template>
    <template #right>
      <RouteTypeFilter v-if="showRouteType" :routes="routes"/>
      <AgencyFilter v-if="showAgency" :agencies="agencies"/>
    </template>
  </UDashboardToolbar>
</template>
