// 文脈の圧縮の hook（.claude/settings.json）：圧縮の前に要約に残すものを指示し、圧縮の後に規則の文書を読み直すよう知らせる。
//
// 圧縮の後は、AGENTS.md は読み直されるが、AGENTS.md が指す文書（「最初に読むもの」）と、作業中の tasks・引き継ぎは
// 文脈に戻らず、エージェントが規則から外れやすいため。
//
// - PreCompact：標準出力が要約の指示になる。要約に残すものを並べる。
// - SessionStart（source が compact）：標準出力が文脈に入る。読み直すものを並べる。
// - それ以外の入力と、入力が読めないときは、何も出さずに終了コード 0 で終わる（圧縮とセッションを止めないため）。
import { readFileSync } from 'node:fs'

/** 圧縮の要約に残すもの */
const PRE_COMPACT = `要約には、次を必ず残す。
- 担当の issue と spec の番号、作業ブランチ、作業ツリーのパス
- 作業中の tasks のファイルと今のタスク、読んだ引き継ぎの文書のパス
- 利用者の決定と、その場限りの特例（利用者の言葉で）
- commit・push していない変更、動いているサブエージェントとセッション
- 次の一歩と、利用者に確認している途中の問い
`

/** 圧縮の後に読み直すもの */
const AFTER_COMPACT = `文脈が圧縮された。AGENTS.md の「最初に読むもの」の 1〜3（constitution、開発の進め方、着手している段階の tasks と参照の文書）と、要約にある引き継ぎの文書を読み直してから、作業を続ける。コードや文書を書く前には、同じ節の 4・5 の文書も読み直す。
`

try {
  const input = JSON.parse(readFileSync(0, 'utf8'))
  const event = input?.hook_event_name
  if (event === 'PreCompact') process.stdout.write(PRE_COMPACT)
  else if (event === 'SessionStart' && input?.source === 'compact') process.stdout.write(AFTER_COMPACT)
} catch {
  // 入力が読めなければ何も出さない
}
