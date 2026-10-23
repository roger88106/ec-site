// commit の前の hook（scripts/dev/hooks/pre-commit）の入口。直接実行の判定を使わず、必ず処理を実行する
import { main } from './pre-commit.mjs'

process.exitCode = main()
