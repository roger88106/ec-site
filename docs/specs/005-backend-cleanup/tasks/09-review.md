# 段階 9：見直し

- 前提：段階 8
- 完了条件：DC と DN をすべて確かめ、満たさないものを直すか、直さない理由を報告している。すべてのテスト、E2E、CI が通る。
- 参照：[plan](../plan.md) 第 7 節、`docs/style/review.md`

## タスク

- [x] **T5901** `docs/style/review.md` を使って、本 spec の変更を見直す。
- [x] **T5902** 文書の見直し：`check-zh.mjs --all` が通る。モジュール設計書・`docs/style/`・用語集と実装の食い違いを直す。
- [ ] **T5903** plan 第 7 節の確かめ方で、DC-27〜44 と DN-4〜6 を確かめて表にする。カバレッジが spec 005 の前より下がっていないことを確かめる。
- [ ] **T5904** 完了確認：直したこと、直さずに残したこと（理由付き）を報告し、spec 005 を完了にする。README の spec の状態を直す。引き継ぎの文書を作る。

## 見直しの結果（T5901）

spec 005 の前（`701481a`）からの差分のうち、中身の変わらない移動（`R100`）を除く 307 の Java のファイルを、`docs/style/review.md` の一覧と照らした。

| 規則 | 結果 |
|------|------|
| 腐敗防止層の口と実装の分け方 | cart・storefront の口を `application/port/`、実装を `infrastructure/directory/` に分けた。catalog・order の口は使う層の port にある |
| パッケージの大きさの目安と例外の表 | 10 を超えるのは `catalog/infrastructure/persistence`（13）だけで、例外の表にある |
| 共通の型の置き場所と基準 | `base/`・`value/` に分けた。足した `BoundedText`・`DisplayOrders` は 2 つ以上のモジュールで使う |
| 文字の判定の重複 | `codePointCount` を直接使うのは `BoundedText` だけ |
| エラーの定義と差し込み | 文に規則の値の数字を書いているのは、定義から決まる値（1 文字、0 円、100%）と正規表現の桁だけ（T5401） |
| infrastructure の `@Transactional` | ない |
| 現在時刻は Clock から | `Instant.now()` などを直接呼ぶ所はない |
| 設定の値の型の置き場所と接頭辞 | T5601 で確かめた |
| Javadoc | 本番のコードに Javadoc のない所はない（T5702）。spec 005 で足したテストの、`@DisplayName` のない準備・後片付けの 5 つのメソッドに書いた |
| コメントに spec の番号を書かない | 本番のコードと設定に残っていない（T5803） |
| 複雑な処理のコメント | T5802 |
| 中国語の字 | `check-zh.mjs --all` が通る（T5902） |

- 移しただけの古いテスト（spec 001〜003 に書いたもの）の項目にも、Javadoc のないものがある。spec 005 で書いたコードではないので、ここでは直さない。

## 確かめた結果（T5903）

○：満たす。△：満たすが、例外や注記がある。

| 項目 | 結果 | 確かめ方と根拠 |
|------|------|----------------|
| DC-27 | ○ | 標準の構成の図と表は `docs/style/backend.md` 第 1.1 節。constitution 4.2 を直した（T5101）。`ModuleStructureTest` の層の名前と依存の向きが通る |
| DC-28 | ○ | `*dir` のパッケージがない（`ModuleStructureTest`）。口は使う層の `port/`、実装は `infrastructure/directory/` |
| DC-29 | ○ | 上限 20 を `ModuleStructureTest` が確かめる。目安を超えるのは `catalog/infrastructure/persistence`（13）だけで、例外の表に理由がある |
| DC-30 | ○ | `ModulithTest` を単体テストに移した（T5202。Docker の接続先を無効にして通ることを確かめた）。構成の規則は `architecture/` の ArchUnit |
| DC-31 | ○ | `codePointCount` を直接使うのは `BoundedText` だけ |
| DC-32 | ○ | `FieldConstraintContractTest`（15 項目）が通る。食い違いはなく、DB を変えていない |
| DC-33 | △ | `OpenApiRequestConstraintTest` が通る。業務のエラーで断っている値（画像の大きさの上限、形式、都道府県の名前）は、エラーコードを変えないため、契約には外側の範囲だけを書いた（T5301）。配列の項目の数は対象外 |
| DC-34 | ○ | `MoneyTypeTest` が通る。api・event・command の金額は `Money` |
| DC-35 | ○ | `common/model/base`・`value`。基準は `docs/style/backend.md` 第 1.5 節 |
| DC-36 | ○ | `MessageContractTest` が通る。`<モジュール>Message` の 8 クラスを消した |
| DC-37 | ○ | `ValidationMessagesTest` が通る（JVM と要求のロケールが英語でも同じ日本語） |
| DC-38 | ○ | `MessageInterpolationTest`（18 件）が通る。値はドメインの定数から差し込み、文は整理の前と同じ |
| DC-39 | ○ | `CurrentMemberIds`。4 つの Controller の同じ補助を消した |
| DC-40 | △ | 並び順は `DisplayOrders`、文字の判定は `BoundedText`。商品の更新を 4 つの service に分けた。`Product` は 669 行で目安の 400 を超える（目安は規則ではない。集約は分けない） |
| DC-41 | ○ | 段階 7 の一覧のすべての項目に結論がある |
| DC-42 | ○ | `RequiredPropertiesTest` が通る。接頭辞を直した（`ecsite.storefront.shop`） |
| DC-43 | ○ | 2 回目のビルドで `generateJooq UP-TO-DATE`、マイグレーションを足すと生成し直す（T5603） |
| DC-44 | ○ | 基準に当たる 40 か所のうち 20 か所に書き、残りは理由を commit に書いた。利用者に聞く所はなかった |
| DC-64 | ○ | `ImgproxyImageConverterFailureTest` の WebP でない応答の場合が通る |
| DC-65 | △ | `src/main` の Java・設定と `build.gradle.kts` に番号はない。適用済みのマイグレーション（15 ファイル）は checksum のため変えず、規則の文書の例外にした |
| DN-4 | △ | `api/` の差分は制約の追加だけ（8 行）。DB のデータは変えていない。テストの変更は、移動と DC-37 のほかに次のもの：型が `Money` になった比べ方（3 ファイル）、消した部品のテスト（`Sorting`）、`MemberApi` を消したことによる会員のテストの宛先（`MemberService`）、足したテスト（都道府県の長さ、WebP の確かめ）。確かめる振る舞いは変えていない |
| DN-5 | ○ | 移動はそれぞれ「移動だけ」の commit（`git log --stat`）。コメントは T5702・T5802・T5803 のコメントだけの commit |
| DN-6 | ○ | `db/migration` に新しいファイルはない |
| カバレッジ | ○ | 単体と統合を合わせた行 98.14%（3,535 / 3,602）→ 98.19%（3,477 / 3,541）、分岐 83.69%（872 / 1,042）→ 84.20%（879 / 1,044）。spec 005 の前（`701481a`）を別の worktree で測って比べた |

- カバレッジを比べて見つけた、検証のエラーの文の既定の扱いの誤りを直した（下の commit）。
  - メソッド引数の検証で、元が Bean Validation の違反でないとき、Spring は例外ではなく null を返す。前の実装は null を受けて、500 になるところだった。
  - 規定の扱いは `ValidationMessages` に移し、単体テストで確かめる。
