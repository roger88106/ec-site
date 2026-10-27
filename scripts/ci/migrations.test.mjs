import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { checkExclusions, describeEmptyReason, describeStaleExclusion } from './lib/exclusions.mjs'
import { withoutGitEnv } from './lib/git-env.mjs'
import { emptyListReason } from './lib/git-files.mjs'
import {
  findChangedMigrations,
  findIncompatibleStatements,
  findRemovedMigrations,
  INCOMPATIBLE_KINDS,
  isCheckedForCompatibility,
  isVersionedMigration,
  LEGACY_MIGRATIONS,
  migrationsEmptyReason,
  parseBlobList,
  scanMigrations,
} from './migrations.mjs'

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
  test('マイグレーションが 0 件のときの理由は、他の検査と同じ共通の関数の文で、置き場所を添える', () => {
    assert.equal(migrationsEmptyReason([], 'db/migration/'), emptyListReason([], 'マイグレーション（db/migration/）'))
    assert.match(migrationsEmptyReason([], 'db/migration/'), /db\/migration\/.*1 件も見つからない/)
    assert.equal(migrationsEmptyReason(['V1__a.sql'], 'db/migration/'), null)
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
    // 今ある 38 のマイグレーション（対象外の一覧にあるもの）。実在しないと一覧の誤りで失敗になる
    for (const name of LEGACY_MIGRATIONS) write(name, 'select 1;\n')
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

describe('互換性のない変更の検出', () => {
  const kinds = (sql) => findIncompatibleStatements(sql).found.map((hit) => hit.kind)

  test('表の削除（DROP TABLE、IF EXISTS、DROP SCHEMA）を見つける', () => {
    assert.deepEqual(kinds('DROP TABLE catalog.product;'), [INCOMPATIBLE_KINDS.dropTable])
    assert.deepEqual(kinds('drop table if exists a, b cascade;'), [INCOMPATIBLE_KINDS.dropTable])
    assert.deepEqual(kinds('DROP SCHEMA old CASCADE;'), [INCOMPATIBLE_KINDS.dropTable])
  })

  test('列の削除（COLUMN の有無、IF EXISTS、複数の変更の中）を見つけ、制約の削除は見つけない', () => {
    assert.deepEqual(kinds('ALTER TABLE a DROP COLUMN b;'), [INCOMPATIBLE_KINDS.dropColumn])
    assert.deepEqual(kinds('ALTER TABLE a DROP b;'), [INCOMPATIBLE_KINDS.dropColumn])
    assert.deepEqual(kinds('alter table if exists only a\n  drop column if exists b;'), [INCOMPATIBLE_KINDS.dropColumn])
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN c int, DROP COLUMN b;'), [INCOMPATIBLE_KINDS.dropColumn])
    assert.deepEqual(kinds('ALTER TABLE a DROP CONSTRAINT a_fk;'), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN b DROP NOT NULL;'), [])
  })

  test('表の改名と列の改名を見つけ、制約の改名は見つけない', () => {
    assert.deepEqual(kinds('ALTER TABLE a RENAME TO b;'), [INCOMPATIBLE_KINDS.renameTable])
    assert.deepEqual(kinds('ALTER SCHEMA a RENAME TO b;'), [INCOMPATIBLE_KINDS.renameTable])
    assert.deepEqual(kinds('ALTER TABLE a RENAME COLUMN b TO c;'), [INCOMPATIBLE_KINDS.renameColumn])
    assert.deepEqual(kinds('ALTER TABLE a RENAME b TO c;'), [INCOMPATIBLE_KINDS.renameColumn])
    assert.deepEqual(kinds('ALTER TABLE a RENAME CONSTRAINT x TO y;'), [])
  })

  test('列の型の変更（TYPE、SET DATA TYPE、COLUMN の有無）を見つける', () => {
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN b TYPE bigint;'), [INCOMPATIBLE_KINDS.changeType])
    assert.deepEqual(kinds('ALTER TABLE a ALTER b SET DATA TYPE text USING b::text;'), [INCOMPATIBLE_KINDS.changeType])
  })

  test('既存の列を NOT NULL にする変更を見つける', () => {
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN b SET NOT NULL;'), [INCOMPATIBLE_KINDS.setNotNull])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN b SET DEFAULT 1;'), [])
  })

  test('既存の列の既定値の削除（DROP DEFAULT、SET DEFAULT NULL）を見つけ、既定値の変更は見つけない', () => {
    const kind = [INCOMPATIBLE_KINDS.dropDefault]
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c DROP DEFAULT;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER c DROP DEFAULT;'), kind)
    assert.deepEqual(kinds('alter table a\n  alter column "c"\n  drop\n  default;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT NULL;'), kind)
    assert.deepEqual(kinds('alter table a alter c set default null;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT NULL::text;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT (NULL);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(NULL AS int);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT (CAST(NULL AS int));'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST((NULL) AS int);'), kind)
    assert.deepEqual(kinds('alter table a alter c set default cast(null as varchar(10));'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST (\n  NULL\n  AS\n  int\n);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(NULL AS int)::text;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(NULL::int AS int);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(CAST(NULL AS int) AS text);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT NULL COLLATE "C";'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT (NULL::text[]);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(NULL AS numeric(10, 2))::numeric(10, 2);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(0 AS int);'), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(0::int AS int);'), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(NULLIF(1, 1) AS int)::text;'), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT CAST(NULLIF(1, 1) AS int);'), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN b DROP NOT NULL, ALTER COLUMN c DROP DEFAULT;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN b SET DEFAULT 0, ALTER COLUMN c DROP DEFAULT, DROP COLUMN d;'), [INCOMPATIBLE_KINDS.dropDefault, INCOMPATIBLE_KINDS.dropColumn])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT 0;'), [])
    assert.deepEqual(kinds("ALTER TABLE a ALTER COLUMN c SET DEFAULT 'x';"), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT now();'), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET DEFAULT NULLIF(1, 1);'), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c SET NOT NULL;'), [INCOMPATIBLE_KINDS.setNotNull])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c DROP NOT NULL;'), [])
    assert.deepEqual(kinds('ALTER TABLE a ALTER COLUMN c DROP IDENTITY;'), [])
  })

  test('既定値のない NOT NULL の列の追加を見つけ、DEFAULT 付き・NULL を許す列の追加は通す', () => {
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN b int NOT NULL;'), [INCOMPATIBLE_KINDS.addNotNullWithoutDefault])
    assert.deepEqual(kinds('ALTER TABLE a ADD b int NOT\n NULL, ADD COLUMN c text;'), [INCOMPATIBLE_KINDS.addNotNullWithoutDefault])
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN b int NOT NULL DEFAULT 0;'), [])
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN b int DEFAULT 0 NOT NULL;'), [])
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN b numeric(10, 2) DEFAULT 0 NOT NULL, ADD COLUMN c text;'), [])
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN b int;'), [])
  })

  test('DEFAULT NULL は既定値なしとみなし、主キーの列の追加は NOT NULL と同じに扱う（IDENTITY は既定値があるので除く）', () => {
    const kind = [INCOMPATIBLE_KINDS.addNotNullWithoutDefault]
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN c int DEFAULT NULL NOT NULL;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN c int NOT NULL DEFAULT NULL;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN c int PRIMARY KEY;'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN c int DEFAULT 1 NOT NULL;'), [])
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN c bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY;'), [])
    assert.deepEqual(kinds('ALTER TABLE a ADD COLUMN c bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY;'), [])
  })

  test('主キーの制約の追加（ADD [CONSTRAINT 名前] PRIMARY KEY）は、既存の列を NOT NULL にする変更として数える', () => {
    const kind = [INCOMPATIBLE_KINDS.setNotNull]
    assert.deepEqual(kinds('ALTER TABLE a ADD PRIMARY KEY (c);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ADD CONSTRAINT a_pk PRIMARY KEY (c, d);'), kind)
    assert.deepEqual(kinds('ALTER TABLE a ADD CONSTRAINT a_uq UNIQUE (c);'), [])
  })

  test('表のスキーマの移動（SET SCHEMA）は、表の改名と同じに数える', () => {
    assert.deepEqual(kinds('ALTER TABLE a SET SCHEMA other;'), [INCOMPATIBLE_KINDS.renameTable])
  })

  test('ドル引用の中の \' と " は文字列の始まりとして読まず、後ろの変更を見落とさない', () => {
    const sql = "COMMENT ON TABLE t IS $$it's$$; ALTER TABLE t DROP COLUMN x; COMMENT ON TABLE u IS $$don't$$;"
    assert.deepEqual(kinds(sql), [INCOMPATIBLE_KINDS.dropColumn])
    assert.deepEqual(findIncompatibleStatements(sql).unreadable, [])
    assert.deepEqual(kinds("COMMENT ON TABLE t IS $tag$say \"hi$tag$; ALTER TABLE t DROP COLUMN x;"), [INCOMPATIBLE_KINDS.dropColumn])
  })

  test('ドル引用の中の EXECUTE の動的な SQL の文字列も、字面で読んで見つける', () => {
    assert.deepEqual(kinds("DO $$ BEGIN EXECUTE 'ALTER TABLE t DROP COLUMN x'; END $$;"), [INCOMPATIBLE_KINDS.dropColumn])
    assert.deepEqual(kinds("DO $do$ BEGIN EXECUTE 'DROP TABLE t'; END $do$;"), [INCOMPATIBLE_KINDS.dropTable])
  })

  test('ドル引用の中の -- と /* */ も、中を読む（誤検出の側に倒す）', () => {
    assert.deepEqual(kinds('DO $$ BEGIN\n -- DROP TABLE t;\nEND $$;'), [INCOMPATIBLE_KINDS.dropTable])
    assert.deepEqual(kinds('DO $$ /* DROP TABLE t; */ BEGIN END $$;'), [INCOMPATIBLE_KINDS.dropTable])
  })

  test('ドル引用の外のコメントと文字列は、今までどおり読まない。$1 や識別子の中の $ はドル引用にしない', () => {
    assert.deepEqual(kinds("-- $$\nSELECT 'DROP TABLE t', $1;\n"), [])
    assert.deepEqual(kinds("SELECT a$b$c, 'x'; ALTER TABLE a DROP COLUMN b;"), [INCOMPATIBLE_KINDS.dropColumn])
  })

  test('閉じていないドル引用は、解釈できない行として返す', () => {
    const reasons = (sql) => findIncompatibleStatements(sql).unreadable.map((item) => [item.line, item.reason])
    assert.deepEqual(reasons('SELECT 1;\nDO $$ BEGIN END;\n'), [[2, '閉じていないドル引用']])
    assert.deepEqual(reasons('DO $a$ BEGIN END $b$;\n'), [[1, '閉じていないドル引用']])
  })

  test('互換性のある変更（表・列・索引・制約の追加、COMMENT）は見つけない', () => {
    const sql = `CREATE TABLE a (id bigint NOT NULL PRIMARY KEY, name text NOT NULL);
CREATE INDEX a_name_idx ON a (name);
ALTER TABLE a ADD CONSTRAINT a_name_check CHECK (name IS NOT NULL);
ALTER TABLE a ADD CONSTRAINT a_fk FOREIGN KEY (id) REFERENCES b (id);
COMMENT ON TABLE a IS 'drop table';
`
    assert.deepEqual(kinds(sql), [])
  })

  test('大文字小文字と改行をまたぐ書き方を見つけ、行は文の始まりの行を返す', () => {
    const hit = findIncompatibleStatements('CREATE TABLE a (id int);\n\nAlter\n  Table\n  a\n  Drop\n  Column b;\n').found
    assert.deepEqual(hit, [{ line: 3, kind: INCOMPATIBLE_KINDS.dropColumn, text: 'Alter' }])
  })

  test('コメント（--、/* */、入れ子）と文字列の中は数えない', () => {
    const sql = `-- ALTER TABLE a DROP COLUMN b;
/* DROP TABLE a; /* ALTER TABLE a RENAME TO b; */ ALTER TABLE a ALTER COLUMN b SET NOT NULL; */
COMMENT ON COLUMN a.b IS 'ALTER TABLE a DROP COLUMN b; -- 説明';
INSERT INTO t VALUES ('it''s DROP TABLE a', E'\\' DROP TABLE a');
CREATE TABLE "drop table x" (id int);
`
    assert.deepEqual(kinds(sql), [])
  })

  test('コメントの後ろにある変更は見つける', () => {
    assert.deepEqual(kinds('-- 説明\nDROP TABLE a; -- 後ろのコメント\n'), [INCOMPATIBLE_KINDS.dropTable])
  })

  test('DO ブロック（ドル引用）の中の変更も見つける', () => {
    assert.deepEqual(kinds('DO $$ BEGIN\n  ALTER TABLE a DROP COLUMN b;\nEND $$;'), [INCOMPATIBLE_KINDS.dropColumn])
  })

  test('閉じていない文字列・コメント・引用符付きの識別子は、読めないものとして返す', () => {
    const reasons = (sql) => findIncompatibleStatements(sql).unreadable.map((item) => [item.line, item.reason])
    assert.deepEqual(reasons("SELECT 1;\nSELECT 'abc;\n"), [[2, '閉じていない文字列']])
    assert.deepEqual(reasons('/* 閉じない\n'), [[1, '閉じていないブロックコメント']])
    assert.deepEqual(reasons('SELECT "abc;\n'), [[1, '閉じていない引用符付きの識別子']])
    assert.deepEqual(reasons("SELECT 'a';\n"), [])
  })
})

describe('互換性の検査の対象と除外', () => {
  const drop = 'ALTER TABLE a DROP COLUMN b;\n'
  const newName = 'V202611010900__drop_b.sql'
  const newFile = `backend/ecsite/src/main/resources/db/migration/${newName}`
  const legacyFile = (name) => `backend/ecsite/src/main/resources/db/migration/${name}`
  const allLegacy = LEGACY_MIGRATIONS.map(legacyFile)

  test('今ある 38 のマイグレーションの一覧は、重複がなく、バージョン付きのファイル名だけを持つ', () => {
    assert.equal(LEGACY_MIGRATIONS.length, 38)
    assert.equal(new Set(LEGACY_MIGRATIONS).size, 38)
    assert.ok(LEGACY_MIGRATIONS.every((name) => /^V\d+__[^/]+\.sql$/.test(name)))
  })

  test('一覧にあるマイグレーションだけを対象にしない。番号が小さくても、日付だけの番号でも、一覧にないものは対象にする', () => {
    assert.equal(isCheckedForCompatibility(legacyFile(LEGACY_MIGRATIONS[0])), false)
    assert.equal(isCheckedForCompatibility(LEGACY_MIGRATIONS[0]), false)
    assert.equal(isCheckedForCompatibility('db/migration/V1__a.sql'), true)
    assert.equal(isCheckedForCompatibility('db/migration/V20261101__a.sql'), true)
    assert.equal(isCheckedForCompatibility('db/migration/V202610090000__other_name.sql'), true)
    assert.equal(isCheckedForCompatibility('db/migration/V1_1__a.sql'), true)
  })

  test('対象外のマイグレーションの禁じる変更は数えず、読みもしない', () => {
    const result = scanMigrations([legacyFile(LEGACY_MIGRATIONS[0])], () => assert.fail('読まない'), {})
    assert.deepEqual(result.found, [])
  })

  test('番号が小さい新しいマイグレーションの禁じる変更も、数える', () => {
    const small = 'backend/ecsite/src/main/resources/db/migration/V1__drop_b.sql'
    assert.equal(scanMigrations([small], () => drop, {}).found.length, 1)
  })

  test('一覧にあって実在しないマイグレーションは、一覧の誤りとして返す', () => {
    const result = scanMigrations(allLegacy.slice(1), () => '', {})
    assert.deepEqual(result.missingLegacy, [LEGACY_MIGRATIONS[0]])
    assert.deepEqual(scanMigrations(allLegacy, () => '', {}).missingLegacy, [])
  })

  test('新しいマイグレーションの禁じる変更は、ファイルと行を添えて返す', () => {
    const result = scanMigrations([newFile], () => `-- 説明\n${drop}`, {})
    assert.deepEqual(result.found, [{ file: newFile, line: 2, kind: INCOMPATIBLE_KINDS.dropColumn, text: 'ALTER TABLE a DROP COLUMN b;' }])
  })

  test('除外（<ファイル名>:<種類>）に置いた変更は数えず、使った除外のキーを返す', () => {
    const key = `${newName}:${INCOMPATIBLE_KINDS.dropColumn}`
    const result = scanMigrations([newFile], () => drop + drop, { [key]: '理由' })
    assert.deepEqual(result.found, [])
    assert.deepEqual([...result.used], [key])
  })

  test('既定値の削除も、除外（<ファイル名>:既存の列の既定値の削除）に置けば数えない', () => {
    const sql = 'ALTER TABLE a ALTER COLUMN c DROP DEFAULT;\n'
    assert.equal(scanMigrations([newFile], () => sql, {}).found[0].kind, INCOMPATIBLE_KINDS.dropDefault)
    const key = `${newName}:${INCOMPATIBLE_KINDS.dropDefault}`
    const result = scanMigrations([newFile], () => sql, { [key]: '理由' })
    assert.deepEqual(result.found, [])
    assert.deepEqual([...result.used], [key])
  })

  test('除外は種類が違う変更には効かない', () => {
    const key = `${newName}:${INCOMPATIBLE_KINDS.dropTable}`
    const result = scanMigrations([newFile], () => drop, { [key]: '理由' })
    assert.equal(result.found.length, 1)
    assert.equal(result.used.size, 0)
  })

  test('理由のない除外と、当たる変更がなくなった除外は、誤りの文にする', () => {
    const used = `${newName}:${INCOMPATIBLE_KINDS.dropColumn}`
    const stale = `${newName}:${INCOMPATIBLE_KINDS.dropTable}`
    const exclusions = { [used]: '', [stale]: '理由' }
    const { used: usedKeys } = scanMigrations([newFile], () => drop, exclusions)
    const problems = checkExclusions('migrations-exclusions.json', exclusions, (key) => usedKeys.has(key))
    assert.deepEqual(problems, [describeEmptyReason('migrations-exclusions.json', used), describeStaleExclusion('migrations-exclusions.json', stale)])
  })

  test('読めない所は除外できず、ファイルを添えて返す', () => {
    const result = scanMigrations([newFile], () => "SELECT 'abc;\n", {})
    assert.deepEqual(result.unreadable.map((item) => [item.file, item.line]), [[newFile, 1]])
  })
})

describe('互換性の検査の実行（一時リポジトリ）', () => {
  const script = fileURLToPath(new URL('./migrations.mjs', import.meta.url))
  const dir = 'backend/ecsite/src/main/resources/db/migration'
  const env = withoutGitEnv()
  const repos = []
  after(() => repos.forEach((repo) => rmSync(repo, { recursive: true, force: true })))

  /** 今ある 38 のマイグレーション（一覧にあるもの）を中身 `select 1;` で置き、files で上書き・追加する（null は置かない） */
  function run(files) {
    const all = { ...Object.fromEntries(LEGACY_MIGRATIONS.map((name) => [name, 'select 1;\n'])), ...files }
    const repo = mkdtempSync(path.join(tmpdir(), 'migrations-compat-'))
    repos.push(repo)
    const git = (...args) => execFileSync('git', args, { cwd: repo, env, encoding: 'utf8' })
    git('init', '-q', '-b', 'main')
    git('config', 'user.email', 'test@example.com')
    git('config', 'user.name', 'test')
    git('config', 'commit.gpgsign', 'false')
    mkdirSync(path.join(repo, dir), { recursive: true })
    for (const [name, content] of Object.entries(all)) if (content !== null) writeFileSync(path.join(repo, dir, name), content)
    git('add', '-A')
    git('commit', '-q', '-m', 'init')
    return spawnSync('node', [script], { cwd: repo, env, encoding: 'utf8' })
  }

  test('今あるマイグレーションの禁じる変更は通し、新しいマイグレーションの禁じる変更は失敗にする', () => {
    const ok = run({ [LEGACY_MIGRATIONS[0]]: 'ALTER TABLE a DROP COLUMN b;\n' })
    assert.equal(ok.status, 0, ok.stdout)
    assert.match(ok.stdout, /互換性のない変更：0/)
    const ng = run({ 'V999999999999999__b.sql': 'ALTER TABLE a DROP COLUMN b;\n' })
    assert.equal(ng.status, 1)
    assert.match(ng.stdout, /V999999999999999__b\.sql:1 ALTER TABLE a DROP COLUMN b;（列の削除/)
    assert.match(ng.stdout, /互換性のない変更：1/)
  })

  test('番号が小さい新しいマイグレーションの禁じる変更も失敗にする', () => {
    const ng = run({ 'V1__b.sql': 'DROP TABLE IF EXISTS a;\n' })
    assert.equal(ng.status, 1)
    assert.match(ng.stdout, /V1__b\.sql:1 .*表の削除/)
  })

  test('一覧にあるマイグレーションが実在しないときは、一覧の誤りとして失敗にする', () => {
    const repoFiles = { [LEGACY_MIGRATIONS[0]]: null }
    const ng = run(repoFiles)
    assert.equal(ng.status, 1)
    assert.match(ng.stdout, new RegExp(`一覧の誤り）：${LEGACY_MIGRATIONS[0]}`))
    assert.match(ng.stdout, /対象外の一覧の誤り：1/)
  })
})
