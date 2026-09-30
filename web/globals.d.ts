declare module '@vue/composition-api' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<unknown, unknown, unknown>
  export default component
  export * from 'vue'
}
