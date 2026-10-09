import type { NextConfig } from 'next'

/**
 * Next.js の設定。<br />
 * API の結果のキャッシュは、fetch の revalidate で扱う（店舗画面の構成 第 4 節）。
 * Cache Components（'use cache'）は使わない。
 */
const nextConfig: NextConfig = {
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
