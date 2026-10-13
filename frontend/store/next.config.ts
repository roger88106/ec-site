import type { NextConfig } from 'next'
import createNextIntlPlugin from 'next-intl/plugin'

/** 文言の道具（next-intl）。リクエストごとの設定のファイルを渡す */
const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts')

/**
 * Next.js の設定。<br />
 * API の結果のキャッシュは、fetch の revalidate で扱う（店舗画面の構成 第 4 節）。
 * Cache Components（'use cache'）は使わない。
 */
const nextConfig: NextConfig = {
  // 共有のパッケージは TypeScript のソースのまま使う（フロントの書き方 第 5 節）
  transpilePackages: ['@ec-site/shared'],
  typedRoutes: true,
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
