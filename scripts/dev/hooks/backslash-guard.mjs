// PreToolUse の hook（.claude/settings.local.json、Bash）：heredoc や node -e の中に \ を含むコマンドを止める。
// Windows の Git Bash では、heredoc と node -e の文字列の \ が落ち、正規表現やエスケープが黙って壊れるため。
// 見るのは heredoc の本文と node -e（--eval、-p、--print を含む）の引数だけ。コマンドの他の箇所の \（grep の \| など）は止めない。
import { readFileSync } from 'node:fs'

let command = ''
try {
  command = JSON.parse(readFileSync(0, 'utf8'))?.tool_input?.command ?? ''
} catch {
  process.exit(0)
}

/** heredoc の本文（<<'EOF' の次の行から、終わりの印の行の前まで）。終わりの印は `END-JS`、`EOF.x` のように - と . を含んでよい */
function heredocBodies(text) {
  const bodies = []
  for (const m of text.matchAll(/<<-?\s*(['"]?)([\w.-]+)\1[^\n]*\n([\s\S]*?)\n[ \t]*\2[ \t]*(?:\n|$)/g)) bodies.push(m[3])
  return bodies
}

/**
 * node の script を引数で渡す指定（-e・--eval・-p・--print・-pe・-ep）の引数（引用符で囲んだ文字列）。
 * `node --input-type=module -e '…'` のように、node と指定の間に別のオプション（- で始まる語）があってもよい
 */
function nodeEvalArgs(text) {
  const args = []
  const pattern = /\bnode(?:\s+-[^\s'"]*)*?\s+(?:-e|--eval|-p|--print|-pe|-ep)(?:\s+|=)(?:'([^']*)'|"((?:[^"\\]|\\.)*)")/g
  for (const m of text.matchAll(pattern)) args.push(m[1] ?? m[2] ?? '')
  return args
}

const risky = [...heredocBodies(command), ...nodeEvalArgs(command)].some((part) => part.includes('\\'))
if (risky) {
  process.stderr.write(
    'heredoc か node -e の中に \\ がある。Windows の Git Bash では \\ が落ちる。' +
      '既存のファイルは Edit で直し、新しいファイルや置き換えの script は Write でスクラッチパッドに書いてから node で実行する。\n',
  )
  process.exit(2)
}
