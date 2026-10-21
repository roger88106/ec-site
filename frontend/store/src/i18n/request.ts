import { LOCALE, TIME_ZONE } from '@ec-site/shared/lib/locale'
import { getRequestConfig } from 'next-intl/server'
import { messages } from '@/i18n/messages'

/**
 * リクエストごとの文言の設定（next-intl）。ロケールは 1 つだけなので、URL やヘッダーからは決めない。
 * next.config.ts の next-intl の plugin が、このファイルを読む。
 */
export default getRequestConfig(async () => ({ locale: LOCALE, timeZone: TIME_ZONE, messages }))
