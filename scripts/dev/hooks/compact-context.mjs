// 文脈の圧縮の hook（.claude/settings.json）：圧縮の前に要約に残すものを指示し、圧縮の後に規則の文書を読み直すよう知らせる。
//
// 圧縮の後は、AGENTS.md は読み直されるが、AGENTS.md が指す文書（「最初に読むもの」）と、作業中の tasks・引き継ぎは
// 文脈に戻らず、エージェントが規則から外れやすいため。
//
// - PreCompact：標準出力が要約の指示になる。要約に残すものを並べる。
// - SessionStart（source が compact）：標準出力が文脈に入る。読み直すものを並べる。
// - それ以外の入力と、入力が読めないときは、何も出さずに終了コード 0 で終わる（圧縮とセッションを止めないため）。
//
// 出力の使われ方と、圧縮の後に読み直されるもの（AGENTS.md は CLAUDE.md の代わりとして読まれる）の出典は、Claude Code の文書
// （https://code.claude.com/docs/en/hooks.md の PreCompact と SessionStart、https://code.claude.com/docs/en/context-window.md）。
import { readFileSync } from 'node:fs'

/** 圧縮の要約に残すもの */
const PRE_COMPACT = `要約には、次を必ず残す。
- 担当の issue と spec の番号、作業ブランチ、作業ツリーのパス
- 作業中の tasks のファイルと今のタスク、読んだ引き継ぎの文書のパス
- 利用者の決定と、その場限りの特例（利用者の言葉で）
- commit・push していない変更、動いているサブエージェントとセッション
- 次にする作業と、利用者に確認している途中の問い
`

/** 圧縮の後に読み直すもの */
const AFTER_COMPACT = `文脈が圧縮された。AGENTS.md の「最初に読むもの」に従って、規則の文書と、作業中の tasks とそれが参照する文書を読み直し、要約にある引き継ぎの文書も読み直してから、作業を続ける。
`

try {
  const input = JSON.parse(readFileSync(0, 'utf8'))
  const event = input?.hook_event_name
  if (event === 'PreCompact') process.stdout.write(PRE_COMPACT)
  else if (event === 'SessionStart' && input?.source === 'compact') process.stdout.write(AFTER_COMPACT)
} catch {
  // 入力が読めなければ何も出さない
}
