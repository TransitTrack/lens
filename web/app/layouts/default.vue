<script setup lang="ts">
import type {NavigationMenuItem} from '@nuxt/ui'
import AppLogo from '~/components/AppLogo.vue'
import UserMenu from '~/components/UserMenu.vue'
import FeedSwitcher from '~/components/FeedSwitcher.vue'
import FeedDrawer from '~/components/FeedDrawer.vue'
import NotificationsSlideover from '~/components/NotificationsSlideover.vue'
import {useFeeds} from '~/composables/useFeeds'
import {useFeedAlerts} from '~/composables/useFeedAlerts'
import {useDashboard} from '~/composables/useDashboard'

useFeedAlerts()

const {feeds, selectedFeedCode, feedIsUnknown, feedPath} = useFeeds()
const {isNotificationsSlideoverOpen} = useDashboard()

// If the URL points at a feed that doesn't exist, fall back to the first one.
watch(feedIsUnknown, (unknown) => {
  if (unknown && feeds.value[0]) {
    navigateTo(`/${feeds.value[0].code}`, {replace: true})
  }
})

const searchOpen = ref(false)

const primaryNav = computed<NavigationMenuItem[]>(() => [
  {label: 'Home', icon: 'i-lucide-layout-dashboard', to: feedPath(), exact: true},
  {
    label: 'Realtime',
    icon: 'i-lucide-radio',
    children: [
      {label: 'Vehicles', icon: 'i-lucide-bus', to: feedPath('/vehicles')},
      {label: 'Headway', icon: 'i-lucide-clock', to: feedPath('/headway')},
    ],
  },
  {
    label: 'Static',
    icon: 'i-lucide-database',
    children: [
      {label: 'Routes', icon: 'i-lucide-route', to: feedPath('/explore/routes')},
      {label: 'Stops', icon: 'i-lucide-map-pin', to: feedPath('/explore/stops')},
      {label: 'Trips', icon: 'i-lucide-git-branch', to: feedPath('/explore/trips')},
      {label: 'Blocks', icon: 'i-lucide-layers', to: feedPath('/explore/blocks')},
      {label: 'Calendar', icon: 'i-lucide-calendar-days', to: feedPath('/explore/calendar')},
      {label: 'Feed', icon: 'i-lucide-file-text', to: feedPath('/explore/feed')},
      {label: 'Drafts', icon: 'i-lucide-file-pen-line', to: feedPath('/drafts')},
    ],
  },
])

const groups = computed(() => [
  {id: 'links', label: 'Go to', items: primaryNav.value.flatMap((i) => i.children ?? [i])},
  {
    id: 'feeds',
    label: 'Switch feed',
    items: feeds.value.map((f) => ({
      id: `feed-${f.code}`,
      label: f.name,
      suffix: f.code,
      icon: 'i-lucide-rss',
      to: `/${f.code}`,
    })),
  },
])
</script>

<template>
  <div>
    <UHeader mode="drawer" :ui="{ center: 'flex-1 justify-center' }">
      <template #left>
        <NuxtLink :to="feedPath()" aria-label="Home">
          <AppLogo :font-size="16" :icon-only="false"/>
        </NuxtLink>
      </template>

      <UNavigationMenu :items="primaryNav" class="justify-center"/>

      <template #right>
        <UButton
          color="neutral"
          variant="ghost"
          square
          aria-label="Feed status"
          @click="isNotificationsSlideoverOpen = true"
        >
          <UChip color="error" inset>
            <UIcon name="i-lucide-bell" class="size-5 shrink-0"/>
          </UChip>
        </UButton>
        <UserMenu/>
      </template>

      <template #content>
        <UNavigationMenu :items="primaryNav" orientation="vertical" class="mx-auto"/>
        <USeparator class="my-3"/>
        <FeedSwitcher block/>
      </template>
    </UHeader>

    <main>
      <slot v-if="selectedFeedCode"/>
    </main>

    <FeedSwitcher/>
    <UDashboardSearch v-model:open="searchOpen" :groups="groups"/>
    <FeedDrawer/>
    <NotificationsSlideover/>
  </div>
</template>
