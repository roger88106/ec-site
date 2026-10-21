import { createTranslator } from 'use-intl/core'
import messages from '../messages/ja.json'
import { LOCALE, TIME_ZONE } from './locale'

/**
 * 共有の文言（common）だけを引く文言の関数。共有パッケージの整形の関数が使う。<br />
 * 各画面のコードは、各画面の translate（共有の文言と画面の文言を合わせたもの）を使う。
 */
export const translate = createTranslator({ locale: LOCALE, timeZone: TIME_ZONE, messages })
