# 段階 3：cart、media、storefront

- 前提：段階 2
- 完了条件：cart に mapper があり、3 つのモジュールの presentation に、生成したモデルの引数を取るコンストラクターの呼び出しと、Controller の中の変換がない。media の公開の API が `api/` の部品から service に委ねる。storefront に、加工のない写しの型と、並び順の重なった写しがない。既存のテストが、名前の変更のほかは変えずに通る。
- 参照：[plan](../plan.md) 第 3 節、第 5.4 節、第 6 節、[バックエンドの書き方](../../../backend/coding.md) 第 1.1 節、第 2.2 節

## タスク

- [ ] **T013-03-01** cart の mapper を作り、Controller の変換（会員のカート、統合の結果と品目）を移す。生成したモデルを項目名のメソッドで組み立てる。作成の応答を共通の補助にする。
- [ ] **T013-03-02** [テスト] ArchUnit：`api/` の型を実装するクラスは `api/` にある（plan 第 2.2 節）。今の media の service が違反して失敗することを確かめる。
- [ ] **T013-03-03** media の `api/` に、公開の API を実装する `@Component` を作り、application の service に委ねる。service から `implements` を外す。T013-03-02 を通す。バックエンドの書き方の規則の一覧の確かめ方を「自動（ArchUnit）」にする。
- [ ] **T013-03-04** storefront の application の view（商品の概要、商品の詳細）をなくし、port の型と在庫の有無を mapper に渡す（plan 第 5.4 節）。
- [ ] **T013-03-05** storefront の並び順の写しを、腐敗防止層の実装の 1 か所と、presentation の mapper の 1 か所にする。一覧の条件の組み立てを Controller から mapper に移す。カテゴリーの木の生成したモデルを項目名のメソッドで組み立てる。
- [ ] **T013-03-06** 3 つのモジュールの application で、参照だけのメソッドを探し、読み取り専用のトランザクションにする。直した所をタスクの下に書く。
- [ ] **T013-03-07** storefront と media の設計書の「構成」を、今のパッケージに合わせる。
- [ ] **T013-03-08** 段階の完了の確認：`./gradlew check`、`check-frontend.sh`、`e2e.sh` が通る。テストの差分の検査が通る。
