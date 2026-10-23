// ============================================================================
// PIT（mutation testing）の XML の報告（mutations.xml）を読む。
// mutation score は、検出した変異の数を、全変異の数で割った割合。「検出した」は、PIT の定義に合わせて、
// 各 <mutation> の detected 属性が true のもの（KILLED と TIMED_OUT など）とする（PIT のコンソールの「Killed」の数と一致する）。
// 生き残った変異は、detected が false で、status が SURVIVED か NO_COVERAGE のもの
// （RUN_ERROR などは、全変異に数えるが、検出にも生き残りにも数えない）。
// 読み方は、属性の並びとクォート（' と "）に依存しない。途中で切れた XML、読めない変異がある XML は、読まずに失敗にする。
// ============================================================================

/** 生き残ったクラスを出す数 */
export const SURVIVORS_LIMIT = 10

/** 生き残りに数える status */
const SURVIVED_STATUSES = new Set(['SURVIVED', 'NO_COVERAGE'])

/**
 * 報告が、PIT の XML か。JaCoCo の XML（<report>）と取り違えないために、ルートの要素で見分ける。
 *
 * @param {string} text XML の中身
 * @returns {boolean} PIT の XML なら true
 */
export function isPitReport(text) {
  return /<mutations[\s>]/.test(text)
}

/** 開始タグの文字列から、属性の値を読む（クォートは ' も " も受ける） */
function attribute(tag, name) {
  return tag.match(new RegExp(`\\s${name}\\s*=\\s*(['"])(.*?)\\1`))?.[2]
}

/**
 * PIT の XML から、変異の数と、生き残った変異が多いクラスを読む。
 *
 * @param {string} text mutations.xml の中身
 * @param {{ limit?: number }} options クラスを出す数
 * @returns {{ total: number, detected: number, survivors: { name: string, survived: number, total: number }[] }}
 *   全変異の数、検出した数、生き残った変異が多いクラス（多い順。同じ数なら名前順）
 * @throws {Error} 閉じの </mutations> がない（途中で切れた）、または <mutation> の開始タグの数と読めた変異の数が違うとき
 */
export function readMutations(text, { limit = SURVIVORS_LIMIT } = {}) {
  if (!/<\/mutations\s*>/.test(text)) {
    throw new Error('PIT の報告が途中で切れています（閉じの </mutations> がありません）')
  }
  const starts = [...text.matchAll(/<mutation(?=[\s>/])([^>]*)>/g)]
  let total = 0
  let detected = 0
  const classes = new Map()
  for (const [i, start] of starts.entries()) {
    const detectedValue = attribute(start[0], 'detected')
    // 本文は次の <mutation の開始位置（なければ文書の終わり）までに限る。閉じタグが欠けた変異が、次の変異の中身を読まないため
    const section = text.slice(start.index + start[0].length, starts[i + 1]?.index ?? text.length)
    const end = section.indexOf('</mutation>')
    const body = end < 0 ? '' : section.slice(0, end)
    const name = body.match(/<mutatedClass>([^<]+)<\/mutatedClass>/)?.[1]
    if ((detectedValue !== 'true' && detectedValue !== 'false') || end < 0 || !name) continue
    total++
    const row = classes.get(name) ?? { name, survived: 0, total: 0 }
    row.total++
    if (detectedValue === 'true') detected++
    else if (SURVIVED_STATUSES.has(attribute(start[0], 'status'))) row.survived++
    classes.set(name, row)
  }
  if (total !== starts.length) {
    throw new Error(`PIT の報告の <mutation> が ${starts.length} 件あるが、読めたのは ${total} 件です（detected、</mutation>、<mutatedClass> のどれかがない変異があります）`)
  }
  const survivors = [...classes.values()]
    .filter((row) => row.survived > 0)
    .sort((a, b) => b.survived - a.survived || a.name.localeCompare(b.name))
    .slice(0, limit)
  return { total, detected, survivors }
}
