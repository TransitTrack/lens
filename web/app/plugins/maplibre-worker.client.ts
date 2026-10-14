import { setWorkerUrl } from 'maplibre-gl'
// maplibre-gl resolves its worker script as a sibling of its own bundled
// chunk's `import.meta.url` at runtime, using a computed (non-literal)
// filename — a pattern Vite's static worker-asset analysis can't follow, so
// the build never emits `maplibre-gl-worker.mjs` and the browser 404s on it.
// Importing the worker file with `?url` is a literal specifier Vite *can*
// resolve statically, which makes it emit the file as a real built asset —
// but a plain `?url` import just copies the file byte-for-byte, so the
// worker's own literal `import ... from "./maplibre-gl-shared.mjs"` is left
// unresolved too and that sibling file 404s the same way. `?worker&url`
// instead runs the file through Vite's worker build pipeline, which bundles
// that import's contents into the emitted worker chunk (no second missing
// file) and returns its URL without instantiating a Worker itself.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'

export default defineNuxtPlugin(() => {
  setWorkerUrl(workerUrl)
})
