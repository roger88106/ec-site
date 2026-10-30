# ブランチとリリース

ブランチの構成、作業ブランチの作り方、保護、リリースと緊急の修正の手順、Dependabot の設定。CI/CD の決まりは [constitution](../constitution.md)の「CI/CD」。CI のジョブは [CI の検査](ci.md)。設定のフラグ、完了の定義、マージの許可と担当は [開発の進め方](../05-開発規則/workflow.md) と [AGENTS.md](../../AGENTS.md)。

## 1. ブランチの構成

| ブランチ | 役割 | 備考 |
|----------|------|------|
| `develop` | 開発。既定のブランチ | 作業ブランチは `develop` から切り、pull request で `develop` に入れる |
| `main` | 本番 | `release-` と `hotfix-` で始まるブランチからの pull request だけを受け付ける（「`main` の元のブランチの制限」） |
| 作業ブランチ | 1 つの spec の作業（規模 S は 1 つの issue） | 名前の付け方は「名前」。マージした後は自動で削除される |

## 2. 作業ブランチ

### 2.1 名前

| 種類 | 名前の形 |
|------|----------|
| 通常 | `<issue の番号>-<短い名前>` |
| 緊急の修正 | `hotfix-<issue の番号>-<短い名前>` |

### 2.2 切り方と pull request

- 通常の作業ブランチは `develop` から切る。緊急の修正は `main` から切る（「緊急の修正」）。
- 作業ブランチは、pull request で `develop` に入れる。
- `develop` と衝突するとき、または `develop` が同じファイルを変えたときは、`develop` を作業ブランチに取り込んでから入れる（[開発の進め方](../05-開発規則/workflow.md)の「マージの許可と後片付け」）。最新の `develop` を含むことは求めない（「ruleset」）。
- 完了の定義（`develop` に入れる条件）は [開発の進め方](../05-開発規則/workflow.md)。
- `.gitignore` は許可リストで、Git に入れる物だけを `!` で戻す。`.claude/` の下は、共有する skill、サブエージェントの定義（`agents/`）、プレビューの設定、`settings.json`（チームの hook と permission）だけを戻す。作業用の script などを置く `/.local/` は Git に入れない。

## 3. 保護

### 3.1 ruleset

GitHub の ruleset を `develop` と `main` に作る。

| 項目 | `develop` | `main` |
|------|-----------|--------|
| pull request を経ない変更 | 禁止 | 禁止 |
| approve の数 | 0（理由：利用者が 1 人のため） | 0 |
| 必須の検査 | CI のジョブのうち、バックエンド、管理画面、店舗画面、E2E、文書の検査 ※1 と、危険度の照合 | 危険度の照合を除いた同左と、元のブランチの検査（「`main` の元のブランチの制限」） |
| マージの前に最新の `develop` を含む | なし ※2 | なし |
| force push | 禁止 | 禁止 |
| 削除 | 禁止 | 禁止 |

※1：分けたバックエンドのジョブ（ビルドと単体テスト、統合テスト 1/2・2/2）は登録しない。「バックエンド」がそれらの結果を確認するため（[CI の検査](ci.md)の「CI のジョブ」）。

※2：2026-10-28 に外した。理由：続けてマージするときの、取り込みと CI の再実行の待ちをなくすため。代わりに、マージの後に `develop` の CI を見届ける（[開発の進め方](../05-開発規則/workflow.md)の「マージの許可と後片付け」）。

### 3.2 `main` の元のブランチの制限

`main` は、`release-` と `hotfix-` で始まるブランチからの pull request だけを受け付ける。理由：リリースするのは確認した commit で、`develop` の最新とは限らないため（「リリース」）。

- 検査の workflow は `.github/workflows/main-source.yml`。`main` への pull request の元のブランチの名前が `release-` か `hotfix-` で始まらなければ失敗する。判定は `scripts/ci/workflow/main-source.mjs`（テストあり）。
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
- リリースの内容は、issue 担当のセッションがまとめる（[開発の進め方](../05-開発規則/workflow.md)）。
- `release-` のブランチの `main` へのマージと tag は、issue 担当のセッションが利用者の許可を得て行う。
- デプロイの spec で決めるまでのリリースは、`main` へのマージと tag までとし、デプロイしない（「デプロイの spec まで」）。

## 5. 緊急の修正

- `hotfix-<issue の番号>-<短い名前>` のブランチを `main` から切り、pull request で `main` に入れる。
- `main` に入れた後に、`develop` に戻す。`main` から `<issue の番号>-<短い名前>` のブランチを切り、`develop` を取り込んでから、pull request で `develop` に入れる。理由：`main` を直接 `develop` に入れる pull request で衝突すると、`main` に commit しなければ解消できないため。
- 必須の検査が GitHub の障害などで動かず、マージできないときは、利用者の許可を得て ruleset の `enforcement` を一時的に `disabled` にし、マージの後にすぐ `active` に戻す。`gh api` で ruleset を変えると、Claude Code の hook が確認の画面を表示する。
- `hotfix-` のブランチから `main` へのマージと、その後の `main` から `develop` への取り込みは、issue 担当のセッションが利用者の許可を得て行う。
- tag と GitHub の Release は、「リリース」の手順 3、4 と同じ。

## 6. Dependabot

`.github/dependabot.yml` で設定する。更新の pull request は `develop` に対して作成される。

| 項目 | 内容 |
|------|------|
| 対象 | `gradle`（`/backend/ecsite`）、`npm`（`/`。pnpm の workspace）、`github-actions`（`/`）、`docker-compose`（`/docker`） |
| 頻度 | 週 1 回（月曜、日本時間） |
| group | minor と patch を ecosystem ごとにまとめて 1 つの pull request にする。major は 1 つずつ pull request にする。`github-actions` は major も含めて 1 つの pull request にする ※3 |
| cooldown | 新しいバージョンがリリースされてから待つ日数。major 30、minor 7、patch 3。`github-actions` と `docker-compose` は、GitHub の文書の cooldown の対応表で SemVer の日数（major・minor・patch）に対応しないため、7 日（`default-days`）にする |
| label | `種類:整理`、`領域:ci`、`依存の更新` |
| commit のメッセージの前置き | `deps` |

※3：`github-actions` の更新は危険度が高いので、利用者が許可する。1 つにまとめると、許可と CI の待ちが 1 回で済む。

- セキュリティの更新は、cooldown を待たずに作成される。リポジトリの設定で、Dependabot のセキュリティの更新を有効にしている。
- Dependabot の pull request に `develop` を取り込むとき（衝突するとき、または `develop` が同じファイルを変えたとき）は、pull request に `@dependabot rebase` とコメントして更新させる。理由：画面の「Update branch」で merge commit を追加すると、Dependabot がその pull request を自動で更新しなくなるため。
- `@types/node` の major は、Dependabot の対象から外す。理由：実行の環境は `.node-version` の Node（`package.json` の `engines.node` はその下限の範囲）で、型の定義だけを上げると、そのバージョンにない API も型の検査を通ってしまうため。Node のバージョンを上げるときに、合わせて上げる。
- `github-actions` は、ワークフローに加え、ローカルの複合の action（`.github/actions/setup-frontend`）で使う action のバージョンも更新する。理由：`directory: /` は `.github/workflows` とルートの `action.yml` しか見ないため、`directories` に複合の action のディレクトリを追加している。複合の action を増やしたら、追加する。
- 危険度は、経路と、pull request の題から読む更新の種類で判定する。`github-actions` の更新（`.github/`）と major の更新、題から更新の種類を読めないものは危険度が高く、利用者が許可する。他の minor と patch は危険度が低く、issue 担当のセッションがマージする。CI の job は作成者で見分け、本文と比べず判定をジョブの要約に出すので、マージの前にその判定を読む（[エージェントの作業フロー](../05-開発規則/subagents.md)の「役割と作業の流れ」）。
- Dependabot の pull request は、プロジェクトの規則の例外とする。issue を必要とせず、commit のメッセージは英語でよい。
- Gradle のバージョンを上げるときは、`backend/ecsite/gradle/wrapper/gradle-wrapper.properties` の `distributionSha256Sum` を、`https://services.gradle.org/distributions/gradle-<バージョン>-bin.zip.sha256` の値に替える。Dependabot の Gradle の wrapper の更新がこの値も替えるかは確認していないので、更新の pull request で確認する。
- TypeScript のバージョンを上げる pull request は、`node scripts/ci/globals/lib-globals.mjs --write` で組み込みの名前の一覧（`scripts/ci/globals/lib-globals.json`）を書き直し、差分を確認する。そのうえで `node scripts/ci/frontend-rules.mjs` を実行し、増えた名前によるシャドーイングがないことを確認する。lib の名前が変わると、CI の店舗画面の「組み込みの名前の一覧の確認」が失敗する。
- 注意：GitHub の文書（2026-10-22 の時点）では、Dependabot の pnpm の対応は v10 まで。このリポジトリは pnpm 12 である。`npm` の更新が失敗したときは、issue を起票し、その issue で Renovate への切り替えを検討する。

## 7. デプロイの spec まで

CD、staging、preview の環境の方式は、デプロイの spec で決める。決めるまでは次のとおり。

| 項目 | 扱い |
|------|------|
| 手動の確認 | 手元の環境で、作業ブランチに対して実行する（[開発環境の起動](setup.md)、[テストの実行方法](running-tests.md)） |
| リリース | `main` へのマージと tag までとし、デプロイしない |
