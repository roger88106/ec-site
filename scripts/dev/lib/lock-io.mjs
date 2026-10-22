// ロックのファイルの操作（slot-lock.mjs の LockIo を、ファイルシステムで実装する）。
// スロットの割り当てのロック（scripts/dev/slot.mjs）と E2E のロック（e2e/scripts/run.ts）が共有する。
import { randomUUID } from 'node:crypto'
import { linkSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'

/** Windows で他方が読んでいる間は退かせない。このときは退かさずに、判定からやり直す */
const MOVE_RETRY_CODES = ['ENOENT', 'EPERM', 'EBUSY', 'EACCES']

/**
 * ロックのファイルの操作を作る。<br />
 * 作成は、一時のファイルに中身を書いてから link で置く。書きかけ（空）のロックを他方に読まれず、link はロックがあれば原子的に失敗する。
 *
 * @param {string} lock ロックのファイルのパス
 * @param {{ writeFileSync: typeof writeFileSync, linkSync: typeof linkSync, renameSync: typeof renameSync,
 *   rmSync: typeof rmSync }} [fs] ファイルの操作。テストで差し替える
 * @returns {import('./slot-lock.mjs').LockIo}
 */
export function createLockIo(lock, fs = { writeFileSync, linkSync, renameSync, rmSync }) {
  /** 退かしたロックのパス */
  let aside = null
  const unique = () => `${lock}.${process.pid}.${randomUUID()}`
  return {
    create(content) {
      const temp = `${unique()}.tmp`
      fs.writeFileSync(temp, content, { flag: 'wx' })
      try {
        fs.linkSync(temp, lock)
        return true
      } catch (e) {
        if (e.code === 'EEXIST') return false
        throw e
      } finally {
        fs.rmSync(temp, { force: true })
      }
    },
    read() {
      try {
        return readFileSync(lock, 'utf8')
      } catch (e) {
        if (e.code === 'ENOENT') return null
        throw e
      }
    },
    moveAside() {
      aside = `${unique()}.stale`
      try {
        fs.renameSync(lock, aside)
      } catch (e) {
        aside = null
        if (MOVE_RETRY_CODES.includes(e.code)) return null
        throw e
      }
      return readFileSync(aside, 'utf8')
    },
    restore() {
      try {
        fs.linkSync(aside, lock)
      } catch (e) {
        if (e.code !== 'EEXIST') throw e
      } finally {
        fs.rmSync(aside, { force: true })
        aside = null
      }
    },
    discard() {
      fs.rmSync(aside, { force: true })
      aside = null
    },
  }
}
