import type { KnipConfig } from 'knip'

// 対象は frontend/* と e2e。使われていないファイル、export、依存を見つける（フロントの書き方の「使われていないものを残さない」）。
// 定義したファイルの中だけで使う export も報告する（ignoreExportsUsedInFile は有効にしない）。

// 管理画面の vite.config.ts は、本番ビルドで IdP の設定がないと失敗する。knip は設定のファイルを読むので、
// CI と scripts/dev/check-frontend.sh と同じローカルの値を、なければ入れる
process.env.VITE_OIDC_AUTHORITY ??= 'http://localhost:8180/realms/ec-admin'
process.env.VITE_OIDC_CLIENT_ID ??= 'ec-admin-web'

const config: KnipConfig = {
  workspaces: {
    // ルートは、依存と設定だけを見る。scripts/ などは対象にしない
    '.': { entry: [], project: [] },
    'frontend/*': {},
    // 共有パッケージは、package.json の exports のワイルドカードで全てのファイルが入口になる。
    // 入口の export も、管理画面・店舗画面・テストから使われているかを見る
    'frontend/shared': { includeEntryExports: true },
    // i18n/types.ts は、どこからも import されず、型の拡張（declare module）で効く。tsconfig が読む
    'frontend/admin': { entry: ['src/i18n/types.ts'] },
    'frontend/store': { entry: ['src/i18n/types.ts'] },
    // prepare.ts は scripts/run.ts が node の子プロセスで起動する
    e2e: { entry: ['scripts/prepare.ts'] },
  },
  // 報告の対象外にするのは、生成物の export だけ。生成物のファイル自体が使われていないことは検査する
  ignoreIssues: {
    // openapi-typescript の型と、入力の制約の定数（pnpm generate:api が作る。Git に入れない）
    'frontend/*/src/api/generated/**': ['exports', 'types'],
    'e2e/src/generated/**': ['exports', 'types'],
    // shadcn/ui の生成物。使わない小部品や variants も、追加したときの形のまま残す（フロントの書き方の「使われていないものを残さない」）
    'frontend/*/src/components/ui/**': ['exports', 'types'],
    'frontend/shared/src/ui/**': ['exports', 'types'],
  },
}

export default config
