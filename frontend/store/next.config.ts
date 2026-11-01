import type { NextConfig } from 'next'
import createNextIntlPlugin from 'next-intl/plugin'

/** 文言のツール（next-intl）。リクエストごとの設定のファイルを渡す */
const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts')

/**
 * Next.js の設定。<br />
 * API の結果のキャッシュは、fetch の revalidate で扱う（店舗画面の構成の「API の呼び出し」）。
 * Cache Components（'use cache'）は使わない。
 */
const nextConfig: NextConfig = {
  // 共有のパッケージは TypeScript のソースのまま使う（フロントの書き方の「共有パッケージ」）
  transpilePackages: ['@ec-site/shared'],
  typedRoutes: true,
  // 開発のサーバーに、エージェント向けの AGENTS.md を作らせない。エージェントの手順はリポジトリのルートの AGENTS.md と skill に書く
  agentRules: false,
  turbopack: {
    rules: {
      '*.css': {
        loaders: ['@tailwindcss/turbopack'],
        as: '*.css',
      },
    },
  },
}

export default withNextIntl(nextConfig)
