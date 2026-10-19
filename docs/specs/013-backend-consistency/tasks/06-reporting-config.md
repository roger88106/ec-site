# 段階 6：reporting、config、残りの ArchUnit の規則

- 前提：段階 5
- 完了条件：reporting の query と report の record が `application/model/` にあり、設計書の「構成」が今のパッケージと合う。イベントの再送の間隔が設定の型で検証され、ロックの上限が定数。生成したモデルのコンストラクターと、設定の型を有効にする宣言の ArchUnit の規則が有効。使われないエラーの公開の仕組みがない。既存のテストが、名前の変更のほかは変えずに通る。
- 参照：[plan](../plan.md) 第 2.2 節、第 7 節、第 8 節、第 9 節、[レポートモジュール](../../../backend/modules/reporting.md)

## タスク

- [x] **T013-06-01** reporting の application の直下の query と report の record 10 個を、`application/model/query/`・`application/model/view/` に移す。`renames.txt` に対応を足す。
- [x] **T013-06-02** reporting の domain（15）を分けない理由を、バックエンドの書き方 第 1.4 節の表に足す。reporting の設計書の「構成」を、今のパッケージ（区切りと年齢層は domain にある）に合わせる。
- [ ] **T013-06-03** reporting の mapper と Controller の生成したモデルを、項目名のメソッドで組み立てる。
- [ ] **T013-06-04** テストだけが使う、都道府県のコードの一覧に、テスト用であることを Javadoc に書く（DC-141。016 で扱いを決める）。
- [ ] **T013-06-05** イベントの再送の間隔を、イベントの再送の設定の型の `Duration` の項目にする。キーと既定値（1 分）は今と同じ。`@Scheduled` は SpEL で設定の型の Bean を参照する。設定の型を有効にする宣言を設定のクラスに移す。
- [ ] **T013-06-06** ロックの上限（10 分）を定数にし、ShedLock の注釈が定数しか取らないことと、環境で変えないことをコメントに書く。
- [ ] **T013-06-07** media の画像の変換の、設定の型を有効にする宣言を、media の設定のクラスに移す。
- [ ] **T013-06-08** [テスト] ArchUnit：`@EnableConfigurationProperties` を付けるクラスは `@Configuration`（plan 第 2.2 節）。今のコードで通るので、`break-check.mjs` で仮に設定のクラスでない Bean に付けて失敗することを確かめ、戻す。規則の一覧の確かめ方を「自動（ArchUnit）」にする。`@Scheduled` に `${` がないことを grep で確かめる。
- [ ] **T013-06-09** [テスト] ArchUnit：presentation は、生成したモデル（`com.ecsite.api..`）の引数を取るコンストラクターを呼ばない（plan 第 2.2 節）。今のコードで通るので、`break-check.mjs` で仮に 1 か所を位置の形に戻して失敗することを確かめ、戻す。規則の一覧の確かめ方を「自動（ArchUnit）」にする。
- [ ] **T013-06-10** 使われない、エラーの公開の段階の「下げる」と、4 つの引数を取る作り方を消す（DC-140）。共通のメッセージの補助の、private の見出しの下にある public のメソッドを、正しい見出しの下に移す。
- [ ] **T013-06-11** reporting の application で、参照だけのメソッドを探し、読み取り専用のトランザクションにする。直した所をタスクの下に書く。
- [ ] **T013-06-12** 段階の完了の確認：`./gradlew check`、`check-frontend.sh`、`e2e.sh` が通る。テストの差分の検査が通る。
