<script setup lang="ts">
import type {NavigationMenuItem} from '@nuxt/ui'
import UserMenu from '~/components/UserMenu.vue'
import NotificationsSlideover from '~/components/NotificationsSlideover.vue'
import {useFeeds} from '~/composables/useFeeds'
import {useFeedAlerts} from '~/composables/useFeedAlerts'
import Logo from "~/components/Logo.vue";

useFeedAlerts()

const {feeds, selectedFeedCode, feedIsUnknown, feedPath} = useFeeds()

// If the URL points at a feed that doesn't exist, fall back to the first one.
watch(feedIsUnknown, (unknown) => {
  if (unknown && feeds.value[0]) {
    navigateTo(`/${feeds.value[0].code}`, {replace: true})
  }
})

const open = ref(false)
const close = () => (open.value = false)

const links = computed<NavigationMenuItem[][]>(() => [
  [
    {label: 'Overview', icon: 'i-lucide-layout-dashboard', to: feedPath(), exact: true, onSelect: close},
    {label: 'Vehicles', icon: 'i-lucide-bus', to: feedPath('/vehicles'), onSelect: close},
    {label: 'Headway', icon: 'i-lucide-clock', to: feedPath('/headway'), onSelect: close},
      {label: 'Drafts', icon: 'i-lucide-file-pen-line', to: feedPath('/drafts'), onSelect: close},
    {
      label: 'Explore',
      icon: 'i-lucide-compass',
      type: 'trigger',
      defaultOpen: true,
      children: [
        {label: 'Routes', to: feedPath('/explore/routes'), onSelect: close},
        {label: 'Stops', to: feedPath('/explore/stops'), onSelect: close},
        {label: 'Trips', to: feedPath('/explore/trips'), onSelect: close},
        {label: 'Blocks', to: feedPath('/explore/blocks'), onSelect: close},
        {label: 'Calendar', to: feedPath('/explore/calendar'), onSelect: close},
        {label: 'Feed', to: feedPath('/explore/feed'), onSelect: close},
      ],
    },
  ],
  [
    {label: 'Nuxt UI docs', icon: 'i-lucide-book-open', to: 'https://ui.nuxt.com', target: '_blank'},
    {label: 'GitHub', icon: 'i-simple-icons-github', to: 'https://github.com/transittrack', target: '_blank'},
  ],
])

const groups = computed(() => [
  {id: 'links', label: 'Go to', items: links.value.flat()},
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
  <UDashboardGroup unit="rem">
    <UDashboardSidebar
      id="default"
      v-model:open="open"
      collapsible
      resizable
      class="bg-elevated/25"
      :ui="{ footer: 'lg:border-t lg:border-default' }"
    >
      <template #header="{ collapsed }">
        <Logo :collapsed="collapsed"/>
      </template>

      <template #default="{ collapsed }">
        <UDashboardSearchButton :collapsed="collapsed" class="bg-transparent ring-default"/>

        <UNavigationMenu
          :collapsed="collapsed"
          :items="links[0]"
          orientation="vertical"
          tooltip
          popover
        />

        <UNavigationMenu
          :collapsed="collapsed"
          :items="links[1]"
          orientation="vertical"
          tooltip
          class="mt-auto"
        />
      </template>

      <template #footer="{ collapsed }">
        <UserMenu :collapsed="collapsed"/>
      </template>
    </UDashboardSidebar>

    <UDashboardSearch :groups="groups"/>

    <slot v-if="selectedFeedCode"/>

    <NotificationsSlideover/>
  </UDashboardGroup>
</template>
