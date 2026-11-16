import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { describeUnreadableImages, findImageProblems, findUnpinned, parseComposeImages, readSourceImages } from './image-versions.mjs'

/** compose の image の一覧だけを読む（読めない項目は見ない） */
const readComposeImages = (text) => parseComposeImages(text).images

/** イメージを、s0・s1… の名前のサービスに 1 つずつ書いた compose にする */
const services = (images) => ['services:', ...images.flatMap((image, index) => [`  s${index}:`, `    image: ${image}`])].join('\n')

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
    const images = readComposeImages(services(['${PG_IMAGE:-postgres:18}', '"${KC:-quay.io/keycloak/keycloak:26.8.1}"']))
    assert.deepEqual([...images.get('postgres')], ['18'])
    assert.deepEqual([...images.get('quay.io/keycloak/keycloak')], ['26.8.1'])
  })

  test('既定値のない差し込みは、読めない項目としてサービスとイメージを集める（読み飛ばさない）', () => {
    const { images, unreadable } = parseComposeImages(services(['${PG_IMAGE}', '${PG_IMAGE:?必須}', 'postgres:${PG_VERSION}', 'a/b:1.0.0']))
    assert.deepEqual(unreadable, [
      { service: 's0', image: '${PG_IMAGE}' },
      { service: 's1', image: '${PG_IMAGE:?必須}' },
      { service: 's2', image: 'postgres:${PG_VERSION}' },
    ])
    assert.deepEqual([...images.keys()], ['a/b'])
  })

  test('読めない項目は、ファイル サービス：イメージ（理由）の形の文にして失敗にする', () => {
    const { unreadable } = parseComposeImages(services(['${PG_IMAGE}']))
    assert.deepEqual(describeUnreadableImages('docker/compose.yaml', unreadable), [
      'docker/compose.yaml s0：${PG_IMAGE}（変数の差し込みを読めない。既定値 ${NAME:-名前:バージョン} の形だけを扱う）',
    ])
  })

  test('同じ名前に別のバージョンがあれば、両方を残す', () => {
    const images = readComposeImages(services(['a/b:1.0.0', 'a/b:1.0.1']))
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
    const images = readComposeImages(services(['postgres:18-alpine']))
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
    const images = readComposeImages(services(['a/b:latest', 'a/c', 'a/d:3', 'a/e:3.6', 'a/f:v1.2.3']))
    assert.deepEqual(findUnpinned(images), ['a/b:latest', 'a/c（バージョンがない）', 'a/d:3', 'a/e:3.6'])
  })

  test('公式の postgres は major だけのバージョン（18-alpine）を許す', () => {
    const images = readComposeImages(services(['postgres:18-alpine']))
    assert.deepEqual(findUnpinned(images), [])
  })

  test('postgres 以外の major だけのバージョンは許さない', () => {
    const images = readComposeImages(services(['mysql:8-alpine']))
    assert.deepEqual(findUnpinned(images), ['mysql:8-alpine'])
  })

  test('patch のないバージョンを使う seaweedfs は major.minor のバージョンを許す。他のイメージは許さない', () => {
    const images = readComposeImages(services(['chrislusf/seaweedfs:4.48', 'a/g:4.48']))
    assert.deepEqual(findUnpinned(images), ['a/g:4.48'])
  })
})
