import { LOCALE, TIME_ZONE } from '@ec-site/shared/lib/locale'
import type { ReactNode } from 'react'
import { IntlProvider } from 'use-intl'
import { messages } from '@/i18n/messages'

/** 文言の提供者（use-intl）。画面全体と、結合テストの描画で包む */
export function I18nProvider({ children }: { children: ReactNode }) {
  return (
    <IntlProvider locale={LOCALE} messages={messages} timeZone={TIME_ZONE}>
      {children}
    </IntlProvider>
  )
}
