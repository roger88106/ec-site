// スロットが対象に付ける環境変数の名前（E2E の入口が、引き継がないように除くために使う）
import { slotEnv } from './slot.mjs'

/**
 * スロットが付ける環境変数の名前を全て返す（backend・store・admin のキーの和）。
 * @returns {string[]}
 */
export function slotEnvNames() {
  const names = new Set()
  for (const target of /** @type {const} */ (['backend', 'store', 'admin'])) {
    for (const key of Object.keys(slotEnv(1, target))) names.add(key)
  }
  return [...names]
}
