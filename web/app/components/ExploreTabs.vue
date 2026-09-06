<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'
import { useFeeds } from '~/composables/useFeeds'

const { feedPath } = useFeeds()

const items = computed<NavigationMenuItem[]>(() => [
  { label: 'Routes', icon: 'i-lucide-route', to: feedPath('/explore/routes') },
  { label: 'Stops', icon: 'i-lucide-map-pin', to: feedPath('/explore/stops') },
  { label: 'Trips', icon: 'i-lucide-git-branch', to: feedPath('/explore/trips') },
  { label: 'Blocks', icon: 'i-lucide-layers', to: feedPath('/explore/blocks') },
  { label: 'Calendar', icon: 'i-lucide-calendar-days', to: feedPath('/explore/calendar') },
  { label: 'Feed', icon: 'i-lucide-database', to: feedPath('/explore/feed') },
])
</script>

<template>
  <UNavigationMenu :items="items" highlight class="-mx-1 flex-1" />
</template>
