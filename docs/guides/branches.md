# ブランチとリリース

ブランチの構成、作業ブランチの作り方、保護、リリースと緊急の修正の手順、Dependabot の設定。CI/CD の決まりは [constitution](../constitution.md) 第 12 節。CI のジョブは [CI の検査](ci.md)。設定のフラグ、完了の定義、マージの許可と担当は [開発の進め方](../rules/workflow.md) と [AGENTS.md](../../AGENTS.md)。

## 1. ブランチの構成

| ブランチ | 役割 | 備考 |
|----------|------|------|
| `develop` | 開発。既定のブランチ | 作業ブランチは `develop` から切り、pull request で `develop` に入れる |
| `main` | 本番 | `release-` と `hotfix-` で始まるブランチからの pull request だけを受け付ける（第 3.2 節） |
| 作業ブランチ | 1 つの spec の作業（規模 S は 1 つの issue） | 名前は第 2.1 節。マージした後は自動で削除される |

## 2. 作業ブランチ

### 2.1 名前

| 種類 | 名前の形 |
|------|----------|
| 通常 | `<issue の番号>-<短い名前>` |
| 緊急の修正 | `hotfix-<issue の番号>-<短い名前>` |

### 2.2 切り方と pull request

- 通常の作業ブランチは `develop` から切る。緊急の修正は `main` から切る（第 5 節）。
- 作業ブランチは、pull request で `develop` に入れる。
- `develop` に入れる前に、作業ブランチが最新の `develop` を含んでいること。含んでいなければ、`develop` を作業ブランチに取り込む（第 3.1 節）。
- 完了の定義（`develop` に入れる条件）は [開発の進め方](../rules/workflow.md)。

## 3. 保護

### 3.1 ruleset

GitHub の ruleset を `develop` と `main` に作る。

| 項目 | `develop` | `main` |
|------|-----------|--------|
| pull request を経ない変更 | 禁止 | 禁止 |
| approve の数 | 0（理由：利用者が 1 人のため） | 0 |
| 必須の検査 | CI のジョブのうち、バックエンド（分けたジョブの結果を合わせるジョブ）、管理画面、店舗画面、E2E と、文書の検査。分けたバックエンドのジョブは、「バックエンド」が結果を合わせるので登録しない（[CI の検査](ci.md) 第 2 節） | 同左と、元のブランチの検査（第 3.2 節） |
| マージの前に最新の `develop` を含む | 必須 | なし |
| force push | 禁止 | 禁止 |
| 削除 | 禁止 | 禁止 |

### 3.2 `main` の元のブランチの制限

`main` は、`release-` と `hotfix-` で始まるブランチからの pull request だけを受け付ける。理由：リリースするのは確認した commit で、`develop` の最新とは限らないため（第 4 節）。

- 検査の workflow は `.github/workflows/main-source.yml`。`main` への pull request の元のブランチの名前が `release-` か `hotfix-` で始まらなければ失敗する。
- この検査を `main` の ruleset の必須の検査にする。

### 3.3 マージの方式

- マージは merge commit で実行する。リポジトリの設定で、squash と rebase は無効にしている。
- `develop` から `main` へは squash しない。理由：履歴が分かれ、次のリリースで衝突するため。

### 3.4 自動の削除

マージした作業ブランチは、リポジトリの設定で自動で削除される。

## 4. リリース

| 手順 | 内容 |
|------|------|
| 1 | 確認した commit から `release-<バージョン>` のブランチを作成する。確認した commit は `develop` の最新とは限らない |
| 2 | pull request で `main` にマージする |
| 3 | tag `v<バージョン>` を付ける |
| 4 | `gh release create` で GitHub の Release を作成する |

- バージョンは SemVer（`MAJOR.MINOR.PATCH`）。上げる位置は、リリースの内容で決める。

| リリースの内容 | 上げる位置 |
|----------------|------------|
| 機能の spec（消費者・運営者に見える振る舞いの追加・変更）を含む | MINOR |
| 不具合の修正、整理、進め方、依存の更新だけ | PATCH |
| 正式な公開 | MAJOR（`1.0.0`。それまでは上げない） |
- リリースの内容は、issue 担当のセッションがまとめる（[開発の進め方](../rules/workflow.md)）。
- `release-` のブランチの `main` へのマージと tag は、issue 担当のセッションが利用者の許可を得て行う。
- デプロイの spec で決めるまでのリリースは、`main` へのマージと tag までとし、デプロイしない（第 7 節）。

## 5. 緊急の修正

- `hotfix-<issue の番号>-<短い名前>` のブランチを `main` から切り、pull request で `main` に入れる。
- `main` に入れた後に、`develop` に戻す。`main` から `<issue の番号>-<短い名前>` のブランチを切り、`develop` を取り込んでから、pull request で `develop` に入れる。理由：`develop` は、マージの前に最新の `develop` を含むことを必須にしているので、`main` を直接 `develop` に入れる pull request はマージできないため。
- 必須の検査が GitHub の障害などで動かず、マージできないときは、利用者の許可を得て ruleset の `enforcement` を一時的に `disabled` にし、マージの後にすぐ `active` に戻す。
- `hotfix-` のブランチから `main` へのマージと、その後の `main` から `develop` への取り込みは、issue 担当のセッションが利用者の許可を得て行う。
- tag と GitHub の Release は、第 4 節の手順 3、4 と同じ。

## 6. Dependabot

`.github/dependabot.yml` で設定する。更新の pull request は `develop` に対して作成される。

| 項目 | 内容 |
|------|------|
| 対象 | `gradle`（`/backend/ecsite`）、`npm`（`/`。pnpm の workspace）、`github-actions`（`/`）、`docker-compose`（`/docker`） |
| 頻度 | 週 1 回（月曜、日本時間） |
| group | minor と patch を ecosystem ごとにまとめて 1 つの pull request にする。major は 1 つずつ pull request にする |
| cooldown | 新しいバージョンがリリースされてから待つ日数。major 30、minor 7、patch 3。`github-actions` と `docker-compose` は、GitHub の文書の cooldown の対応表で SemVer の日数（major・minor・patch）に対応しないため、7 日（`default-days`）にする |
| label | `種類:整理`、`領域:ci`、`依存の更新` |
| commit のメッセージの前置き | `deps` |

- セキュリティの更新は、cooldown を待たずに作成される。リポジトリの設定で、Dependabot のセキュリティの更新を有効にしている。
- `develop` が進んで Dependabot の pull request が最新でなくなったときは、pull request に `@dependabot rebase` とコメントして更新させる。理由：画面の「Update branch」で merge commit を追加すると、Dependabot がその pull request を自動で更新しなくなるため。
- `@types/node` の major は、Dependabot の対象から外す。理由：実行の環境は `package.json` の engines の Node で、型の定義だけを上げると、その版にない API も型の検査を通ってしまうため。Node の版を上げるときに、合わせて上げる。
- Dependabot の pull request は、プロジェクトの規則の例外とする。issue を必要とせず、commit のメッセージは英語でよい。
- 注意：GitHub の文書（2026-10-22 の時点）では、Dependabot の pnpm の対応は v10 まで。このリポジトリは pnpm 12 である。`npm` の更新が失敗したときは、issue を起票し、その issue で Renovate への切り替えを検討する。

## 7. デプロイの spec まで

CD、staging、preview の環境の方式は、デプロイの spec で決める。決めるまでは次のとおり。

| 項目 | 扱い |
|------|------|
| 手動の確認 | 手元の環境で、作業ブランチに対して実行する（[開発環境の起動](setup.md)、[テストの実行方法](running-tests.md)） |
| リリース | `main` へのマージと tag までとし、デプロイしない |
