import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { withoutGitEnv } from './lib/git-env.mjs'
import { findChangedMigrations, findEmptyReason, findRemovedMigrations, isVersionedMigration, parseBlobList } from './migrations.mjs'

describe('適用済みのマイグレーションの検査', () => {
  test('バージョン付きのマイグレーション（V*.sql）だけを見る。繰り返しのもの（R__*.sql）は見ない', () => {
    assert.equal(isVersionedMigration('backend/ecsite/src/main/resources/db/migration/V202610140910__payment.sql'), true)
    assert.equal(isVersionedMigration('backend/ecsite/src/main/resources/db/seed/local/R__seed.sql'), false)
    assert.equal(isVersionedMigration('backend/ecsite/src/main/resources/db/migration/README.md'), false)
  })

  test('追加した時と今で中身が違うマイグレーションを見つける（コメントだけの変更も含む）', () => {
    const added = { 'V1__a.sql': 'create table a;\n', 'V2__b.sql': '-- 説明\ncreate table b;\n' }
    const now = { 'V1__a.sql': 'create table a;\n', 'V2__b.sql': '-- 説明を変えた\ncreate table b;\n' }
    assert.deepEqual(
      findChangedMigrations(['V1__a.sql', 'V2__b.sql'], (file) => added[file], (file) => now[file]),
      ['V2__b.sql'],
    )
  })

  test('まだ commit していないマイグレーション（追加した commit がない）は見ない', () => {
    assert.deepEqual(
      findChangedMigrations(['V1__a.sql', 'V2__new.sql'], (file) => (file === 'V1__a.sql' ? 'create table a;\n' : null), () => 'create table a;\n'),
      [],
    )
  })

  test('改行の違い（CRLF と LF）は変更とみなさない', () => {
    assert.deepEqual(
      findChangedMigrations(['V1__a.sql'], () => 'create table a;\n', () => 'create table a;\r\n'),
      [],
    )
  })

  test('追加した後に今はないマイグレーション（削除）を見つける', () => {
    const added = ['V1__a.sql', 'V2__b.sql', 'V3__c.sql']
    assert.deepEqual(findRemovedMigrations(added, ['V1__a.sql', 'V3__c.sql', 'V4__new.sql']), ['V2__b.sql'])
  })

  test('改名は、古い名前が今ないので見つかる', () => {
    assert.deepEqual(findRemovedMigrations(['V1__a.sql'], ['V1__renamed.sql']), ['V1__a.sql'])
  })

  test('削除も改名もなければ、何も見つけない', () => {
    assert.deepEqual(findRemovedMigrations(['V1__a.sql'], ['V1__a.sql']), [])
  })

})

describe('マイグレーションの検査の判定', () => {
  test('マイグレーションが 0 件のときは失敗の理由を返し、あるときは null', () => {
    assert.match(findEmptyReason([], 'db/migration/'), /1 件も見つからない.*db\/migration\//)
    assert.equal(findEmptyReason(['V1__a.sql'], 'db/migration/'), null)
  })

  test('git ls-tree と git ls-files -s の出力から、パスごとの blob を読む', () => {
    const tree = ['100644 blob aaa\tdb/V1__a.sql', '100644 blob bbb\tdb/V2__日本語.sql', ''].join('\0')
    assert.deepEqual([...parseBlobList(tree, 2)], [['db/V1__a.sql', 'aaa'], ['db/V2__日本語.sql', 'bbb']])
    const index = ['100644 ccc 0\tdb/V1__a.sql', ''].join('\0')
    assert.deepEqual([...parseBlobList(index, 1)], [['db/V1__a.sql', 'ccc']])
  })
})

describe('develop・main との分岐点の tree を基準にした判定（一時リポジトリ）', () => {
  const script = fileURLToPath(new URL('./migrations.mjs', import.meta.url))
  const dir = 'backend/ecsite/src/main/resources/db/migration'
  const env = withoutGitEnv()
  const repos = []
  after(() => repos.forEach((repo) => rmSync(repo, { recursive: true, force: true })))

  /** 試すリポジトリを作る。V1 は develop の分岐の前からあり、作業ブランチ（feature）に切り替えた状態で返す */
  function createRepo({ developRef = 'refs/heads/develop' } = {}) {
    const repo = mkdtempSync(path.join(tmpdir(), 'migrations-'))
    repos.push(repo)
    const git = (...args) => execFileSync('git', args, { cwd: repo, env, encoding: 'utf8' })
    git('init', '-q', '-b', 'main')
    git('config', 'user.email', 'test@example.com')
    git('config', 'user.name', 'test')
    git('config', 'commit.gpgsign', 'false')
    const write = (name, content) => {
      mkdirSync(path.join(repo, dir), { recursive: true })
      writeFileSync(path.join(repo, dir, name), content)
    }
    const commit = (message) => {
      git('add', '-A')
      git('commit', '-q', '-m', message)
    }
    write('V1__a.sql', 'create table a;\n')
    commit('V1')
    if (developRef) git('update-ref', developRef, 'HEAD')
    git('checkout', '-q', '-b', 'feature')
    const run = () => spawnSync('node', [script], { cwd: repo, env, encoding: 'utf8' })
    return { git, write, commit, run, remove: (name) => rmSync(path.join(repo, dir, name)) }
  }

  test('分岐の後に追加したマイグレーションは、改名しても、削除しても、中身を変えても通る', () => {
    const r = createRepo()
    r.write('V2__b.sql', 'create table b;\n')
    r.commit('V2')
    r.git('mv', `${dir}/V2__b.sql`, `${dir}/V3__b.sql`)
    r.write('V3__b.sql', 'create table b2;\n')
    r.commit('振り直し')
    const result = r.run()
    assert.equal(result.status, 0, result.stdout)
    r.remove('V3__b.sql')
    r.commit('削除')
    assert.equal(r.run().status, 0)
  })

  test('分岐の前からあるマイグレーションの改名は失敗にする', () => {
    const r = createRepo()
    r.git('mv', `${dir}/V1__a.sql`, `${dir}/V1__renamed.sql`)
    r.commit('改名')
    const result = r.run()
    assert.equal(result.status, 1)
    assert.match(result.stdout, /削除か改名された.*V1__a\.sql/)
  })

  test('分岐の前からあるマイグレーションの削除と、中身の変更は失敗にする', () => {
    const removed = createRepo()
    removed.remove('V1__a.sql')
    removed.write('V9__other.sql', 'create table o;\n')
    removed.commit('削除')
    assert.match(removed.run().stdout, /削除か改名された.*V1__a\.sql/)
    const changed = createRepo()
    changed.write('V1__a.sql', 'create table a2;\n')
    changed.commit('変更')
    const result = changed.run()
    assert.equal(result.status, 1)
    assert.match(result.stdout, /変わった.*V1__a\.sql/)
  })

  test('origin/develop だけがある（CI の checkout）ときも、その分岐点で判定する', () => {
    const r = createRepo({ developRef: 'refs/remotes/origin/develop' })
    r.write('V2__b.sql', 'create table b;\n')
    r.commit('V2')
    r.git('mv', `${dir}/V2__b.sql`, `${dir}/V3__b.sql`)
    r.commit('振り直し')
    assert.equal(r.run().status, 0)
  })

  test('main にだけあるマイグレーション（hotfix）は、develop に入っていなくても、改名と削除を失敗にする', () => {
    const r = createRepo()
    r.git('checkout', '-q', 'main')
    r.write('V5__hotfix.sql', 'create table h;\n')
    r.commit('hotfix')
    r.git('checkout', '-q', 'feature')
    r.git('merge', '-q', '--no-edit', 'main')
    r.git('mv', `${dir}/V5__hotfix.sql`, `${dir}/V6__hotfix.sql`)
    r.commit('振り直し')
    const result = r.run()
    assert.equal(result.status, 1)
    assert.match(result.stdout, /削除か改名された.*V5__hotfix\.sql/)
  })

  test('main だけがある（develop がない）ときは、main との分岐点で判定する', () => {
    const r = createRepo({ developRef: null })
    r.write('V2__b.sql', 'create table b;\n')
    r.commit('V2')
    r.git('mv', `${dir}/V2__b.sql`, `${dir}/V3__b.sql`)
    r.commit('振り直し')
    const result = r.run()
    assert.equal(result.status, 0, result.stdout)
    assert.doesNotMatch(result.stdout, /警告/)
  })

  test('develop も main もない（origin/ の方も含む）ときは、HEAD の 1 つ目の親を基準にし、警告を出す', () => {
    const r = createRepo({ developRef: null })
    r.git('branch', '-D', 'main')
    r.write('V2__b.sql', 'create table b;\n')
    r.commit('V2')
    r.git('mv', `${dir}/V2__b.sql`, `${dir}/V3__b.sql`)
    r.commit('振り直し')
    const result = r.run()
    assert.equal(result.status, 1)
    assert.match(result.stdout, /警告：.*main/)
    assert.match(result.stdout, /削除か改名された.*V2__b\.sql/)
  })

  test('自分のブランチで追加して改名したものを merge commit で develop にマージした後も、develop の上と次のブランチで通る', () => {
    const r = createRepo()
    r.write('V2__b.sql', 'create table b;\n')
    r.commit('V2')
    r.git('mv', `${dir}/V2__b.sql`, `${dir}/V3__b.sql`)
    r.commit('振り直し')
    r.git('checkout', '-q', 'develop')
    r.git('merge', '-q', '--no-ff', '--no-edit', 'feature')
    const onDevelop = r.run()
    assert.equal(onDevelop.status, 0, onDevelop.stdout)
    r.git('checkout', '-q', '-b', 'next')
    r.write('V4__c.sql', 'create table c;\n')
    r.commit('V4')
    const onNext = r.run()
    assert.equal(onNext.status, 0, onNext.stdout)
  })

  test('develop の上（分岐点が HEAD 自身）では、HEAD の 1 つ目の親にあるものの改名を失敗にする', () => {
    const r = createRepo()
    r.git('checkout', '-q', 'develop')
    r.git('mv', `${dir}/V1__a.sql`, `${dir}/V1__renamed.sql`)
    r.commit('改名')
    const result = r.run()
    assert.equal(result.status, 1)
    assert.match(result.stdout, /削除か改名された.*V1__a\.sql/)
  })

  test('ステージした（まだ commit していない）変更も、次の commit として判定する（commit の前の hook）', () => {
    const r = createRepo()
    r.write('V1__a.sql', 'create table a2;\n')
    r.git('add', '-A')
    const result = r.run()
    assert.equal(result.status, 1)
    assert.match(result.stdout, /変わった.*V1__a\.sql/)
  })

  test('初めの commit だけで develop も main もなければ、全てを新しいものとみなして通る（警告を出す）', () => {
    const r = createRepo({ developRef: null })
    r.git('branch', '-D', 'main')
    const result = r.run()
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /警告：/)
  })
})
