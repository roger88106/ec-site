import type { LOCALE } from '@ec-site/shared/lib/locale'
import type { messages } from '@/i18n/messages'

// 文言のキーとロケールの型（use-intl の型の拡張）。存在しないキーは型チェックで失敗する
declare module 'use-intl' {
  interface AppConfig {
    Locale: typeof LOCALE
    Messages: typeof messages
  }
}
