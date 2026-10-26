// 適用済みのマイグレーション（バージョン付きの V*.sql）を、追加した後に変えていないことを確認する。
// Flyway は適用済みのマイグレーションの checksum を確認するので、コメントだけの変更でも、
// 既に適用した DB で起動に失敗する（バックエンドの書き方、コメントの書き方 第 1 節）。
// 新しい DB で動くテストと CI では見つからないので、ここで確認する。
// 基準は、HEAD と develop・main（origin/ の方を先に見る）のそれぞれとの分岐点の tree。基準にあるものが今の index にない（削除、改名）か、中身が違えば失敗にする。
// まだ develop にも main にも入っていない自分のブランチで加えたもの（分岐の後に加えたもの）は、番号の振り直しも中身の変更も通す。どの DB にも適用されておらず、checksum が残っていないため。
// マイグレーションが 0 件のときも失敗にする。分岐点を求めるので、shallow clone では失敗する。
// もう 1 つ、新しいマイグレーションが、前のリリースのアプリが動き続けられない変更（表・列の削除と改名、列の型の変更、既存の列を NOT NULL にする、既定値のない NOT NULL の列の追加。バックエンドの書き方 第 8.1 節）を含むと失敗にする。
// 例外は migrations-exclusions.json に理由を書く。キーは `<マイグレーションのファイル名>:<変更の種類>`（種類は INCOMPATIBLE_KINDS の値）。同じファイルの同じ種類の変更全てに効く。
// 理由のない除外と、当たる変更がなくなった除外も失敗にする。SQL の字面で探すので、読み落としより誤検出の側に倒す（誤検出は除外で理由を残す）。
// 閉じていない文字列・コメントは読めないので、失敗にする（除外できない）。
// 使い方：node scripts/ci/migrations.mjs（リポジトリのルートで）
import { execFileSync } from 'node:child_process'
import { checkExclusions, exclusionsPath, readExclusions } from './lib/exclusions.mjs'
import { emptyListReason, splitNulList } from './lib/git-files.mjs'
import { isMain } from './lib/is-main.mjs'
import { createLineLocator } from './lib/line-number.mjs'
import { exitIfShallow } from './lib/shallow-clone.mjs'
import { describeUnreadableLine } from './lib/unreadable-line.mjs'

/** マイグレーションの置き場所 */
const MIGRATIONS = 'backend/ecsite/src/main/resources/db/migration/'

/**
 * バージョン付きのマイグレーションかを返す。繰り返しのもの（R__）は、変えると適用し直されるので対象外。
 *
 * @param {string} file リポジトリのルートからのパス
 * @returns {boolean} バージョン付きなら true
 */
export function isVersionedMigration(file) {
  return /(^|\/)V[^/]*__[^/]*\.sql$/.test(file)
}

/**
 * 基準と今で中身が違うマイグレーションを返す。改行の違いは無視する。
 *
 * @param {string[]} files マイグレーション
 * @param {(file: string) => string | null} readBase 基準の中身。基準になければ null（新しいものなので見ない）
 * @param {(file: string) => string} readNow 今の中身
 * @returns {string[]} 変わったマイグレーション
 */
export function findChangedMigrations(files, readBase, readNow) {
  const normalize = (text) => text.replace(/\r\n/g, '\n')
  return files.filter((file) => {
    const base = readBase(file)
    return base !== null && normalize(base) !== normalize(readNow(file))
  })
}

/**
 * マイグレーションが 1 件もないときの失敗の理由を返す。文は他の検査と同じ共通の関数（emptyListReason）で作り、置き場所を添える。
 *
 * @param {string[]} files 今あるバージョン付きのマイグレーション
 * @param {string} dir マイグレーションの置き場所
 * @returns {string | null} 0 件なら失敗の理由、あれば null
 */
export function migrationsEmptyReason(files, dir) {
  return emptyListReason(files, `マイグレーション（${dir}）`)
}

/**
 * 基準にあって、今はないマイグレーション（削除と改名。改名は古い名前が消える）を返す。
 *
 * @param {Iterable<string>} baseFiles 基準にあるマイグレーション
 * @param {string[]} currentFiles 今あるマイグレーション
 * @returns {string[]} 今はないマイグレーション
 */
export function findRemovedMigrations(baseFiles, currentFiles) {
  const current = new Set(currentFiles)
  return [...baseFiles].filter((file) => !current.has(file))
}

/**
 * 互換性の検査の対象にしない、今あるマイグレーションの最大のバージョン。これ以下は対象外にする。
 * 今あるマイグレーションは本番に適用しておらず（本番の環境がまだない）、互換性を問わない。
 * 番号の上限を検査の中の定数にするのは、ファイルごとに印や除外の一覧を持つより単純で、新しいマイグレーションは
 * 必ずこれより大きいバージョンになるため（Flyway は最大のバージョンの後ろに加える）。
 * 本番に適用した後は、この定数を上げずに、除外の一覧に理由を書く。
 */
export const LEGACY_MAX_VERSION = 202610211600n

/**
 * マイグレーションのファイル名から、バージョンの数を返す。
 *
 * @param {string} file リポジトリのルートからのパス、またはファイル名（V<数>__<名前>.sql）
 * @returns {bigint | null} バージョン。数でなければ null
 */
export function migrationVersion(file) {
  const match = /(?:^|\/)V(\d+)__[^/]*\.sql$/.exec(file)
  return match ? BigInt(match[1]) : null
}

/**
 * 互換性の検査の対象（今あるマイグレーションより新しいもの）かを返す。
 *
 * @param {string} file リポジトリのルートからのパス、またはファイル名
 * @returns {boolean} バージョンが LEGACY_MAX_VERSION より大きければ true。バージョンが数でないもの（V1_1__ など）は、見落とさないよう true
 */
export function isCheckedForCompatibility(file) {
  const version = migrationVersion(file)
  return version === null || version > LEGACY_MAX_VERSION
}

/** 互換性のない変更の種類。除外のキーの後ろに使う */
export const INCOMPATIBLE_KINDS = {
  dropTable: '表の削除',
  dropColumn: '列の削除',
  renameTable: '表の改名',
  renameColumn: '列の改名',
  changeType: '列の型の変更',
  setNotNull: '既存の列を NOT NULL にする',
  addNotNullWithoutDefault: '既定値のない NOT NULL の列の追加',
}

/**
 * SQL のコメントと文字列の中身を、同じ長さの空白（改行は残す）にする。位置と行番号は変わらない。<br />
 * 行コメント（--）、入れ子にできるブロックコメント、文字列（'' の重ね書き、E'...' の \ のエスケープ）、引用符付きの識別子（中は _ にする）を読む。
 * ドル引用（$$...$$）の中は、DO ブロックなどで実際に実行される文を書くので、コードとして読む。
 *
 * @param {string} sql マイグレーションの中身
 * @returns {{ code: string, unreadable: { index: number, reason: string }[] }} コメントと文字列を除いた SQL と、閉じていないもの（位置と理由）
 */
export function blankSqlNonCode(sql) {
  const out = sql.split('')
  const unreadable = []
  const blank = (from, to, fill = ' ') => {
    for (let i = from; i < to; i += 1) if (out[i] !== '\n' && out[i] !== '\r') out[i] = fill
  }
  let i = 0
  while (i < sql.length) {
    if (sql.startsWith('--', i)) {
      const end = sql.indexOf('\n', i)
      const to = end === -1 ? sql.length : end
      blank(i, to)
      i = to
    } else if (sql.startsWith('/*', i)) {
      let depth = 1
      let j = i + 2
      while (j < sql.length && depth > 0) {
        if (sql.startsWith('/*', j)) {
          depth += 1
          j += 2
        } else if (sql.startsWith('*/', j)) {
          depth -= 1
          j += 2
        } else {
          j += 1
        }
      }
      if (depth > 0) unreadable.push({ index: i, reason: '閉じていないブロックコメント' })
      blank(i, j)
      i = j
    } else if (sql[i] === "'" || sql[i] === '"') {
      const quote = sql[i]
      const escapes = quote === "'" && /[eE]/.test(sql[i - 1] ?? '') && !/[\w$]/.test(sql[i - 2] ?? '')
      let j = i + 1
      let closed = false
      while (j < sql.length) {
        if (escapes && sql[j] === '\\') {
          j += 2
        } else if (sql[j] === quote && sql[j + 1] === quote) {
          j += 2
        } else if (sql[j] === quote) {
          closed = true
          j += 1
          break
        } else {
          j += 1
        }
      }
      if (!closed) unreadable.push({ index: i, reason: quote === "'" ? '閉じていない文字列' : '閉じていない引用符付きの識別子' })
      const end = Math.min(j, sql.length)
      blank(i + 1, closed ? end - 1 : end, quote === '"' ? '_' : ' ')
      i = end
    } else {
      i += 1
    }
  }
  return { code: out.join(''), unreadable }
}

/**
 * ALTER TABLE の後ろの変更（カンマで区切る。括弧の中のカンマは区切りにしない）を分ける。
 *
 * @param {string} body ALTER TABLE の表の名前の後ろ
 * @returns {string[]} 変更ごとの文字列（前後の空白を除く。空は除く）
 */
function splitActions(body) {
  const actions = []
  let depth = 0
  let from = 0
  for (let i = 0; i < body.length; i += 1) {
    if (body[i] === '(') depth += 1
    else if (body[i] === ')') depth -= 1
    else if (body[i] === ',' && depth === 0) {
      actions.push(body.slice(from, i).trim())
      from = i + 1
    }
  }
  actions.push(body.slice(from).trim())
  return actions.filter((action) => action !== '')
}

/**
 * ALTER TABLE の 1 つの変更が、互換性のない変更なら、その種類を返す。
 *
 * @param {string} action 大文字にして空白を 1 つにした変更（例：DROP COLUMN IF EXISTS X）
 * @returns {string | null} INCOMPATIBLE_KINDS の値。なければ null
 */
function classifyAction(action) {
  if (/^DROP\s+(?!CONSTRAINT\b)/.test(action)) return INCOMPATIBLE_KINDS.dropColumn
  if (/^RENAME\s+TO\b/.test(action)) return INCOMPATIBLE_KINDS.renameTable
  if (/^RENAME\s+(?!CONSTRAINT\b)/.test(action)) return INCOMPATIBLE_KINDS.renameColumn
  if (/^ALTER\s+(?:COLUMN\s+)?\S+\s+(?:SET\s+DATA\s+)?TYPE\b/.test(action)) return INCOMPATIBLE_KINDS.changeType
  if (/^ALTER\s+(?:COLUMN\s+)?\S+\s+SET\s+NOT\s+NULL\b/.test(action)) return INCOMPATIBLE_KINDS.setNotNull
  const isAddColumn = /^ADD\s+/.test(action) && !/^ADD\s+(?:CONSTRAINT|PRIMARY|FOREIGN|UNIQUE|CHECK|EXCLUDE|LIKE)\b/.test(action)
  if (isAddColumn && /\bNOT\s+NULL\b/.test(action) && !/\bDEFAULT\b/.test(action)) return INCOMPATIBLE_KINDS.addNotNullWithoutDefault
  return null
}

/**
 * マイグレーションの SQL から、互換性のない変更を探す。コメントと文字列の中は読まない。
 * 文は ; で分け、DO ブロックの中の文も、文の途中の ALTER TABLE・DROP TABLE として読む。
 *
 * @param {string} sql マイグレーションの中身
 * @returns {{ found: { line: number, kind: string, text: string }[], unreadable: { line: number, text: string, reason: string }[] }} 見つけた変更（文の順）と、読めなかった所
 */
export function findIncompatibleStatements(sql) {
  const lineOf = createLineLocator(sql)
  const lines = sql.split('\n')
  const { code, unreadable } = blankSqlNonCode(sql)
  const found = []
  const add = (index, kind) => {
    const line = lineOf(index)
    found.push({ line, kind, text: lines[line - 1].trim() })
  }
  let offset = 0
  for (const piece of code.split(';')) {
    for (const match of piece.matchAll(/\bDROP\s+(?:TABLE|SCHEMA)\b/gi)) add(offset + match.index, INCOMPATIBLE_KINDS.dropTable)
    for (const match of piece.matchAll(/\bALTER\s+SCHEMA\s+\S+\s+RENAME\s+TO\b/gi)) add(offset + match.index, INCOMPATIBLE_KINDS.renameTable)
    const alter = /\bALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?(?:ONLY\s+)?\S+\s*\*?\s*/i.exec(piece)
    if (alter) {
      const body = piece.slice(alter.index + alter[0].length)
      for (const action of splitActions(body)) {
        const kind = classifyAction(action.replace(/\s+/g, ' ').toUpperCase())
        if (kind) add(offset + alter.index, kind)
      }
    }
    offset += piece.length + 1
  }
  return {
    found,
    unreadable: unreadable.map(({ index, reason }) => ({ line: lineOf(index), text: lines[lineOf(index) - 1], reason })),
  }
}

/**
 * 除外のキーを作る。
 *
 * @param {string} file マイグレーションのパスまたはファイル名
 * @param {string} kind 変更の種類（INCOMPATIBLE_KINDS の値）
 * @returns {string} `<ファイル名>:<種類>`
 */
export function exclusionKey(file, kind) {
  return `${file.split('/').at(-1)}:${kind}`
}

/**
 * 新しいマイグレーションの互換性のない変更を、除外を引いて集める。除外は、同じファイルの同じ種類の変更全てに効く。
 *
 * @param {string[]} files マイグレーション（互換性の検査の対象かは、ここで見る）
 * @param {(file: string) => string} read 中身を読む関数
 * @param {Record<string, unknown>} exclusions 除外の一覧
 * @returns {{ found: { file: string, line: number, kind: string, text: string }[], unreadable: { file: string, line: number, text: string, reason: string }[], used: Set<string> }} 除外に当たらない変更、読めなかった所、当たった除外のキー
 */
export function scanMigrations(files, read, exclusions) {
  const found = []
  const unreadable = []
  const used = new Set()
  for (const file of files.filter((name) => isCheckedForCompatibility(name))) {
    const result = findIncompatibleStatements(read(file))
    unreadable.push(...result.unreadable.map((item) => ({ file, ...item })))
    for (const hit of result.found) {
      const key = exclusionKey(file, hit.kind)
      if (key in exclusions) used.add(key)
      else found.push({ file, ...hit })
    }
  }
  return { found, unreadable, used }
}

/** git を実行し、失敗したら null を返す */
function tryGit(args, cwd) {
  try {
    return execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
  } catch {
    return null
  }
}

/**
 * 守る基準（どこかの DB に適用されうる状態）の commit を決める。<br />
 * まだ develop にも main にも入っていない自分のブランチのマイグレーションは、どの DB にも適用されていないので、振り直してよい。
 * main に入ったもの（hotfix を含む）は、リリースに出るので、develop に入っていなくても守る。
 * そのため、HEAD と develop・main のそれぞれ（CI の origin/ の方を先に見る）との merge-base を基準にする。
 * 履歴の commit（追加と改名）でなく tree を比べるので、自分のブランチの追加と改名が merge commit で develop の履歴に入っても、失敗にならない。
 * merge-base が HEAD 自身（develop の上で実行する、push の CI）のときは、HEAD と、HEAD の 1 つ目の親（マージの前の develop）を基準にする。
 * どの基準も求められないとき（ref がない、履歴が繋がらない）は、HEAD の 1 つ目の親を基準にし、警告を出す。
 *
 * @param {string} [cwd] 実行する場所（リポジトリの中）。なければ今のディレクトリ
 * @returns {{ revisions: string[], bases: string[] }} 基準の commit（重複なし。初めの commit で親がなければ空）と、使った ref（なければ空）
 */
export function resolveBaseRevisions(cwd = process.cwd()) {
  const head = tryGit(['rev-parse', '--verify', '-q', 'HEAD'], cwd)
  if (!head) return { revisions: [], bases: [] }
  const parent = tryGit(['rev-parse', '--verify', '-q', 'HEAD^1'], cwd)
  const revisions = new Set()
  const bases = []
  for (const branch of ['develop', 'main']) {
    for (const base of [`origin/${branch}`, branch]) {
      const ref = (base.includes('/') ? 'refs/remotes/' : 'refs/heads/') + base
      if (!tryGit(['rev-parse', '--verify', '-q', ref], cwd)) continue
      const revision = tryGit(['merge-base', 'HEAD', ref], cwd)
      if (!revision) continue
      revisions.add(revision)
      if (revision === head && parent) revisions.add(parent)
      bases.push(base)
      break
    }
  }
  if (bases.length === 0 && parent) revisions.add(parent)
  return { revisions: [...revisions], bases }
}

/**
 * git ls-tree -r -z と git ls-files -s -z の出力から、パスごとの blob を読む。
 *
 * @param {string} output NUL 区切りの出力（各項目は「空白で区切った属性、タブ、パス」）
 * @param {number} column 属性を空白で分けたときの blob の位置
 * @returns {Map<string, string>} パス → blob
 */
export function parseBlobList(output, column) {
  const blobs = new Map()
  for (const entry of splitNulList(output)) {
    const tab = entry.indexOf('\t')
    blobs.set(entry.slice(tab + 1), entry.slice(0, tab).split(' ')[column])
  }
  return blobs
}

if (isMain(import.meta.url)) {
  exitIfShallow()
  const git = (args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  // 今の index（commit の前の hook では次の commit。ステージした削除・改名・変更を含む）
  const current = parseBlobList(git(['ls-files', '-s', '-z', '--', MIGRATIONS]), 1)
  const files = [...current.keys()].filter(isVersionedMigration)
  const emptyReason = migrationsEmptyReason(files, MIGRATIONS)
  if (emptyReason) {
    console.log(emptyReason)
    process.exit(1)
  }
  const { revisions, bases } = resolveBaseRevisions()
  if (bases.length === 0) {
    console.log('警告：develop と main（と origin/ の方）がどれもない（または履歴が繋がらない）ので、HEAD の 1 つ目の親を基準にする（初めの commit なら全てを新しいものとみなす）')
  }
  const removed = new Set()
  const changed = new Set()
  for (const revision of revisions) {
    const base = parseBlobList(git(['ls-tree', '-r', '-z', revision, '--', MIGRATIONS]), 2)
    const baseFiles = [...base.keys()].filter(isVersionedMigration)
    findRemovedMigrations(baseFiles, files).forEach((file) => removed.add(file))
    // blob が同じものは読まない。違うものだけ中身を読み、改行の違いを無視して比べる
    const differing = files.filter((file) => base.has(file) && base.get(file) !== current.get(file))
    const readBlob = (blob) => git(['cat-file', 'blob', blob])
    findChangedMigrations(differing, (file) => readBlob(base.get(file)), (file) => readBlob(current.get(file))).forEach((file) => changed.add(file))
  }
  const exclusions = readExclusions(exclusionsPath('migrations-exclusions.json'))
  const scan = scanMigrations(files, (file) => git(['cat-file', 'blob', current.get(file)]), exclusions)
  const exclusionProblems = checkExclusions('migrations-exclusions.json', exclusions, (key) => scan.used.has(key))
  for (const file of removed) {
    console.log(`追加した後に削除か改名された（元に戻す）：${file}`)
  }
  for (const file of changed) {
    console.log(`追加した後に変わった（元に戻し、変更は新しいマイグレーションで行う）：${file}`)
  }
  for (const problem of exclusionProblems) console.log(problem)
  for (const item of scan.unreadable) console.log(describeUnreadableLine(item))
  for (const hit of scan.found) {
    console.log(`${hit.file}:${hit.line} ${hit.text}（${hit.kind}。前のリリースのアプリが動き続けられない。3 段に分ける。段 3 の削除などは migrations-exclusions.json に理由を書く）`)
  }
  const problems = scan.found.length + scan.unreadable.length + exclusionProblems.length
  console.log(`変わったマイグレーション：${changed.size}、削除か改名：${removed.size}、互換性のない変更：${scan.found.length}、解釈できない行：${scan.unreadable.length}、除外の誤り：${exclusionProblems.length}（${files.length} 件中）`)
  process.exit(changed.size || removed.size || problems ? 1 : 0)
}
