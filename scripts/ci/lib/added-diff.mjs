// 追加行を読む差分（git diff -U0）の引数。差分の追加行の文体の警告（ja-text --changed）と、commit の前の hook（ステージした差分）が使う。
// 1 か所に定義し、CI の注釈と hook の表示で追加行の判定が食い違わないようにする。
// 利用者の git の設定（diff.renames、diff.mnemonicPrefix など）によらないよう、名前の変更の検出（-M）と接頭辞を固定する。
// 名前を変えたファイルの変えていない行と、改行コードだけの違いは、追加行にしない。

/** `git diff` の後ろに、比べる対象（revision や --cached）と `--` を足して使う。 */
export const ADDED_DIFF_ARGS = [
  '-c',
  'core.quotePath=false',
  'diff',
  '-M',
  '--ignore-cr-at-eol',
  '--no-color',
  '--no-ext-diff',
  '--no-textconv',
  '-U0',
  '--src-prefix=a/',
  '--dst-prefix=b/',
  '--diff-filter=AMR',
]
