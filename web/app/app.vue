<script setup lang="ts">
import * as uiLocales from '@nuxt/ui/locale'

const { locale } = useLocaleSwitcher()
const uiLocale = computed(() => uiLocales[locale.value as 'en' | 'pl' | 'fr'] ?? uiLocales.en)

useHead({ htmlAttrs: { lang: computed(() => locale.value) } })
</script>

<template>
  <UApp :locale="uiLocale">
    <NuxtRouteAnnouncer />
    <RouteLoadingGlobe />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </UApp>
</template>
