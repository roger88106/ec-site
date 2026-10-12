# 段階 2：標準の構成

- 前提：段階 1
- 完了条件：DC-27〜30。ArchUnit と Modulith のテスト（単体テスト）が通る。`./gradlew check` が通る。
- 参照：[plan](../plan.md) 第 1 節

## 準備

- [ ] **T5201** 全ファイルの移し先の表を作り（plan 第 1.2 節を元に。order と catalog の application の分け方を含む）、利用者に確かめる。
- [ ] **T5202** `ModulithTest` を `src/test` に移す。Docker を止めた状態で通ることを確かめる。

## 検査のテスト

- [ ] **T5203** [テスト] `src/test/java/com/ecsite/architecture/` に ArchUnit の規則を書く：層の依存の向き、モジュールの直下のパッケージの名前、`@Transactional`・`@RestController` の置き場所、パッケージの直下のファイルが 20 を超えない、`*dir` のパッケージがない。今のコードで失敗することを確かめる（DC-30）。

## 移動（モジュールごとに、`git mv` の commit と参照の修正の commit を分ける）

- [ ] **T5204** cart を標準の構成に（Directory の口と実装を分ける）。
- [ ] **T5205** media を標準の構成に（`service/` 11 を application・domain・infrastructure に）。
- [ ] **T5206** member を標準の構成に。
- [ ] **T5207** storefront を標準の構成に（`catalogdir`・`orderdir`・`stockdir` を `application/port/` と `infrastructure/directory/` に）。
- [ ] **T5208** catalog：`infrastructure/` を `persistence/`・`directory/`・`cache/` に、application の参照の Repository の口を `application/port/` に。
- [ ] **T5209** order、tax、inventory：application の口を `application/port/` に、order の `domain/port/` の Directory を使う層の port に。
- [ ] **T5210** 各モジュールの設計書と `package-info.java` の説明を新しい構成に合わせる。T5203 が通る（DC-27〜30）。
