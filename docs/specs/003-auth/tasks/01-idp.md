# 段階 1：IdP と開発環境

- 前提：spec 002 の完了
- 完了条件：ローカルで両方の realm にログインでき、会員登録の確認のメールと、パスワードの再設定のメールが Mailpit に届く。下記の手動確認が通る。`./gradlew check` が通る（既存のテストを壊していない）。
- 参照：[plan](../plan.md) 第 1、2 節、constitution 9・10
- タスク番号は `T3` ＋ 段階 ＋ 連番（spec 001・002 の番号と重ならないようにするため）。

## 文書

- [x] **T3101** constitution 9 のエラーレスポンスのステータスに、401（認証がない・無効）と 403（権限がない）を足す。`errorCode` は `UNAUTHENTICATED`、`FORBIDDEN`（plan 第 3.3 節）。
- [x] **T3102** 用語集に、会員、会員 ID、IdP のアカウントの ID、運営者、運営者 ID、職務、権限、IdP、realm を足す。職務と権限（BR-12）の識別子（`administrator`、`viewer`、`resource:action`）も書く。

## 開発環境

- [x] **T3103** compose に Keycloak（ポート 8180。イメージのタグは着手の時点の最新の安定版に固定）と Mailpit（1025、8025）を足す。
  - Keycloak の DB は、既存の PostgreSQL の別の database（`keycloak`）にし、初期化のスクリプトで作る。既存の DB の volume では初期化のスクリプトが走らないので、作り直す手順を README に書く。
  - Keycloak は起動時に `keycloak/realms/` の realm を取り込む。
- [x] **T3104** 消費者の realm（`ec-store`）の設定を作り、JSON に書き出す（plan 第 2.1、2.3 節）。
  - client：`ec-store-web`（confidential、認可コード + PKCE、戻り先は店舗画面）、`ec-store-api`（audience 用）。`ec-store-web` の access token の audience に `ec-store-api` を入れる
  - 会員登録を有効、ログイン ID はメールアドレス、メールアドレスの確認を必須、パスワードを忘れたときを有効、姓と名を必須
  - パスワードの条件、ログインの失敗による一時的な停止、SSO セッションの時間、access token の有効期間
  - 画面とメールの言語は日本語、SMTP は Mailpit
  - ローカル用の会員 1 人（メールアドレス確認済み）
- [x] **T3105** 運営者の realm（`ec-admin`）の設定を作り、JSON に書き出す（plan 第 2.1〜2.3 節）。
  - client：`ec-admin-web`（public、PKCE、戻り先は管理画面）、`ec-admin-api`（audience 用と、権限の role の置き場所）。`ec-admin-web` の access token の audience に `ec-admin-api` を入れる
  - `ec-admin-api` の client role に、`admin.yaml` の権限（10 個）をそのままの名前で作る。職務の composite role：`administrator`（すべて）、`viewer`（`:read` すべて）
  - 会員登録は無効、「ログインしたままにする」は無効、メールアドレスの確認は不要
  - パスワードの条件、ログインの失敗による一時的な停止、SSO セッションの時間、access token の有効期間
  - ローカル用の管理者 1 人、閲覧者 1 人
- [x] **T3106** ログインの画面に、店舗のロゴと色だけを合わせる（既定のテーマを元にした最小のテーマ。spec の Non-goals）。
- [x] **T3107** README の「起動とテスト」を直す：Keycloak と Mailpit の URL、ローカル用のアカウント、realm の設定を変えたときの取り込み直し方。skill `local-dev` に、Keycloak・Mailpit の起動と確認の手順を足す。
- [x] **T3108** 手動確認（Keycloak のアカウントの画面で行う）。
  - 消費者：会員登録（メールアドレスと氏名）→ 確認のメールが Mailpit に届く → リンクを開いてパスワードを設定する → ログインできる。登録済みのメールアドレスと、条件を満たさないパスワードは拒否される（AC-109、110）。パスワードの再設定のメールが届き、登録されていないメールアドレスでも同じ表示になる（AC-118）。5 回の失敗で一時的に止まる（AC-116）
  - 運営者：会員登録の入口がない（AC-134）。管理者・閲覧者でログインできる。会員のアカウントでは運営者の realm にログインできない（逆も。AC-133）
  - 発行された access token に audience が載り、運営者の token には `resource_access.ec-admin-api.roles` に職務を展開した権限が載る
