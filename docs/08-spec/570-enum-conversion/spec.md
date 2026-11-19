# Spec 570：同名の列挙の変換を、共通の変換のメソッドにする

種類：整理　規模：M　ステータス：確定

## 背景と目的

定数の名前の集合が同じ列挙の間は、共通の変換のメソッドで、名前で変換してよい。変換元と変換先の組ごとに、全ての値を変換できることを単体テストで確認する。この方針は #558 で決め（2026-11-18）、規則を[バックエンドの書き方](../../05-開発規則/backend-coding.md)の「他のモジュールは腐敗防止層で包む」に書いた。

今は、全ての値を同名の定数に変換する switch 式が、本番のコードに 49 個ある。内訳は次のとおり。

| 場所 | 個数 |
|------|------|
| presentation と生成したモデルの間 | 25 |
| 腐敗防止層（`infrastructure/directory/`） | 10 |
| `api/` | 7 |
| ドメインの列挙からイベントの列挙 | 4 |
| その他（reporting の application の service） | 3 |

この spec は、同名の列挙の変換を共通の変換のメソッドにまとめる。消費者・運営者に見える振る舞いは変えない。追跡の issue は #570（全体の issue #544 の sub-issue。段階 2 の「#558 の決定から作った issue」）。

規模は、[開発の進め方](../../05-開発規則/workflow.md)の「規模」の判定の表では、2 つ以上のモジュールにまたがるので L になる。ただし、API の契約、DB の構造、AC を変えない。全ての箇所に同じ機械的な規則を適用する整理なので、特例で M とする（issue 担当のセッションが、利用者の委任で承認する）。

## スコープ

| 含める | 含めない |
|--------|----------|
| 共通の変換のメソッドを common に作ること | 名前が違う変換と、1 対 1 でない変換の switch 式（そのまま残す） |
| 名前の変換を禁止する 2 つの検査を、共通の変換のメソッドの中だけ許す形に変えること ※1 | 変換元より変換先の定数が多い組（決定 2） |
| 定数の名前の集合が同じ組の switch 式（48 個）を、共通の変換のメソッドの呼び出しに置き換えること | テストのコードの中の switch 式（変換でなく、テストの準備のため） |
| 列挙の変換の組ごとに、全ての値を変換する単体テストを置くこと（今あるテストは形を合わせ、テストのない組には加える） | 列挙を名前の文字列から読む永続化（`infrastructure/persistence/`）の `valueOf`（今のまま許す） |
| 規則の確認方法の文と、検査の説明を、変えた検査に合わせること ※2 | 同じ値の列挙を 1 つにまとめること（#569 が公開の型の重複を扱う） |

※1：ArchUnit の `EnumStringConversionTest` と、`scripts/ci/java/java-rules.mjs`。

※2：`backend-coding.md` の同じ節の「確認方法」と、[CI の検査の script](../../06-手順書/ci-checks.md)の java-rules の行。

決定：

- 共通の変換のメソッドは、common の下に新しいパッケージ `com.ecsite.common.conversion` を作り、クラス `EnumConversion` の static メソッド `byName(source, targetType)` として置く。変換元の定数と同じ名前の、変換先の定数を返す。変換先に同名の定数がなければ `IllegalArgumentException` を投げ、変換元と変換先の型の名前と定数の名前をメッセージに入れる。null は受け取らず、呼び出し元が扱う（今の switch 式と同じ）。理由：common の既存のパッケージ（`error`、`message`、`model`、`paging`、`persistence`、`presentation`、`scheduling`、`security`、`validation`）は、どれも列挙の変換の役割に合わないため。domain からも呼ぶので、`presentation` の下には置けない。
- 変換元より変換先の定数が多い組は、switch 式のまま残す。該当は 1 個で、在庫の presentation の、運営者が選べる調整理由（4 値）から、ドメインの調整理由（6 値。注文と注文のキャンセルが加わる）への変換。理由：規則は「定数の名前の集合が同じ列挙の間」だけを名前の変換の対象にしているため。
- 共通の変換のメソッドは、変換するメソッド（今の switch 式を持つメソッド。例：注文の状態をレスポンスの状態にするメソッド、ドメインの列挙の `toEvent`）の本体から呼ぶ。変換するメソッドは残し、呼び出し元は変えない。理由：変換の組を、変換するメソッドの引数と戻り値の型で示せるため。ドメインの列挙からイベントの列挙への変換は、規則でドメインの列挙のメソッドに置くと決まっているため。
- 単体テストは、組ごとに、変換するメソッドを変換元の全ての値で呼び、同じ名前の定数が返ることを確認する（JUnit の `@EnumSource`）。テストは、変換するクラスのテストのクラスに置く。変換するメソッドが private なら、テストから呼べるよう package-private にする。理由：組の型の検査（変換先の型の誤り）と値の検査を、実際に変換するメソッドで同時に確認できるため。
- 今ある変換のテスト（`ProductMapperTest`、`InventoryMapperTest`、`StorefrontMapperTest`、`CatalogApiSortTest`、`OrderApiGenderTest`、`OrderQuoteApiTest`、`CarrierTest`、`ShippingApiCarrierNameKeyTest`、notification・reporting・storefront の `ShippingApiDirectoryTest` と `OrderApiDirectoryTest`、`ReportCollectionCommandServiceTest` など。正確な一覧は実装で調べる）は、上の形にそろえる。期待値は変えない（同じ名前の定数が返ること）。switch 式のまま残す組のテストも、全ての値を変換するテストにする。理由：規則は switch 式の組にもテストを求めているため。
- ArchUnit の `EnumStringConversionTest` は、今の対象（列挙型の `valueOf(String)` と `fromValue`）に加えて、`java.lang.Enum` の `valueOf(Class, String)` も対象にする。許す場所は、domain、永続化（`infrastructure.persistence`）、共通の変換のメソッドのクラス、HTTP のクエリの文字列を列挙にする設定の変換（`config` の `WebMvcConfig` の中のクラス）。設定の変換を許すのは、永続化と同じく外から来る文字列を読む場所で、変換先の型が実行時にしか決まらず switch 式にできないため（2026-11-19 に issue 担当のセッションが利用者の委任で決めた）。規則の文（バックエンドの書き方の、名前の文字列から列挙にしてよい場所）にも、外から来る文字列を読む場所として加える。理由：共通の変換のメソッドは `Enum.valueOf(Class, String)` で書くため、今の検査では検出できない。他の場所に同じ書き方が入っても検出できるようにする。検査の例のクラス（`architecture/sample/enumconversion/`）に、共通の変換のメソッドの外で `Enum.valueOf(Class, String)` を呼ぶ例を加え、検査がそれを検出することをテストで確認する。
- `java-rules.mjs` の「列挙型の変換に `name()` を渡さない」は、共通の変換のメソッドのファイルだけを除外する。除外は script の先頭の「除外」に書く。script のテストに、除外のファイルでは検出されない例を加える。

## 目的と完了条件

### DS-570-1 同名の列挙の変換が、共通の変換のメソッドの 1 か所にまとまっている（D / P1）

- **DC-570-1** 定数の名前の集合が同じ列挙の組を、switch 式で変換している本番のコードがない。
- **DC-570-2** 共通の変換のメソッドは、変換先に同名の定数がないとき、例外を投げる。これを単体テストで確認している。
- **DC-570-3** 名前の違う変換、1 対 1 でない変換、変換先の定数が多い変換は、switch 式のまま残っている。

### DS-570-2 変換の書き忘れを、検査とテストで見つけられる（D / P1）

- **DC-570-4** 共通の変換のメソッドの外に、列挙の名前の文字列からの変換（`valueOf`、`fromValue`、`Enum.valueOf(Class, String)`）か、`name()` を渡す変換があると、検査（ArchUnit と `java-rules.mjs`）が失敗する。ただし、domain と永続化の `valueOf`・`fromValue` と、HTTP のクエリの文字列を列挙にする設定の変換は除く。
- **DC-570-5** 本番のコードの全ての列挙の変換の組（名前で変換する組も、switch 式の組も）に、変換元の全ての値を変換する単体テストがある。

### DS-570-3 規則の文書と検査の説明が、変えた検査と合っている（D / P1）

- **DC-570-6** `backend-coding.md` の確認方法の文と、`ci-checks.md` の java-rules の説明が、変えた検査の対象と除外に合っている。

### DS-570-4 既存の検査が通る（D / P1）

- **DC-570-7** `./gradlew check` と統合テストが通る。
- **DC-570-8** 文書の検査（`doc-refs.mjs`、`note-numbers.mjs`、`ja-text.mjs`）と、`java-rules.mjs` のテストが通る。

## 変える範囲

| まとまり | ファイル |
|----------|----------|
| 共通の変換のメソッド | `backend/ecsite/src/main/java/com/ecsite/common/conversion/`（新規）、`backend/ecsite/src/test/java/com/ecsite/common/conversion/`（新規） |
| presentation | `backend/ecsite/src/main/java/com/ecsite/module/{catalog,inventory,order,reporting,storefront}/presentation/` の Mapper |
| 腐敗防止層 | `backend/ecsite/src/main/java/com/ecsite/module/{notification,order,reporting,storefront}/infrastructure/directory/` |
| 公開の API | `backend/ecsite/src/main/java/com/ecsite/module/{catalog,order,payment,shipping}/api/` |
| ドメイン | order の `OrderStatus`・`Carrier`・`CancellationKind`、shipping の `DeliveryStatus` |
| その他 | reporting の `ReportCollectionCommandService` |
| テスト | 上のクラスのテスト（`backend/ecsite/src/test/java/com/ecsite/module/` の下） |
| 検査 | `backend/ecsite/src/test/java/com/ecsite/architecture/EnumStringConversionTest.java` と `sample/enumconversion/`、`scripts/ci/java/java-rules.mjs` とそのテスト |
| 規則と手順 | `docs/05-開発規則/backend-coding.md`、`docs/06-手順書/ci-checks.md` |

## 非機能要件

- **DN-570-1** 消費者・運営者に見える振る舞いを変えない。
- **DN-570-2** API の契約（OpenAPI）、DB の構造、イベントの項目と型、公開の API の型を変えない。
- **DN-570-3** 既存のテストの期待値を変えない。変えるのは、テストの形（全ての値を変換する形）と、変換するメソッドの可視性（private から package-private）だけ。

## 依存

| 対象 | 関係 |
|------|------|
| #569（マージ済み） | 公開の API の型と名前を修正した。同じファイルを変えるので、その後に行う |
| #549（並行。支払の期限切れのジョブ） | payment の `api/` と order の支払の腐敗防止層を変える可能性がある。重なるファイルでは、この spec で変える行だけを変え、実装を始める前の照合で確認する |
| #556（並行。永続化の DB の例外） | `infrastructure/persistence/` を変える。この spec は永続化を変えないので重ならない見込み。照合で確認する |
| #572（同時に進めない） | 注文の `api/` を変えるため |

## Non-goals

| 項目 | 理由 |
|------|------|
| 変換元より変換先の定数が多い組を名前で変換すること | 規則が、定数の名前の集合が同じ組だけを対象にしているため（決定 2） |
| 名前が違う、または 1 対 1 でない変換 | 規則で switch 式に書くと決まっているため |
| テストのコードの中の switch 式 | 変換でなく、テストの準備のため |
| 永続化での名前の文字列からの変換 | DB の文字列を読むので、規則が許しているため |
| 列挙の重複の解消（同じ値の列挙を 1 つにまとめる） | #569 が公開の型の重複を扱い、この spec は変換の書き方だけを扱うため |

## Open questions

なし。4 つの問い（共通の変換のメソッドの置き場所と名前、変換先の定数が多い組の扱い、単体テストの形、ArchUnit の対象に `Enum.valueOf(Class, String)` を加えること）は、2026-11-19 に issue 担当のセッションが利用者の委任で、推奨のとおりに決めた。結果は「決定」にある。

## tasks

実装は実装役（汎用）に 1 タスクずつ任せる。

- [x] **T570-01-01** [テスト] 共通の変換のメソッドの単体テストを書き、レッドを確認する（DC-570-2）。
  - 依存：なし
  - 範囲：`backend/ecsite/src/test/java/com/ecsite/common/conversion/`
  - 確認方法：テストがコンパイルエラーか失敗でレッドになる
- [x] **T570-01-02** 共通の変換のメソッドを作る（DC-570-2）。
  - 依存：T570-01-01
  - 範囲：`backend/ecsite/src/main/java/com/ecsite/common/conversion/`
  - 確認方法：T570-01-01 のテストが通る
- [x] **T570-01-03** [テスト] ArchUnit と `java-rules.mjs` の検査の例とテストを、決定 6・7 の形に変え、レッドを確認する（DC-570-4）。
  - 依存：T570-01-02（検査の例が、共通の変換のメソッドのクラスを使うため）
  - 範囲：`architecture/EnumStringConversionTest.java` と `sample/enumconversion/`、`scripts/ci/java/java-rules.mjs` のテスト
  - 確認方法：変えたテストが失敗する
- [x] **T570-01-04** ArchUnit と `java-rules.mjs` の検査を変える（DC-570-4）。
  - 依存：T570-01-02、T570-01-03
  - 範囲：`architecture/EnumStringConversionTest.java`、`scripts/ci/java/java-rules.mjs`
  - 確認方法：`EnumStringConversionTest` と `java-rules.mjs` のテストが通る。`java-rules.mjs` は commit の前の hook がテストを実行するので、T570-01-03 と同じ commit にする（[開発の進め方](../../05-開発規則/workflow.md)の「循環」の例外）
- [x] **T570-01-05** presentation の switch 式を置き換え、変換の組のテストを置く（DC-570-1、DC-570-3、DC-570-5）。
  - 依存：T570-01-04
  - 範囲：5 つのモジュールの presentation の Mapper とテスト
  - 確認方法：変えたモジュールのテスト（単体を `--tests` で絞る）が通る
- [x] **T570-01-06** 腐敗防止層・公開の API・ドメイン・その他の switch 式を置き換え、組のテストを置く（DC-570-1、DC-570-3、DC-570-5）。
  - 依存：T570-01-05
  - 範囲：「変える範囲」の腐敗防止層、公開の API、ドメイン、その他と、それらのテスト
  - 確認方法：変えたモジュールのテスト（単体と統合を `--tests` で絞る）が通る
- [x] **T570-01-07** 規則の確認方法と検査の説明を修正し、名前の文字列から列挙にしてよい場所に、外から来る文字列を読む設定の変換を加える（DC-570-6、DC-570-8）。
  - 依存：T570-01-04
  - 範囲：`docs/05-開発規則/backend-coding.md`、`docs/06-手順書/ci-checks.md`
  - 確認方法：文書の検査が通る
- [x] **T570-01-08** 同名の switch 式が残っていないことと、全ての組にテストがあることを確認する。`./gradlew check` と統合テストを通す（DC-570-1、DC-570-5、DC-570-7）。
  - 依存：T570-01-06、T570-01-07
  - 範囲：`backend/ecsite/`
  - 確認方法：同名の変換の switch 式を探す script で 0 件。重い検査は、issue 担当のセッションから合図を受けてから実行する
- [ ] **T570-01-09** spec の最後のレビュー。指摘を修正し、draft の pull request を作る。
  - 依存：T570-01-08
- [ ] **T570-01-10** pull request の前のレビュー。指摘を修正し、draft を外す。
  - 依存：T570-01-09
- [ ] **T570-01-11** spec をアーカイブに移す。
  - 依存：T570-01-10
