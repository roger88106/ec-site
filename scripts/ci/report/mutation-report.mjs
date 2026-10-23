// ============================================================================
// PIT（mutation testing）の XML の報告（mutations.xml）を読む。
// mutation score は、検出した変異（detected="true"。KILLED と TIMED_OUT）の数を、全変異の数で割った割合（PIT の定義。
// PIT のコンソールの「Killed」の数と一致する）。生き残った変異は detected="false"（SURVIVED と NO_COVERAGE）。
// ============================================================================

/** 生き残ったクラスを出す数 */
export const SURVIVORS_LIMIT = 10

/**
 * 報告が、PIT の XML か。JaCoCo の XML（<report>）と取り違えないために、ルートの要素で見分ける。
 *
 * @param {string} text XML の中身
 * @returns {boolean} PIT の XML なら true
 */
export function isPitReport(text) {
  return /<mutations[\s>]/.test(text)
}

/**
 * PIT の XML から、変異の数と、生き残った変異が多いクラスを読む。
 *
 * @param {string} text mutations.xml の中身
 * @param {{ limit?: number }} options クラスを出す数
 * @returns {{ total: number, detected: number, survivors: { name: string, survived: number, total: number }[] }}
 *   全変異の数、検出した数、生き残った変異が多いクラス（多い順。同じ数なら名前順）
 */
export function readMutations(text, { limit = SURVIVORS_LIMIT } = {}) {
  let total = 0
  let detected = 0
  const classes = new Map()
  for (const [, flag, body] of text.matchAll(/<mutation detected='(true|false)'[^>]*>([\s\S]*?)<\/mutation>/g)) {
    const name = body.match(/<mutatedClass>([^<]+)<\/mutatedClass>/)?.[1] ?? '(不明)'
    const row = classes.get(name) ?? { name, survived: 0, total: 0 }
    row.total++
    total++
    if (flag === 'true') detected++
    else row.survived++
    classes.set(name, row)
  }
  const survivors = [...classes.values()]
    .filter((row) => row.survived > 0)
    .sort((a, b) => b.survived - a.survived || a.name.localeCompare(b.name))
    .slice(0, limit)
  return { total, detected, survivors }
}
