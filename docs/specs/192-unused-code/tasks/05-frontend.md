# 段階 5：フロントの削除と knip

- 前提：段階 4（CI の検査の文書を共に変えるため）。spec 194 のマージの有無を確認し、マージ済みなら `develop` を取り込んだ（[plan](../plan.md) 第 9 節）。
- 完了条件：DC-192-9〜12 を満たす。knip が、違反の例で違反を検出し、今のコードで違反が 0 件である。`scripts/dev/check-frontend.sh`、`scripts/ci/check-all.sh` が通る。
- 参照：[plan](../plan.md) 第 5 節、[plan](../plan.md) 第 6 節、[plan](../plan.md) 第 9 節、[spec](../spec.md) の DS-192-3 と DC-192-9〜12、DN-192-1〜5、[フロントの書き方](../../../rules/frontend-coding.md)

## タスク

実装は実装役のサブエージェントに任せる。knip の設定は、knip の公式の文書を読んでから決める。T192-05-03 で検査を加えると、残った違反で knip が失敗するので、T192-05-03 は T192-05-01、02 の後に行う。

- [ ] **T192-05-01** どこからも使われない API 型の別名を削除する。`cn` を再び export する 3 つのファイルを削除し、店舗画面のレイアウトはパッケージ `cn` から import する（DC-192-9、10）。フロントの書き方、フロントの構成、管理画面と店舗画面の構成を直す。
  - 依存：なし
  - 範囲：`frontend/store/src/api/types.ts`、`frontend/admin/src/api/types.ts`、`frontend/admin/src/lib/utils.ts`、`frontend/store/src/lib/utils.ts`、`frontend/store/src/app/layout.tsx`、`frontend/shared/src/lib/cn.ts`、`docs/rules/frontend-coding.md`、`docs/design/frontend-architecture.md`、`docs/design/apps/admin.md`、`docs/design/apps/store.md`
- [ ] **T192-05-02** 定義したファイルの中だけで使う export を外し、外して使われていないと分かったものを削除する（DC-192-11）。対象は [plan](../plan.md) 第 5.1 節の表である。E2E は、spec 194 が未マージでも同じに扱い、変更を export を外すことだけにする。
  - 依存：T192-05-01
  - 範囲：[plan](../plan.md) 第 5.1 節の表のファイル
- [ ] **T192-05-03** knip 6 をルートの devDependencies に加え、`knip.json` を作る。生成物の export を、理由を付けて対象外にする。`check-frontend.sh` と CI のフロントのジョブで実行する。違反の例を一時的に入れて、knip が失敗することを確認する（DC-192-12）。フロントの書き方と CI の検査の文書に、knip が確認することを書く。
  - 依存：T192-05-02
  - 範囲：`package.json`、`pnpm-lock.yaml`、`knip.json`（新規）、`scripts/dev/check-frontend.sh`、`.github/workflows/ci.yml`、`docs/rules/frontend-coding.md`、`docs/guides/ci.md`
- [ ] **T192-05-04** 段階のレビュー（profile「厚め」）と全体の検査：`scripts/dev/check-frontend.sh`、`scripts/ci/check-all.sh` を実行する。DC-192-9〜12 を満たすことを確認する。指摘を修正する。
  - 依存：T192-05-01〜03
