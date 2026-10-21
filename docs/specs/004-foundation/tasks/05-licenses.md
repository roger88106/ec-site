# 段階 5：ライセンスの検査

- 前提：段階 4（workspace の後に、lock が 1 つになってから）
- 完了条件：方針に合わない依存で CI が失敗し、今の依存では通る（DC-10）。
- 参照：[plan](../plan.md) 第 3 節、constitution 17、`/.local/memo/licenses.md`（前の調査の結果）

## タスク

- [x] **T4501** 調べ残しを片付ける：バックエンドのテスト・ビルド用の依存の一覧、jOOQ の POM の表記、`.claude/skills/` の LICENSE のコピー（原文の著作権表示が保たれているか）、Keycloak のテーマの CSS。結果を報告し、方針に合わないものがあれば利用者に確認する。
- [x] **T4502** `scripts/ci/licenses/policy.json`：許可・条件付き（依存ごとに理由）・禁止の一覧、表記の揺れの正規化、Gradle のプラグインの手書きの一覧。
- [x] **T4503** バックエンド：`com.github.jk1.dependency-license-report` を入れ、`checkLicense` が policy の許可と条件付きの一覧で検査する（実行時、コンパイル時、テスト、アノテーション処理、jOOQ のコード生成）。CI のバックエンドの job で実行する。
- [x] **T4504** [テスト] `scripts/ci/licenses/check-frontend.mjs` のテスト：許可、条件付き（一覧にある依存・ない依存）、禁止、`OR`・`AND`、不明。
- [x] **T4505** `check-frontend.mjs` を作り（`pnpm licenses list --json` を policy と照合する）、CI のフロントの job で実行する。T4504 を通す。
- [x] **T4506** 禁止のライセンスの依存を一時的に追加すると、バックエンドとフロントの検査がそれぞれ失敗することをローカルで確認する（commit しない）。
