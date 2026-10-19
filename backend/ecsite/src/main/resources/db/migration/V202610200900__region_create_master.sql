-- 地域のマスターデータ（docs/backend/modules/region.md、spec 016 US-56）
-- 都道府県・市区町村は地方公共団体コードで識別する。他のモジュールの表から外部キーを張らない
CREATE SCHEMA IF NOT EXISTS region;

CREATE TABLE region.prefecture
(
    code char(2)     NOT NULL,
    name varchar(10) NOT NULL,
    CONSTRAINT prefecture_pkey PRIMARY KEY (code),
    CONSTRAINT prefecture_code_check CHECK (code ~ '^[0-9]{2}$')
);

CREATE TABLE region.municipality
(
    code            char(5)     NOT NULL,
    prefecture_code char(2)     NOT NULL,
    name            varchar(30) NOT NULL,
    CONSTRAINT municipality_pkey PRIMARY KEY (code),
    CONSTRAINT municipality_prefecture_code_fkey FOREIGN KEY (prefecture_code) REFERENCES region.prefecture (code),
    -- 市区町村のコードの先頭 2 桁は都道府県のコード
    CONSTRAINT municipality_code_check CHECK (code ~ '^[0-9]{5}$' AND left(code, 2) = prefecture_code)
);

CREATE INDEX municipality_prefecture_code_idx ON region.municipality (prefecture_code, code);

-- 郵便番号は他のデータから参照されず、更新では表をまるごと入れ替える（町域を識別するコードがないため）
CREATE TABLE region.postal_code
(
    postal_code       char(7)      NOT NULL,
    display_order     smallint     NOT NULL,
    municipality_code char(5)      NOT NULL,
    town_name         varchar(100) NOT NULL,
    CONSTRAINT postal_code_pkey PRIMARY KEY (postal_code, display_order),
    CONSTRAINT postal_code_municipality_code_fkey FOREIGN KEY (municipality_code) REFERENCES region.municipality (code),
    CONSTRAINT postal_code_postal_code_check CHECK (postal_code ~ '^[0-9]{7}$'),
    CONSTRAINT postal_code_display_order_check CHECK (display_order >= 1)
);

COMMENT ON TABLE region.prefecture IS '都道府県';
COMMENT ON COLUMN region.prefecture.code IS '都道府県のコード（地方公共団体コードの先頭 2 桁）';
COMMENT ON COLUMN region.prefecture.name IS '都道府県名';
COMMENT ON TABLE region.municipality IS '市区町村（政令指定都市は区）';
COMMENT ON COLUMN region.municipality.code IS '市区町村のコード（地方公共団体コードの 5 桁）';
COMMENT ON COLUMN region.municipality.prefecture_code IS '都道府県のコード';
COMMENT ON COLUMN region.municipality.name IS '市区町村名';
COMMENT ON TABLE region.postal_code IS '郵便番号の住所';
COMMENT ON COLUMN region.postal_code.postal_code IS '郵便番号';
COMMENT ON COLUMN region.postal_code.display_order IS '同じ郵便番号の中の並び順';
COMMENT ON COLUMN region.postal_code.municipality_code IS '市区町村のコード';
COMMENT ON COLUMN region.postal_code.town_name IS '町域名（注記を除く。注記だけなら空）';
