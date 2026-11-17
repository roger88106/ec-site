// gh の規則（gh api のマージ、GraphQL、ruleset）のテスト
import path from 'node:path'
import { describe, test } from 'node:test'
import { CWD, assertAsked, assertDenied, assertPassed, fakeDeps } from './support/test-support.mjs'

/** GraphQL の、pull request をマージする mutation */
const MERGE_MUTATION = 'mutation { mergePullRequest(input: {pullRequestId: "x"}) { clientMutationId } }'

/** GraphQL の、自動マージを有効にする mutation */
const AUTO_MERGE_MUTATION = 'mutation { enablePullRequestAutoMerge(input: {pullRequestId: "x"}) { clientMutationId } }'

/** GraphQL の、マージでない mutation（sub-issue の追加） */
const SUB_ISSUE_MUTATION = 'mutation { addSubIssue(input: {issueId: "a", subIssueId: "b"}) { issue { number } } }'

describe('gh api のマージを拒否する', () => {
  test('gh api のマージは拒否し、gh pr merge --match-head-commit を使うよう返す', () => {
    for (const command of [
      `gh api -X PUT repos/roger88106/ec-site/pulls/246/merge`,
      `gh api --method PUT /repos/{owner}/{repo}/pulls/246/merge`,
      `gh api repos/roger88106/ec-site/pulls/246/merge -f merge_method=squash`,
    ]) {
      assertDenied(command, /--match-head-commit/, fakeDeps())
    }
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
