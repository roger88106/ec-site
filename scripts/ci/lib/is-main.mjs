// 検査の script が直接実行されたか（import されたのでないか）の判定。全ての検査が使う
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
 * この script が直接実行されたか。`isMain(import.meta.url)` の形で使う。
 *
 * @param {string} metaUrl 呼び出した script の `import.meta.url`
 * @param {string} [entry] 実行された script のパス。なければ `process.argv[1]`
 * @returns {boolean} 直接実行されたなら true
 */
export function isMain(metaUrl, entry = process.argv[1]) {
  return Boolean(entry) && samePath(path.resolve(entry), fileURLToPath(metaUrl))
}
