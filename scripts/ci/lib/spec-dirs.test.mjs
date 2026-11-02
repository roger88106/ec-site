import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { isSpecDirName, listSpecDirs } from './spec-dirs.mjs'

/** 直下とアーカイブに spec を置いた docs/08-spec の代わり */
const specsDir = mkdtempSync(path.join(tmpdir(), 'spec-dirs-'))
for (const dir of ['019-mvp-completion', 'archive/mvp/001-catalog', 'archive/mvp/018-docs', 'archive/mvp/notes', 'templates']) {
  mkdirSync(path.join(specsDir, dir), { recursive: true })
}

after(() => rmSync(specsDir, { recursive: true, force: true }))

describe('spec のディレクトリ', () => {
  test('直下とアーカイブのまとまりの下の spec（<番号>-名前）を、番号の順に返す', () => {
    assert.deepEqual(
      listSpecDirs(specsDir).map(({ name, rel }) => [name, rel]),
      [
        ['001-catalog', 'archive/mvp/001-catalog'],
        ['018-docs', 'archive/mvp/018-docs'],
        ['019-mvp-completion', '019-mvp-completion'],
      ],
    )
  })

  test('0 埋めなしの番号（1→10 の spec）と 3 桁の 0 埋めの番号（MVP）の両方を、番号の順に返す', () => {
    const mixed = mkdtempSync(path.join(tmpdir(), 'spec-dirs-mixed-'))
    try {
      for (const dir of ['7-dev-process', '12-next', 'archive/mvp/011-dev-foundation', 'archive/mvp/019-mvp-completion', 'x-notes']) {
        mkdirSync(path.join(mixed, dir), { recursive: true })
      }
      assert.deepEqual(
        listSpecDirs(mixed).map(({ name }) => name),
        ['7-dev-process', '011-dev-foundation', '12-next', '019-mvp-completion'],
      )
    } finally {
      rmSync(mixed, { recursive: true, force: true })
    }
  })

  test('spec のディレクトリの名前は <番号>-名前 の形（0 埋めの有無を問わない）', () => {
    for (const name of ['321-design-templates', '7-dev-process', '011-dev-foundation']) assert.equal(isSpecDirName(name), true, name)
    for (const name of ['design-templates', '321_design-templates', 'archive', 'templates', '321']) assert.equal(isSpecDirName(name), false, name)
  })

  test('spec のディレクトリの絶対パスを返す', () => {
    assert.equal(listSpecDirs(specsDir)[0].dir, path.join(specsDir, 'archive/mvp/001-catalog'))
  })

  test('アーカイブがなければ直下だけを返す', () => {
    const bare = mkdtempSync(path.join(tmpdir(), 'spec-dirs-bare-'))
    try {
      mkdirSync(path.join(bare, '001-catalog'))
      assert.deepEqual(
        listSpecDirs(bare).map(({ rel }) => rel),
        ['001-catalog'],
      )
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
})
