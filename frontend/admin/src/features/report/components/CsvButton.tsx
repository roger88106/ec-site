import { Button } from '@ec-site/shared/ui/button'
import { DownloadIcon } from 'lucide-react'
import { useTranslations } from 'use-intl'
import { type CsvValue, downloadCsv, toCsv } from '@/features/report/csv'

/** 表示している表を CSV で取り出すボタン */
export function CsvButton({
  filename,
  rows,
}: {
  filename: string
  /** 表の行（見出しの行を含む） */
  rows: readonly (readonly CsvValue[])[]
}) {
  const t = useTranslations('report')
  return (
    <Button onClick={() => downloadCsv(filename, toCsv(rows))} size="sm" variant="outline">
      <DownloadIcon />
      {t('csv') /* CSV で取り出す */}
    </Button>
  )
}
