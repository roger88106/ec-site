// script（scripts/ の下と frontend/shared/scripts/ の下）が直接実行されたか（import されたのでないか）の判定。
// 画面の API の生成（frontend/shared/scripts/）も相対パスで読むので、node の標準のモジュールの他を import しない。
import { realpathSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 2 つのパスが同じファイルを指すか。Windows ではドライブ文字の大小と区切り（\ と /）を区別しない。
 *
 * @param {string} a パス
 * @param {string} b パス
 * @param {boolean} [caseInsensitive] 大小を区別しないか。なければ実行している OS で決める
 * @returns {boolean} 同じなら true
 */
export function samePath(a, b, caseInsensitive = process.platform === 'win32') {
  const normalize = (value) => (caseInsensitive ? path.win32.normalize(value).toLowerCase() : path.normalize(value))
  return normalize(a) === normalize(b)
}

/**
 * パスのシンボリックリンクを解決する。解決できなければ（ファイルがないなど）そのまま返す。
 *
 * @param {string} value パス
 * @returns {string} 解決したパス
 */
function resolveLink(value) {
  try {
    return realpathSync(value)
  } catch {
    return value
  }
}

/**
 * この script が直接実行されたか。`isMain(import.meta.url)` の形で使う。
 * シンボリックリンク経由で実行されても同じファイルなら true にするため、両方を実体のパスにして比べる。
 *
 * @param {string} metaUrl 呼び出した script の `import.meta.url`
 * @param {string} [entry] 実行された script のパス。なければ `process.argv[1]`
 * @returns {boolean} 直接実行されたなら true
 */
export function isMain(metaUrl, entry = process.argv[1]) {
  if (!entry) return false
  const self = fileURLToPath(metaUrl)
  return samePath(path.resolve(entry), self) || samePath(resolveLink(path.resolve(entry)), resolveLink(self))
}
