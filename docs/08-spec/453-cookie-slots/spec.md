# Spec 453：ローカルのスロットの店舗画面を、スロットごとのホスト名で開く

種類：整理　規模：M　ステータス：確定

## 背景と目的

店舗画面の Cookie はポートで分かれない。今は、スロットごとに環境変数 `STORE_COOKIE_PREFIX` で Cookie の名前の接頭辞を変えている（[開発環境の起動](../../06-手順書/setup.md)の「スロット」）。接頭辞が違う Cookie は互いを上書きしないので、全てのスロットの Cookie が同じ `localhost` に残り、`localhost` の全てのポートへのリクエストに付く。リクエストに付く Cookie の大きさの合計がヘッダーの上限を超えると、mock の支払画面（バックエンド）が 400 を、店舗画面のサーバーアクションが 431 を返す。

店舗画面をスロットごとのホスト名（`slot<番号>.localhost`）で開き、Cookie をホストで分ける。ブラウザー（Chromium など）は `*.localhost` をループバックアドレスに解決する。開発環境だけの変更で、消費者・運営者に見える振る舞い（本番）は変わらない。追跡の issue は #453（全体の issue #544 の sub-issue）。

## スコープ

| 含める | 含めない |
|--------|----------|
| スロット 1〜3 の店舗画面のホスト名の変更 | スロット 0 と E2E の URL の変更 |
| 店舗の realm の client への、新しい origin の追加 | バックエンド・管理画面の URL の変更 |
| [開発環境の起動](../../06-手順書/setup.md)の更新 | `STORE_COOKIE_PREFIX` の廃止 |

決定：

- スロット 1〜3 の店舗画面は、ブラウザーから `http://slot<番号>.localhost:<店舗画面のポート>` で開く（例：`http://slot1.localhost:13000`）。Cookie はスロットごとのホストに設定されるので、他のスロットと `localhost` へのリクエストには付かない。
- ホスト名を変えるのは、ブラウザーが開く店舗画面の URL だけ。対象は下の表のとおり。スロット 0（メインの作業ツリー）と E2E の URL、バックエンド・管理画面の URL、サーバーからバックエンドを呼ぶ URL は変えない（理由は Non-goals）。
- 店舗の realm の client に、スロット 1〜3 の新しい origin を加える（リダイレクト先、Web の origin、ログアウト後のリダイレクト先）。今の `localhost:<ポート>` の origin も残す。理由：この変更をマージしていない並行の作業ツリー（今の `localhost` の URL で動く）が、realm の反映の後もログインできるようにするため。
- `STORE_COOKIE_PREFIX` は残す（理由は Non-goals）。
- Next.js の開発サーバーは既定で `*.localhost` からのリクエストを許す（`allowedDevOrigins` の既定）ので、許可する origin の設定は加えない。
- [開発環境の起動](../../06-手順書/setup.md)の「スロット」を更新する（DC-453-6、DC-453-7）。

| ホスト名を変える対象 | 置き場所 |
|----------------------|----------|
| Auth.js の公開 URL | 環境変数 `AUTH_URL` |
| 支払後の戻り先 | 環境変数 `ECSITE_PAYMENT_RETURN_URL` |
| スロットの URL の表示 | `node scripts/dev/slot.mjs show` |
| 店舗画面のスロットの起動設定 | `.claude/launch.json` |

## 目的と完了条件

### DS-453-1 複数のスロットを使っても、他のスロットの Cookie で店舗画面と mock の支払画面が失敗しない（D / P1）

- **DC-453-1** スロット 1〜3 で、店舗画面の公開 URL と支払後の戻り先が、`http://slot<番号>.localhost:<ポート>` になっている。`slot.mjs` の環境変数、`show` の表示、`.claude/launch.json` の全てで揃っている。
- **DC-453-2** 店舗の realm が、スロット 1〜3 の新しい origin をリダイレクト先、Web の origin、ログアウト後のリダイレクト先に含む。今の `localhost` の origin も残っている。
- **DC-453-3** スロット 0 と E2E の URL が、変更の前と同じ `localhost` である。
- **DC-453-4** スロットの店舗画面で、ログイン、mock の支払画面への遷移と戻り、ログアウトができる。
- **DC-453-5** スロットの店舗画面の Cookie が、`slot<番号>.localhost` に設定されている（`localhost` に設定されていない）。

### DS-453-2 開発者が手順書で、ホスト名で分けることと、残った Cookie の削除の方法を読める（D / P2）

- **DC-453-6** `docs/06-手順書/setup.md` の「スロット」の表と本文が、スロットの店舗画面の URL と、ホスト名で分ける理由を示している。
- **DC-453-7** 同じ節に、移る前に `localhost` に残った店舗画面の Cookie を削除する手順がある。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| スロットの値と環境変数 | `scripts/shared/slot/slot.mjs`、`scripts/shared/slot/slot.test.mjs` |
| スロットの script の表示 | `scripts/dev/slot.mjs` |
| realm | `docker/keycloak/realms/ec-store-realm.json` |
| 起動設定 | `.claude/launch.json` |
| 手順書 | `docs/06-手順書/setup.md` |

## 非機能要件

- **DN-453-1** 本番、CI、E2E、スロット 0 の振る舞いを変えない。
- **DN-453-2** 店舗画面のコード（`frontend/store/`。とくに `proxy.ts` と `auth/`）を変えない。
- **DN-453-3** realm の変更は加えるだけにし、今の origin を削除しない。

## 依存

| 対象 | 関係 |
|------|------|
| #560（店舗画面の token の更新の経路） | `frontend/store/src/auth/` と `proxy.ts` を変える。本 spec はそこを変えない |
| 共有の基盤（Keycloak） | realm の JSON を変える。マージの後、メインの作業ツリーで反映する（[開発環境の起動](../../06-手順書/setup.md)の「共有の基盤の設定を変える変更をマージしたとき」） |

## Non-goals

| 項目 | 理由 |
|------|------|
| `STORE_COOKIE_PREFIX` の廃止 | 名前を変える処理は店舗画面の `auth/` にあり、#560 の範囲と重なる。接頭辞は Cookie の名前を変えるだけなので、ホストで分けても妨げにならない |
| スロット 0・E2E のホスト名の変更 | スロット 0 は「起動」の手順の値のまま動かす（setup.md）。E2E は別のブラウザーで動くので、Cookie が混ざらない |
| バックエンド・管理画面のホスト名の変更 | 失敗の原因は店舗画面の Cookie だけ。変更の後、mock の支払画面（バックエンド）に届くのは、スロット 0 の店舗画面の Cookie だけになる |
| ヘッダーの大きさの上限を上げる | 原因（Cookie が全てのスロットに送られる）を残し、スロットを増やすとまた失敗するため |

## Open questions

なし。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。

- [ ] **T453-01-01** [テスト] スロットの値のテストを、新しい URL に合わせて書き換え、失敗することを確認する（DC-453-1、DC-453-2、DC-453-3）。
  - 依存：なし
  - 範囲：`scripts/shared/slot/slot.test.mjs`
  - 確認方法：`node --test scripts/shared/slot/slot.test.mjs` が失敗する（レッド）
- [ ] **T453-01-02** スロットの値と環境変数を変える。`show` の表示と realm と `.claude/launch.json` も合わせる（DC-453-1、DC-453-2、DC-453-3）。
  - 依存：T453-01-01
  - 範囲：`scripts/shared/slot/slot.mjs`、`scripts/dev/slot.mjs`、`docker/keycloak/realms/ec-store-realm.json`、`.claude/launch.json`
  - 確認方法：`node --test scripts/shared/slot/slot.test.mjs` が通る。`node scripts/ci/config/local-ports.mjs` が通る
- [ ] **T453-01-03** 手順書を書き換える（DC-453-6、DC-453-7）。
  - 依存：T453-01-02
  - 範囲：`docs/06-手順書/setup.md`
  - 確認方法：`node scripts/ci/docs/doc-refs.mjs` と `node scripts/ci/ja/ja-text.mjs --warning`
- [ ] **T453-01-04** [手動] スロットの店舗画面の操作（DC-453-4 の 3 つ）と、Cookie のホストを確認する（DC-453-4、DC-453-5）。
  - 依存：T453-01-02
  - 範囲：動いている Keycloak の店舗の client（加えるだけ）
  - 確認方法：issue 担当のセッションに知らせてから、Keycloak の Admin REST API で client に新しい origin を加える。ブラウザーで操作し、Cookie の一覧を見る
- [ ] **T453-01-05** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T453-01-03、T453-01-04
- [ ] **T453-01-06** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T453-01-05
- [ ] **T453-01-07** spec をアーカイブに移す。
  - 依存：T453-01-06
