# catalog モジュール

商品、バリアント、カテゴリ、公開状態、商品検索を管理する。

- レベル：A（軸の組み合わせの一意性、公開の条件など、不変条件が多い）
- 依存：`tax`（api）
- 関連 spec：001（US-2〜7、9、11、13）

## 集約

### Product

- `Product`（集約）：`id`、`ProductCode`、名称、説明、`taxCategoryId`、`categoryIds`、軸（最大 3）、`PublicationStatus`、`firstShownAt`、公開期間、`variants`、`images`、`version`
- `Variant`（エンティティ）：`id`、`VariantCode`、軸の値、`Money`（税抜）、表示順、販売停止、`imageId`

- Product と Variant は 1 つの集約にする。軸の組み合わせの一意性が、商品単位の不変条件のため。
- `PublicationStatus`：`UNPUBLISHED` / `PUBLISHED` / `DISCONTINUED`
- `firstShownAt`：最初に C 側へ表示された日時。新着順のキーであり、null かどうかで「公開したことがあるか」を判定する（商品・バリアントの削除可否）。
- `ProductCode` / `VariantCode` は属性であり、他のデータからの参照には使わない（constitution 7.1）。

### Category

- `Category`（集約）：`id`、`parentId`、名称、`depth`（1〜3）、表示順、`version`
- 商品とは ID でだけつながる。

## C 側に表示される条件

状態が `PUBLISHED` で、公開期間内であること。

この条件は、ドメインの `Product.isVisibleAt(Instant)` と参照用 SQL の 2 か所にある。SQL 側は 1 つの部品にまとめ、一覧と検索の両方で使う（検索だけに非公開の商品が出る事故を防ぐ）。ドメインと SQL の一致は統合テストで確認する。

## 税込価格での並べ替え・絞り込み

税区分ごとに税率が違うため、税抜の順と税込の順は一致しない（例：1,000 円・8% は 1,080 円、990 円・10% は 1,089 円）。そのため、一覧の SQL で税込価格を計算する。

1. `taxdir` 経由で、税区分ごとの税率の一覧（予定を含む）を取得する。Caffeine で 30 秒キャッシュする。
2. 表示時刻で有効な税率を決め、`CASE tax_category_id WHEN ... THEN rate END` として SQL に埋め込む。
3. `floor(price * (100 + rate) / 100)` で、販売中バリアントの最安値、価格帯の絞り込み、並べ替えを行う。

- 有効な税率は表示時刻で判定するので、税率の切り替え時刻ちょうどに切り替わる。キャッシュの影響は「税率の登録・変更の反映が最大 30 秒遅れる」ことだけ。
- 税込計算が tax（Java）とここ（SQL）の 2 か所にある。端数の境界値を含めて、結果の一致を統合テストで確認する。

## キーワード検索

- 検索用の port `ProductSearch` を置く。通常の一覧（`ProductQueryRepository`）とはコードを分ける（constitution 4.4）。
- MVP の実装 `PostgresProductSearch`：商品名と説明への `ILIKE`。`%` と `_` はエスケープする。並びは新着順で固定。専用のインデックスは作らない。
- 検索エンジン導入時は、`ProductSearch` の実装を差し替え、下記のイベントでインデックスを同期する。

## 他モジュールへの公開

- `api/store`：C 側向けの商品一覧・検索・詳細（税込価格を含む）、カテゴリの木
- `event`：

| イベント | 購読者 |
|----------|--------|
| `VariantRegistered(productId, variantId)` | inventory |
| `VariantDeleted(productId, variantId)` | inventory |
| `ProductDeleted(productId, variantIds)` | inventory |
| `ProductPublished` / `ProductUnpublished` / `ProductDiscontinued` / `VariantPriceChanged` | なし（将来の検索インデックス同期用） |

## データモデル（草案。Flyway のマイグレーションができたら、そちらへのリンクに置き換える）

```sql
CREATE TABLE catalog.category (
    id             uuid PRIMARY KEY,
    parent_id      uuid REFERENCES catalog.category (id),
    name           varchar(50) NOT NULL,
    depth          smallint    NOT NULL CHECK (depth BETWEEN 1 AND 3),
    display_order  int         NOT NULL,
    version        bigint      NOT NULL,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    UNIQUE NULLS NOT DISTINCT (parent_id, name)
);

CREATE TABLE catalog.product (
    id               uuid PRIMARY KEY,
    product_code     varchar(32)  NOT NULL UNIQUE,
    name             varchar(200) NOT NULL,
    description      text         NOT NULL,
    tax_category_id  uuid         NOT NULL,
    status           varchar(20)  NOT NULL,
    first_shown_at   timestamptz,
    publish_start    timestamptz,
    publish_end      timestamptz,
    version          bigint       NOT NULL,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL
);

CREATE TABLE catalog.product_axis (
    product_id  uuid        NOT NULL REFERENCES catalog.product (id),
    position    smallint    NOT NULL CHECK (position BETWEEN 1 AND 3),
    name        varchar(30) NOT NULL,
    PRIMARY KEY (product_id, position)
);

CREATE TABLE catalog.product_category (
    product_id   uuid NOT NULL REFERENCES catalog.product (id),
    category_id  uuid NOT NULL REFERENCES catalog.category (id),
    PRIMARY KEY (product_id, category_id)
);

CREATE TABLE catalog.variant (
    id               uuid PRIMARY KEY,
    product_id       uuid        NOT NULL REFERENCES catalog.product (id),
    variant_code     varchar(32) NOT NULL UNIQUE,
    axis_key         text        NOT NULL,  -- 軸の値を連結した値
    price_excl_tax   bigint      NOT NULL CHECK (price_excl_tax >= 1),
    display_order    int         NOT NULL,
    sales_suspended  boolean     NOT NULL DEFAULT false,
    image_id         uuid,
    UNIQUE (product_id, axis_key)
);

CREATE TABLE catalog.variant_axis_value (
    variant_id  uuid        NOT NULL REFERENCES catalog.variant (id),
    position    smallint    NOT NULL,
    value       varchar(50) NOT NULL,
    PRIMARY KEY (variant_id, position)
);
```

- `tax_category_id` は tax モジュールの ID なので、外部キーは張らない（constitution 6）。
- 画像のテーブルは spec 001 の US-7 で追加する。
