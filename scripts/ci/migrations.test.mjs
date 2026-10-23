import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { findChangedMigrations, findEmptyReason, gitLogArgs, findRemovedMigrations, isVersionedMigration, parseAddedLog } from './migrations.mjs'

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

  test('git log の出力から、ファイルごとの最初に追加した commit を読む（新しい順の出力で、最後に出たものが最初の追加）', () => {
    const c1 = 'a'.repeat(40)
    const c2 = 'b'.repeat(40)
    const log = [c2, 'V2__b.sql', 'V1__a.sql', '', c1, 'V1__a.sql', ''].join('\n')
    const added = parseAddedLog(log)
    assert.equal(added.get('V1__a.sql'), c1)
    assert.equal(added.get('V2__b.sql'), c2)
    assert.equal(added.size, 2)
  })
})

describe('マイグレーションの検査の判定', () => {
  test('マイグレーションが 0 件のときは失敗の理由を返し、あるときは null', () => {
    assert.match(findEmptyReason([], 'db/migration/'), /1 件も見つからない.*db\/migration\//)
    assert.equal(findEmptyReason(['V1__a.sql'], 'db/migration/'), null)
  })

  test('履歴を読む git log は改名を追加として扱う（--no-renames）', () => {
    const args = gitLogArgs('db/migration/')
    assert.ok(args.includes('--no-renames'))
    assert.ok(args.includes('--diff-filter=A'))
    assert.deepEqual(args.slice(-2), ['--', 'db/migration/'])
  })

  test('履歴を読む起点を渡せる（なければ HEAD）', () => {
    assert.ok(gitLogArgs('db/migration/', 'abc123').includes('abc123'))
    assert.ok(gitLogArgs('db/migration/').includes('HEAD'))
  })
})

describe('develop との分岐点までの履歴での判定（一時リポジトリ）', () => {
  const script = fileURLToPath(new URL('./migrations.mjs', import.meta.url))
  const dir = 'backend/ecsite/src/main/resources/db/migration'
  // commit の hook の中では GIT_DIR などが設定され、試すリポジトリでなく commit 中のリポジトリを指すので、外す
  const env = Object.fromEntries(Object.entries(process.env).filter(([name]) => !name.startsWith('GIT_')))
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

  test('develop も main もない（origin/ の方も含む）ときは、全ての履歴で判定し（弱めない）、警告を出す', () => {
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
})
