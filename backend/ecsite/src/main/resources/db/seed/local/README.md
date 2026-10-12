# seed

開発と E2E で使うデータ。`db/seed/` の下に、使う profile ごとに分けて置く（`application-local.yml` と `application-e2e.yml` の `spring.flyway.locations`）。
本番相当の環境と統合テストには適用されない（constitution 7.2）。

| 置き場所 | 中身 | 使う profile |
|----------|------|--------------|
| `db/seed/base/` | ローカルと E2E の両方に要るデータ：税区分と税率（送料の税区分の固定の ID を含む） | local、e2e |
| `db/seed/local/` | 画面で見るためのサンプルデータ：カテゴリ、商品、在庫など | local |
| `db/seed/e2e/` | E2E だけに要るデータ。今は無いので置いていない。要るものが出たら作る | e2e |

- E2E は、テストのたびに要るデータを API で作る（`e2e/src/data.ts`）。`db/seed/local/` のデータに頼らない。
- 新しいデータは、まず `local` に置く。E2E も要るなら `base` に、E2E だけが要るなら `e2e` に置く。

## 書き方

- Flyway の繰り返し実行のマイグレーション（`R__<モジュール>_seed.sql`）にする。
  - 繰り返し実行のマイグレーションは、すべてのバージョン付きのマイグレーションの後に実行されるので、テーブルが必ず先にある。
  - 内容を変えると、次の起動時に再実行される。各段階では、モジュールのファイルに追記する。
  - ファイル名は、`base`・`local`・`e2e` を通して重ねない（Flyway は名前で繰り返し実行のマイグレーションを見分けるため）。
- 何度実行しても同じ結果になるように書く（`INSERT ... ON CONFLICT (id) DO UPDATE` など）。
- ID は固定の UUID にする。画面や `.http` から同じデータを指せるようにするため。
- データ（名称など）は日本語で書く（constitution 14）。

例：

```sql
INSERT INTO tax.tax_category (id, name, version, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', '標準', 0, now(), now())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;
```
