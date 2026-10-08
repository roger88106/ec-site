# inventory モジュール

バリアントごとの在庫数と、その調整履歴を管理する。

- レベル：A（並行して調整されても失われないこと。将来の引当・減算の土台）
- 依存：`catalog`（event）
- 関連 spec：001（US-8）

## モデル

- `StockItem`（集約）：`id`、`variantId`、`productId`、`quantity`
- `StockAdjustment`（値オブジェクト）：増減量（0 以外）、理由（`ARRIVAL` / `STOCKTAKE` / `DAMAGE_OR_LOSS` / `OTHER`）、メモ（`OTHER` は必須）
- 調整履歴は集約に含めず、追記専用で別に保存する（増え続けるため）。

## 在庫の調整は条件付きの差分更新で行う

集約を読み込んで保存する方式（楽観ロック）は使わない。

```sql
UPDATE inventory.stock_item
   SET quantity = quantity + :delta
 WHERE variant_id = :variantId AND quantity + :delta >= 0
RETURNING quantity;
```

- 楽観ロックだと、同時の +5 と +3 の片方が失敗し、リトライが必要になる。差分更新なら DB が直列化するので、両方が反映される。将来の引当では同じ行への同時更新がさらに増える。
- 更新が 0 件なら「負になる」か「レコードがない」のどちらか。後者かを確認してエラーを分ける。
- `RETURNING` の値を、同じトランザクションで履歴に書く。

**「在庫は負にならない」というルールは、ドメインではなく SQL の条件と DB の `CHECK` にある。** ルールを変えるときは、ここを変える。

## 在庫レコードの作成と削除

- `VariantRegistered` を購読し、在庫数 0 のレコードを作る。`ON CONFLICT (variant_id) DO NOTHING` で冪等にする。
- `VariantDeleted` / `ProductDeleted` を購読し、レコードと履歴を消す。
- イベントはコミット後に非同期で届くため、バリアント登録の直後のごく短い間はレコードがない。
  - C 側：レコードがなければ「在庫なし」として扱う。
  - B 側：その間の調整は 404 になる。

## 他モジュールへの公開

- `api/store`：バリアント ID の一覧に対する在庫の有無
- `event`：なし

## データモデル（草案。Flyway のマイグレーションができたら、そちらへのリンクに置き換える）

```sql
CREATE TABLE inventory.stock_item (
    id          uuid PRIMARY KEY,
    variant_id  uuid        NOT NULL UNIQUE,
    product_id  uuid        NOT NULL,
    quantity    int         NOT NULL CHECK (quantity >= 0),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL
);

CREATE TABLE inventory.stock_adjustment (
    id              uuid PRIMARY KEY,
    stock_item_id   uuid        NOT NULL REFERENCES inventory.stock_item (id),
    delta           int         NOT NULL CHECK (delta <> 0),
    reason          varchar(20) NOT NULL,
    memo            varchar(500),
    quantity_after  int         NOT NULL,
    actor_id        uuid,       -- 認証の導入までは null
    adjusted_at     timestamptz NOT NULL
);
CREATE INDEX ON inventory.stock_adjustment (stock_item_id, adjusted_at DESC);
```

- `variant_id` / `product_id` は catalog の ID なので、外部キーは張らない（constitution 6）。
- `product_id` は B 側の商品ごとの在庫数合計に使う。
