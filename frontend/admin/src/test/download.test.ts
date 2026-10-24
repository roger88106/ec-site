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

  test('download のないリンクのクリックは記録せず、本物のクリックの処理に渡す', async () => {
    const recorder = recordDownloads()
    const link = document.createElement('a')
    const clicked = vi.fn()
    link.addEventListener('click', clicked)

    link.click()

    expect(clicked).toHaveBeenCalledTimes(1)
    expect(await recorder.downloads()).toEqual([])
  })

  test('download のないリンクのクリックがあっても、ダウンロードの保存名は対にずれない', async () => {
    const recorder = recordDownloads()
    document.createElement('a').click()

    downloadCsv('after.csv', 'a,b\r\n')

    expect(await recorder.downloads()).toEqual([{ filename: 'after.csv', text: 'a,b\r\n' }])
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
