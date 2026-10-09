# 在庫モジュール

バリアントごとの在庫数と、その調整履歴を管理する。

- レベル：A（並行して調整されても失われないこと。将来の引当・減算の土台）
- 依存：商品カタログモジュール（イベント）
- 関連 spec：001（US-8）
- 用語と識別子：[用語集](../glossary.md)

## 在庫（集約）と在庫調整

- 在庫は、バリアント、商品、在庫数を持つ。
- 在庫調整は、増減量（0 以外）、調整理由、メモを持つ。調整理由が「その他」のときはメモが必須。
- 調整履歴は集約に含めず、追記するだけの記録として別に保存する（増え続けるため）。

## 在庫の調整は「差分の加算」で行う

在庫を読み込んで書き戻す方式（楽観ロック）は使わず、DB 上で「現在の在庫数に増減量を足す。ただし結果が負にならない場合だけ」という 1 回の更新で行う。

```sql
UPDATE inventory.stock_item
   SET quantity = quantity + :delta
 WHERE variant_id = :variantId AND quantity + :delta >= 0
RETURNING quantity;
```

- 楽観ロックだと、+5 と +3 が同時に来ると片方が失敗し、やり直しが必要になる。差分の加算なら DB が順番に処理するので、両方が反映される。将来の引当では、同じ在庫への同時更新がさらに増える。
- 更新されなかった場合は、「負になる」か「在庫がない」のどちらか。どちらかを確認して、エラーを分ける。
- 更新後の在庫数を受け取り、同じトランザクションで調整履歴に書く。

**「在庫数は負にならない」というルールは、ドメインではなく、この更新の条件と DB の CHECK 制約にある。** ルールを変えるときは、ここを変える。

## 在庫の作成と削除

- バリアントが登録されたら、在庫数 0 の在庫を作る。同じイベントが 2 回届いても、在庫は 1 つだけになるようにする。
- バリアントまたは商品が削除されたら、その在庫と調整履歴を消す。
- イベントはコミット後に非同期で届くため、バリアントの登録直後のごく短い間は在庫がない。
  - C 側：在庫がなければ「在庫なし」として扱う。
  - B 側：その間の在庫調整は「見つからない」になる。

### イベントの処理に失敗したとき（MVP で受け入れるリスク）

MVP では、処理に失敗したイベントを自動では再発行しない（[plan](../specs/001-catalog-and-inventory/plan.md) 第 4 節）。複数インスタンスで再発行が重複しないようにするには分散ロックが必要で、その方式が未定のため（constitution 16）。

- **起きること**：バリアント登録のイベントの処理に失敗すると（DB の一時的な障害など）、そのバリアントの在庫は作られないままになる。C 側では常に「在庫なし」、B 側では在庫の取得・調整が「見つからない」になる。削除のイベントで失敗すると、在庫と調整履歴が残る（実害はない）。
- **気づき方**：未完了のイベントはイベント発行記録に残り、エラーログが出る。B 側で、登録したバリアントの在庫が「見つからない」ままになる。
- **復旧手順**：原因を取り除いたあと、起動時の未完了イベントの再発行を設定で一時的に有効にし、1 インスタンスだけ再起動する。リスナーは冪等なので、再発行で在庫が重複することはない。終わったら設定を戻す。
- **順序の逆転**：削除のイベントが登録のイベントより先に処理されると、削除済みのバリアントの在庫が残る。参照されないので実害はなく、MVP では放置する。

自動の再発行は、constitution 16 の「未完了イベントの自動再発行」で決める。

## 他モジュールへの公開

| 種類 | 内容 | 利用者 |
|------|------|--------|
| API | 複数のバリアントについての在庫の有無（在庫数が 1 以上なら「あり」。在庫がまだないバリアントは「なし」） | 店舗 |
| イベント | なし | |

## データモデル（草案。Flyway のマイグレーションができたら、リンクに置き換える）

```sql
CREATE TABLE inventory.stock_item (
    id          uuid PRIMARY KEY,
    variant_id  uuid        NOT NULL UNIQUE,
    product_id  uuid        NOT NULL,
    quantity    int         NOT NULL CHECK (quantity >= 0),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL
);

CREATE TABLE inventory.stock_adjustment (
    id              uuid PRIMARY KEY,
    stock_item_id   uuid        NOT NULL REFERENCES inventory.stock_item (id),
    delta           int         NOT NULL CHECK (delta <> 0),
    reason          varchar(20) NOT NULL,
    memo            varchar(500),
    quantity_after  int         NOT NULL,
    actor_id        uuid,       -- 認証の導入までは null
    adjusted_at     timestamptz NOT NULL
);
CREATE INDEX ON inventory.stock_adjustment (stock_item_id, adjusted_at DESC, sequence_no DESC);
```

- バリアントと商品は商品カタログモジュールの ID なので、外部キーは張らない（constitution 6）。
- 商品を持つのは、B 側で商品ごとの在庫数の合計を出すため。
