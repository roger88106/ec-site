// 変更の危険度（高か低）を、develop との差分のファイルと、差分に含まれる spec.md の宣言から判定する。
// - 経路：差分のファイルが、危険度が高い経路の一覧（scripts/shared/risk.json）のどれかの下にあれば高。テストのファイルも経路で判定する。
//   経路とファイルは小文字にしてから比べる（Windows と macOS では、大文字の違うパスが同じ所に入るため）
// - spec の宣言：差分に含まれる spec.md（アーカイブへの移動を含む）の冒頭に「危険度：高」の行があれば高。読み方は scripts/shared/risk-rules.mjs の findDeclaredSpecs
//
// 判定と、当たった経路と、宣言した spec を、pull request の本文の「## 危険度」の節に貼れる数行で出す。
// --body-file を渡すと、本文の「判定：」の値と比べ、本文が低で判定が高のとき、値が空・節がないときに失敗にする。
// 本文が高で判定が低のときは成功にする（規模 S の issue で高とした変更は、本文で高にするため）。
// Dependabot の pull request（--author が dependabot[bot]。ブランチ名は誰でも付けられるので見分けに使わない）は、本文と比べず、
// --title の題から読む更新の種類（judgeDependabotTitle）と、経路の判定の高い方にする。
//
// 差分の基準は、HEAD と基準の ref（既定は origin/develop）の分岐点。手元では、commit していない変更と、追跡していない無視されないファイルも差分に含める。
// 引数なし（check-all.sh から）は、本文と比べずに判定だけを表示する。分岐点を求められないとき（shallow clone を含む）と、
// risk.json の経路に当たるファイルがないとき（書き間違い、置き場所の移動。--skip-path-check で外す）は失敗にする。
//
// CI（.github/workflows/risk.yml）は、基準の側の risk.mjs と risk.json でも実行する（理由は risk.yml の冒頭）。基準の側には --skip-path-check を付ける。
//   一覧の経路に当たるファイルがあるかは変えた後のバージョンで確認すればよく、基準の一覧の経路を消した変更で失敗にしないため。
// risk.yml そのものを書き換える変更は、基準の側でも実行する仕組みでは防げない。
//
// 失敗したとき：本文の「判定：」を script の出力に合わせて修正する（出力を貼り替える）。経路の誤りは risk.json を修正する。
//
// 除外：なし。
//
// 使い方：リポジトリのルートで node scripts/ci/config/risk.mjs [--base <ref>] [--body-file <ファイル。- は標準入力>] [--author <作成者>] [--title <題>] [--skip-path-check]
import { existsSync, readFileSync } from 'node:fs'
import { parseArgs } from 'node:util'
import { listGitFiles, splitNulList } from '../../shared/git/git-files.mjs'
import { tryGit } from '../lib/git/merge-base.mjs'
import { applyDependabotTitle, checkPullRequest, findMissingPaths, formatJudgement, isDependabot, judgeRisk, parseRiskConfig } from '../../shared/risk-rules.mjs'
import { isShallowRepository, SHALLOW_CLONE_MESSAGE } from '../lib/git/shallow-clone.mjs'
import { describeUnreadableLine } from '../lib/source/unreadable-line.mjs'

/** 危険度が高い経路の一覧 */
const CONFIG_FILE = new URL('../../shared/risk.json', import.meta.url)

/** 差分の基準の既定の ref */
const DEFAULT_BASE = 'origin/develop'

/**
 * 差分のファイルを返す。基準は HEAD と ref の分岐点。改名は、元と先の両方のパスにする（危険度が高い経路から出す改名も当てるため）。
 *
 * 手元で commit の前に確認できるよう、作業ツリーの変更と、追跡していない無視されないファイルも含める。
 *
 * @param {string} ref 基準の ref
 * @param {string[]} gitFiles Git の対象のファイル（追跡していない無視されないファイルを含む）
 * @param {string} [cwd] 実行する場所（リポジトリのルート）。なければ今のディレクトリ
 * @returns {{ files: string[], base: string } | { error: string }} 差分のファイル（名前の順）と分岐点の commit か、求められない理由
 */
export function listChangedFiles(ref, gitFiles, cwd = process.cwd()) {
  const base = tryGit(['merge-base', 'HEAD', ref], cwd)
  if (!base) {
    if (isShallowRepository(cwd)) return { error: SHALLOW_CLONE_MESSAGE }
    return { error: `HEAD と ${ref} の分岐点を求められない（ref があるか、git fetch したかを確認する）` }
  }
  // NUL 区切りの一覧は、先頭のファイル名の空白を除かないよう、そのまま受け取る
  const changed = tryGit(['diff', '--name-only', '-z', '--no-renames', base], cwd, { trim: false })
  const committed = tryGit(['ls-tree', '-r', '-z', '--name-only', 'HEAD'], cwd, { trim: false })
  if (changed === null || committed === null) return { error: `${ref} との差分を読めない` }
  // HEAD にないファイルは、git add したもの（diff に入る）か、追跡していないもの
  const inHead = new Set(splitNulList(committed))
  const added = gitFiles.filter((file) => !inHead.has(file))
  return { files: [...new Set([...splitNulList(changed), ...added])].sort(), base }
}

/**
 * 基準のバージョンのファイルの中身を返す関数を作る。
 *
 * @param {string} base 基準の commit
 * @param {string} [cwd] 実行する場所（リポジトリのルート）。なければ今のディレクトリ
 * @returns {(file: string) => string | null} 中身を返す関数（基準にないファイルは null）
 */
export function baseFileReader(base, cwd = process.cwd()) {
  return (file) => tryGit(['show', `${base}:${file}`], cwd, { trim: false })
}

if (import.meta.main) {
  let values
  try {
    ;({ values } = parseArgs({
      args: process.argv.slice(2),
      options: {
        base: { type: 'string', default: DEFAULT_BASE },
        'body-file': { type: 'string' },
        author: { type: 'string' },
        title: { type: 'string' },
        'skip-path-check': { type: 'boolean', default: false },
      },
    }))
  } catch (e) {
    console.log(`引数を読めない：${e.message}`)
    process.exit(2)
  }
  /** 読むファイルがなければ、理由を出して終了コード 2 で終わる */
  const readRequired = (file) => {
    try {
      return readFileSync(file, 'utf8')
    } catch (e) {
      console.log(`${file} を読めない：${e.code ?? e.message}`)
      process.exit(2)
    }
  }
  const config = parseRiskConfig(readRequired(CONFIG_FILE))
  const gitFiles = listGitFiles()
  const missing = values['skip-path-check'] ? [] : findMissingPaths(config, gitFiles)
  if (missing.length > 0) {
    for (const pattern of missing) console.log(`risk.json の経路に当たるファイルがない（書き間違いか、置き場所が変わった）：${pattern}`)
    console.log(`当たるファイルのない経路：${missing.length}`)
    process.exit(1)
  }
  const changed = listChangedFiles(values.base, gitFiles)
  if ('error' in changed) {
    console.log(changed.error)
    process.exit(2)
  }
  const judged = judgeRisk(changed.files, config, (file) => (existsSync(file) ? readFileSync(file, 'utf8') : null), baseFileReader(changed.base))
  const result = isDependabot(values) ? applyDependabotTitle(judged, values.title) : judged
  const lines = formatJudgement(result)
  for (const line of lines.slice(0, -1)) console.log(line)
  for (const item of result.unreadable) console.log(describeUnreadableLine(item))
  // - は標準入力（CI は本文を環境変数から標準入力に渡す）
  const bodyFile = values['body-file']
  const body = bodyFile === undefined ? undefined : readRequired(bodyFile === '-' ? 0 : bodyFile)
  const checked = checkPullRequest({ level: result.level, body, author: values.author })
  if (checked.message !== null) console.log(checked.message)
  console.log(lines.at(-1))
  process.exit(checked.ok && result.unreadable.length === 0 ? 0 : 1)
}
