<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'

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
    :content="{ align: 'end', collisionPadding: 12 }"
    :ui="{ content: 'w-48' }"
  >
    <UButton
      :avatar="user.avatar"
      :aria-label="user.name"
      color="neutral"
      variant="ghost"
      square
      class="data-[state=open]:bg-elevated"
    />
  </UDropdownMenu>
</template>
