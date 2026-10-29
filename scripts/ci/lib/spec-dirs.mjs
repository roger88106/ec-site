// spec のディレクトリを探す。spec は docs/08-spec/ の直下（進行中）か、docs/08-spec/archive/<まとまり>/ の下（完了してアーカイブした）にある。
// ac-check.mjs、progress.mjs、scripts/dev/showcase-counts.mjs が使う
import { existsSync, readdirSync } from 'node:fs'
import path from 'node:path'

/** spec のディレクトリの名前（<番号>-名前）。1→10 の spec は 0 で埋めない番号（7-dev-process）、MVP は 3 桁の 0 埋め（011-dev-foundation） */
const SPEC_DIR = /^\d+-/

/** アーカイブのディレクトリ（docs/08-spec/ から） */
const ARCHIVE = 'archive'

/**
 * spec のディレクトリを、直下とアーカイブのまとまりの下から探す。
 *
 * @param {string} specsDir docs/08-spec のパス
 * @returns {{ name: string, rel: string, dir: string }[]} 名前、docs/08-spec からの相対パス（/ 区切り）、絶対パス。番号の順
 */
export function listSpecDirs(specsDir) {
  const parents = ['']
  const archiveDir = path.join(specsDir, ARCHIVE)
  if (existsSync(archiveDir)) {
    for (const group of readdirSync(archiveDir, { withFileTypes: true }).filter((entry) => entry.isDirectory())) {
      parents.push(`${ARCHIVE}/${group.name}`)
    }
  }
  return parents
    .flatMap((parent) =>
      readdirSync(path.join(specsDir, parent), { withFileTypes: true })
        .filter((entry) => entry.isDirectory() && SPEC_DIR.test(entry.name))
        .map((entry) => {
          const rel = parent ? `${parent}/${entry.name}` : entry.name
          return { name: entry.name, rel, dir: path.join(specsDir, rel) }
        }),
    )
    .sort((a, b) => a.name.localeCompare(b.name, 'en', { numeric: true }))
}

/**
 * MVP の完成までの一覧を探す。直下になければ、アーカイブのまとまりの下から探す。
 *
 * @param {string} specsDir docs/08-spec のパス
 * @returns {string | null} 一覧のパス。なければ null
 */
export function findMvpList(specsDir) {
  const direct = path.join(specsDir, 'mvp.md')
  if (existsSync(direct)) return direct
  const archiveDir = path.join(specsDir, ARCHIVE)
  if (!existsSync(archiveDir)) return null
  for (const group of readdirSync(archiveDir, { withFileTypes: true }).filter((entry) => entry.isDirectory())) {
    const archived = path.join(archiveDir, group.name, 'mvp.md')
    if (existsSync(archived)) return archived
  }
  return null
}
