import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findTerms, isTargetDoc } from './doc-terms.mjs'

describe('文書の用語の検査', () => {
  test('一覧の語を書いた行を見つける', () => {
    const terms = [{ word: '道具', use: 'ツール' }]
    const text = ['ビルドのツール', 'ビルドの道具', '開発の道具と道具箱'].join('\n')
    assert.deepEqual(
      findTerms(text, terms).map((hit) => hit.line),
      [2, 3],
    )
  })

  test('docs・README・AGENTS・skill と進行中の spec の Markdown を見て、アーカイブした spec は見ない', () => {
    assert.equal(isTargetDoc('docs/frontend/coding.md'), true)
    assert.equal(isTargetDoc('README.md'), true)
    assert.equal(isTargetDoc('AGENTS.md'), true)
    assert.equal(isTargetDoc('.claude/skills/docs/SKILL.md'), true)
    assert.equal(isTargetDoc('docs/specs/019-mvp-completion/plan.md'), true)
    assert.equal(isTargetDoc('docs/specs/mvp.md'), true)
    assert.equal(isTargetDoc('docs/specs/archive/mvp/002-cart-and-order/spec.md'), false)
    assert.equal(isTargetDoc('frontend/store/README.md'), false)
  })

  test('言い換える前の語を例に挙げる、言い回しの規則の文書は見ない', () => {
    assert.equal(isTargetDoc('.claude/skills/docs/tech-writing.md'), false)
  })

  test('.github の yml（issue のテンプレート、workflow、action、dependabot）と Markdown を見る', () => {
    assert.equal(isTargetDoc('.github/ISSUE_TEMPLATE/bug.yml'), true)
    assert.equal(isTargetDoc('.github/workflows/ci.yml'), true)
    assert.equal(isTargetDoc('.github/actions/setup-frontend/action.yml'), true)
    assert.equal(isTargetDoc('.github/dependabot.yml'), true)
    assert.equal(isTargetDoc('.github/pull_request_template.md'), true)
    assert.equal(isTargetDoc('frontend/store/.github/workflows/ci.yml'), false)
  })
})
