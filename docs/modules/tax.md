# tax モジュール

税区分と、適用期間付きの税率を管理する。指定時点の税率と、税込価格を答える。

- レベル：A（期間の重複・途切れの禁止、開始済みの税率の変更制限など、不変条件が多い）
- 依存：なし
- 関連 spec：001（US-1）

## TaxCategory 集約

- `TaxCategory`（集約）：`id`、`name`、`rates`、`version`
- `TaxRate`（エンティティ）：`id`、`RatePercent`、`EffectivePeriod`
- `EffectivePeriod`：半開区間 `[from, to)`。null は無限
- `RatePercent`：0 以上 100 未満、小数第 2 位まで

### 税率は一覧ごと置き換える

`replaceRates(drafts, now)` で、税区分の税率をまとめて置き換える。1 件ずつの追加・変更・削除は提供しない。期間限定の税率を入れるには、既存の税率の終了日時の変更と追加を同時に行う必要があり、1 件ずつだと途中で期間が途切れるため。

`replaceRates` の検証：

1. 期間が重複しない、途切れがない、最後の `to` が null
2. 既存の税率と ID で対応づけ、`now` と比べて判定する
   - 開始済み（`from` が null または `from <= now`）：率と `from` は変更・削除不可。`to` は `now` より後に変更できる
   - 終了済み（`to <= now`）：変更不可
   - 未開始：変更・削除できる
3. 新たに設定する `from` / `to` は、null か `now` より後

`rateAt(t)`：`t` を含む税率を返す。期間は重複しないので、該当は 0 件か 1 件。

### 不変条件の置き場所

| 不変条件 | ドメイン | DB |
|----------|----------|----|
| 期間が重複しない | `replaceRates` | 排他制約（`EXCLUDE`） |
| 途切れがない、最後は無期限 | `replaceRates` | なし（テストで担保） |
| 開始済み・終了済みの変更制限 | `replaceRates` | なし |
| 率の範囲 | `RatePercent` | `CHECK` |

## 税込価格

`floor(税抜 × (100 + 税率) / 100)`。`BigDecimal` と `RoundingMode.FLOOR` で計算する。

catalog は並べ替え・絞り込みのため、SQL でも同じ計算をする（[catalog.md](catalog.md)）。

## 他モジュールへの公開

- `api/catalog`：税区分ごとの税率の一覧（予定を含む）、指定時点の税率、税込価格の計算
- `event`：なし

## データモデル（草案。Flyway のマイグレーションができたら、そちらへのリンクに置き換える）

```sql
CREATE TABLE tax.tax_category (
    id          uuid PRIMARY KEY,
    name        varchar(50) NOT NULL UNIQUE,
    version     bigint      NOT NULL,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL
);

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE tax.tax_rate (
    id               uuid PRIMARY KEY,
    tax_category_id  uuid         NOT NULL REFERENCES tax.tax_category (id),
    rate_percent     numeric(4,2) NOT NULL CHECK (rate_percent >= 0 AND rate_percent < 100),
    effective_from   timestamptz,  -- null：過去すべて
    effective_to     timestamptz,  -- null：無期限
    CHECK (effective_from IS NULL OR effective_to IS NULL OR effective_from < effective_to),
    EXCLUDE USING gist (
        tax_category_id WITH =,
        tstzrange(effective_from, effective_to, '[)') WITH &&
    )
);
```

`tstzrange` は null の端を無限として扱う。
