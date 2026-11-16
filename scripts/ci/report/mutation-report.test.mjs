import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { readMutations } from './mutation-report.mjs'

/** 実際の mutations.xml から切り出した 7 件（検出 4、生き残り 3：Order 2、Product 1）。57.14% */
const PIT = path.join(path.dirname(fileURLToPath(import.meta.url)), 'testdata', 'pit-mutations.xml')

describe('mutation score（PIT の XML）', () => {
  test('全変異の数と、検出した変異（detected が true）の数を数える', () => {
    const result = readMutations(readFileSync(PIT, 'utf8'))
    assert.equal(result.total, 7)
    assert.equal(result.detected, 4)
  })

  test('生き残った変異（SURVIVED と NO_COVERAGE）が多いクラスを、多い順に返す', () => {
    const { survivors } = readMutations(readFileSync(PIT, 'utf8'))
    assert.deepEqual(survivors, [
      { name: 'com.ecsite.module.order.domain.order.Order', survived: 2, total: 3 },
      { name: 'com.ecsite.module.catalog.domain.product.Product', survived: 1, total: 3 },
    ])
  })

  test('status ごとの数を、多い順（同じ数なら名前順）に返す', () => {
    const { statuses } = readMutations(readFileSync(PIT, 'utf8'))
    assert.deepEqual(statuses, [
      { status: 'KILLED', count: 3 },
      { status: 'NO_COVERAGE', count: 2 },
      { status: 'SURVIVED', count: 1 },
      { status: 'TIMED_OUT', count: 1 },
    ])
  })

  test('上位は 10 件までにする', () => {
    const mutation = (n) => `<mutation detected='false' status='SURVIVED'><mutatedClass>c.C${n}</mutatedClass></mutation>`
    const xml = `<mutations>${Array.from({ length: 12 }, (_, n) => mutation(n)).join('')}</mutations>`
    assert.equal(readMutations(xml).survivors.length, 10)
  })
})

describe('PIT の XML の厳密な読み取り', () => {
  const mutation = (attributes, cls = 'c.A') => `<mutation ${attributes}><mutatedClass>${cls}</mutatedClass></mutation>`

  test('閉じの </mutations> がない（途中で切れた）XML は、読まずに失敗にする', () => {
    const truncated = `<mutations partial="true">${mutation("detected='true' status='KILLED'")}`
    assert.throws(() => readMutations(truncated), /途中で切れ/)
  })

  test('属性の並びとクォートに依存しない', () => {
    const xml = `<mutations>${[
      mutation("status='KILLED' numberOfTestsRun='1' detected='true'"),
      mutation('status="SURVIVED" detected="false"'),
      mutation('numberOfTestsRun="0" detected="false" status="NO_COVERAGE"'),
    ].join('')}</mutations>`
    const result = readMutations(xml)
    assert.equal(result.total, 3)
    assert.equal(result.detected, 1)
    assert.deepEqual(result.survivors, [{ name: 'c.A', survived: 2, total: 3 }])
  })

  test('RUN_ERROR などの status は、全変異に数え、検出にも生き残りにも数えない', () => {
    const xml = `<mutations>${[
      mutation("detected='true' status='KILLED'"),
      mutation("detected='false' status='RUN_ERROR'"),
      mutation("detected='false' status='NON_VIABLE'"),
      mutation("detected='false' status='SURVIVED'"),
    ].join('')}</mutations>`
    const result = readMutations(xml)
    assert.equal(result.total, 4)
    assert.equal(result.detected, 1)
    assert.deepEqual(result.survivors, [{ name: 'c.A', survived: 1, total: 4 }])
  })

  test('<mutation> の開始タグの数と、読めた変異の数が違うと失敗にする', () => {
    const noDetected = `<mutations>${mutation("detected='true'")}${mutation("status='KILLED'")}</mutations>`
    assert.throws(() => readMutations(noDetected), /2 件あるが、読めたのは 1 件/)
    const noClass = `<mutations><mutation detected='true' status='KILLED'></mutation></mutations>`
    assert.throws(() => readMutations(noClass), /1 件あるが、読めたのは 0 件/)
    const noClose = `<mutations><mutation detected='true' status='KILLED'><mutatedClass>c.A</mutatedClass></mutations>`
    assert.throws(() => readMutations(noClose), /読めたのは 0 件/)
  })

  test('閉じタグが 1 つ欠けた変異は、次の変異の <mutatedClass> を数えず、失敗にする', () => {
    const missingClose = `<mutations><mutation detected='true' status='KILLED'><mutatedClass>c.A</mutatedClass>${mutation("detected='true' status='KILLED'", 'c.B')}</mutations>`
    assert.throws(() => readMutations(missingClose), /2 件あるが、読めたのは 1 件/)
  })
})
