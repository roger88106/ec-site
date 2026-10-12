-- Keycloak 用の database とユーザーを作る（EC の database とは分ける）。
-- PostgreSQL の初期化のスクリプトは、volume が空のとき（初回の起動）だけ走る。
-- 既存の volume では走らないので、README の手順で手で作る。
CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;
