-- カテゴリ（docs/modules/catalog.md）

CREATE TABLE catalog.category
(
    id            uuid        NOT NULL,
    -- null：最上位
    parent_id     uuid,
    name          varchar(50) NOT NULL,
    -- 階層の深さ。最上位が 1（AC-9）
    depth         smallint    NOT NULL,
    -- 兄弟の中の並び順（小さいほど先）
    display_order int         NOT NULL,
    version       bigint      NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT category_pkey PRIMARY KEY (id),
    -- 子カテゴリを持つカテゴリは削除できない（AC-12）
    CONSTRAINT category_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES catalog.category (id),
    CONSTRAINT category_depth_check CHECK (depth BETWEEN 1 AND 3),
    CONSTRAINT category_root_depth_check CHECK ((parent_id IS NULL) = (depth = 1)),
    -- 同じ親の下で名称は重複できない。最上位同士も重複できない（AC-10）
    CONSTRAINT category_parent_id_name_key UNIQUE NULLS NOT DISTINCT (parent_id, name)
);
