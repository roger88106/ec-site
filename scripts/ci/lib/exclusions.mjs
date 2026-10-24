// 検査の除外の一覧（<検査>-exclusions.json）を読み、理由のない除外と不要になった除外を見つける。
// 除外の一覧の形は、全ての検査で {キー: 理由}。キーが何を指すか（AC 番号、ファイルのパス、識別子など）は検査が決める。
// ac-check、frontend-rules、glossary-ids、hardcoded-text、message-numbers、related-specs、time-waits が使う
import { readFileSync } from 'node:fs'

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
  return [
    ...findEmptyReasons(exclusions).map((key) => `${name}：${key} の理由がない（除外には理由を書く）`),
    ...findStaleExclusions(exclusions, isNeeded).map((key) => `${name}：${key} は不要になった除外（一覧から消す）`),
  ]
}
