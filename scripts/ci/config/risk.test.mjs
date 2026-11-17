import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { listGitFiles } from '../../shared/git/git-files.mjs'
import { removeGitEnv, withoutGitEnv } from '../../shared/git/git-env.mjs'
import { judgeRisk, parseRiskConfig } from '../../shared/risk-rules.mjs'
import { baseFileReader, listChangedFiles } from './risk.mjs'

/** 実際の危険度が高い経路の一覧 */
const CONFIG = parseRiskConfig(readFileSync(new URL('../../shared/risk.json', import.meta.url), 'utf8'))

/** 冒頭に「危険度：高」の行を持つ spec */
const DECLARED_SPEC = ['# Spec 900：例', '', 'ステータス：確定', '危険度：高', '', '## 1. 背景と目的', ''].join('\n')

/** 冒頭に宣言のない spec（本文の中にだけ「危険度：高」がある） */
const PLAIN_SPEC = ['# Spec 901：例', '', 'ステータス：確定', '', '## 1. 背景と目的', '', '危険度：高'].join('\n')

describe('差分のファイルの求め方', () => {
  /** 試す Git のリポジトリ */
  const repo = mkdtempSync(path.join(tmpdir(), 'risk-'))
  after(() => rmSync(repo, { recursive: true, force: true }))

  const git = (...args) => execFileSync('git', args, { cwd: repo, encoding: 'utf8' })
  const write = (file, content = 'x\n') => {
    mkdirSync(path.dirname(path.join(repo, file)), { recursive: true })
    writeFileSync(path.join(repo, file), content)
  }

  before(() => {
    removeGitEnv()
    git('init', '-q', '-b', 'develop')
    git('config', 'user.email', 'test@example.com')
    git('config', 'user.name', 'test')
    git('config', 'commit.gpgsign', 'false')
    write('scripts/ci/moved.mjs', 'export const a = 1\n')
    write('docs/05-開発規則/removed.md')
    write('docs/08-spec/900-example/spec.md', DECLARED_SPEC)
    write('README.md')
    git('add', '-A')
    git('commit', '-q', '-m', 'base')
    git('checkout', '-q', '-b', 'feature')
    // 危険度が高い経路から外への移動（改名）、削除、宣言の行を消す変更
    mkdirSync(path.join(repo, 'tools'), { recursive: true })
    renameSync(path.join(repo, 'scripts/ci/moved.mjs'), path.join(repo, 'tools/moved.mjs'))
    rmSync(path.join(repo, 'docs/05-開発規則/removed.md'))
    write('docs/08-spec/900-example/spec.md', PLAIN_SPEC)
    git('add', '-A')
    git('commit', '-q', '-m', 'feature')
    write('untracked.md')
  })

  test('改名は元と先の両方、削除したファイル、追跡していないファイルを差分に入れる', () => {
    const changed = listChangedFiles('develop', listGitFiles([], repo), repo)
    assert.deepEqual(changed.files, ['docs/05-開発規則/removed.md', 'docs/08-spec/900-example/spec.md', 'scripts/ci/moved.mjs', 'tools/moved.mjs', 'untracked.md'])
  })

  test('危険度が高い経路から外へ移す変更・削除・宣言の行を消す変更は高になる', () => {
    const changed = listChangedFiles('develop', listGitFiles([], repo), repo)
    const readFile = (file) => {
      try {
        return readFileSync(path.join(repo, file), 'utf8')
      } catch {
        return null
      }
    }
    const result = judgeRisk(changed.files, CONFIG, readFile, baseFileReader(changed.base, repo))
    assert.equal(result.level, '高')
    assert.deepEqual(result.hits, [{ name: 'エージェントの規則と設定', files: ['docs/05-開発規則/removed.md', 'scripts/ci/moved.mjs'] }])
    assert.deepEqual(result.specs, ['docs/08-spec/900-example/spec.md'])
  })

  test('基準の ref がなければ、理由を返す', () => {
    assert.match(listChangedFiles('nothing', [], repo).error, /分岐点を求められない/)
  })
})

describe('script として実行する', () => {
  const script = fileURLToPath(new URL('./risk.mjs', import.meta.url))

  /**
   * 一時の Git のリポジトリ（develop に README.md だけを commit し、feature に切り替えた状態）に、ファイルを置いて script を実行する。
   *
   * @param {string[]} args script の引数
   * @param {Record<string, string>} [files] 作業ツリーに置くファイル（リポジトリのルートからのパスと内容）
   * @returns {import('node:child_process').SpawnSyncReturns<string>} 実行の結果
   */
  function run(args, files = {}) {
    const dir = mkdtempSync(path.join(tmpdir(), 'risk-run-'))
    const env = withoutGitEnv()
    const git = (...gitArgs) => execFileSync('git', gitArgs, { cwd: dir, env, encoding: 'utf8' })
    try {
      git('init', '-q', '-b', 'develop')
      git('config', 'user.email', 'test@example.com')
      git('config', 'user.name', 'test')
      git('config', 'commit.gpgsign', 'false')
      writeFileSync(path.join(dir, 'README.md'), 'x\n')
      git('add', '-A')
      git('commit', '-q', '-m', 'base')
      git('checkout', '-q', '-b', 'feature')
      for (const [file, text] of Object.entries(files)) {
        mkdirSync(path.dirname(path.join(dir, file)), { recursive: true })
        writeFileSync(path.join(dir, file), text)
      }
      return spawnSync(process.execPath, [script, ...args], { cwd: dir, env, encoding: 'utf8' })
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  }

  const base = ['--base', 'develop', '--skip-path-check']

  test('危険度が高い経路に当たらなければ、判定が低で、終了コード 0 で終わる', () => {
    const result = run(base, { 'notes.md': 'メモ\n' })
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /^判定：低/)
    assert.match(result.stdout, /当たったファイル：0、宣言した spec：0（差分のファイル 1 件中）/)
  })

  test('危険度が高い経路に当たれば、判定が高で、当たったファイルを出し、終了コード 0 で終わる', () => {
    const result = run(base, { 'scripts/ci/x.mjs': '\n' })
    assert.equal(result.status, 0, result.stdout)
    assert.match(result.stdout, /^判定：高/)
    assert.match(result.stdout, /- エージェントの規則と設定：scripts\/ci\/x\.mjs/)
  })

  test('本文の危険度が低で判定が高なら、理由を出し、終了コード 1 で終わる', () => {
    const result = run([...base, '--body-file', 'body.md'], { 'scripts/ci/x.mjs': '\n', 'body.md': '## 危険度\n\n判定：低\n' })
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /本文の危険度は低だが、判定は高/)
  })

  test('spec の冒頭に読めない危険度の行があれば、ファイルと行を出し、終了コード 1 で終わる', () => {
    const result = run(base, { 'docs/08-spec/900-example/spec.md': '# Spec 900：例\n\n危険度：たかい\n\n## 背景\n' })
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /docs\/08-spec\/900-example\/spec\.md:3 危険度：たかい（/)
  })

  test('一覧の経路に当たるファイルがなければ、経路を出し、終了コード 1 で終わる', () => {
    const result = run(['--base', 'develop'], { 'notes.md': 'メモ\n' })
    assert.equal(result.status, 1, result.stdout)
    assert.match(result.stdout, /risk\.json の経路に当たるファイルがない（書き間違いか、置き場所が変わった）：AGENTS\.md/)
  })

  test('知らない引数は、理由を出して終了コード 2 で終わる', () => {
    const result = run([...base, '--unknown'])
    assert.equal(result.status, 2, result.stdout)
    assert.match(result.stdout, /引数を読めない：/)
  })

  test('基準の ref がなく分岐点を求められなければ、理由を出して終了コード 2 で終わる', () => {
    const result = run(['--base', 'nothing', '--skip-path-check'])
    assert.equal(result.status, 2, result.stdout)
    assert.match(result.stdout, /分岐点を求められない/)
  })

  test('--body-file のファイルがなければ、理由を出して終了コード 2 で終わる', () => {
    const result = run([...base, '--body-file', 'none.md'])
    assert.equal(result.status, 2, result.stdout)
    assert.match(result.stdout, /none\.md を読めない/)
  })
})

describe('危険度の workflow', () => {
  const workflow = readFileSync(new URL('../../../.github/workflows/risk.yml', import.meta.url), 'utf8')

  test('基準（origin/develop）の側の risk.mjs と risk.json でも判定する（変えた後の一覧で自分を低と判定するのを防ぐ）', () => {
    assert.match(workflow, /base_paths=\(scripts\/ci\)/)
    assert.match(workflow, /base_paths\+=\(scripts\/shared\)/)
    assert.match(workflow, /git archive origin\/develop "\$\{base_paths\[@\]\}"/)
    assert.match(workflow, /node "\$base_dir\/scripts\/ci\/config\/risk\.mjs" --skip-path-check/)
  })

  test('作成者と題を渡し、ブランチ名は渡さない', () => {
    assert.match(workflow, /--author "\$PR_AUTHOR"/)
    assert.match(workflow, /--title "\$PR_TITLE"/)
    assert.doesNotMatch(workflow, /--branch/)
  })
})
