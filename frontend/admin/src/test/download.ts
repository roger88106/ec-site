import { vi } from 'vitest'

/** 記録したダウンロード 1 件 */
export interface Download {
  /** 保存名（リンクの `download`） */
  filename: string
  /** 本文。BOM を残したまま文字にしたもの */
  text: string
}

/**
 * ダウンロードの記録。{@link recordDownloads} が返す。
 */
export interface DownloadRecorder {
  /** これまでのダウンロードを、起きた順に返す */
  downloads(): Promise<Download[]>
}

/**
 * ファイルのダウンロード（Blob の URL を作り、リンクをクリックする操作）を記録する。<br />
 * ブラウザーの保存は起こさない。差し替えは `vi.restoreAllMocks()`（setup.ts がテストごとに実行する）で戻る。
 * 例：`expect(await recorder.downloads()).toEqual([{ filename: 'a.csv', text: '﻿a,b\r\n' }])`
 *
 * @returns 画面の操作の前に呼び、操作の後に `downloads()` で確認する
 */
export function recordDownloads(): DownloadRecorder {
  const blobs: Blob[] = []
  const filenames: string[] = []
  vi.spyOn(URL, 'createObjectURL').mockImplementation((object) => {
    if (!(object instanceof Blob)) {
      throw new Error('Blob 以外の URL は作らない')
    }
    blobs.push(object)
    return `blob:download-${blobs.length}`
  })
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    filenames.push(this.download)
  })
  return {
    async downloads() {
      const decoder = new TextDecoder('utf-8', { ignoreBOM: true })
      return Promise.all(
        blobs.map(async (blob, index) => ({
          filename: filenames[index],
          text: decoder.decode(await blob.arrayBuffer()),
        })),
      )
    },
  }
}
