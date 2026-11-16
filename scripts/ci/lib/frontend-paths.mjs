// フロントのテストと E2E のファイルの場所。

/** 画面の単体・結合テストのファイル */
export const FRONTEND_TEST = /^frontend\/(?:store|admin|shared)\/src\/.*\.test\.tsx?$/

/** 画面のテストの補助のファイル（src の下の test/ ディレクトリの下） */
export const FRONTEND_TEST_SUPPORT = /^frontend\/(?:store|admin|shared)\/src\/(?:.*\/)?test\/.*\.tsx?$/

/** E2E のファイル */
export const E2E_FILE = /^e2e\/.*\.tsx?$/
