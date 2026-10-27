import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { describeUnreadableImages, findImageProblems, findUnpinned, parseComposeImages, readSourceImages } from './image-versions.mjs'

/** compose の image: の一覧だけを読む（読めない行は見ない） */
const readComposeImages = (text) => parseComposeImages(text).images

const compose = [
  'services:',
  '  postgres:',
  '    image: postgres:18-alpine',
  '  keycloak:',
  '    image: "quay.io/keycloak/keycloak:26.8.1"',
  '  keycloak-e2e:',
  '    image: quay.io/keycloak/keycloak:26.8.1  # 同じ',
  '  # image: ignored:1.0.0',
].join('\n')

describe('compose のイメージの読み取り', () => {
  test('image: の名前とバージョンを読み、同じ名前は 1 つにまとめる', () => {
    const images = readComposeImages(compose)
    assert.deepEqual(
      [...images].map(([name, versions]) => [name, [...versions]]),
      [
        ['postgres', ['18-alpine']],
        ['quay.io/keycloak/keycloak', ['26.8.1']],
      ],
    )
  })

  test('変数の差し込みは、既定値（:- の後）を読む', () => {
    const images = readComposeImages('    image: ${PG_IMAGE:-postgres:18}\n    image: "${KC:-quay.io/keycloak/keycloak:26.8.1}"')
    assert.deepEqual([...images.get('postgres')], ['18'])
    assert.deepEqual([...images.get('quay.io/keycloak/keycloak')], ['26.8.1'])
  })

  test('既定値のない差し込みは、読めない行として行番号と内容を集める（読み飛ばさない）', () => {
    const text = ['services:', '    image: ${PG_IMAGE}', '    image: ${PG_IMAGE:?必須}', '    image: postgres:${PG_VERSION}', '    image: a/b:1.0.0'].join('\n')
    const { images, unreadable } = parseComposeImages(text)
    assert.deepEqual(
      unreadable.map(({ line, text: content }) => [line, content.trim()]),
      [
        [2, 'image: ${PG_IMAGE}'],
        [3, 'image: ${PG_IMAGE:?必須}'],
        [4, 'image: postgres:${PG_VERSION}'],
      ],
    )
    assert.match(unreadable[0].reason, /変数/)
    assert.deepEqual([...images.keys()], ['a/b'])
  })

  test('読めない行は、ファイル:行 内容（理由）の形の文にして失敗にする', () => {
    const { unreadable } = parseComposeImages('    image: ${PG_IMAGE}')
    assert.deepEqual(describeUnreadableImages('docker/compose.yaml', unreadable), [
      `docker/compose.yaml:1 image: \${PG_IMAGE}（${unreadable[0].reason}）`,
    ])
  })

  test('同じ名前に別のバージョンがあれば、両方を残す', () => {
    const images = readComposeImages(['    image: a/b:1.0.0', '    image: a/b:1.0.1'].join('\n'))
    assert.deepEqual([...images.get('a/b')], ['1.0.0', '1.0.1'])
  })
})

describe('ソースのイメージの読み取り', () => {
  test('compose にある名前のバージョンを、書いた行とともに読む', () => {
    const images = readComposeImages(compose)
    const source = 'x(DockerImageName.parse("postgres:18-alpine"));\nString IMAGE = "quay.io/keycloak/keycloak:26.8.0";'
    assert.deepEqual(readSourceImages(source, images).known, [
      { line: 1, name: 'postgres', version: '18-alpine' },
      { line: 2, name: 'quay.io/keycloak/keycloak', version: '26.8.0' },
    ])
  })

  test('compose にない名前は DockerImageName.parse の文字列だけを別に集める', () => {
    const source = 'DockerImageName.parse("other/img:2.0.0");\nString s = "not/image:1";'
    const result = readSourceImages(source, new Map())
    assert.deepEqual(result.known, [])
    assert.deepEqual(result.unknown, [{ line: 1, name: 'other/img', version: '2.0.0' }])
  })

  test('名前の前後が続く別の名前（部分一致）は読まない', () => {
    const images = readComposeImages('    image: postgres:18-alpine')
    const source = '"my-postgres:1.0.0" "postgres-extra:2.0.0"'
    assert.deepEqual(readSourceImages(source, images).known, [])
  })
})

describe('バージョンの食い違い', () => {
  const images = readComposeImages(compose)

  test('compose とバージョンが違うものを見つける', () => {
    const files = [{ path: 'A.java', text: 'String IMAGE = "quay.io/keycloak/keycloak:26.8.0";' }]
    assert.deepEqual(findImageProblems(images, files).mismatches, [
      'A.java:1 quay.io/keycloak/keycloak:26.8.0（compose は 26.8.1）',
    ])
  })

  test('バージョンが一致すれば通る。compose にないイメージは報告だけ', () => {
    const files = [{ path: 'A.java', text: 'DockerImageName.parse("postgres:18-alpine"); DockerImageName.parse("x/y:1.2.3");' }]
    const result = findImageProblems(images, files)
    assert.deepEqual(result.mismatches, [])
    assert.deepEqual(result.unknown, ['A.java:1 x/y:1.2.3'])
  })
})

describe('バージョンの固定', () => {
  test('latest、バージョンのないもの、patch のないバージョンを見つける', () => {
    const images = readComposeImages(
      ['    image: a/b:latest', '    image: a/c', '    image: a/d:3', '    image: a/e:3.6', '    image: a/f:v1.2.3'].join('\n'),
    )
    assert.deepEqual(findUnpinned(images), ['a/b:latest', 'a/c（バージョンがない）', 'a/d:3', 'a/e:3.6'])
  })

  test('公式の postgres は major だけのバージョン（18-alpine）を許す', () => {
    const images = readComposeImages('    image: postgres:18-alpine')
    assert.deepEqual(findUnpinned(images), [])
  })

  test('postgres 以外の major だけのバージョンは許さない', () => {
    const images = readComposeImages('    image: mysql:8-alpine')
    assert.deepEqual(findUnpinned(images), ['mysql:8-alpine'])
  })

  test('patch のないバージョンを使う seaweedfs は major.minor のバージョンを許す。他のイメージは許さない', () => {
    const images = readComposeImages(['    image: chrislusf/seaweedfs:4.48', '    image: a/g:4.48'].join('\n'))
    assert.deepEqual(findUnpinned(images), ['a/g:4.48'])
  })
})
