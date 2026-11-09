/**
 * NFKC で `-` にならないハイフンの類。
 * U+30FC 長音符、U+2212 マイナス記号、U+2010 ハイフン、U+2011 非改行ハイフン、U+2013 エヌダッシュ、U+2014 エムダッシュ、U+2015 水平線。
 * 半角の長音符（U+FF70）は、NFKC で U+30FC になるので、ここに入れない。
 */
const HYPHEN_LIKE = /[\u30FC\u2212\u2010\u2011\u2013\u2014\u2015]/g

/**
 * 郵便番号と電話番号の全角の数字とハイフンの類を、半角にする。<br />
 * Unicode の NFKC で正規化し、続けて NFKC で `-` にならないハイフンの類を `-` にする。氏名と住所には使わない。
 *
 * @param value 入力の値
 * @returns 半角にした値
 */
export function toHalfWidth(value: string): string {
  return value.normalize('NFKC').replace(HYPHEN_LIKE, '-')
}
