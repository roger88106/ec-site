-- 会員のローカル用 seed
-- member@example.com（realm の JSON で ID を固定したアカウント）の会員。注文の seed がこの会員を指す。
-- 会員は初めてのログインでも作られるので、同じアカウントの会員がすでにあれば何もしない

INSERT INTO member.member (id, idp_subject, created_at)
VALUES ('00000000-0000-0000-0000-000000006001', '00000000-0000-0000-0000-000000009001', now() - interval '30 days 0 hours')
ON CONFLICT DO NOTHING;
