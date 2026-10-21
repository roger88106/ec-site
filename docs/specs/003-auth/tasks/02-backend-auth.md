# 段階 2：バックエンドの認証の土台

- 前提：段階 1
- 完了条件：AC-136〜138（管理 API の側）、NFR-3 のテストと、IdP の設定の統合テストが通る。既存のテストがすべて通る（`./gradlew check`）。
- 参照：[plan](../plan.md) 第 3.1〜3.3 節、第 5.2 節、constitution 10.2、`admin.yaml`

## API の契約

- [x] **T3201** `admin.yaml` を修正する：説明の「認証は MVP では実装しない」を、認証の方式（運営者の realm の token と、`x-required-permission` の権限）の説明に書き換え、全操作に 401・403 の応答を追加する。`store.yaml` には、401 の応答の部品だけを追加する（操作に付けるのは段階 3）。

## 認証と認可

- [x] **T3202** Spring Security の Resource Server を依存に追加し、両 realm の issuer と audience を設定に持つ（環境変数で上書きできる）。テスト用に、権限や IdP のアカウントの ID を指定して token を付ける補助を作る（Spring Security のテストの機能を使い、Keycloak は起動しない）。
- [x] **T3203** [テスト] 認可の統合テスト。
  - 管理 API：token がないと 401、権限があれば通る、権限が足りないと 403（AC-136、137）
  - `admin.yaml` にない要求（メソッドかパスが違う）は、権限があっても拒否される（NFR-3）
  - 401・403 が Problem Details（`errorCode`、`message`、`traceId`）で返る
  - 店舗 API：今までどおり認証なしで使える endpoint が、token なしで使える
- [x] **T3204** [テスト] token から権限への変換の単体テスト：`resource_access.ec-admin-api.roles` だけを権限にし、ほかの client の role と realm の role は権限にしない。
- [x] **T3205** 認証と認可を実装する（plan 第 3.1〜3.3 節）。T3203、T3204 を通す。
  - パスごとの filter chain と、realm ごとの token の検証（issuer と audience）
  - 権限の変換（1 か所）
  - `admin.yaml` をビルドのときにリソースにコピーし、起動時に読んで、要求ごとに必要な権限を判定する。対応にない要求は拒否する
  - 401・403 の Problem Details
- [x] **T3206** 既存の統合テストのうち、管理 API を呼ぶものに、必要な権限の token を付ける。テストの意図は変えない。
- [x] **T3207** [テスト] `admin.yaml` の権限と、運営者の realm の JSON の `ec-admin-api` の client role が一致し、`administrator` がすべての権限を、`viewer` が `:read` の権限すべてを含むことを確認する契約テスト（Keycloak は起動しない）。
- [x] **T3208** [テスト] IdP の設定の統合テスト（Keycloak を Testcontainers で起動し、両 realm の JSON を取り込む）。token は、テストの中で web の client のパスワードによる取得（Direct Access Grant）を有効にして取る（realm の JSON には入れない。画面と同じ client の token で確認するため）。
  - 運営者の token で、管理 API を職務どおりに使える：管理者は更新でき、閲覧者は参照だけで、更新は 403（AC-136、139）
  - 会員の token では、管理 API は 401（AC-138）
  - 署名の不正な token は 401（AC-137）。期限切れは、検証の処理が同じなので、T3203 のテストの鍵の token で確認する（Keycloak の token の有効期間 5 分を待たないため）
  - 会員と運営者のアカウントが別の realm にあり、ほかの realm ではログインできない（AC-133）。access token の有効期間が 5 分（AC-140）
  - 店舗の側（運営者の token で会員向けの操作が 401）は、操作ができる段階 3 で追加する（T3313）
- [x] **T3209** [テスト] realm の設定の契約テスト（両 realm の JSON を読む。Keycloak は起動しない）：登録済みのメールアドレスを許さない（AC-109）、パスワードの条件（AC-110）、メールアドレスの確認が必須（AC-111、112）、ログインの失敗による一時的な停止（AC-116）、SSO セッションの時間（AC-117）、パスワードを忘れたときが有効（AC-118）、運営者の realm は会員登録が無効（AC-134）、access token の有効期間（AC-140）が plan 第 2.3 節のとおりであること。会員と運営者のアカウントが別の realm にあること（AC-133）は、T3208 と段階 1 の手動確認で確認する。
