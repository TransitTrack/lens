<script setup lang="ts">
/**
 * Renders a ```mermaid fence from docs/*.md (the rehypeMermaid content plugin turns
 * those fences into `<docs-mermaid code="…">`). mermaid is imported on demand so
 * it's only downloaded by docs pages that actually contain a diagram.
 */
const props = defineProps<{ code: string }>()

const colorMode = useColorMode()
const svg = ref<string | null>(null)
const failed = ref(false)
// mermaid.render needs a DOM-id-safe, unique id per call
const baseId = `docs-mermaid-${useId().replace(/[^\w-]/g, '')}`
let renders = 0

async function render() {
  try {
    const { default: mermaid } = await import('mermaid')
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      theme: colorMode.value === 'dark' ? 'dark' : 'neutral',
    })
    svg.value = (await mermaid.render(`${baseId}-${++renders}`, props.code)).svg
    failed.value = false
  } catch {
    failed.value = true
  }
}

watch(() => [props.code, colorMode.value], render, { immediate: true })
</script>

<template>
  <pre
    v-if="failed"
    class="my-5 overflow-x-auto rounded-md border border-muted bg-muted px-4 py-3 text-sm"
    >{{ code }}</pre>
  <USkeleton v-else-if="!svg" class="my-5 h-48 w-full" />
  <!-- eslint-disable-next-line vue/no-v-html -- mermaid output, securityLevel: strict, from our own docs -->
  <figure
    v-else
    class="docs-mermaid my-5 overflow-x-auto rounded-md border border-muted bg-default p-4"
    v-html="svg"
  />
</template>

<style scoped>
.docs-mermaid :deep(svg) {
  max-width: 100%;
  height: auto;
  margin-inline: auto;
}
</style>
