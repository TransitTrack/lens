<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'
import { useFeeds } from '~/composables/useFeeds'

const { t } = useI18n()
const { feedPath } = useFeeds()

const items = computed<NavigationMenuItem[]>(() => [
  { label: t('nav.routes'), icon: 'i-lucide-route', to: feedPath('/explore/routes') },
  { label: t('nav.stops'), icon: 'i-lucide-map-pin', to: feedPath('/explore/stops') },
  { label: t('nav.trips'), icon: 'i-lucide-git-branch', to: feedPath('/explore/trips') },
  { label: t('nav.blocks'), icon: 'i-lucide-layers', to: feedPath('/explore/blocks') },
  { label: t('nav.calendar'), icon: 'i-lucide-calendar-days', to: feedPath('/explore/calendar') },
  { label: t('nav.feed'), icon: 'i-lucide-database', to: feedPath('/explore/feed') },
])
</script>

<template>
  <UNavigationMenu :items="items" highlight class="-mx-1 flex-1" />
</template>
