# 段階 8：レビュー

- 前提：段階 7
- 完了条件：DC と DN をすべて確認し、満たさないものを修正するか、修正しない理由を報告している。すべてのテスト、E2E、CI が通る。
- 参照：[plan](../plan.md) 第 7 節、`docs/style/review.md`

## タスク

- [x] **T4801** `docs/style/review.md` を使って、本 spec の変更をレビューする（DC-4 の初めての使用。使いにくい所があれば `review.md` を修正する）。
- [x] **T4802** 文書のレビュー：`.local/agent/check-zh.mjs --all` が通る。constitution、AGENTS.md、README、`docs/style/`、`docs/dev/`、skill を読み、中国語の言い回しや食い違いを修正する。
- [x] **T4803** plan 第 7 節の確認方法で、DC-1〜26、63 と DN-1〜3 を 1 つずつ確認して表にする。CI の所要時間（DN-3）を含める。
- [x] **T4804** 完了確認：修正したこと、修正せずに残したこと（理由付き）を報告し、spec 004 を完了にする。README の spec の状態を修正する。spec の区切りなので、引き継ぎの文書を作る。
- [x] **T4805** CI の所要時間を DN-3 に戻す：段階 7 の後の CI が 11 分 59 秒で、上限（10 分 37 秒）を超えた。主な原因は、`seaweedfs-init` が seed の画像を 1 枚ずつ AWS CLI で確認してコピーすること（画像の置き場所の起動が 32 秒から 82 秒）。1 回の `aws s3 sync` にする。
- [x] **T4806** CI の所要時間を DN-3 に戻す（2 回目）：T4805 の後の CI が 10 分 42 秒で、上限を 5 秒超えた。バックエンドの job の、別の手順にしたライセンスの検査（82 秒）が、Gradle の起動と依存の解決を 2 回にしている。ビルドと同じ Gradle の呼び出しで実行する。

## 確認した結果（T4803）

2026-10-13 に、plan 第 7 節の確認方法で確認した。○ は満たす、△ は残りの確認がある。

| DC・DN | 結果 | 確認したこと |
|--------|------|--------------|
| DC-1 | ○ | `docs/style/`（backend、frontend、testing、comments、review）に主題ごとの規則がある |
| DC-2 | ○ | AGENTS.md に skill の一覧（名前と、いつ使うか）がある |
| DC-3 | △ | 置き場所（`/.local/handoff/yyyyMMddHHmmss-<名前>/`）を AGENTS.md と skill `handoff` に書いた。T4804 で作る引き継ぎの文書を、次のセッションで読めるかが残る |
| DC-4 | ○ | T4801 で `docs/style/review.md` を使ってレビューした。使いにくかった所（移動の多い spec での変えたファイルの探し方）を `review.md` に追加した |
| DC-5 | ○ | `docs/notation.md` に番号と記号の辞書がある。AGENTS.md の最初に読むものに入っている |
| DC-6 | ○ | constitution 15 に、spec の 2 つの種類と書き方がある |
| DC-63 | ○ | README は全体像・文書の地図・進み具合だけ。手順・アカウント・環境変数は `docs/dev/` にあり、README から引ける |
| DC-7 | ○ | CI の push は、文書・skill・`scripts/dev/` などだけの変更では実行されない（`paths-ignore`）。T4804 の commit（7c161b2、MD だけ）を push して、CI が実行されないことを確認した（T4204）。pull request はいつも実行される設定 |
| DC-8 | △ | 直下に `LICENSE`（MIT）、各 `package.json` に `"license": "MIT"`。GitHub の判定は既定のブランチ（main）を見るので、develop を main に入れた後に確認する |
| DC-9 | ○ | constitution 17 と `scripts/ci/licenses/policy.json`（許可・条件付き・禁止、有料の版がある製品の扱い） |
| DC-10 | ○ | 手元で禁止の部品を追加すると、バックエンド（`checkLicense`）とフロント（`check-frontend.mjs`）の検査がそれぞれ失敗した（T4506）。どちらも CI で実行する |
| DC-11 | ○ | CI の artifact（coverage-backend、coverage-admin、coverage-store）と、job の summary の合計 |
| DC-12 | ○ | `ac-check.mjs` が CI で通る。範囲の表記と足りない AC で失敗することを script のテストで確認する |
| DC-13 | ○ | `SiteHeader.test.tsx`（AC-119） |
| DC-14 | ○ | 店舗画面のサーバーの処理のテスト（`actions/cart`、`api/cart`、`actions/checkout`、`auth`、`proxy`） |
| DC-15 | ○ | `CategoryPage.test.tsx` |
| DC-16 | ○ | `ImgproxyImageConverterFailureTest`、`S3ObjectStorageTest`、`MediaApiTest` |
| DC-17 | ○ | テストに `Thread.sleep`・`waitForTimeout` がない。E2E の税区分の 32 秒の待ちは T4801 で削除した（残る待ちは、条件を確認する繰り返しだけ） |
| DC-18 | ○ | 直下は `api/`、`backend/ecsite/`、`docker/`、`docs/`、`e2e/`、`frontend/`、`scripts/` と、ルートの設定 |
| DC-19 | ○ | 移す前後で、ローカルの DB・画像・IdP のデータの数が同じ（T4405） |
| DC-20 | ○ | `pnpm-lock.yaml` は 1 つ。ルートの `pnpm install` で 3 つのプロジェクトに入る。共通の Biome と TypeScript の設定 |
| DC-21 | ○ | 古い置き場所を指す記述が、今のルールの文書にない（T4404、T4802 で検索） |
| DC-22〜24 | ○ | 手元で E2E を 2 回続けて実行し、どちらも 12 件が通った。前後で、ローカルの DB の行数（288）と Keycloak のアカウントの数（5）が変わらない（T4606、T4803 で 2 度） |
| DC-25 | ○ | seed を `db/seed/base/`（ローカルと E2E）と `db/seed/local/` に分けた。E2E だけに必要なデータはない |
| DC-26 | ○ | ローカルの DB を再作成して画面で確認した（T4706） |
| DN-1 | ○ | `api/` に差分がない。テストは CI ですべて通る |
| DN-2 | ○ | 移動の commit（77c6efc）は 452 ファイルがすべて中身の変わらない移動。ただし `gradlew` の実行の権限が落ち、T4409 で戻した |
| DN-3 | △（利用者の判断で受け入れ） | 基準 9 分 39 秒、上限 10 分 37 秒。段階 6 の後 9 分 44 秒。段階 7 の後 11 分 59 秒で、seed の画像のコピー（T4805）とライセンスの検査の手順（T4806）を修正した。T4806 の後は 10 分 49 秒で、上限を 12 秒超える。バックエンドの job（後に E2E が続く）にライセンスの検査が加わった分と、ランナーの揺れ（±1 分ほど）による。利用者が「できればよく、際限なく延びなければよい」と判断し、これ以上は詰めない |
