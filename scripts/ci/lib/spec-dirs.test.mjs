import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { findMvpList, listSpecDirs } from './spec-dirs.mjs'

/** 直下とアーカイブに spec を置いた docs/specs の代わり */
const specsDir = mkdtempSync(path.join(tmpdir(), 'spec-dirs-'))
for (const dir of ['019-mvp-completion', 'archive/mvp/001-catalog', 'archive/mvp/018-docs', 'archive/mvp/notes', 'templates']) {
  mkdirSync(path.join(specsDir, dir), { recursive: true })
}
writeFileSync(path.join(specsDir, 'archive/mvp/mvp.md'), '')

after(() => rmSync(specsDir, { recursive: true, force: true }))

describe('spec のディレクトリ', () => {
  test('直下とアーカイブのまとまりの下の spec（NNN-名前）を、番号の順に返す', () => {
    assert.deepEqual(
      listSpecDirs(specsDir).map(({ name, rel }) => [name, rel]),
      [
        ['001-catalog', 'archive/mvp/001-catalog'],
        ['018-docs', 'archive/mvp/018-docs'],
        ['019-mvp-completion', '019-mvp-completion'],
      ],
    )
  })

  test('spec のディレクトリの絶対パスを返す', () => {
    assert.equal(listSpecDirs(specsDir)[0].dir, path.join(specsDir, 'archive/mvp/001-catalog'))
  })

  test('アーカイブがなければ直下だけを返し、MVP の完成までの一覧がどこにもなければ null を返す', () => {
    const bare = mkdtempSync(path.join(tmpdir(), 'spec-dirs-bare-'))
    try {
      mkdirSync(path.join(bare, '001-catalog'))
      assert.deepEqual(
        listSpecDirs(bare).map(({ rel }) => rel),
        ['001-catalog'],
      )
      assert.equal(findMvpList(bare), null)
    } finally {
      rmSync(bare, { recursive: true, force: true })
    }
  })

  test('アーカイブのまとまりが複数あれば、全てのまとまりの spec を番号の順に返す', () => {
    const multi = mkdtempSync(path.join(tmpdir(), 'spec-dirs-multi-'))
    try {
      for (const dir of ['archive/mvp/001-catalog', 'archive/v2/020-coupon', '021-wishlist']) {
        mkdirSync(path.join(multi, dir), { recursive: true })
      }
      assert.deepEqual(
        listSpecDirs(multi).map(({ rel }) => rel),
        ['archive/mvp/001-catalog', 'archive/v2/020-coupon', '021-wishlist'],
      )
    } finally {
      rmSync(multi, { recursive: true, force: true })
    }
  })

  test('MVP の完成までの一覧は、直下になければアーカイブから探す', () => {
    assert.equal(findMvpList(specsDir), path.join(specsDir, 'archive/mvp/mvp.md'))
    writeFileSync(path.join(specsDir, 'mvp.md'), '')
    assert.equal(findMvpList(specsDir), path.join(specsDir, 'mvp.md'))
  })
})
