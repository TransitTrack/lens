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
        scalars: {
          JSON: 'any',
        },
      },
    },
  },
}

export default config
