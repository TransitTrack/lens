<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'

defineProps<{
  /** Icon-only trigger for a collapsed sidebar; full name row otherwise. */
  collapsed?: boolean
}>()

const colorMode = useColorMode()

const user = {
  name: 'Operator',
  avatar: { icon: 'i-lucide-user-round' },
}

const items = computed<DropdownMenuItem[][]>(() => [
  [{ type: 'label', label: user.name, avatar: user.avatar }],
  [
    {
      label: 'Appearance',
      icon: 'i-lucide-sun-moon',
      children: [
        {
          label: 'Light',
          icon: 'i-lucide-sun',
          type: 'checkbox',
          checked: colorMode.value === 'light',
          onSelect(e: Event) {
            e.preventDefault()
            colorMode.preference = 'light'
          },
        },
        {
          label: 'Dark',
          icon: 'i-lucide-moon',
          type: 'checkbox',
          checked: colorMode.value === 'dark',
          onSelect(e: Event) {
            e.preventDefault()
            colorMode.preference = 'dark'
          },
        },
      ],
    },
  ],
  [
    {
      label: 'GitHub repository',
      icon: 'i-simple-icons-github',
      to: 'https://github.com/transittrack',
      target: '_blank',
    },
  ],
])
</script>

<template>
  <UDropdownMenu
    :items="items"
    :content="{ side: 'top', align: 'start', collisionPadding: 12 }"
    :ui="{ content: 'w-56' }"
  >
    <UButton
      v-if="collapsed"
      :avatar="user.avatar"
      :aria-label="user.name"
      color="neutral"
      variant="ghost"
      square
      class="data-[state=open]:bg-elevated"
    />
    <UButton
      v-else
      :avatar="user.avatar"
      :label="user.name"
      color="neutral"
      variant="ghost"
      trailing-icon="i-lucide-chevrons-up-down"
      block
      class="justify-start data-[state=open]:bg-elevated"
      :ui="{ trailingIcon: 'ms-auto text-dimmed' }"
    />
  </UDropdownMenu>
</template>
