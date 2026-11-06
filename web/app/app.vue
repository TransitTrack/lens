<script setup lang="ts">
import { en, pl, fr, ro } from '@nuxt/ui/locale'

const uiLocales = { en, pl, fr, ro } as const

const { locale } = useLocaleSwitcher()
const uiLocale = computed(() => uiLocales[locale.value as keyof typeof uiLocales] ?? en)

useHead({ htmlAttrs: { lang: computed(() => locale.value) } })
</script>

<template>
  <UApp :locale="uiLocale">
    <NuxtRouteAnnouncer />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
  </UApp>
</template>
