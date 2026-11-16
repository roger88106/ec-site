// gh の規則（マージ、gh api、ruleset）と、マージの条件（merge-rules）のテスト
import assert from 'node:assert/strict'
import path from 'node:path'
import { describe, test } from 'node:test'
import { judgeCommand } from '../agent-guard.mjs'
import { GH_BUDGET_MS } from './merge-rules.mjs'
import { CWD, assertAsked, assertDenied, assertPassed, fakeDeps } from './test-support.mjs'

/** マージする pull request の今の head の commit */
const HEAD_SHA = '28361e9899ad2db8de4c6452d1a2b21aa83e370d'

/** 危険度が低く、issue を閉じる本文 */
const LOW_BODY = '## 概要\n\n変更の説明\n\nCloses #246\n\n## 危険度\n\n<!-- 判定：高 -->\n判定：低\n'

/** gh pr merge の、許可なしに通せる形 */
const MERGE = `gh pr merge 246 --squash --match-head-commit ${HEAD_SHA}`

/** GraphQL の、pull request をマージする mutation */
const MERGE_MUTATION = 'mutation { mergePullRequest(input: {pullRequestId: "x"}) { clientMutationId } }'

/** GraphQL の、自動マージを有効にする mutation */
const AUTO_MERGE_MUTATION = 'mutation { enablePullRequestAutoMerge(input: {pullRequestId: "x"}) { clientMutationId } }'

/** GraphQL の、マージでない mutation（sub-issue の追加） */
const SUB_ISSUE_MUTATION = 'mutation { addSubIssue(input: {issueId: "a", subIssueId: "b"}) { issue { number } } }'

/** Dependabot の作成者（gh api graphql の author の形） */
const DEPENDABOT = { __typename: 'Bot', login: 'dependabot' }

/**
 * CI の検査（check run）を作る。
 *
 * @param {string} name 検査の名前
 * @param {string} [conclusion] 結論
 * @param {string} [status] 状態。COMPLETED でなければ結論は null
 * @param {string | null} [startedAt] 開始の時刻
 * @param {string} [workflow] workflow の名前
 */
function check(name, conclusion = 'SUCCESS', status = 'COMPLETED', startedAt = '2026-10-28T10:00:00Z', workflow = 'CI') {
  return {
    __typename: 'CheckRun',
    name,
    status,
    conclusion: status === 'COMPLETED' ? conclusion : null,
    startedAt,
    checkSuite: { workflowRun: { workflow: { name: workflow } } },
  }
}

/** 前の実行の開始の時刻 */
const OLDER = '2026-10-28T09:00:00Z'

/** 再実行の開始の時刻 */
const NEWER = '2026-10-28T11:00:00Z'

/**
 * gh api graphql が返す pull request を作る。既定は、許可なしにマージする条件を全て満たすもの。
 *
 * closing は閉じる issue とそのラベル、files は変更のファイル、checks は CI の検査、ほかの項目は pull request の項目を上書きする。
 */
function pullRequest({
  closing = [{ number: 246, labels: ['種類:整理'] }],
  files = ['README.md'],
  checks = [check('危険度の照合'), check('バックエンド'), check('E2E', 'SKIPPED'), check('CodeQL', 'NEUTRAL')],
  ...fields
} = {}) {
  const rollup = checks === null ? null : { contexts: { nodes: checks, pageInfo: { hasNextPage: false } } }
  return {
    data: {
      repository: {
        pullRequest: {
          state: 'OPEN',
          baseRefName: 'develop',
          headRefOid: HEAD_SHA,
          title: 'hooks: マージに確認を求める',
          body: LOW_BODY,
          author: { __typename: 'User', login: 'roger88106' },
          changedFiles: files.length,
          files: { nodes: files.map((file) => ({ path: file })), pageInfo: { hasNextPage: false } },
          closingIssuesReferences: {
            nodes: closing.map(({ number, labels }) => ({ number, labels: { nodes: labels.map((name) => ({ name })), pageInfo: { hasNextPage: false } } })),
            pageInfo: { hasNextPage: false },
          },
          commits: { nodes: [{ commit: { statusCheckRollup: rollup } }] },
          ...fields,
        },
      },
    },
  }
}

/** pull request を返す gh を持つ依存を作る */
function mergeDeps(fields = {}) {
  return fakeDeps({ gh: { status: 0, stdout: JSON.stringify(pullRequest(fields)), stderr: '' } })
}

describe('マージのコマンドを判定する', () => {
  test('gh api のマージは拒否し、gh pr merge --match-head-commit を使うよう返す', () => {
    for (const command of [
      `gh api -X PUT repos/roger88106/ec-site/pulls/246/merge`,
      `gh api --method PUT /repos/{owner}/{repo}/pulls/246/merge`,
      `gh api repos/roger88106/ec-site/pulls/246/merge -f merge_method=squash`,
    ]) {
      assertDenied(command, /--match-head-commit/, mergeDeps())
    }
  })

  test('--match-head-commit がない gh pr merge は拒否する', () => {
    for (const command of ['gh pr merge 246', 'gh pr merge 246 --squash --auto', 'gh pr merge']) {
      assertDenied(command, /--match-head-commit/, mergeDeps())
    }
  })

  test('自動のマージの解除（--disable-auto だけの形）は、gh を呼ばずに通す', () => {
    for (const command of ['gh pr merge 246 --disable-auto', 'gh pr merge --disable-auto', 'gh pr merge 246 -R other/repo --disable-auto']) {
      const deps = mergeDeps()
      assertPassed(command, deps)
      assert.deepEqual(deps.ghCalls, [], command)
    }
    assertDenied('gh pr merge 246 --disable-auto --squash', /--match-head-commit/, mergeDeps())
    assertDenied('gh pr merge 246 --disable-auto --auto', /--match-head-commit/, mergeDeps())
  })

  test('--admin と --auto は、ほかのマージと同じ条件で判定する（GitHub の設定が受け付けない）', () => {
    assertPassed(`${MERGE} --auto`, mergeDeps())
    assertPassed(`${MERGE} --admin`, mergeDeps())
  })

  test('条件を全て満たすマージは通す（skipped と neutral の検査を含む）', () => {
    const deps = mergeDeps()
    assertPassed(MERGE, deps)
    assert.equal(deps.ghCalls.length, 1)
  })

  test('向き先が agent/ で始まるマージは、CI・危険度・閉じる issue を見ずに通し、閉じたものと head の不一致は確認を求める', () => {
    const unchecked = { baseRefName: 'agent/458-module-review', checks: [check('バックエンド', 'FAILURE')], closing: [], body: '' }
    assertPassed(MERGE, mergeDeps(unchecked))
    assertPassed(MERGE, mergeDeps({ ...unchecked, closing: [{ number: 1, labels: ['種類:機能'] }] }))
    assertAsked(MERGE, /開いていない/, mergeDeps({ ...unchecked, state: 'MERGED' }))
    assertAsked(MERGE, /head の commit/, mergeDeps({ ...unchecked, headRefOid: 'f'.repeat(40) }))
    assertDenied('gh pr merge 246', /--match-head-commit/, mergeDeps(unchecked))
    assertAsked(MERGE, /向き先/, mergeDeps({ baseRefName: 'agents/x' }))
  })

  test('フルパスの gh と gh.exe でも判定する', () => {
    for (const program of ['"/c/Program Files/GitHub CLI/gh.exe"', 'gh.exe', '/usr/bin/gh']) {
      assertPassed(`${program} pr merge 246 --match-head-commit ${HEAD_SHA}`, mergeDeps())
      assertDenied(`${program} pr merge 246`, /--match-head-commit/, mergeDeps())
      assertAsked(`${program} pr merge 246 --match-head-commit ${HEAD_SHA}`, /向き先/, mergeDeps({ baseRefName: 'main' }))
    }
  })

  test('番号も URL もない gh pr merge は、今のブランチの pull request を指すので、gh を呼ばずに確認を求める', () => {
    for (const command of [`gh pr merge --match-head-commit ${HEAD_SHA}`, `gh pr merge 246-enforce-hooks --match-head-commit ${HEAD_SHA}`]) {
      const deps = mergeDeps()
      assertAsked(command, /番号か github\.com の URL/, deps)
      assert.deepEqual(deps.ghCalls, [])
    }
  })

  test('条件が 1 つ欠けると、欠けた条件を書いて確認を求める', () => {
    const cases = [
      [{ state: 'MERGED' }, /開いていない/],
      [{ baseRefName: 'main' }, /向き先.*main/],
      [{ headRefOid: 'ffffffffffffffffffffffffffffffffffffffff' }, /head/],
      [{ checks: [check('危険度の照合'), check('E2E', null, 'IN_PROGRESS')] }, /終わっていない.*E2E/],
      [{ checks: [check('危険度の照合'), check('E2E', null, 'QUEUED')] }, /終わっていない.*E2E/],
      [{ checks: [check('危険度の照合'), check('E2E', 'FAILURE')] }, /成功していない.*E2E/],
      [{ checks: [check('危険度の照合'), check('E2E', 'CANCELLED')] }, /成功していない.*E2E/],
      [{ checks: [check('危険度の照合'), { __typename: 'StatusContext', context: '外部の検査', state: 'PENDING' }] }, /終わっていない.*外部の検査/],
      [{ checks: [] }, /CI の検査がない/],
      [{ checks: null }, /CI の検査がない/],
      [{ checks: [check('バックエンド')] }, /危険度の照合/],
      [{ checks: [check('危険度の照合', 'SKIPPED'), check('バックエンド')] }, /危険度の照合/],
      [{ body: LOW_BODY.replace('判定：低', '判定：高') }, /本文の危険度/],
      [{ body: 'Closes #246' }, /本文の危険度/],
      [{ closing: [] }, /Closes/],
      [{ closing: [{ number: 246, labels: ['種類:整理'] }, { number: 247, labels: ['種類:機能'] }] }, /種類:機能.*#247/],
    ]
    for (const [fields, pattern] of cases) {
      assertAsked(MERGE, pattern, mergeDeps(fields))
    }
  })

  test('同じ検査が再実行で重なれば、最も新しい実行だけで判定する', () => {
    const base = [check('危険度の照合'), check('バックエンド')]
    for (const oldConclusion of ['FAILURE', 'CANCELLED']) {
      assertPassed(MERGE, mergeDeps({ checks: [...base, check('E2E', oldConclusion, 'COMPLETED', OLDER), check('E2E', 'SUCCESS', 'COMPLETED', NEWER)] }))
    }
    assertPassed(MERGE, mergeDeps({ checks: [check('バックエンド'), check('危険度の照合', 'FAILURE', 'COMPLETED', OLDER, '危険度'), check('危険度の照合', 'SUCCESS', 'COMPLETED', NEWER, '危険度')] }))
    assertPassed(
      MERGE,
      mergeDeps({
        checks: [
          ...base,
          { __typename: 'StatusContext', context: '外部の検査', state: 'FAILURE', createdAt: OLDER },
          { __typename: 'StatusContext', context: '外部の検査', state: 'SUCCESS', createdAt: NEWER },
        ],
      }),
    )
    assertAsked(MERGE, /終わっていない.*E2E/, mergeDeps({ checks: [...base, check('E2E', 'SUCCESS', 'COMPLETED', OLDER), check('E2E', null, 'IN_PROGRESS', NEWER)] }))
    assertAsked(MERGE, /成功していない.*E2E/, mergeDeps({ checks: [...base, check('E2E', 'SUCCESS', 'COMPLETED', OLDER), check('E2E', 'FAILURE', 'COMPLETED', NEWER)] }))
  })

  test('workflow が違えば、同じ名前の検査も別の検査として判定する', () => {
    const checks = [check('危険度の照合'), check('検査', 'SUCCESS', 'COMPLETED', NEWER, 'A'), check('検査', 'FAILURE', 'COMPLETED', OLDER, 'B')]
    assertAsked(MERGE, /成功していない.*検査/, mergeDeps({ checks }))
  })

  test('重なった検査の時刻が読めなければ、確認を求める', () => {
    const checks = [check('危険度の照合'), check('E2E', 'FAILURE', 'COMPLETED', null), check('E2E', 'SUCCESS', 'COMPLETED', NEWER)]
    assertAsked(MERGE, /時刻/, mergeDeps({ checks }))
  })

  test('欠けた条件が複数あれば、全てを書く', () => {
    const result = judgeCommand(MERGE, CWD, mergeDeps({ baseRefName: 'main', closing: [] }))
    assert.equal(result?.decision, 'ask')
    assert.match(result.reason, /向き先/)
    assert.match(result.reason, /Closes/)
  })

  test('gh の失敗、タイムアウト、読めない JSON では、確認を求める', () => {
    assertAsked(MERGE, /読めない.*Could not resolve/s, fakeDeps({ gh: { status: 1, stdout: '', stderr: 'GraphQL: Could not resolve to a PullRequest' } }))
    assertAsked(MERGE, /タイムアウト/, fakeDeps({ gh: { status: null, stdout: '', stderr: '', error: Object.assign(new Error('spawnSync gh ETIMEDOUT'), { code: 'ETIMEDOUT' }) } }))
    assertAsked(MERGE, /読めない/, fakeDeps({ gh: { status: 0, stdout: '{', stderr: '' } }))
    assertAsked(MERGE, /読めない/, fakeDeps({ gh: { status: 0, stdout: '{"data":{"repository":{"pullRequest":null}}}', stderr: '' } }))
    assertAsked(MERGE, /読めない/, fakeDeps({ gh: () => { throw new Error('gh がない') } }))
  })

  test('検査・ファイル・閉じる issue・ラベルの一覧が途中までしか読めなければ、確認を求める', () => {
    const data = pullRequest()
    data.data.repository.pullRequest.commits.nodes[0].commit.statusCheckRollup.contexts.pageInfo.hasNextPage = true
    assertAsked(MERGE, /一覧/, fakeDeps({ gh: { status: 0, stdout: JSON.stringify(data), stderr: '' } }))
  })

  test('gh には、1 つのコマンドで gh に使える時間の中のタイムアウトを渡す（hook のタイムアウトとの比較は agent-guard.test.mjs）', () => {
    const deps = mergeDeps()
    judgeCommand(MERGE, CWD, deps)
    const { timeout } = deps.ghCalls[0]
    assert.ok(Number.isInteger(timeout) && timeout > 0 && timeout <= GH_BUDGET_MS, `タイムアウト：${timeout}`)
  })

  test('Dependabot は、本文と Closes の代わりに、変更のファイルと題を危険度の判定に渡して、危険度が低いなら通す', () => {
    const dependabot = { author: DEPENDABOT, body: 'Bumps the gradle-minor-patch group.', closing: [] }
    const low = { ...dependabot, title: 'deps: bump the gradle-minor-patch group in /backend/ecsite with 4 updates', files: ['backend/ecsite/gradle/libs.versions.toml'] }
    assertPassed(MERGE, mergeDeps(low))
    assertAsked(MERGE, /危険度が高.*major/, mergeDeps({ ...low, title: 'deps: bump org.springframework.boot from 3.5.0 to 4.0.0 in /backend/ecsite' }))
    assertAsked(MERGE, /危険度が高.*CI とリリース/, mergeDeps({ ...low, files: ['.github/workflows/ci.yml'] }))
    assertAsked(MERGE, /終わっていない/, mergeDeps({ ...low, checks: [check('バックエンド', null, 'IN_PROGRESS')] }))
  })

  test('作成者の login が dependabot でも、bot でなければ Dependabot として扱わない', () => {
    assertAsked(MERGE, /本文の危険度/, mergeDeps({ author: { __typename: 'User', login: 'dependabot' }, body: '', closing: [] }))
  })

  test('対象のリポジトリは、URL、-R・--repo、前に付けた GH_REPO から決め、なければ対象のディレクトリで gh に決めさせる', () => {
    const cases = [
      [`gh pr merge https://github.com/roger88106/ec-site/pull/246 --match-head-commit ${HEAD_SHA}`, ['owner=roger88106', 'name=ec-site', 'number=246'], CWD],
      [`gh pr merge 246 -R other/repo --match-head-commit ${HEAD_SHA}`, ['owner=other', 'name=repo', 'number=246'], CWD],
      [`gh pr merge 246 --repo=github.com/other/repo --match-head-commit ${HEAD_SHA}`, ['owner=other', 'name=repo', 'number=246'], CWD],
      [`GH_REPO=other/repo gh pr merge 246 --match-head-commit ${HEAD_SHA}`, ['owner=other', 'name=repo', 'number=246'], CWD],
      [`cd sub && gh pr merge 246 --match-head-commit ${HEAD_SHA}`, ['owner={owner}', 'name={repo}', 'number=246'], path.join(CWD, 'sub')],
    ]
    for (const [command, expected, cwd] of cases) {
      const deps = mergeDeps()
      assertPassed(command, deps)
      assert.equal(deps.ghCalls.length, 1, command)
      for (const arg of expected) assert.ok(deps.ghCalls[0].args.includes(arg), `${command}：${deps.ghCalls[0].args.join(' ')}`)
      assert.equal(deps.ghCalls[0].cwd, cwd, command)
    }
  })

  test('github.com でない URL は、確認を求める', () => {
    assertAsked(`gh pr merge https://example.com/o/r/pull/246 --match-head-commit ${HEAD_SHA}`, /番号か github\.com の URL/, mergeDeps())
  })

  test('つないだコマンドでは、拒否を確認より先に返す', () => {
    assertDenied('gh pr merge 1 --match-head-commit abc && git push --force', /force push/, mergeDeps())
  })

  test('読むだけの gh と、マージでない gh api graphql は通す', () => {
    const deps = fakeDeps({ files: { [path.join(CWD, 'sub-issue.graphql')]: SUB_ISSUE_MUTATION } })
    for (const command of [
      'gh pr view 246',
      'gh pr checks 246',
      'gh api repos/roger88106/ec-site/pulls/246',
      'gh api repos/roger88106/ec-site/pulls/246/merge',
      `gh api graphql -f query='${SUB_ISSUE_MUTATION}'`,
      `gh api graphql -f query='query { viewer { login } }'`,
      'gh api graphql -F query=@sub-issue.graphql',
      'gh api graphql --input sub-issue.graphql',
      // -f（--raw-field）は @ でファイルを読まないので、文字のまま判定する
      'gh api graphql -f query=@missing.graphql',
      'echo gh pr merge 246',
    ]) {
      assertPassed(command, deps)
    }
  })

  test('gh api graphql の mergePullRequest は、語のどれかに含めば拒否し、gh pr merge --match-head-commit を使うよう返す', () => {
    for (const command of [
      `gh api graphql -f query='${MERGE_MUTATION}'`,
      `gh api graphql -F query='${MERGE_MUTATION}'`,
      `gh api graphql --raw-field query='${MERGE_MUTATION}'`,
      `gh api graphql --field=query='${MERGE_MUTATION}'`,
      `"/c/Program Files/GitHub CLI/gh.exe" api graphql -f query='${MERGE_MUTATION}'`,
      'gh api graphql -f query="mutation { mergePullRequest(input: {pullRequestId: \\"$PR_ID\\"}) { clientMutationId } }"',
      `gh api /graphql -f query='${MERGE_MUTATION}'`,
      `gh api https://api.github.com/graphql -f query='${MERGE_MUTATION}'`,
      `gh api https://github.example.com/api/graphql -f query='${MERGE_MUTATION}'`,
    ]) {
      assertDenied(command, /mergePullRequest.*--match-head-commit/s, fakeDeps())
    }
  })

  test('gh api graphql のファイル・標準入力・シェルの展開から渡す本文は読まずに通す（意図した回避なので見ない）', () => {
    for (const command of [
      'gh api graphql -F query=@merge.graphql',
      'gh api graphql --input body.json',
      'gh api graphql --input -',
      'gh api graphql -f query="$(cat merge.graphql)"',
      `gh api graphql -f query='${AUTO_MERGE_MUTATION}'`,
    ]) {
      assertPassed(command, fakeDeps())
    }
  })

  test('本文の編集の後に検査「危険度の照合」が再実行されていなければ確認を求め、再実行されていれば通す', () => {
    // 照合の開始の時刻は、check の既定（10:00）
    assertAsked(MERGE, /危険度の照合.*編集/, mergeDeps({ lastEditedAt: NEWER }))
    assertPassed(MERGE, mergeDeps({ lastEditedAt: OLDER }))
    assertPassed(MERGE, mergeDeps({ lastEditedAt: null }))
  })

  test('マージの条件を読む問い合わせは、本文の最後の編集の時刻を読む', () => {
    const deps = mergeDeps()
    judgeCommand(MERGE, CWD, deps)
    assert.match(deps.ghCalls[0].args.find((arg) => arg.startsWith('query=')), /\blastEditedAt\b/)
  })
})

describe('ruleset とブランチの保護の変更で確認を求める', () => {
  test('GET 以外のメソッド（-f・-F・--input による POST を含む）で確認を求める', () => {
    for (const command of [
      'gh api -X PUT repos/roger88106/ec-site/rulesets/123',
      'gh api -XPATCH /repos/roger88106/ec-site/rulesets/123',
      'gh api --method DELETE repos/roger88106/ec-site/rulesets/123',
      'gh api --method=PUT repos/roger88106/ec-site/rulesets/123 --input ruleset.json',
      'gh api repos/roger88106/ec-site/rulesets -f name=x',
      'gh api repos/roger88106/ec-site/rulesets --field enforcement=disabled',
      'gh api repos/roger88106/ec-site/rulesets --raw-field name=x',
      'gh api repos/roger88106/ec-site/rulesets --input ruleset.json',
      'gh api orgs/roger88106/rulesets -F x=1',
      'gh api -X PUT repos/roger88106/ec-site/branches/main/protection',
      'gh api -X DELETE repos/roger88106/ec-site/branches/develop/protection/required_status_checks',
      '"/c/Program Files/GitHub CLI/gh.exe" api -X PUT repos/roger88106/ec-site/rulesets/123',
    ]) {
      assertAsked(command, /ruleset/)
    }
  })

  test('読むだけの gh api、ruleset でないパス、gh api graphql は通す', () => {
    for (const command of [
      'gh api repos/roger88106/ec-site/rulesets',
      'gh api -X GET repos/roger88106/ec-site/rulesets -f includes_parents=true',
      'gh api repos/roger88106/ec-site/branches/main/protection',
      'gh api -X POST repos/roger88106/ec-site/issues -f title=x',
      `gh api graphql -f query='mutation { updateRepositoryRuleset(input: {}) { clientMutationId } }'`,
    ]) {
      assertPassed(command)
    }
  })
})
