# 段階 1：規則の文書

- 前提：spec 004 の確定
- 完了条件：DC-1〜6、8、9、63 の文書がある。文書だけの変更なので、テストは変わらない。
- 参照：[plan](../plan.md) 第 1 節、第 3.1、3.2 節、constitution 14・15、skill `writing-for-agents`
- タスク番号は `T4` ＋ 段階 ＋ 連番。

## constitution と AGENTS.md

- [x] **T4101** constitution 15 に、spec の 2 つの種類（機能・整理）と、整理の spec の書き方（DS・DC・DN、目的・完了の条件、対象 D）を書く。「1 つの spec は機能の縦切り 1 つ」を機能の spec の規則にする。`docs/notation.md`（草案は作成済み）を確かめて、constitution・README・AGENTS.md から引けるようにする（DC-5、DC-6）。
- [ ] **T4102** AGENTS.md と skill `handoff` の追記：引き継ぎの文書の置き場所（`/.local/handoff/yyyyMMddHHmmss-<名前>/`、中の MD は 1 つでも複数でもよい）、新しいセッションは指示がなければ最新のものを読む、古いものは利用者が消す（DC-3）。
- [ ] **T4103** AGENTS.md に「skill の一覧」の節を足す。skill を足す・消すときに一覧も直す決まりを、skill `writing-for-agents` のこのリポジトリ向けの追記に書く。「最初に読むもの」に `docs/notation.md` と `docs/style/` を足す（DC-2）。
- [ ] **T4104** constitution 新 17 節「依存とライセンス」：プロジェクトのライセンス（MIT）、依存のライセンスの方針（許可・条件付き・禁止）、有料の版がある製品（jOOQ、Flyway、imgproxy、Keycloak）の使う範囲（DC-9。plan 第 3.2 節）。
- [ ] **T4105** リポジトリの直下に `LICENSE`（MIT。著作権者 `roger88106`、2026）。admin・store・e2e の `package.json` に `"license": "MIT"`（DC-8。ルートの workspace の `package.json` は段階 4 で作るときに付ける）。

## 規則の文書と開発の手順

- [ ] **T4106** `docs/style/` を作る：`README.md`（一覧、constitution との分担、規則の形「規則・理由・確かめ方」）、`backend.md`、`frontend.md`、`testing.md`、`comments.md`。今のコードで守られている習慣を書き出す（DB の制約の違反の変換、生成した interface の実装、`@Transactional` の置き場所、テスト名の AC 番号、`unstable_rethrow`、Javadoc・TSDoc の書き方など）。書く前に、コードと既存の文書（constitution、`docs/frontend/`、モジュール設計書）を読んで、食い違いを一覧にして報告する（DC-1）。
- [ ] **T4107** `docs/style/review.md`：「見直しで読む」規則の一覧（DC-1 の文書から集める）と、見直しの段階での使い方。各 spec の見直しの tasks がこれを使うことを constitution 15 か AGENTS.md に書く（DC-4）。
- [ ] **T4108** `docs/dev/`（`setup.md`、`testing.md`、`environment.md`）に README の起動・アカウント・テスト・環境変数を移し、README を概要・文書の地図・進め方・進捗に絞る。AGENTS.md と skill `local-dev` の参照を直す（DC-63）。
- [x] **T4110** AGENTS.md「テストが先」と constitution 12.2 を、TDD の循環（レッド → グリーン → リファクタリング → 次の振る舞いのレッド）の形に書き直す。リファクタリングの前に今の振る舞いを固定するテスト（最初から通る）は、一時的にコードか期待値を変えて失敗しうることを確かめる。あわせて AGENTS.md に「段階を始める前に、その段階で利用者に確かめることをまとめて聞く」を書く（2026-10-12 利用者の指示）。
- [ ] **T4109** 用語集に、カバレッジ、引き継ぎの文書、規則の文書、整理の spec を足す。README の spec の表に「種類」の列を足し、004〜006 を載せる。
