-- 会員（docs/modules/member.md、spec 003 BR-13）
-- IdP のアカウントと、EC の会員 ID の対応。メールアドレスと氏名は持たない（IdP が正本）
CREATE SCHEMA IF NOT EXISTS member;

CREATE TABLE member.member
(
    id          uuid         NOT NULL,
    -- token の subject。同じアカウントの同時の初回の要求でも、会員は 1 人だけ作る
    idp_subject varchar(255) NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT member_pkey PRIMARY KEY (id),
    CONSTRAINT member_idp_subject_key UNIQUE (idp_subject)
);

COMMENT ON TABLE member.member IS '会員';
COMMENT ON COLUMN member.member.id IS '会員 ID';
COMMENT ON COLUMN member.member.idp_subject IS 'IdP のアカウントの ID';
COMMENT ON COLUMN member.member.created_at IS '登録日時';
