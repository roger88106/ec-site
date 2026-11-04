# Spec 367：作業ブランチの名前を `agent/` で始まる形に変える

種類：整理　規模：M　ステータス：草案

## 背景と目的

並行する作業が増え、ブランチの一覧が読みにくい。`main`・`develop` 以外のブランチを、フォルダーの形（`/` の区切り）にまとめる。出典は利用者の指示（2026-11-04）である。消費者・運営者に見える振る舞いは変えない。追跡の issue は #367。

## スコープ

| 含める | 含めない |
|--------|----------|
| 1. 規則と手順の文書のブランチの名前 | `main`・`develop` の名前 |
| 2. 名前を判定する script とそのテスト | Dependabot のブランチの名前 |
| 3. 移行の扱いの記述 | 作業ツリーの置き場所（`.claude/worktrees/<issue の番号>-<短い名前>` のまま。`agent/` を付けない） |
| | ruleset の変更 |
| | 進行中のブランチの改名 |
| | `docs/08-spec/archive/` の変更 |

決定：

- 新しい名前は次の表のとおり。

  | 種類 | 今の名前 | 新しい名前 |
  |------|----------|------------|
  | 通常の作業ブランチ | `<issue の番号>-<短い名前>` | `agent/<issue の番号>-<短い名前>` |
  | 試作の一時ブランチ | `<issue の番号>-trial` | `agent/<issue の番号>-trial` |
  | 緊急の修正 | `hotfix-<issue の番号>-<短い名前>` | `hotfix/<issue の番号>-<短い名前>` |
  | リリース | `release-<バージョン>` | `release/<バージョン>`（Open questions の 1。issue の表にないので、確認する） |

- 名前を判定する箇所は次の表のとおり。2026-11-05 に、作業ツリーで検索して読んだ結果である。

  | 箇所 | 判定の内容 | 扱い |
  |------|------------|------|
  | `scripts/ci/workflow/main-source.mjs` | `main` への pull request の元のブランチの名前の先頭（許可の一覧は `release-`、`hotfix-`） | 許可の一覧を `release/`、`hotfix/` に変える。古い名前は通さない |
  | `.github/workflows/main-source.yml` | 元のブランチの名前を上の script に渡す。マージ先は `main` | 判定は script にある。コメントだけ変える。ジョブの名前は変えない（`main` の ruleset が、必須の検査をこの名前で指定するため） |
  | `scripts/dev/lib/pre-commit.mjs` | 今のブランチが `develop`、`main` と完全に一致するか。`refs/heads/` を除いた残りを名前として読む | 判定は変えない。失敗のときに表示する手順の中のブランチの名前を、新しい形にする。`/` を含む名前で commit できることをテストで固定する |
  | `scripts/dev/hooks/agent-guard.mjs` | マージはマージ先が `develop` かを見て、元のブランチの名前を見ない。push は、送る参照が tag かだけを見る | 判定は変えない。新しい名前の push が通ること、force と削除が拒否されることをテストで固定する |
  | `scripts/ci/risk.mjs` | Dependabot は作成者で見分け、ブランチの名前を見ない | 変えない |
  | `scripts/ci/lib/merge-base.mjs`、`scripts/ci/migrations.mjs` | `develop`・`main` の名前だけを見る | 変えない |
  | `scripts/ci/glossary-ids-exclusions.json` | 用語集の識別子の除外の一覧に `release-`、`hotfix-` がある | `release/`、`hotfix/` に変える |
  | `.github/workflows/ci.yml`、`docs.yml`、`risk.yml` | 元のブランチの名前の条件がない（`risk.yml` はマージ先が `develop`） | 変えない |
  | ruleset `develop`、`main` | 条件は `refs/heads/develop`、`refs/heads/main` の一致だけで、除外と bypass はない ※1 | 変更は要らない |
  | `.claude/settings.json`、`.claude/settings.local.json` | ブランチの名前を含む条件がない（同日に検索した） | 変更は要らない |

  ※1：2026-11-05 に `gh api` で読んだ。規則は、削除の禁止、force の禁止、pull request、必須の検査である。

- `main-source.mjs` の判定は、許可する形の一覧で書き、誤りの防止とする。誤って付けた名前は拒否する。意図的な回避は防止の対象にしない。理由：[文書の構成](../../05-開発規則/doc-structure.md)の「spec と plan の書き方」に、判定の script は許可する形の一覧で書き、誤りの防止か回避の防止かを DC で先に決めると定めているため。
- 移行の扱いは次の表のとおり。文書では `docs/06-手順書/branches.md` の「名前」に書く。

  | 対象 | 扱い |
  |------|------|
  | 進行中の作業ブランチ（本 spec のマージの前に作ったもの。まとめの作業の `346-cleanup-batch` と本 spec のブランチ `367-branch-names` を含む） | 名前を変えない。`develop` へのマージでは元のブランチの名前を判定しないので、名前を変えずにマージできる |
  | 本 spec のマージの後に作る作業ブランチ | 新しい名前を使う |
  | 古い名前のリモートの `hotfix-`・`release-` のブランチ | 今はない（2026-11-05 に `git ls-remote` で確認した）ので、改名の手順は要らない |
  | 本 spec の変更が `main` に入るまでの緊急の修正 | `hotfix-` の名前を使う ※2 |
  | 本 spec の変更を含む最初のリリース | リリースのブランチが新しい script を含むので、新しい名前を使う |

  ※2：`main` への pull request の検査は、pull request の merge commit の script を使う（`.github/workflows/main-source.yml` のコメント）。`main` から切ったブランチは古い script で判定される。この動作は workflow のコメントを読んだものであり、`main` への実際の pull request では確認していない。

- 本 spec の変更を含むリリースが `main` に入ったら、移行の説明を文書から削る。この作業は本 spec に含めず（Non-goals）、issue の起票の案を pull request の本文に書く。

## 目的と完了条件

### DS-367-1 規則と手順の文書が新しい名前を示す（D / P1）

- **DC-367-1** 次の文書が、通常の作業ブランチの名前を新しい形で示す：AGENTS.md、branches.md、開発の用語集、skill `sessions` の handover.md と session-reads.md。
- **DC-367-2** 起票と記録の操作の「構成が決まっていないときの試作」が、試作の一時ブランチを新しい形で示す。
- **DC-367-3** 緊急の修正のブランチを新しい形で示す文書は、次の 6 つである。
  - branches.md
  - 開発の用語集
  - ci.md
  - マージと後片付け
  - AGENTS.md の ※3
  - skill `zh-mixing`
- **DC-367-4** DC-367-3 の文書のうち AGENTS.md を除くものが、リリースのブランチを新しい形で示す。
- **DC-367-5** branches.md に移行の扱いがある。
- **DC-367-6** アーカイブを除く文書と script で、古い名前の形は移行の扱いの説明と、古い名前を通さないことを確認するテストにだけある。

### DS-367-2 判定の script が新しい名前で動く（D / P1）

- **DC-367-7** `main` への元のブランチの判定は、`hotfix/`、`release/` で始まる名前だけを通し、`hotfix-`、`release-` で始まる古い名前を通さない。
- **DC-367-8** DC-367-7 の判定は、許可する形の一覧で書き、誤りの防止とする。
- **DC-367-9** pre-commit hook が `develop`・`main` で表示する手順が、新しい名前を示す。
- **DC-367-10** `/` を含むブランチで commit でき、`develop`・`main` だけを拒否する判定は変わらない。
- **DC-367-11** agent-guard は、新しい名前の push を通し、force と削除を今のとおり拒否する。判定のコードは変えない。
- **DC-367-12** `CI=true` を指定した `scripts/ci/check-all.sh` が成功する。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 判定とテスト | `scripts/ci/workflow/main-source.mjs`、`main-source.test.mjs`、`.github/workflows/main-source.yml`、`scripts/dev/lib/pre-commit.mjs`、`pre-commit.test.mjs`、`scripts/dev/hooks/agent-guard.test.mjs`、`scripts/ci/glossary-ids-exclusions.json` |
| 規則と手順 | `AGENTS.md`、`docs/05-開発規則/glossary.md`、`issues.md`、`merging.md`、`docs/06-手順書/branches.md`、`ci.md`、`.claude/skills/sessions/handover.md`、`session-reads.md`、`.claude/skills/zh-mixing/SKILL.md` |
| spec | `docs/08-spec/367-branch-names/spec.md` |

## 非機能要件

- **DN-367-1** 消費者・運営者に見える振る舞いを変えない。
- **DN-367-2** ruleset と `.claude/settings.local.json` を変えない。
- **DN-367-3** `main-source.yml` のジョブの名前を変えない。
- **DN-367-4** `docs/08-spec/archive/` を変えない。

## 依存

| 対象 | 関係 |
|------|------|
| #317 | 同じ `agent-guard.mjs` の文を変える。先に commit 済みで、本 spec のブランチはその上にある |
| #347、#349 | 並行する作業。2026-11-05 の時点で、両方のブランチに commit がなく、変えるファイルの重なりは未確定である。実装の前に照合する |
| まとめの作業（`346-cleanup-batch`） | 本 spec のブランチはここから切り、ここへ取り込む。push と pull request は issue 担当のセッションが行う |

## Non-goals

| 項目 | 理由 |
|------|------|
| 作業ブランチの名前を拒否する検査を加えない | `develop` への pull request では元のブランチの名前を判定せず、issue の範囲にも含まれないため。移行の間は、古い名前の作業ブランチも `develop` にマージする必要があるため |
| デスクトップアプリが自動で作るブランチの名前 | アプリの設定であり、リポジトリの規則が扱う範囲にないため |
| 移行の説明の削除 | 本 spec の変更を含むリリースが `main` に入った後に行う。issue の起票の案を pull request の本文に書く |

## Open questions

1. リリースのブランチも `release/<バージョン>` にするか。issue の表は `hotfix` だけを挙げ、背景は `main`・`develop` 以外のブランチをまとめるとしている。推奨：含める。理由：背景の目的を満たし、判定の許可の一覧の形がそろうため。リモートに古い名前のブランチがないため、移行の負担がない。含めないときは、DC-367-4 を除き、許可の一覧に `release-` を残す。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。文書の本文は日本語の要点から、日本語だけの文脈のサブエージェント（汎用）が書く。

- [ ] **T367-01-01** [テスト] `main-source.test.mjs` を新しい名前に変える。新しい名前は通し、古い名前、`hotfix`、`release`、`feature/release-x`、`Release/1` は通さない。`pre-commit.test.mjs` の表示の期待を新しい名前に変え、`agent/367-x` のブランチで commit できるテストを加える。失敗することを確認する（DC-367-7、DC-367-9、DC-367-10）。commit は T367-01-02 と同じにする（[開発の進め方](../../05-開発規則/workflow.md)の「循環」の例外）。
  - 依存：なし
  - 範囲：`scripts/ci/workflow/main-source.test.mjs`、`scripts/dev/lib/pre-commit.test.mjs`
  - 確認方法：`node --test` で新しいテストが失敗する
- [ ] **T367-01-02** `main-source.mjs`、`main-source.yml` のコメント、`pre-commit.mjs` の表示を変える（DC-367-7〜10）。
  - 依存：T367-01-01
  - 範囲：`scripts/ci/workflow/main-source.mjs`、`.github/workflows/main-source.yml`、`scripts/dev/lib/pre-commit.mjs`
  - 確認方法：`node --test` が通る
- [ ] **T367-01-03** [テスト] `agent-guard.test.mjs` に、`git push -u origin agent/367-x`、`hotfix/1-x`、`release/1.2.0` が通り、force と削除が拒否されるテストを加える（DC-367-11）。
  - 依存：なし
  - 範囲：`scripts/dev/hooks/agent-guard.test.mjs`
  - 確認方法：今の振る舞いを固定するテストなので、一時的に壊して失敗することを `scripts/dev/break-check.mjs` で確認する
- [ ] **T367-01-04** 規則と手順の文書、`glossary-ids-exclusions.json` を変え、branches.md に移行の扱いを書く（DC-367-1〜6）。
  - 依存：T367-01-02
  - 範囲：「変える範囲」の規則と手順の行、`scripts/ci/glossary-ids-exclusions.json`
  - 確認方法：`git grep` で古い名前の形を検索する、`node scripts/ci/doc-refs.mjs`
- [ ] **T367-01-05** spec の最後のレビュー、文書のレビュー、校閲、pull request の前のレビューを行い、指摘を修正する（DC-367-12）。
  - 依存：T367-01-01〜04
  - 確認する項目：spec の最後のレビューは高性能で、段階をまたぐ影響、ファイルをまたぐ影響、spec との一致を見る。校閲は差分の追加行に行う。pull request の前のレビューは `/code-review` の high と、文脈を持たない高性能のレビュー役で行う（hook と CI の検査の script を変えるため）
  - 確認方法：`CI=true scripts/ci/check-all.sh`
  - 結果：
- [ ] **T367-01-06** spec を `docs/08-spec/archive/1to10/` に移し、全てのチェックボックスを `[x]` にする（[起票と記録の操作](../../05-開発規則/issues.md)の「アーカイブの操作」）。
  - 依存：T367-01-05
  - 確認方法：`node scripts/ci/spec-structure.mjs`、`node scripts/ci/doc-refs.mjs`
