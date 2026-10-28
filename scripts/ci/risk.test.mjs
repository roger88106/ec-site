import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { after, before, describe, test } from 'node:test'
import { listGitFiles } from './lib/git-files.mjs'
import { removeGitEnv } from './lib/git-env.mjs'
import {
  applyDependabotTitle,
  baseFileReader,
  checkPullRequest,
  compareBody,
  findDeclaredSpecs,
  findMissingPaths,
  formatJudgement,
  isDependabot,
  judgeDependabotTitle,
  judgeRisk,
  listChangedFiles,
  parseRiskConfig,
  readBodyRisk,
} from './risk.mjs'

/** 実際の高の経路の一覧 */
const CONFIG = parseRiskConfig(readFileSync(new URL('./risk.json', import.meta.url), 'utf8'))

/** spec.md の中身を返す関数を作る（一覧にないファイルは読めない＝削除とみなす） */
const reader = (contents) => (file) => contents[file] ?? null

/** 冒頭に「危険度：高」の行を持つ spec */
const DECLARED_SPEC = ['# Spec 900：例', '', 'ステータス：確定', '危険度：高', '', '## 1. 背景と目的', ''].join('\n')

/** 冒頭に宣言のない spec（本文の中にだけ「危険度：高」がある） */
const PLAIN_SPEC = ['# Spec 901：例', '', 'ステータス：確定', '', '## 1. 背景と目的', '', '危険度：高'].join('\n')

/** 高の経路を含まないファイル */
const LOW_FILES = ['frontend/store/src/features/cart/CartPage.tsx', 'docs/design/modules/catalog.md', 'README.md']

describe('変更の危険度の判定', () => {
  // まとまりごとに、高の経路の例（テストのファイルも含む）
  const examples = {
    'エージェントの規則と設定': ['AGENTS.md', 'docs/constitution.md', 'docs/rules/workflow.md', '.claude/skills/review/SKILL.md', 'scripts/ci/risk.mjs', 'scripts/dev/hooks/pre-commit', 'docs/guides/branches.md'],
    'CI とリリース': ['.github/workflows/ci.yml', '.github/dependabot.yml'],
    '認証': [
      'backend/ecsite/src/main/java/com/ecsite/config/security/SecurityConfig.java',
      'backend/ecsite/src/main/java/com/ecsite/common/security/CurrentUser.java',
      'frontend/store/src/auth/session.ts',
      'frontend/store/src/app/api/auth/[...nextauth]/route.ts',
      'frontend/admin/src/auth/permissions.ts',
      'docker/keycloak/realms/ec-store-realm.json',
    ],
    '支払': [
      'backend/ecsite/src/main/java/com/ecsite/module/payment/domain/Payment.java',
      'backend/ecsite/src/main/java/com/ecsite/module/order/application/payment/PayOrder.java',
    ],
    '金額': [
      'backend/ecsite/src/main/java/com/ecsite/module/tax/domain/TaxCalculator.java',
      'backend/ecsite/src/test/java/com/ecsite/module/tax/domain/TaxCalculatorTest.java',
      'backend/ecsite/src/main/java/com/ecsite/module/order/application/checkout/CheckoutOrder.java',
      'backend/ecsite/src/test/java/com/ecsite/module/order/application/checkout/CheckoutOrderTest.java',
    ],
    '権限': ['docs/design/role-permissions.md'],
    'データベース': ['backend/ecsite/src/main/resources/db/migration/V202611010900__x.sql'],
    '契約': ['api/openapi/admin.yaml'],
  }

  test('高の経路の一覧は、まとまりの名前と経路を持つ', () => {
    assert.deepEqual(CONFIG.groups.map((group) => group.name), Object.keys(examples))
    for (const group of CONFIG.groups) assert.ok(group.paths.length > 0, group.name)
  })

  for (const [name, files] of Object.entries(examples)) {
    test(`高の経路のまとまり「${name}」の例は、どれも高になり、そのまとまりに当たる`, () => {
      for (const file of files) {
        const result = judgeRisk([file], CONFIG, reader({}))
        assert.equal(result.level, '高', file)
        assert.deepEqual(result.hits, [{ name, files: [file] }], file)
      }
    })
  }

  test('テストのファイルも、高の経路の下にあれば高になる（バックエンドは本番のコードと同じパッケージのテスト・統合テスト・テストの補助）', () => {
    const files = [
      'backend/ecsite/src/test/java/com/ecsite/module/payment/PaymentTest.java',
      'backend/ecsite/src/integrationTest/java/com/ecsite/config/security/AuthorizationTest.java',
      'backend/ecsite/src/testFixtures/java/com/ecsite/config/security/TestTokens.java',
      'backend/ecsite/src/test/java/com/ecsite/contract/AdminApiPermissionContractTest.java',
      'frontend/admin/src/auth/permissions.test.ts',
    ]
    for (const file of files) assert.equal(judgeRisk([file], CONFIG, reader({})).level, '高', file)
  })

  test('経路の「*」は、ディレクトリの 1 つの階層に当たる', () => {
    const config = parseRiskConfig(JSON.stringify({ groups: [{ name: '例', paths: ['src/*/java/'] }] }))
    assert.equal(judgeRisk(['src/main/java/A.java'], config, reader({})).level, '高')
    assert.equal(judgeRisk(['src/main/kotlin/java/A.kt'], config, reader({})).level, '低')
  })

  test('経路は、ディレクトリの区切りまで一致したときだけ当たる（前方が同じだけの名前は当たらない）', () => {
    const files = ['AGENTS.md.bak', 'docs/rules-old/a.md', '.claude-local/x', 'api/openapi-legacy/a.yaml']
    assert.equal(judgeRisk(files, CONFIG, reader({})).level, '低')
  })

  test('高の経路に当たらないファイルだけなら低になる', () => {
    const result = judgeRisk(LOW_FILES, CONFIG, reader({}))
    assert.equal(result.level, '低')
    assert.deepEqual(result.hits, [])
    assert.deepEqual(result.specs, [])
  })

  test('差分がないときは低になる', () => {
    const result = judgeRisk([], CONFIG, reader({}))
    assert.equal(result.level, '低')
    assert.equal(result.total, 0)
  })

  test('冒頭に「危険度：高」の行がある spec.md を含む差分は、経路が低でも高になる', () => {
    const file = 'docs/specs/900-example/spec.md'
    const result = judgeRisk([file, ...LOW_FILES], CONFIG, reader({ [file]: DECLARED_SPEC }))
    assert.equal(result.level, '高')
    assert.deepEqual(result.hits, [])
    assert.deepEqual(result.specs, [file])
  })

  test('アーカイブへ移した spec.md（移した先が差分に入る）の宣言も読む', () => {
    const moved = 'docs/specs/archive/1to10/900-example/spec.md'
    const result = judgeRisk(['docs/specs/900-example/spec.md', moved], CONFIG, reader({ [moved]: DECLARED_SPEC }))
    assert.equal(result.level, '高')
    assert.deepEqual(result.specs, [moved])
  })

  test('宣言が冒頭（最初の節の見出しより前）になければ、宣言とみなさない', () => {
    const file = 'docs/specs/901-example/spec.md'
    assert.deepEqual(findDeclaredSpecs([file], reader({ [file]: PLAIN_SPEC })).specs, [])
  })

  test('spec.md でないファイル（plan.md、tasks）の「危険度：高」の行は、宣言とみなさない', () => {
    const files = ['docs/specs/900-example/plan.md', 'docs/specs/900-example/tasks/01-a.md']
    const contents = Object.fromEntries(files.map((file) => [file, DECLARED_SPEC]))
    assert.deepEqual(findDeclaredSpecs(files, reader(contents)).specs, [])
  })

  test('改行が CRLF の spec.md の宣言も読む', () => {
    const file = 'docs/specs/900-example/spec.md'
    assert.deepEqual(findDeclaredSpecs([file], reader({ [file]: DECLARED_SPEC.replaceAll('\n', '\r\n') })).specs, [file])
  })

  test('出力に、判定と、当たった経路（まとまりの名前とファイル）と、宣言した spec が入り、最後の行が要約の行になる', () => {
    const spec = 'docs/specs/900-example/spec.md'
    const files = ['docs/rules/workflow.md', 'api/openapi/admin.yaml', spec, 'README.md']
    const lines = formatJudgement(judgeRisk(files, CONFIG, reader({ [spec]: DECLARED_SPEC })))
    assert.equal(lines[0], '判定：高')
    assert.ok(lines.includes('- エージェントの規則と設定：docs/rules/workflow.md'), lines.join('\n'))
    assert.ok(lines.includes('- 契約：api/openapi/admin.yaml'), lines.join('\n'))
    assert.ok(lines.includes(`- spec の宣言：${spec}`), lines.join('\n'))
    assert.match(lines.at(-1), /^当たったファイル：2、宣言した spec：1（差分のファイル 4 件中）$/)
  })

  test('当たったファイルが多いまとまりは、先頭の数件と残りの件数だけを出す（本文に貼れる短さにする）', () => {
    const files = Array.from({ length: 12 }, (_, index) => `.claude/skills/x/file${String(index).padStart(2, '0')}.md`)
    const lines = formatJudgement(judgeRisk(files, CONFIG, reader({})))
    const line = lines.find((item) => item.startsWith('- エージェントの規則と設定：'))
    assert.match(line, /ほか 7 件$/)
    assert.ok(lines.length <= 4, lines.join('\n'))
  })

  test('一覧の経路が実在しなければ、誤りとして返す（経路の書き間違いで判定が低になるのを防ぐ）', () => {
    const config = parseRiskConfig(JSON.stringify({ groups: [{ name: '例', paths: ['docs/rules/', 'docs/nothing/', 'NOTHING.md'] }] }))
    assert.deepEqual(findMissingPaths(config, ['docs/rules/workflow.md', 'README.md']), ['docs/nothing/', 'NOTHING.md'])
  })

  test('一覧の形が違えば（まとまりの名前がない、経路が空）、読むときに失敗にする', () => {
    assert.throws(() => parseRiskConfig(JSON.stringify({ groups: [{ paths: ['a/'] }] })))
    assert.throws(() => parseRiskConfig(JSON.stringify({ groups: [{ name: '例', paths: [] }] })))
    assert.throws(() => parseRiskConfig(JSON.stringify({})))
  })
})

describe('pull request の本文の危険度との比べ', () => {
  /** 「## 危険度」の節に値を書いた本文 */
  const body = (value) => ['## 概要', '', '何かを変える', '', '## 危険度', '', '<!-- risk.mjs の出力を貼る -->', '', value, '- 契約：api/openapi/admin.yaml', '', '## 確認したこと', '', '- [x] CI が通る'].join('\n')

  test('本文の「## 危険度」の節の「判定：」の行の値を読む', () => {
    assert.deepEqual(readBodyRisk(body('判定：高')), { value: '高' })
    assert.deepEqual(readBodyRisk(body('判定：低').replaceAll('\n', '\r\n')), { value: '低' })
  })

  test('閉じていない <!-- の後ろは、GitHub が表示しないので読まない', () => {
    const text = ['## 危険度', '', '判定：低', '<!-- 閉じていない', '判定：高', '', '## 確認したこと'].join('\n')
    assert.deepEqual(readBodyRisk(text), { value: '低' })
    assert.deepEqual(readBodyRisk(['## 危険度', '<!-- 閉じていない', '判定：高'].join('\n')), { error: '「## 危険度」の節に「判定：」の行がない' })
  })

  test('コメントが続いても、それぞれの中を読まず、表示される部分の判定を読む', () => {
    const text = ['## 危険度', '<!-- 判定：高 --><!-- 判定：高 -->', '判定：低'].join('\n')
    assert.deepEqual(readBodyRisk(text), { value: '低' })
  })

  test('<!--<!-- --> のように入れ子に見えても、最初の --> までを除き、残りを表示される部分として読む', () => {
    const text = ['## 危険度', '<!--<!-- 判定：高 -->-->', '判定：低'].join('\n')
    assert.deepEqual(readBodyRisk(text), { value: '低' })
    assert.deepEqual(readBodyRisk(['## 危険度', '<!--<!-- -->', '判定：低', '-->'].join('\n')), { value: '低' })
  })

  test('本文が低で判定が高のときは失敗にする', () => {
    const result = compareBody('高', body('判定：低'))
    assert.equal(result.ok, false)
    assert.match(result.message, /高/)
  })

  test('本文と判定が一致すれば成功にする', () => {
    assert.equal(compareBody('高', body('判定：高')).ok, true)
    assert.equal(compareBody('低', body('判定：低')).ok, true)
  })

  test('規模 S の issue で高とした変更のように、本文が高で判定が低のときは成功にする（本文で上げてよい）', () => {
    const result = compareBody('低', body('判定：高'))
    assert.equal(result.ok, true)
    assert.match(result.message, /本文で高/)
  })

  test('節の値が空のときは失敗にする', () => {
    assert.equal(compareBody('低', body('判定：')).ok, false)
    assert.equal(compareBody('低', body('')).ok, false)
  })

  test('「## 危険度」の節がないときは失敗にする', () => {
    const result = compareBody('低', '## 概要\n\n判定：低\n')
    assert.equal(result.ok, false)
    assert.match(result.message, /節がない/)
  })

  test('本文が空のときは失敗にする', () => {
    assert.equal(compareBody('低', '').ok, false)
  })

  test('HTML のコメントの中の「判定：」は値とみなさない', () => {
    assert.equal(compareBody('低', body('<!-- 判定：低 -->')).ok, false)
  })

  test('節の中で値が食い違う（「判定：」の行が高と低の両方）ときは失敗にする', () => {
    assert.equal(compareBody('高', body('判定：高\n判定：低')).ok, false)
  })

  test('Dependabot の pull request は、作成者だけで見分ける', () => {
    assert.equal(isDependabot({ author: 'dependabot[bot]' }), true)
    assert.equal(isDependabot({ author: 'roger88106' }), false)
    assert.equal(isDependabot({}), false)
  })

  test('ブランチ名だけが dependabot/ の人の pull request は、Dependabot とみなさず本文と比べる（ブランチ名は誰でも付けられる）', () => {
    assert.equal(isDependabot({ author: 'roger88106', branch: 'dependabot/npm_and_yarn/next-15.5.1' }), false)
    const result = checkPullRequest({ level: '高', body: body('判定：低'), author: 'roger88106', branch: 'dependabot/x' })
    assert.equal(result.ok, false)
  })

  test('Dependabot の pull request は本文と比べず、本文が低・空で判定が高でも成功にする', () => {
    for (const text of [body('判定：低'), '', undefined]) {
      const result = checkPullRequest({ level: '高', body: text, author: 'dependabot[bot]' })
      assert.equal(result.ok, true)
      assert.match(result.message, /Dependabot/)
    }
  })

  test('Dependabot でない pull request（規模 S を含む）は本文と比べ、本文が空・低で判定が高なら失敗にする', () => {
    assert.equal(checkPullRequest({ level: '高', body: '', author: 'roger88106' }).ok, false)
    assert.equal(checkPullRequest({ level: '高', body: body('判定：低'), author: 'roger88106' }).ok, false)
    assert.equal(checkPullRequest({ level: '高', body: body('判定：高'), author: 'roger88106' }).ok, true)
    assert.equal(checkPullRequest({ level: '低', body: body('判定：高'), author: 'roger88106' }).ok, true)
  })

  test('本文を渡さない（check-all.sh から）ときは比べず成功にし、何も出さない', () => {
    assert.deepEqual(checkPullRequest({ level: '高' }), { ok: true, message: null })
  })
})

describe('spec の宣言の基準のバージョンと書き方', () => {
  const file = 'docs/specs/900-example/spec.md'

  test('基準のバージョンに宣言があり、差分の後のバージョンで宣言の行を消した spec.md は高になる', () => {
    const result = judgeRisk([file], CONFIG, reader({ [file]: PLAIN_SPEC }), reader({ [file]: DECLARED_SPEC }))
    assert.equal(result.level, '高')
    assert.deepEqual(result.specs, [file])
  })

  test('改名した spec.md は、元のパスの基準のバージョンの宣言も読む（差分に元のパスが入る）', () => {
    const moved = 'docs/specs/archive/1to10/900-example/spec.md'
    const result = judgeRisk([file, moved], CONFIG, reader({ [moved]: PLAIN_SPEC }), reader({ [file]: DECLARED_SPEC }))
    assert.equal(result.level, '高')
    assert.deepEqual(result.specs, [file])
  })

  test('どちらのバージョンにも宣言がなければ低', () => {
    assert.equal(judgeRisk([file], CONFIG, reader({ [file]: PLAIN_SPEC }), reader({ [file]: PLAIN_SPEC })).level, '低')
  })

  test('冒頭の「危険度」を含む行が正確な「危険度：高」でなければ、ファイルと行を読めない行として返す', () => {
    const variants = ['危険度: 高', '**危険度：高**', '危険度：高（理由）', 'ステータス：確定　危険度：高', '- 危険度：高', '危険度：低']
    for (const variant of variants) {
      const text = DECLARED_SPEC.replace('危険度：高', variant)
      const { specs, unreadable } = findDeclaredSpecs([file], reader({ [file]: text }))
      assert.deepEqual(specs, [], variant)
      assert.deepEqual(unreadable.map((item) => [item.file, item.line, item.text]), [[file, 4, variant]], variant)
    }
  })

  test('正確な宣言の行と、題の行と、最初の節の後の「危険度」は、読めない行にしない', () => {
    const text = ['# Spec 900：危険度の判定を変える', '', '危険度：高', '', '## 1. 背景', '', '危険度: 高'].join('\n')
    assert.deepEqual(findDeclaredSpecs([file], reader({ [file]: text })).unreadable, [])
  })

  test('基準のバージョンの書き方は見ない（読めない行にしない）', () => {
    const base = DECLARED_SPEC.replace('危険度：高', '危険度: 高')
    assert.deepEqual(findDeclaredSpecs([file], reader({ [file]: DECLARED_SPEC }), reader({ [file]: base })).unreadable, [])
  })
})

describe('経路の大文字と小文字', () => {
  test('大文字の違うパス（Windows と macOS では同じ所に入る）も、高の経路に当てる', () => {
    for (const file of ['.CLAUDE/skills/x/SKILL.md', 'Docs/Rules/x.md', 'agents.md', 'API/OpenAPI/admin.yaml']) {
      assert.equal(judgeRisk([file], CONFIG, reader({})).level, '高', file)
    }
  })

  test('大文字の違う spec.md の宣言も読む', () => {
    const file = 'Docs/Specs/900-example/SPEC.md'
    assert.deepEqual(findDeclaredSpecs([file], reader({ [file]: DECLARED_SPEC })).specs, [file])
  })
})

describe('Dependabot の更新の種類', () => {
  test('題の major が違えば高', () => {
    assert.equal(judgeDependabotTitle('deps: bump next from 15.5.1 to 16.0.0').level, '高')
    assert.equal(judgeDependabotTitle('deps: bump org.flywaydb:flyway-core from 11.3.0 to 12.0.0 in /backend/ecsite').level, '高')
    assert.equal(judgeDependabotTitle('deps: bump postgres from 17.4-alpine to 18.0-alpine in /docker').level, '高')
    assert.match(judgeDependabotTitle('deps: bump next from 15.5.1 to 16.0.0').reason, /major/)
  })

  test('major が 0 で minor が違えば高', () => {
    assert.equal(judgeDependabotTitle('deps: bump foo from 0.3.1 to 0.4.0').level, '高')
  })

  test('major の違わない更新は、経路の判定のまま', () => {
    assert.equal(judgeDependabotTitle('deps: bump next from 15.5.1 to 15.6.0').level, null)
    assert.equal(judgeDependabotTitle('deps: bump foo from 0.3.1 to 0.3.2').level, null)
  })

  test('group の名前が -minor-patch で終われば経路の判定のまま、それ以外の group（github-actions-all）は高', () => {
    assert.equal(judgeDependabotTitle('deps: bump the npm-minor-patch group with 3 updates').level, null)
    assert.equal(judgeDependabotTitle('deps: bump the gradle-minor-patch group across 1 directory with 2 updates').level, null)
    assert.equal(judgeDependabotTitle('deps: bump the github-actions-all group across 2 directories with 4 updates').level, '高')
  })

  test('読めない題・空の題は高', () => {
    for (const title of ['deps: update dependencies', '', undefined, 'deps: bump foo from abc123 to def456']) {
      assert.equal(judgeDependabotTitle(title).level, '高', String(title))
    }
  })

  test('Dependabot の差分の例：workflow は高、package.json の minor の group は低、major の題は高。理由を出力に出す', () => {
    const workflow = applyDependabotTitle(judgeRisk(['.github/workflows/ci.yml'], CONFIG, reader({})), 'deps: bump the github-actions-all group with 2 updates')
    assert.equal(workflow.level, '高')
    const minor = applyDependabotTitle(judgeRisk(['package.json', 'pnpm-lock.yaml'], CONFIG, reader({})), 'deps: bump the npm-minor-patch group with 3 updates')
    assert.equal(minor.level, '低')
    assert.ok(formatJudgement(minor).some((line) => line.startsWith('- Dependabot の更新：group「npm-minor-patch」')))
    const major = applyDependabotTitle(judgeRisk(['package.json', 'pnpm-lock.yaml'], CONFIG, reader({})), 'deps: bump next from 15.5.1 to 16.0.0')
    assert.equal(major.level, '高')
    assert.ok(formatJudgement(major).includes('- Dependabot の更新：major の更新（15.5.1 → 16.0.0）'))
  })

  test('経路が高なら、題が minor でも高のまま', () => {
    const result = applyDependabotTitle(judgeRisk(['.github/workflows/ci.yml'], CONFIG, reader({})), 'deps: bump actions/checkout from 7.0.0 to 7.1.0')
    assert.equal(result.level, '高')
  })
})

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
    write('docs/rules/removed.md')
    write('docs/specs/900-example/spec.md', DECLARED_SPEC)
    write('README.md')
    git('add', '-A')
    git('commit', '-q', '-m', 'base')
    git('checkout', '-q', '-b', 'feature')
    // 高の経路から外への移動（改名）、削除、宣言の行を消す変更
    mkdirSync(path.join(repo, 'tools'), { recursive: true })
    renameSync(path.join(repo, 'scripts/ci/moved.mjs'), path.join(repo, 'tools/moved.mjs'))
    rmSync(path.join(repo, 'docs/rules/removed.md'))
    write('docs/specs/900-example/spec.md', PLAIN_SPEC)
    git('add', '-A')
    git('commit', '-q', '-m', 'feature')
    write('untracked.md')
  })

  test('改名は元と先の両方、削除したファイル、追跡していないファイルを差分に入れる', () => {
    const changed = listChangedFiles('develop', listGitFiles([], repo), repo)
    assert.deepEqual(changed.files, ['docs/rules/removed.md', 'docs/specs/900-example/spec.md', 'scripts/ci/moved.mjs', 'tools/moved.mjs', 'untracked.md'])
  })

  test('高の経路から外へ移す変更・削除・宣言の行を消す変更は高になる', () => {
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
    assert.deepEqual(result.hits, [{ name: 'エージェントの規則と設定', files: ['docs/rules/removed.md', 'scripts/ci/moved.mjs'] }])
    assert.deepEqual(result.specs, ['docs/specs/900-example/spec.md'])
  })

  test('基準の ref がなければ、理由を返す', () => {
    assert.match(listChangedFiles('nothing', [], repo).error, /分岐点を求められない/)
  })
})

describe('危険度の workflow', () => {
  const workflow = readFileSync(new URL('../../.github/workflows/risk.yml', import.meta.url), 'utf8')

  test('基準（origin/develop）の側の risk.mjs と risk.json でも判定する（変えた後の一覧で自分を低と判定するのを防ぐ）', () => {
    assert.match(workflow, /git archive origin\/develop scripts\/ci/)
    assert.match(workflow, /node "\$base_dir\/scripts\/ci\/risk\.mjs" --skip-path-check/)
  })

  test('作成者と題を渡し、ブランチ名は渡さない', () => {
    assert.match(workflow, /--author "\$PR_AUTHOR"/)
    assert.match(workflow, /--title "\$PR_TITLE"/)
    assert.doesNotMatch(workflow, /--branch/)
  })
})
