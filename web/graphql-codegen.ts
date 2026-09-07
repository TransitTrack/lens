import type { CodegenConfig } from '@graphql-codegen/cli'

const config: CodegenConfig = {
  schema: '../src/main/resources/graphql/*.graphqls',
  documents: ['app/graphql/**/*.graphql'],
  generates: {
    'generated/graphql.ts': {
      plugins: ['typescript', 'typescript-operations', 'typescript-vue-apollo'],
      config: {
        withCompositionFunctions: true,
        vueApolloComposableImportFrom: '@vue/apollo-composable',
        // Enums as string-literal unions so they compare cleanly against string
        // values; the postinstall dedupe script drops the duplicate declaration
        // the operations plugin emits for the same types.
        enumsAsTypes: true,
        scalars: {
          JSON: 'any',
          Long: 'number',
        },
      },
    },
  },
}

export default config
