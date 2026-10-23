/** HTML の開始タグの属性（名前と値。値のない属性は空の文字） */
export type Attributes = Record<string, string>

/** 開始タグ 1 つ（引用符の中の `>` は、タグの終わりとみなさない） */
const START_TAG = /<([a-zA-Z][\w-]*)((?:[^>"]|"[^"]*")*)>/g

/** 属性 1 つ（`name="value"` か、値のない `name`） */
const ATTRIBUTE = /([^\s="/]+)(?:="([^"]*)")?/g

/** 属性の値の HTML の文字参照（描画が書き出すもの）と、元の文字 */
const CHARACTER_REFERENCES: Record<string, string> = {
  '&quot;': '"',
  '&#x27;': "'",
  '&lt;': '<',
  '&gt;': '>',
  '&amp;': '&',
}

/** 属性の値の文字参照を、元の文字に戻す（1 回の置き換えで戻すので、`&amp;lt;` は `&lt;` になる） */
function decode(value: string): string {
  return value.replace(/&(?:quot|#x27|lt|gt|amp);/g, (reference) => CHARACTER_REFERENCES[reference])
}

/**
 * HTML から、指定したタグの開始タグを全て取り出し、属性の対にする（店舗画面の構成 第 7 節）。<br />
 * 属性の並びに依存しない確認のために使う（属性の並びは、描画の実装で変わるため）。
 *
 * @param html 描画した HTML
 * @param tag  タグの名前（例：`input`）
 * @returns 開始タグごとの属性。HTML に出てくる順
 */
export function startTags(html: string, tag: string): Attributes[] {
  const found: Attributes[] = []
  for (const matched of html.matchAll(START_TAG)) {
    if (matched[1] !== tag) {
      continue
    }
    const attributes: Attributes = {}
    for (const attribute of matched[2].matchAll(ATTRIBUTE)) {
      attributes[attribute[1]] = decode(attribute[2] ?? '')
    }
    found.push(attributes)
  }
  return found
}

/**
 * HTML から、指定した属性を全て持つ開始タグを 1 つ取り出す。<br />
 * 取り出したタグの他の属性は、`expect(tag.src).toBe(...)` のように別々に確認する。
 *
 * @param html  描画した HTML
 * @param tag   タグの名前（例：`input`）
 * @param where 探すタグが持つ属性（値は完全に一致させる）
 * @returns 見つかったタグの属性。なければ、テストを失敗させる
 */
export function findTag(html: string, tag: string, where: Attributes): Attributes {
  const found = startTags(html, tag).find((attributes) =>
    Object.entries(where).every(([name, value]) => attributes[name] === value),
  )
  if (!found) {
    throw new Error(`タグがない：<${tag}> ${JSON.stringify(where)}`)
  }
  return found
}
