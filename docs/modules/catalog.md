# 商品カタログモジュール

商品、バリアント、カテゴリ、公開状態、商品検索を管理する。

- レベル：A（軸の組み合わせの一意性、公開の条件など、不変条件が多い）
- 依存：税モジュール（API）
- 関連 spec：001（US-2〜7、9、11、13）
- 用語と識別子：[用語集](../glossary.md)

## 商品（集約）

- 商品は、商品品番、名称、説明、税区分、税抜価格、所属カテゴリ、バリアント軸（最大 3 つ）、公開状態、初回表示日時、公開期間、バリアント、画像を持つ。
- バリアントは、バリアント品番、軸の値、表示順、販売停止、画像を持つ。価格は持たず、商品の価格を使う（MVP。バリアントごとの価格は将来の spec）。
- 商品とバリアントは 1 つの集約にする。軸の値の組み合わせが商品内で一意であることは、商品単位の不変条件のため。
- 品番は属性であり、他のデータからの参照には使わない（constitution 7.1）。

### 初回表示日時

初回表示日時は、C 側に最初に表示された（または表示される予定の）日時。新着順の基準であり、商品・バリアントを削除できるかの判定にも使う。

- 公開したとき、または公開中に公開期間を変えたときに、「公開した時刻と公開開始日時の遅いほう」を入れる。その日時が公開終了日時以降なら、表示されないので空にする。
- 値が過去になったら確定し、以後は変わらない（再公開しても新着順の順位は上がらない。AC-54）。
- 値が未来の間は、まだ表示されていない。非公開にしたら空に戻し、公開期間を変えたら計算し直す。
- 削除できるのは、初回表示日時が空か未来の商品（C 側に一度も表示されていない。AC-33、71）。

定期ジョブを使わず、操作の時点の現在時刻と比べて判定する（constitution 5-5）。

### 状態の切り替え

公開・非公開・販売終了、販売停止・解除は冪等にする（constitution 9）。すでにその状態なら何もせず成功とする。運営者からバージョンは受け取らず、保存時に他の更新と衝突した場合だけ競合として返す。

- 販売終了からは、公開にも非公開にも戻せない（AC-32）。

## カテゴリ（集約）

- カテゴリは、親カテゴリ、名称、階層の深さ（1〜3）、表示順を持つ。
- 商品とは ID でだけつながる。
- 深さは親の深さ＋1。親を変える操作はないので、登録後に深さは変わらない。
- 登録したカテゴリは、兄弟の最後に並べる（表示順は兄弟の最大＋1）。同時に登録されて表示順が同じになったら、登録日時と ID の順に並べる。

### 並び順の変更

兄弟の並び順は、1 件ずつではなく兄弟の全体を送って変える（AC-11）。

- 送る ID は、その親の今の子カテゴリと過不足なく一致しなければならない。足りない・余分・別の親のカテゴリが入っていれば 400。画面を開いた後に兄弟が増減していたら、並べ直してもらう。
- `version` は送らない。兄弟の行を ID の順にロックしてから変えるので、同時に変えても直列になり、後の変更が残る。
- 表示順はカテゴリの一部なので、表示順が変わったカテゴリはバージョンを上げる。並べ替えの前に開いた画面で名称を変えると 409 になる。

### 削除

子カテゴリを持つカテゴリは削除できない（AC-12）。削除と子カテゴリの登録が同時に起きて外部キーの違反になった場合も、業務のエラーに変換する（削除は 400、登録は親が見つからない 404）。

## C 側に表示される条件

公開状態が「公開」で、公開期間内であること。

この条件は、ドメインと参照用の SQL の 2 か所にある。SQL 側は 1 つの部品にまとめて一覧と検索の両方で使い、検索だけに非公開の商品が出る事故を防ぐ。両者が一致することは統合テストで確認する。

## 税込価格での並べ替え・絞り込み

税区分ごとに税率が違うため、税抜の順と税込の順は一致しない（例：1,000 円・8% は 1,080 円、990 円・10% は 1,089 円）。そのため、一覧の SQL で税込価格を計算する。

1. 税モジュールから、税区分ごとの税率の一覧（予定を含む）を取得する。インスタンス内で 30 秒キャッシュする。
2. 表示時刻で有効な税率を決め、税区分ごとの率を SQL に埋め込む。
3. 商品の税込価格を計算し、価格帯の絞り込みと並べ替えに使う。

- 有効な税率は表示時刻で判定するので、切り替えの時刻ちょうどに切り替わる。キャッシュの影響は、税率の登録・変更の反映が最大 30 秒遅れることだけ。
- 税込価格の計算が、税モジュール（Java）とここ（SQL）の 2 か所にある。端数の境界値を含めて、結果の一致を統合テストで確認する。

## キーワード検索

- キーワード検索は専用の窓口（商品検索）の後ろに置き、通常の商品照会とはコードを分ける（constitution 4.4）。
- MVP では、商品名と説明に対する部分一致で探す。キーワード中のワイルドカード文字はエスケープする。並びは新着順で固定し、専用のインデックスは作らない。
- 検索エンジンを入れるときは、商品検索の実装を差し替え、下記のイベントでインデックスを同期する。

## 他モジュールへの公開

| 種類 | 内容 | 利用者 |
|------|------|--------|
| API | C 側向けの商品一覧・検索・詳細（税込価格を含む）、カテゴリの木 | 店舗 |
| イベント | バリアント登録、バリアント削除、商品削除 | 在庫 |
| イベント | 商品公開、商品非公開、商品販売終了、商品価格変更 | なし（将来の検索インデックス同期用） |

## データモデル

- カテゴリ：[V202610091200__catalog_create_category.sql](../../backend/src/main/resources/db/migration/V202610091200__catalog_create_category.sql)
  - 同じ親の下での名称の一意性は、最上位（親が null）同士も含める（`UNIQUE NULLS NOT DISTINCT`）。

### 草案（Flyway のマイグレーションができたら、リンクに置き換える）

```sql
CREATE TABLE catalog.product (
    id               uuid PRIMARY KEY,
    product_code     varchar(32)  NOT NULL UNIQUE,
    name             varchar(200) NOT NULL,
    description      text         NOT NULL,
    tax_category_id  uuid         NOT NULL,
    price_excl_tax   bigint       NOT NULL CHECK (price_excl_tax >= 1),
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
    axis_key         text        NOT NULL,
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

- 税区分は税モジュールの ID なので、外部キーは張らない（constitution 6）。
- 画像のテーブルは spec 001 の US-7 で追加する。
