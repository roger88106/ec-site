# storefront モジュール

C 側 API（`/api/store/**`）。複数モジュールの結果を組み合わせて返す。

- レベル：B（ドメインもテーブルも持たない参照専用の層）
- 依存：`catalog`（api）、`inventory`（api）
- 関連 spec：001（US-10〜12）

## 置く理由

C 側の画面は、商品情報（catalog）と在庫の有無（inventory）を同時に必要とする。しかし inventory は catalog のイベントを購読しているため、catalog が inventory を呼ぶと依存が循環する。両方を呼んで組み合わせる層をここに置く（constitution 4.1）。

## 構成

```
storefront/
├── catalogdir/   # catalog.api.store を自分の型に変換する腐敗防止層
├── stockdir/     # inventory.api.store を自分の型に変換する腐敗防止層
├── service/      # 組み合わせ
└── controller/   # OpenAPI（store.yaml）から生成したインターフェースを実装
```

## 組み合わせ方

- 商品一覧・検索：catalog から 1 ページ分の商品（バリアント ID を含む）を取得し、そのバリアント ID の在庫の有無を inventory からまとめて取得する。全バリアントが在庫なしの商品に「在庫なし」を付ける。
- 商品詳細：catalog から商品を取得し、全バリアントの在庫の有無を付ける。
- 在庫レコードがまだないバリアントは「在庫なし」とする（[inventory.md](inventory.md)）。
