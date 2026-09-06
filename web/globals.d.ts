declare module '@vue/composition-api' {
  import {DefineComponent} from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
  export * from 'vue'
}
