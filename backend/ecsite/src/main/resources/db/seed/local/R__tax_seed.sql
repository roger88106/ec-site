-- 税のローカル用 seed（plan 001 第 4 節）
-- 税区分「標準」（10%）、「軽減」（8%）。税率は開始・終了とも空白（過去すべて〜無期限）。

INSERT INTO tax.tax_category (id, name, version, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', '標準', 0, now(), now()),
       ('00000000-0000-0000-0000-000000000002', '軽減', 0, now(), now())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- 税率は、画面や API で変更した内容を seed の再実行で上書きしない（期間が重なり、排他制約に違反しうるため）
INSERT INTO tax.tax_rate (id, tax_category_id, rate_percent, effective_from, effective_to)
VALUES ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 10, NULL, NULL),
       ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000002', 8, NULL, NULL)
ON CONFLICT (id) DO NOTHING;
