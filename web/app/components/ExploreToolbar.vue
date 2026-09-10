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
  <div class="flex h-12 items-center gap-2 overflow-x-auto">
    <ExploreTabs/>
    <div class="ml-auto flex shrink-0 items-center gap-2">
      <RouteTypeFilter v-if="showRouteType"/>
      <AgencyFilter v-if="showAgency"/>
    </div>
  </div>
</template>
