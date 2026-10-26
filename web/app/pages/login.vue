<script setup lang="ts">
definePageMeta({ layout: false })

const route = useRoute()
const { signIn } = useDemoAuth()
const username = ref('')
const password = ref('')
const error = ref(false)
const submitting = ref(false)

const redirectPath = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : '/'
})

async function submit() {
  error.value = false
  submitting.value = true

  if (!signIn(username.value, password.value)) {
    error.value = true
    submitting.value = false
    return
  }

  await navigateTo(redirectPath.value)
}
</script>

<template>
  <main class="relative grid min-h-svh place-items-center overflow-hidden bg-default p-4">
    <svg
      class="pointer-events-none absolute inset-0 size-full text-primary/25"
      viewBox="0 0 1440 900"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
    >
      <defs>
        <pattern id="map-grid" width="64" height="64" patternUnits="userSpaceOnUse">
          <path d="M0 32H64M32 0V64" stroke="currentColor" stroke-width="0.5" opacity="0.16" />
        </pattern>
        <radialGradient id="map-fade" cx="50%" cy="50%" r="64%">
          <stop offset="0%" stop-color="var(--ui-bg)" stop-opacity="0.1" />
          <stop offset="100%" stop-color="var(--ui-bg)" stop-opacity="0.92" />
        </radialGradient>
      </defs>
      <rect width="1440" height="900" fill="url(#map-grid)" />
      <g fill="none" stroke-linecap="round" stroke-linejoin="round">
        <path d="M-80 680 200 510 420 590 660 370 920 440 1150 220 1520 290" stroke="currentColor" stroke-width="7" opacity="0.48" />
        <path d="M-30 240 220 320 390 180 650 260 840 130 1110 280 1490 160" stroke="currentColor" stroke-width="4" opacity="0.32" />
        <path d="M140 980 290 730 500 760 680 590 870 690 1080 520 1320 600" stroke="currentColor" stroke-width="3" opacity="0.28" />
        <path d="M160 70 270 260 170 450 360 610 560 520 730 720 910 580 1210 760" stroke="currentColor" stroke-width="2" opacity="0.25" />
        <path d="M340-20 490 170 680 110 800 310 1040 360 1240 500 1510 450" stroke="currentColor" stroke-width="2" opacity="0.25" />
      </g>
      <g fill="var(--ui-bg)" stroke="currentColor" stroke-width="3" opacity="0.7">
        <circle cx="200" cy="510" r="9" />
        <circle cx="420" cy="590" r="9" />
        <circle cx="660" cy="370" r="10" />
        <circle cx="920" cy="440" r="9" />
        <circle cx="1150" cy="220" r="9" />
        <circle cx="390" cy="180" r="7" />
        <circle cx="840" cy="130" r="7" />
        <circle cx="1110" cy="280" r="7" />
      </g>
      <rect width="1440" height="900" fill="url(#map-fade)" />
    </svg>

    <section class="relative z-1 w-full max-w-sm border border-default bg-default/92 backdrop-blur-sm">
      <header class="border-b border-default p-5">
        <UIcon name="i-lucide-shield-check" class="size-5 text-primary" />
        <h1 class="mt-4 text-2xl font-semibold tracking-tight text-highlighted">Sign in</h1>
        <p class="mt-1 text-sm text-muted">Access the operations workspace.</p>
      </header>

      <form class="space-y-4 p-5" @submit.prevent="submit">
        <UFormField label="Username" name="username">
          <UInput v-model="username" autocomplete="username" autofocus class="w-full" />
        </UFormField>
        <UFormField label="Password" name="password">
          <UInput v-model="password" type="password" autocomplete="current-password" class="w-full" />
        </UFormField>
        <p v-if="error" class="text-sm text-error">Incorrect username or password.</p>
        <UButton type="submit" block :loading="submitting" label="Sign in" />
      </form>
    </section>
  </main>
</template>
