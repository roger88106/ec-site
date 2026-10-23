import { describe, expect, test, vi } from 'vitest'
import { downloadCsv } from '@/features/report/csv'
import { recordDownloads } from '@/test/download'

describe('ダウンロードの記録', () => {
  test('保存名と本文を、ダウンロードした順に対にして返す', async () => {
    const recorder = recordDownloads()

    downloadCsv('first.csv', 'a,b\r\n')
    downloadCsv('second.csv', 'c,d\r\n')

    expect(await recorder.downloads()).toEqual([
      { filename: 'first.csv', text: 'a,b\r\n' },
      { filename: 'second.csv', text: 'c,d\r\n' },
    ])
  })

  test('本文の先頭の BOM を残す', async () => {
    const recorder = recordDownloads()

    downloadCsv('bom.csv', '﻿a,b\r\n')

    const [download] = await recorder.downloads()
    expect(download.text).toBe('﻿a,b\r\n')
  })

  test('ダウンロードがなければ、空の一覧を返す', async () => {
    expect(await recordDownloads().downloads()).toEqual([])
  })

  describe('差し替えの復元', () => {
    test('記録を始めると、Blob の URL の作成とリンクのクリックを差し替える', () => {
      recordDownloads()

      expect(vi.isMockFunction(URL.createObjectURL)).toBe(true)
      expect(vi.isMockFunction(HTMLAnchorElement.prototype.click)).toBe(true)
    })

    test('前のテストの差し替えは、テストの後に元の実装へ戻っている', () => {
      expect(vi.isMockFunction(URL.createObjectURL)).toBe(false)
      expect(vi.isMockFunction(HTMLAnchorElement.prototype.click)).toBe(false)
    })
  })
})
