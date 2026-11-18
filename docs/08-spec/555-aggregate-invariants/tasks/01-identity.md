# 段階 1：規則と同一性

- 前提：plan と tasks が承認された。#476 がマージされ、開始の合図があり、実装を始める前の照合（[開発の進め方](../../../05-開発規則/workflow.md)の「実装を始める前の照合」）が完了した。
- 完了条件：DC-555-1、DC-555-7〜10 を満たす。DC-555-8 は、段階 1 では一覧と対応を T555-01-01 の結果に書いたところまでとし、pull request への記載は段階 2 の T555-02-10 で行う。変えたモジュールのテスト、`EntityEqualityTest`、`EntityEqualityAssertionTest` が通る。
- 参照：[plan](../plan.md)の「規則の文書」、[plan](../plan.md)の「entity と集約の同一性」、[plan](../plan.md)の「テストの等価の検査」、[spec](../spec.md)の DS-555-1、DS-555-2

## タスク

実装は実装役（汎用）に 1 タスクずつ任せる。T555-01-04 は T555-01-02（文書）と並行できる。

- [x] **T555-01-01** 参照の同一性か record の全項目の等価に頼る所を、main とテストで調べ、一覧と対応をこのタスクの結果に書く（DC-555-8）。
  - 依存：なし
  - 範囲：調査だけ（コードは変えない）
  - 確認方法：結果に一覧がある
  - 結果：main は 0 件。entity と集約（plan の対象の 14 個）を Set・Map のキー、`remove`・`contains`・`indexOf`、`==`・`equals`・`hashCode`・`toString` で扱う所はない（Map の値に入れる所と、識別子で照合する所だけ）。テストは次の 12 件で、Category の 1 件（※1）を除き、識別子での比較に変えると、テストの確認が弱くなる。対応は、12 件とも項目ごとか `usingRecursiveComparison` で比べる形に書き換える（T555-01-04、T555-01-05）。漏れは T555-01-07 の検査で見つける。

    | ファイル | 行 | 型 | 今の比べ方 |
    |----------|----|----|------------|
    | `JooqAssetRepositoryTest` | 61、96、126、154 | Asset | `containsExactly`・`containsExactlyInAnyOrder` |
    | `JooqCheckoutRepositoryTest` | 76、77 | Checkout | `contains` |
    | `TaxCategoryTest` | 275、517 | TaxRate | `containsExactly`、`isEqualTo` |
    | `ProductImageTest` | 428 | ProductImage | `containsExactly` |
    | `JooqProductRepositoryTest` | 462、588 | ProductImage、Variant | `containsExactly` |
    | `CategoryTest` | 330 | Category | `containsExactlyInAnyOrder` ※1 |

    ※1：同じインスタンスどうしの比較で、識別子で比べても結果は同じ。T555-01-07 の検査に当たるので、書き換える。
- [x] **T555-01-02** [P] 規則の文書の本文だけを書く（不変条件の置き場所、entity と集約を record にしない、テストの等価）。規則の確認方法はレビューにしておく（DC-555-1、DC-555-9）。
  - 依存：なし
  - 範囲：`docs/05-開発規則/backend-coding.md`、`docs/05-開発規則/test-writing-backend.md`
  - 確認方法：文書の検査（`doc-refs`、`ja-text`）が通る。規則の確認方法はレビューにしてある
- [x] **T555-01-03** [テスト] 同一性の規則を検査する ArchUnit のテストを書き、レッドを確認する（DC-555-7）。
  - 依存：なし
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/architecture/EntityEqualityTest.java`
  - 確認方法：テストが失敗する。テストを通すためにテストを弱めない
- [ ] **T555-01-04** [P] class の 7 個（Category、Product、Order、Payment、TaxCategory、Cart、Shipment）に Lombok の equals を付ける。Cart と Shipment には注釈も付ける。基底クラスを比較に含めない指定を `lombok.config` に置く。等価に頼るテストを修正する（DC-555-7）。
  - 依存：T555-01-01、T555-01-03
  - 範囲：各モジュールの domain、`backend/ecsite/lombok.config`、対象の型のテスト
  - 確認方法：変えたモジュールのテストが通る。`-Werror` のときに Lombok の callSuper の警告が出るかを確認する
- [ ] **T555-01-05** record の 7 個（ProductImage、Variant、StockAdjustment、Checkout、TaxRate、Asset、Member）を class に変え、Lombok の equals を付ける。Asset と Member には注釈も付ける。全項目の等価に頼るテストを、項目ごとか `usingRecursiveComparison` に書き換える。`EntityEqualityTest` が通った後に、backend-coding.md の「entity と集約の同一性」の確認方法を自動に変える（DC-555-7、DC-555-8）。
  - 依存：T555-01-04（`lombok.config` の基底クラスの指定を前提にするため）
  - 範囲：各モジュールの domain、対象の型のテスト、`docs/05-開発規則/backend-coding.md`
  - 確認方法：同一性の検査のテストと、変えたモジュールのテストが通る
- [ ] **T555-01-06** [テスト] ソースセットと `build.gradle.kts` の組み込みを試作し、例の表の全てを入れた検査のテストを書き、レッドを確認する。組み込めなければ止めて報告する（DC-555-10）。最初に、例の表を spec の文脈を持たないレビュー役に読ませ、例を追加させる。
  - 依存：T555-01-05
  - 範囲：`backend/ecsite/src/errorprone/java/`、`backend/ecsite/build.gradle.kts`、`backend/ecsite/gradle/libs.versions.toml`、`backend/ecsite/src/test/java/com/ecsite/errorprone/EntityEqualityAssertionTest.java`
  - 確認方法：検査のテストが失敗する。追加した依存がライセンスの検査に通る。`assertThat(T)` の引数の静的な型がクラスファイルに残らないことを確認する
- [ ] **T555-01-07** T555-01-06 のテストが通る検査を実装し、テストのソースセットに当てる。見つかったテストを修正する。検査が動いた後に、test-writing-backend.md の規則の確認方法を自動に変える（DC-555-10）。
  - 依存：T555-01-06
  - 範囲：`backend/ecsite/src/errorprone/java/`、見つかったテスト、`docs/05-開発規則/test-writing-backend.md`
  - 確認方法：検査のテストが通り、test・integrationTest・testFixtures のコンパイルが通る
- [ ] **T555-01-08** 段階の最後のレビュー。等価の検査の例の表と、判定の区分（誤りの防止）でレビューする。指摘を修正する。
  - 依存：T555-01-01〜07
  - 確認する項目：変えたコードを[書き方の規則とレビュー](../../../05-開発規則/review.md)と照合する。
  - 結果：
