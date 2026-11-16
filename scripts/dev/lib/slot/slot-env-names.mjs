// スロットが対象に付ける環境変数の名前
import { slotEnv } from './slot.mjs'

/**
 * スロットが付ける環境変数の名前を全て返す（backend・store・admin のキーの和）。
 *
 * @returns {string[]}
 */
export function slotEnvNames() {
  const names = new Set()
  // スロット 0 は何も上書きしないので、名前は 0 以外のスロットで求める
  for (const target of /** @type {const} */ (['backend', 'store', 'admin'])) {
    for (const key of Object.keys(slotEnv(1, target))) names.add(key)
  }
  return [...names]
}
