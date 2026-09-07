<script setup lang="ts">
import { ref, watch } from "vue";
import {
  editorIdentityDialog,
  resolveEditorIdentity,
  cancelEditorIdentity,
} from "~/composables/useEditorIdentity";

const open = editorIdentityDialog.open;
const value = ref("");

watch(open, (isOpen) => {
  if (isOpen) value.value = "";
});

function save() {
  if (!value.value.trim()) return;
  resolveEditorIdentity(value.value);
}

function onOpenChange(isOpen: boolean) {
  if (!isOpen) cancelEditorIdentity();
}
</script>

<template>
  <UModal
    :open="open"
    title="Who's editing?"
    @update:open="onOpenChange"
  >
    <template #body>
      <UInput
        v-model="value"
        placeholder="Your name"
        autofocus
        class="w-full"
        @keydown.enter="save"
      />
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" label="Cancel" @click="cancelEditorIdentity" />
        <UButton :disabled="!value.trim()" label="Save" @click="save" />
      </div>
    </template>
  </UModal>
</template>
