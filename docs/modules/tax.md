# 税モジュール

税区分と、適用期間付きの税率を管理する。指定した時点の税率と、税込価格を答える。

- レベル：A（期間の重複・途切れの禁止、開始済みの税率の変更制限など、不変条件が多い）
- 依存：なし
- 関連 spec：001（US-1）
- 用語と識別子：[用語集](../glossary.md)

## 税区分（集約）

- 税区分は、名称と、税率の一覧を持つ。
- 税率は、率と適用期間を持つ。適用期間は開始を含み、終了を含まない。空白は無限を意味する。
- 率は 0 以上 100 未満、小数第 2 位まで。

### 税率は一覧ごと置き換える

税率を 1 件ずつ追加・変更・削除する操作は作らず、税区分の税率を一覧ごと置き換える操作だけを作る。期間限定の税率を入れるには、既存の税率の終了日時の変更と、新しい税率の追加を同時に行う必要があり、1 件ずつでは途中で期間が途切れるため。

置き換えるときは、現在時刻と比べて次を検証する。

1. 期間が重複しない。途切れがない。最後の税率の終了は空白。
2. 既存の税率と照らし合わせて、変更できるかを判定する。
   - 開始済み（開始が空白のものを含む）：率と開始は変更も削除もできない。終了は、現在より後に変更できる。
   - 終了済み：変更できない。
   - 未開始：変更も削除もできる。
3. 新たに設定する開始・終了は、空白か、現在より後。

指定した時点の税率は、その時点を含む税率を返す。期間は重複しないので、該当は 0 件か 1 件。

### 不変条件の置き場所

| 不変条件 | ドメイン | DB |
|----------|----------|----|
| 期間が重複しない | 一覧の置き換え時の検証 | 排他制約 |
| 途切れがない、最後は無期限 | 一覧の置き換え時の検証 | なし（テストで担保） |
| 開始済み・終了済みの変更制限 | 一覧の置き換え時の検証 | なし |
| 率の範囲 | 率の値オブジェクト | CHECK 制約 |

## 税込価格

税抜価格 ×（100 ＋ 率）÷ 100 の、1 円未満を切り捨てる。小数を含む計算は、誤差の出ない十進数で行う。

商品カタログモジュールは、並べ替え・絞り込みのため SQL でも同じ計算をする（[catalog.md](catalog.md)）。

## 他モジュールへの公開

| 種類 | 内容 | 利用者 |
|------|------|--------|
| API | 税区分ごとの税率の一覧（予定を含む）、指定時点の税率、税込価格の計算 | 商品カタログ |
| イベント | なし | |

## データモデル（草案。Flyway のマイグレーションができたら、リンクに置き換える）

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
    ) DEFERRABLE INITIALLY DEFERRED
);
```

- 排他制約の範囲型は、null の端を無限として扱う。
- 排他制約の検査はコミット時まで遅らせる。一覧の置き換えでは複数の税率を 1 件ずつ書き換えるため、途中で一時的に期間が重なることがあるが、置き換えの結果に重なりがなければよい。
