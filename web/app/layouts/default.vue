<script setup lang="ts">
import FeedPicker from '../components/FeedPicker.vue'
</script>

<template>
  <div class="flex h-screen flex-col">
    <div class="border-default flex items-center justify-between border-b p-4">
      <nav class="flex gap-4 text-sm font-medium">
        <NuxtLink to="/" class="hover:text-primary" active-class="text-primary">Vehicles</NuxtLink>
        <NuxtLink to="/headway" class="hover:text-primary" active-class="text-primary"
          >Headway</NuxtLink
        >
      </nav>
      <FeedPicker />
    </div>
    <div class="min-h-0 flex-1">
      <slot />
    </div>
  </div>
</template>
