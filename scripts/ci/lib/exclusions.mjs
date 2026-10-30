// 検査の除外の一覧（<検査>-exclusions.json）を読み、理由のない除外と不要になった除外を見つける。
// 除外の一覧の形は、全ての検査で {キー: 理由}。キーが何を指すか（AC 番号、ファイルのパス、識別子など）は検査が決める。
// ac-check、ja-text、frontend-rules、glossary-ids、hardcoded-text、message-numbers、migrations、related-specs、time-waits が使う（ja-text は、ja-text.json の許容用語の誤りの文だけに使う）。
// パスは exclusionsPath で求め、誤りの文は checkExclusions などで作る（検査が文を直接組み立てない）
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * 検査の除外の一覧のパスを返す。除外の一覧は scripts/ci/ の直下に置くので、どの検査も実行する場所に関わらず同じパスを得る。
 *
 * @param {string} name 除外の一覧のファイルの名前（例：time-waits-exclusions.json）
 * @returns {string} 絶対パス
 */
export function exclusionsPath(name) {
  return fileURLToPath(new URL(`../${name}`, import.meta.url))
}

/**
 * 除外の一覧を読む。
 *
 * @param {string} file 除外の一覧の JSON のパス
 * @returns {Record<string, unknown>} キーと理由（書いた順）。理由が文字列かは見ない（findEmptyReasons で見る）
 * @throws {Error} JSON として読めない、またはオブジェクトでないとき（ファイル名を添える）
 */
export function readExclusions(file) {
  let parsed
  try {
    parsed = JSON.parse(readFileSync(file, 'utf8'))
  } catch (error) {
    throw new Error(`${file}：除外の一覧を読めない（${error.message}）`, { cause: error })
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${file}：除外の一覧は {キー: 理由} の形で書く`)
  }
  return parsed
}

/**
 * 理由のない除外を探す。
 *
 * @param {Record<string, unknown>} exclusions 除外の一覧
 * @returns {string[]} 理由が文字列でない、または空白だけの除外のキー（書いた順）
 */
export function findEmptyReasons(exclusions) {
  return Object.entries(exclusions)
    .filter(([, reason]) => typeof reason !== 'string' || reason.trim() === '')
    .map(([key]) => key)
}

/**
 * 不要になった除外を探す。
 *
 * @param {Record<string, unknown>} exclusions 除外の一覧
 * @param {(key: string) => boolean} isNeeded そのキーの除外が今も必要か（除外がなければ違反になるか、など）を返す関数
 * @returns {string[]} 必要でなくなった除外のキー（書いた順）
 */
export function findStaleExclusions(exclusions, isNeeded) {
  return Object.keys(exclusions).filter((key) => !isNeeded(key))
}

/**
 * 除外の一覧の誤り（理由がない、不要になった）を、表示する文にして返す。
 *
 * @param {string} name 除外の一覧のファイルの名前（文に添える）
 * @param {Record<string, unknown>} exclusions 除外の一覧
 * @param {(key: string) => boolean} isNeeded そのキーの除外が今も必要かを返す関数
 * @returns {string[]} 誤りの文。理由のない除外、不要な除外の順。なければ空
 */
export function checkExclusions(name, exclusions, isNeeded) {
  const { emptyReasons, stale } = describeExclusionProblems(name, exclusions, isNeeded)
  return [...emptyReasons, ...stale]
}

/**
 * 除外の一覧の誤りを、理由がないものと不要になったものに分けて、表示する文にして返す。
 * 不要な除外だけを警告にしたい検査（手元と CI で扱いを変えるもの）が使う。
 *
 * @param {string} name 除外の一覧のファイルの名前（文に添える）
 * @param {Record<string, unknown>} exclusions 除外の一覧
 * @param {(key: string) => boolean} isNeeded そのキーの除外が今も必要かを返す関数
 * @returns {{ emptyReasons: string[], stale: string[] }} 理由のない除外の文と、不要な除外の文（どちらも書いた順）
 */
export function describeExclusionProblems(name, exclusions, isNeeded) {
  return {
    emptyReasons: findEmptyReasons(exclusions).map((key) => describeEmptyReason(name, key)),
    stale: findStaleExclusions(exclusions, isNeeded).map((key) => describeStaleExclusion(name, key)),
  }
}

/**
 * 理由のない除外の文を返す。
 *
 * @param {string} name 除外の一覧のファイルの名前
 * @param {string} key 除外のキー
 * @returns {string} 表示する文
 */
export function describeEmptyReason(name, key) {
  return `${name}：${key} の理由がない（除外には理由を書く）`
}

/**
 * 不要になった除外の文を返す。
 *
 * @param {string} name 除外の一覧のファイルの名前
 * @param {string} key 除外のキー
 * @returns {string} 表示する文
 */
export function describeStaleExclusion(name, key) {
  return `${name}：${key} は不要になった除外（一覧から消す）`
}
