-- 会員のローカル用 seed
-- member@example.com（realm の JSON で ID を固定したアカウント）の会員。注文の seed がこの会員を指す。
-- 会員は初めてのログインでも作られるので、同じアカウントの会員がすでにあれば何もしない

INSERT INTO member.member (id, idp_subject, created_at)
VALUES ('00000000-0000-0000-0000-000000006001', '00000000-0000-0000-0000-000000009001', now() - interval '30 days 0 hours')
ON CONFLICT DO NOTHING;

-- レポート用の会員 300 人（README の「レポート用の注文」）。IdP のアカウントは持たないので、ログインはできない。
-- ID は番号から作る：会員 00000000-0000-0000-0001-<番号 12 桁>、IdP のアカウント 00000000-0000-0000-0002-<番号 12 桁>。
-- 生年月日と性別は IdP が持つ値なので会員にはない。注文の時点の年齢と性別は、会員の番号から R__ordering_seed.sql で決める。
INSERT INTO member.member (id, idp_subject, created_at)
SELECT ('00000000-0000-0000-0001-' || lpad(n::text, 12, '0'))::uuid,
       '00000000-0000-0000-0002-' || lpad(n::text, 12, '0'),
       timestamptz '2024-09-01 10:00:00+09' + n * interval '1 hour'
FROM generate_series(1, 300) AS n
ON CONFLICT DO NOTHING;
