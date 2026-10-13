import { LOCALE, TIME_ZONE } from '@ec-site/shared/lib/locale'
import { createTranslator } from 'use-intl'
import { messages } from '@/i18n/messages'

/**
 * 部品の外（整形の関数、エラーの変換、表示名）で使う文言の関数。キーは文言のファイルの最上位から書く。<br />
 * 部品の中では useTranslations を使う。
 */
export const translate = createTranslator({ locale: LOCALE, timeZone: TIME_ZONE, messages })
