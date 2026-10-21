# 段階 9：レビュー

- 前提：段階 8
- 完了条件：DC と DN をすべて確認し、満たさないものを修正するか、修正しない理由を報告している。すべてのテスト、E2E、CI が通る。
- 参照：[plan](../plan.md) 第 7 節、`docs/style/review.md`

## タスク

- [x] **T5901** `docs/style/review.md` を使って、本 spec の変更をレビューする。
- [x] **T5902** 文書のレビュー：`check-zh.mjs --all` が通る。モジュール設計書・`docs/style/`・用語集と実装の食い違いを修正する。
- [x] **T5903** plan 第 7 節の確認方法で、DC-27〜44 と DN-4〜6 を確認して表にする。カバレッジが spec 005 の前より下がっていないことを確認する。
- [x] **T5904** 完了確認：修正したこと、修正せずに残したこと（理由付き）を報告し、spec 005 を完了にする。README の spec の状態を修正する。引き継ぎの文書を作る。
- [x] **T5905** 各モジュールが自分の schema の jOOQ の生成クラスだけを使うことを、ArchUnit のテストで確認する。jOOQ の生成コード（`com.ecsite.jooq.<schema>`）はモジュールのパッケージの外にあるので、`ModulithTest` では他のモジュールのテーブルの読み書きを検出できない。モジュールの名前と schema の名前の対応（order → ordering など）はテストの中の表で持つ。一時的に違反を入れて失敗することを確認してから戻す。

## レビューの結果（T5901）

spec 005 の前（`701481a`）からの差分のうち、中身の変わらない移動（`R100`）を除く 307 の Java のファイルを、`docs/style/review.md` の一覧と照合した。

| 規則 | 結果 |
|------|------|
| 腐敗防止層の portと実装の分け方 | cart・storefront の portを `application/port/`、実装を `infrastructure/directory/` に分けた。catalog・order の portは使う層の port にある |
| パッケージの大きさの目安と例外の表 | 10 を超えるのは `catalog/infrastructure/persistence`（13）だけで、例外の表にある |
| 共通の型の置き場所と基準 | `base/`・`value/` に分けた。追加した `BoundedText`・`DisplayOrders` は 2 つ以上のモジュールで使う |
| 文字の判定の重複 | `codePointCount` を直接使うのは `BoundedText` だけ |
| エラーの定義と差し込み | 文に規則の値の数字を書いているのは、定義から決まる値（1 文字、0 円、100%）と正規表現の桁だけ（T5401） |
| infrastructure の `@Transactional` | ない |
| 現在時刻は Clock から | `Instant.now()` などを直接呼ぶ所はない |
| 設定の値の型の置き場所と接頭辞 | T5601 で確認した |
| Javadoc | 本番のコードに Javadoc のない所はない（T5702）。spec 005 で追加したテストの、`@DisplayName` のない準備・後片付けの 5 つのメソッドに書いた |
| コメントに spec の番号を書かない | 本番のコードと設定に残っていない（T5803） |
| 複雑な処理のコメント | T5802 |
| 中国語の字 | `check-zh.mjs --all` が通る（T5902） |

- 移しただけの古いテスト（spec 001〜003 に書いたもの）の項目にも、Javadoc のないものがある。spec 005 で書いたコードではないので、ここでは修正しない。

## 確認した結果（T5903）

○：満たす。△：満たすが、例外や注記がある。

| 項目 | 結果 | 確認方法と根拠 |
|------|------|----------------|
| DC-27 | ○ | 標準の構成の図と表は `docs/style/backend.md` 第 1.1 節。constitution 4.2 を修正した（T5101）。`ModuleStructureTest` の層の名前と依存の向きが通る |
| DC-28 | ○ | `*dir` のパッケージがない（`ModuleStructureTest`）。port は使う層の `port/`、実装は `infrastructure/directory/` |
| DC-29 | ○ | 上限 20 を `ModuleStructureTest` が確認する。目安を超えるのは `catalog/infrastructure/persistence`（13）だけで、例外の表に理由がある |
| DC-30 | ○ | `ModulithTest` を単体テストに移した（T5202。Docker の接続先を無効にして通ることを確認した）。構成の規則は `architecture/` の ArchUnit |
| DC-31 | ○ | `codePointCount` を直接使うのは `BoundedText` だけ |
| DC-32 | ○ | `FieldConstraintContractTest`（15 項目）が通る。食い違いはなく、DB を変えていない |
| DC-33 | △ | `OpenApiRequestConstraintTest` が通る。業務のエラーで断っている値（画像の大きさの上限、形式、都道府県の名前）は、エラーコードを変えないため、契約には外側の範囲だけを書いた（T5301）。配列の項目の数は対象外 |
| DC-34 | ○ | `MoneyTypeTest` が通る。api・event・command の金額は `Money` |
| DC-35 | ○ | `common/model/base`・`value`。基準は `docs/style/backend.md` 第 1.5 節 |
| DC-36 | ○ | `MessageContractTest` が通る。`<モジュール>Message` の 8 クラスを削除した |
| DC-37 | ○ | `ValidationMessagesTest` が通る（JVM と要求のロケールが英語でも同じ日本語） |
| DC-38 | ○ | `MessageInterpolationTest`（18 件）が通る。値はドメインの定数から差し込み、文は整理の前と同じ |
| DC-39 | ○ | `CurrentMemberIds`。4 つの Controller の同じ補助を削除した |
| DC-40 | △ | 並び順は `DisplayOrders`、文字の判定は `BoundedText`。商品の更新を 4 つの service に分けた。`Product` は 669 行で目安の 400 を超える（目安は規則ではない。集約は分けない） |
| DC-41 | ○ | 段階 7 の一覧のすべての項目に結論がある |
| DC-42 | ○ | `RequiredPropertiesTest` が通る。接頭辞を修正した（`ecsite.storefront.shop`） |
| DC-43 | ○ | 2 回目のビルドで `generateJooq UP-TO-DATE`、マイグレーションを追加すると再生成する（T5603） |
| DC-44 | ○ | 基準に当たる 40 か所のうち 20 か所に書き、残りは理由を commit に書いた。利用者に聞く所はなかった |
| DC-64 | ○ | `ImgproxyImageConverterFailureTest` の WebP でない応答の場合が通る |
| DC-65 | △ | `src/main` の Java・設定と `build.gradle.kts` に番号はない。適用済みのマイグレーション（15 ファイル）は checksum のため変えず、規則の文書の例外にした |
| DN-4 | △ | `api/` の差分は制約の追加だけ（8 行）。DB のデータは変えていない。テストの変更は、移動と DC-37 のほかに次のもの：型が `Money` になった比べ方（3 ファイル）、削除した部品のテスト（`Sorting`）、`MemberApi` を削除したことによる会員のテストの宛先（`MemberService`）、追加したテスト（都道府県の長さ、WebP の確認）。確認する振る舞いは変えていない |
| DN-5 | ○ | 移動はそれぞれ「移動だけ」の commit（`git log --stat`）。コメントは T5702・T5802・T5803 のコメントだけの commit |
| DN-6 | ○ | `db/migration` に新しいファイルはない |
| カバレッジ | ○ | 単体と統合を合わせた行 98.14%（3,535 / 3,602）→ 98.19%（3,477 / 3,541）、分岐 83.69%（872 / 1,042）→ 84.20%（879 / 1,044）。spec 005 の前（`701481a`）を別の worktree で測って比べた |

- カバレッジを比べて見つけた、検証のエラーの文の既定の扱いの誤りを修正した（下の commit）。
  - メソッド引数の検証で、元が Bean Validation の違反でないとき、Spring は例外ではなく null を返す。前の実装は null を受けて、500 になるところだった。
  - 既定の扱いは `ValidationMessages` に移し、単体テストで確認する。

## 完了の確認（T5904）

- すべてのテスト（`./gradlew check`、管理画面のテスト、型チェック、E2E 12 件）が手元で通る。CI は、利用者の review の後の push で確認する（利用者の指示で、review の前に push しない）。
- 修正したこと：段階 1〜8 の各タスク。spec の外で見つけて修正したもの：
  - domain の Factory の Spring の注釈（T5211）
  - Spring の部品の名前の衝突（完全修飾名にした）
  - 検証のエラーの既定の文の誤り（T5903）
- 修正せずに残したこと：
  - `Product` の行数（669 行。目安の 400 を超える。集約は分けない）
  - 適用済みのマイグレーションのコメントの番号（checksum のため）
  - 移しただけの古いテストの Javadoc の漏れ
  - 段階 7 の「修正しない」の項目（各項目に理由）
