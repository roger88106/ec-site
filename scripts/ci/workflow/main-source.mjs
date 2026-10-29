#!/usr/bin/env node
// ============================================================================
// main への pull request の元のブランチの判定（docs/06-手順書/branches.md 第 3.2 節）
//
//   使い方：node scripts/ci/workflow/main-source.mjs <元のブランチの名前>
//
// release- か hotfix- で始まる名前だけを通す。通さないときは終了コード 1 を返す。
// 引数が必要なので、check-all.sh が引数なしで流す scripts/ci/ の直下には置かない。
// ============================================================================
import { isMain } from '../lib/is-main.mjs'

/** main に入れられる元のブランチの名前の始まり */
const ALLOWED_PREFIXES = ['release-', 'hotfix-']

/** 元のブランチの名前が、main に入れられるものか */
export function isAllowedMainSource(headRef) {
  return typeof headRef === 'string' && ALLOWED_PREFIXES.some((prefix) => headRef.startsWith(prefix))
}

/** 判定し、結果と GitHub Actions に出す 1 行を返す */
export function checkMainSource(headRef) {
  if (isAllowedMainSource(headRef)) {
    return { ok: true, message: `元のブランチ ${headRef} は main に入れられる。` }
  }
  return {
    ok: false,
    message: `::error::main に入れられるのは release- か hotfix- で始まるブランチだけ。元のブランチ「${headRef ?? ''}」は入れられない。`,
  }
}

if (isMain(import.meta.url)) {
  const result = checkMainSource(process.argv[2])
  console.log(result.message)
  process.exit(result.ok ? 0 : 1)
}
