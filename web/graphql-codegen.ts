import type { CodegenConfig } from '@graphql-codegen/cli'

const config: CodegenConfig = {
  schema: '../app/src/main/resources/graphql/*.graphqls',
  documents: ['app/graphql/**/*.graphql'],
  generates: {
    'generated/graphql.ts': {
      plugins: ['typescript', 'typescript-operations', 'typed-document-node'],
      config: {
        // Enums as string-literal unions so they compare cleanly against string
        // values; the postinstall dedupe script drops the duplicate declaration
        // the operations plugin emits for the same types.
        enumsAsTypes: true,
        // Apollo Client 4 re-exports TypedDocumentNode, so no extra runtime dependency.
        documentNodeImport: '@apollo/client#TypedDocumentNode',
        useTypeImports: true,
        scalars: {
          JSON: 'any',
          Long: 'number',
        },
      },
    },
  },
}

export default config
