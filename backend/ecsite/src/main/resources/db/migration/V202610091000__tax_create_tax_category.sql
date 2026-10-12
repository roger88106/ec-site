-- 税区分と税率（docs/modules/tax.md）

-- 税区分
CREATE TABLE tax.tax_category
(
    id         uuid        NOT NULL,
    name       varchar(50) NOT NULL,
    version    bigint      NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT tax_category_pkey PRIMARY KEY (id),
    -- 名称は重複できない（AC-1）
    CONSTRAINT tax_category_name_key UNIQUE (name)
);

-- 排他制約で uuid の等価比較を使うため
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 税率。適用期間は開始を含み、終了を含まない。null は無限（AC-2）
CREATE TABLE tax.tax_rate
(
    id              uuid         NOT NULL,
    tax_category_id uuid         NOT NULL,
    rate_percent    numeric(4, 2) NOT NULL,
    -- null：過去すべて
    effective_from  timestamptz,
    -- null：無期限
    effective_to    timestamptz,
    CONSTRAINT tax_rate_pkey PRIMARY KEY (id),
    CONSTRAINT tax_rate_tax_category_id_fkey FOREIGN KEY (tax_category_id) REFERENCES tax.tax_category (id),
    -- 率は 0 以上 100 未満（AC-7）
    CONSTRAINT tax_rate_rate_percent_check CHECK (rate_percent >= 0 AND rate_percent < 100),
    CONSTRAINT tax_rate_period_check CHECK (effective_from IS NULL OR effective_to IS NULL OR effective_from < effective_to),
    -- 同じ税区分の適用期間は重複しない（AC-3）。範囲型は null の端を無限として扱う。
    -- 一覧の置き換えでは複数の行を 1 件ずつ書き換え、途中で一時的に期間が重なることがあるため、検査を遅らせる
    CONSTRAINT tax_rate_period_no_overlap EXCLUDE USING gist (
        tax_category_id WITH =,
        tstzrange(effective_from, effective_to, '[)') WITH &&
    ) DEFERRABLE INITIALLY DEFERRED
);
