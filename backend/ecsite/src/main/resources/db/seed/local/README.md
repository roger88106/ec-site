# ローカル用の seed

ローカル開発用のサンプルデータ。`local` profile でだけ適用される（`application-local.yml` の `spring.flyway.locations`）。
本番相当の環境と統合テストには適用されない（constitution 7.2）。

## 書き方

- Flyway の繰り返し実行のマイグレーション（`R__<モジュール>_seed.sql`）にする。
  - 繰り返し実行のマイグレーションは、すべてのバージョン付きのマイグレーションの後に実行されるので、テーブルが必ず先にある。
  - 内容を変えると、次の起動時に再実行される。各段階では、モジュールのファイルに追記する。
- 何度実行しても同じ結果になるように書く（`INSERT ... ON CONFLICT (id) DO UPDATE` など）。
- ID は固定の UUID にする。画面や `.http` から同じデータを指せるようにするため。
- データ（名称など）は日本語で書く（constitution 14）。

例：

```sql
INSERT INTO tax.tax_category (id, name, version, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', '標準', 0, now(), now())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;
```
