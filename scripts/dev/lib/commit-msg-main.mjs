// commit のメッセージの hook（scripts/dev/hooks/commit-msg）の入口。直接実行の判定を使わず、必ず処理を実行する
import { main } from './commit-msg.mjs'

process.exitCode = main(process.argv[2])
