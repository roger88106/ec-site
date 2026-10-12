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
