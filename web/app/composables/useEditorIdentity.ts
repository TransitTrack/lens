import { ref, watch, onMounted, type Ref } from "vue";

/**
 * Editor identity — a display name for the person currently editing a draft,
 * persisted to `localStorage['tt-editor']`.
 *
 * The project has no programmatic modal helper (`useOverlay` / `overlay.create`
 * is unused; existing dialogs are inline `<UModal v-model:open>`), so the dialog
 * is driven by a module-level singleton that `EditorIdentityDialog.vue` — mounted
 * once, e.g. in the editor layout — reads.
 */

const STORAGE_KEY = "tt-editor";

const name: Ref<string | null> = ref(null);

let hydrated = false;
function hydrate() {
  if (hydrated || typeof window === "undefined") return;
  hydrated = true;
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (stored && stored.trim()) name.value = stored;
  } catch {
    /* localStorage unavailable — ignore */
  }
  watch(name, (value) => {
    try {
      if (value && value.trim()) window.localStorage.setItem(STORAGE_KEY, value);
      else window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  });
}

interface PendingRequest {
  resolve: (value: string) => void;
  reject: (reason: Error) => void;
}

/** Shared dialog state consumed by `EditorIdentityDialog.vue`. */
export const editorIdentityDialog = {
  open: ref(false),
  pending: null as PendingRequest | null,
};

/** Called by the dialog when the user saves a non-empty name. */
export function resolveEditorIdentity(entered: string) {
  const value = entered.trim();
  if (!value) return;
  name.value = value;
  editorIdentityDialog.open.value = false;
  editorIdentityDialog.pending?.resolve(value);
  editorIdentityDialog.pending = null;
}

/** Called by the dialog when it is dismissed without saving. */
export function cancelEditorIdentity() {
  editorIdentityDialog.open.value = false;
  editorIdentityDialog.pending?.reject(new Error("Editor identity dialog was dismissed"));
  editorIdentityDialog.pending = null;
}

export function useEditorIdentity(): {
  name: Ref<string | null>;
  ensure: () => Promise<string>;
} {
  hydrate();
  onMounted(hydrate);

  function ensure(): Promise<string> {
    const current = name.value;
    if (typeof current === "string" && current.trim()) {
      return Promise.resolve(current);
    }
    // Replace any in-flight request.
    editorIdentityDialog.pending?.reject(new Error("Superseded by a new editor identity request"));
    return new Promise<string>((resolve, reject) => {
      editorIdentityDialog.pending = { resolve, reject };
      editorIdentityDialog.open.value = true;
    });
  }

  return { name, ensure };
}
