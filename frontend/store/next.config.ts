import type { NextConfig } from 'next'

/**
 * Next.js の設定。<br />
 * API の結果のキャッシュは、fetch の revalidate で扱う（店舗画面の構成 第 4 節）。
 * Cache Components（'use cache'）は使わない。
 */
const nextConfig: NextConfig = {
  // 共有のパッケージは TypeScript のソースのまま使う（フロントの書き方 第 5 節）
  transpilePackages: ['@ec-site/shared'],
  turbopack: {
    rules: {
      '*.css': {
        loaders: ['@tailwindcss/turbopack'],
        as: '*.css',
      },
    },
  },
}

export default nextConfig
