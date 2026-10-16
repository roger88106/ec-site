# Plan 011：開発の基盤の整理

ステータス：承認済み（tasks 作成済み）　対象：[spec.md](spec.md)

前提：spec 010 が完了している。CI は `.github/workflows/ci.yml`（バックエンド、管理画面、店舗画面、AC とテストの対応、E2E の 5 つのジョブ）と `docs.yml`。CI の検査の script は `scripts/ci/`、手元の script は `scripts/dev/`。

| 対象 | 本書 | 主な担当 |
|------|------|----------|
| 番号と、MVP の完成までの一覧 | 第 1 節 | DS-21 |
| CI の検査を手元で流す | 第 2 節 | DS-22 |
| ローカルの環境の作り直し | 第 3 節 | DS-23 |
| CI の時間 | 第 4 節 | DS-24 |
| 書き方の規則の機械の検査 | 第 5 節 | DS-25 |
| 文書の言語 | 第 6 節 | DS-26 |
| ポートの予約 | 第 7 節 | DS-27 |
| 手元の待ち時間と手間 | 第 8 節 | DS-28 |
| 段階と確かめ方 | 第 9 節、第 10 節 | すべて |

## 1. 番号と、MVP の完成までの一覧（DS-21）

### 1.1 タスク番号（DC-73、DC-74、DC-75）

| 項目 | 決定 |
|------|------|
| 形式 | `T` ＋ spec の番号 ＋ `-` ＋ 段階（2 桁） ＋ `-` ＋ 連番（2 桁）。例：`T011-01-01` は spec 011 の段階 1 の 1 つ目 |
| spec の番号の桁 | 固定しない。spec の番号をそのまま書く（999 を超えたら `T1000-01-01`） |
| 段階・連番の桁 | 2 桁。100 を超える段階・タスクは作らない（超えそうなら段階を分ける） |
| 途中で足すタスク | その段階のファイルの末尾に、続きの番号で足す。番号の順とファイルの中の順を同じにする |
| commit のメッセージ | 末尾に `（T011-01-01）` |
| spec 001〜010 | 変えない。古い形式（`T3101` など）のまま |
| 書く場所 | [文書の記号と番号](../../process/notation.md)の「tasks の番号と記号」 |

理由：

- 下 1 桁の形式は spec 011 で spec 001 と重なる。spec の番号を全部入れれば重ならない。
- `-` で区切ると、桁を固定しなくても spec・段階・連番を読み分けられる。
- 途中で足したタスクを間に入れると番号が飛び、末尾に足すと順が保てる（spec 010 で間に入れて、番号の順に並ばなくなった）。

番号を読む script：

| script | 直すこと |
|--------|----------|
| `scripts/ci/spec-numbers.mjs` | 本番のコードのコメントの中のタスク番号に、新しい形式（`\bT\d{3,}-\d{2}-\d{2}\b`）を足す。古い形式も残す |
| その他 | 段階 1 の最初のタスクで、`T\d` を読む script・skill（`handoff` など）を grep で洗い出し、同じく両方を読むようにする |

テストは `spec-numbers.test.mjs` に、新しい形式と古い形式の両方の例を足す。

### 1.2 MVP の完成までの一覧（DC-76）

| 項目 | 決定 |
|------|------|
| 置き場所 | `docs/specs/mvp.md`（新しい文書） |
| 中身 | ① spec 011〜018 の表（番号、名前、目的、順序の理由、状態）。② 並べた原則（道具 → テストの安全網 → 書き方の統一 → 振る舞いの変更）。③ MVP の後に回した決定の表（項目、決める時、理由） |
| 辿り方 | README の進捗の表の下に「MVP の完成までの一覧」へのリンク。一覧の各 spec は、spec の文書ができたらリンクにする |
| 状態の更新 | spec を始めたとき、終えたときに、README の進捗と一緒に直す |

③ に最初に書く決定：

| 項目 | 決める時 |
|------|----------|
| spec の番号が 999 を超えたときの、spec とタスクの番号の付け方 | MVP の後の開発の進め方を決めるとき（**必ず決める**） |
| 後の開発の進め方（今の仕様の単位、変更の単位、起票、ブランチの運用） | 1 → 10 の最初の大きな機能 |
| 都道府県・市区町村の主データの取得と更新（国の API）、郵便番号の主データ（日本郵便） | 1 → 10 |
| デプロイ、依存の自動の更新 | 1 → 10 |
| AC にテストがないと分かったもの（第 2.3 節） | spec 012 |

理由：spec が 018 まで増えると、README の表だけでは順序の理由が分からない。後に回した決定を spec ごとの Non-goals だけに書くと、MVP の後に探しにくい。

用語集に足す語：「MVP の完成までの一覧」「CI の検査の script」。

## 2. CI の検査を手元で流す（DS-22）

### 2.1 まとめて流すコマンド（DC-77）

| 項目 | 決定 |
|------|------|
| コマンド | `scripts/ci/check-all.sh`（新しい script） |
| 流すもの | ① `node --test "scripts/ci/**/*.test.mjs"`（script のテスト）。② `scripts/ci/` の直下の `*.mjs` のうち `*.test.mjs` 以外のすべて |
| 規則 | `scripts/ci/` の直下の `.mjs` は、引数なしで流せる検査だけを置く。引数の要る補助（`coverage-summary.mjs`）は `scripts/ci/report/` に移す。`licenses/` はすでに下のディレクトリにある |
| 出力 | 1 つの検査ごとに 1 行（OK / FAIL と名前）。失敗したものだけ末尾の出力を出す。全体の終了コードは、1 つでも失敗すれば 1 |
| CI | ジョブ「AC とテストの対応」を「CI の検査の script」に改め、`check-all.sh` を流す。店舗画面のジョブにある `input-constraints.mjs`・`hardcoded-text.mjs` の手順は、こちらに移す。`docs.yml` の文書の検査は、文書だけの push でも流すため残す |

理由：script を足すたびに CI・skill・hook を直すと、どれかを直し忘れる（spec 010 で skill の一覧が漏れた）。置き場所の規則で決めれば、足すだけで流れる。CI と手元で同じ script を呼べば、食い違わない。

### 2.2 E2E のコードの lint・型チェック（DC-78）

- `scripts/dev/check-frontend.sh` の対象に `e2e` を足す（手順は `lint`・`typecheck` だけ。テストは `e2e.sh`）。引数なしで流すと、admin・store に続けて e2e も流す。
- CI は、E2E のジョブにすでにある（`pnpm lint && pnpm typecheck`）。

### 2.3 AC とテストの対応の漏れ（DC-79）

| 対象 | 今 | 直した後 |
|------|----|----------|
| Vitest・Playwright | `describe`・`test`・`it` の第 1 引数を読む | `test`・`it` だけ読む。`describe` は読まない |
| Java | すべての `@DisplayName` を読む | メソッドに付いた `@DisplayName` だけ読む。クラス（`@Nested` を含む）に付いたものは読まない |

- Java は、`@DisplayName(...)` の後に続く宣言（注釈を飛ばした次の語）が `class` かどうかで見分ける。構文の解析はしない。
- テスト：`ac-check.test.mjs` に、`describe` だけ・`@Nested` のクラスだけにある AC が対応とみなされない例を足す（直す前に失敗することを確かめる）。
- 直した後に検査で失敗した AC は、`ac-exclusions.json` に理由「spec 012 でテストを足す」と書いて除き、一覧を `docs/specs/mvp.md` の ③ に書く（DN-10。本 spec ではテストを足さない）。

### 2.4 正規表現のリテラル（DC-80）

- `hardcoded-text.mjs` の `stripComments` は、文字列とコメントを読み分けている。ここに正規表現のリテラルを足す：`/` の前（空白を除く）が、式の始まりになる記号（`(`、`,`、`=`、`:`、`[`、`!`、`&`、`|`、`?`、`{`、`}`、`;`）か、行の始まりか、`return`・`typeof` などの語なら、正規表現の始まりとみなし、`[...]` の中と `\` の後を除いた次の `/` まで（フラグを含む）を読み飛ばす。
- テスト：`hardcoded-text.test.mjs` に `/["'「]/` のような正規表現を含む行が文言とみなされない例と、割り算（`a / b`）を正規表現とみなさない例を足す。

### 2.5 CI の検査の文書（DC-81）

- `docs/dev/ci.md`（新しい文書）：CI のジョブの一覧（何を流すか、どのファイルを変えたときに流れるか）と、CI の検査の script の一覧（何を確かめるか、失敗したときにどこを直すか、除外の書き方）。
- skill `local-dev` の「CI の script の検査」は、`check-all.sh` の 1 行と、`docs/dev/ci.md` へのリンクにする。ci.yml の冒頭のコメントのジョブの説明は、この文書を指す 1 行にする。

### 2.6 commit の前の hook（DC-94）

| 項目 | 決定 |
|------|------|
| hook の本体 | `scripts/dev/hooks/pre-commit`（Git に入れる）。`scripts/ci/check-all.sh` を流す |
| 置き方 | `.git/hooks/pre-commit` から本体を呼ぶ 1 行を書く。手順は[開発環境の起動](../../dev/setup.md)に書く |
| `core.hooksPath` | 使わない |
| 流さないもの | テスト（バックエンド・フロント・E2E）。時間がかかるため |

理由：`core.hooksPath` を変えると、`.git/hooks/` に各自が置いた hook（マシンに固有の検査など）が動かなくなる。呼ぶ 1 行を置く形なら、各自の hook と並べられる。

## 3. ローカルの環境の作り直し（DS-23）

### 3.1 作り直しのコマンド（DC-82、DC-83）

`scripts/dev/reset.sh`（新しい script）：

| 引数 | すること | 触れないもの |
|------|----------|--------------|
| `db`（既定） | バックエンド（:8080）が動いていれば止まるよう求めて終わる。動いていなければ、DB `ec_site` を消して作る。seed は次のバックエンドの起動で流れる | DB `ec_site_e2e`、IdP（`keycloak` の database と realm）、画像 |
| `keycloak` | ローカルの IdP の database を作り直し、realm の JSON を取り込み直す（今の setup 第 4 節の 2 つ目の手順） | DB `ec_site`、E2E の IdP |
| `all` | `docker compose down -v` の後に `docker compose up -d`。DB・IdP・画像を、Git の正本から作り直す | なし（E2E の DB は次の E2E の準備で作られる） |

- 正本の場所：DB の seed は `backend/ecsite/src/main/resources/db/seed/`、画像は `docker/seaweedfs/seed/`、IdP は `docker/keycloak/realms/`。3 つは今も Git にある。
- そろうことの確かめ：段階 3 で `reset.sh all` の後にバックエンドを起動し、seed の商品の画像が表示され（画像の ID が seed の DB と合う）、seed の注文を `member@example.com` で見られる（会員の ID が realm と合う）ことを画面で確かめる。合わないものがあれば、正本を直す。
- 文書：setup 第 4 節の表とコマンドを、`reset.sh` の 3 つに置き換える。

理由：コマンドを 1 つにすると、エージェントの自動のモードでも許可の設定で通せる（`docker exec ... drop database` は毎回止められる）。`db` を既定にするのは、最も多い使い方だから。

### 3.2 E2E の前提の確かめ（DC-84）

- `scripts/dev/e2e.sh` の始めに、`docker inspect -f '{{.State.Running}}'` で次のコンテナを確かめ、止まっているものの名前と `docker compose up -d <名前>` を出して終わる（終了コード 2）。

| コンテナ | 役割 |
|----------|------|
| `ec-site-postgres` | DB、IdP の database |
| `ec-site-mailpit` | メール |
| `ec-site-seaweedfs` | 画像の置き場所 |
| `ec-site-imgproxy` | 画像の変換 |

- E2E の IdP（`ec-site-keycloak-e2e`）は、準備の script（`e2e/scripts/prepare.ts`）が作り直して起動するので、確かめない。

### 3.3 開発のサーバーが作るファイル（DC-93）

- `frontend/store/AGENTS.md` は Next.js の開発のサーバーが作る。段階 3 で Next.js の設定で作らせない方法があるか調べ、あればそれを使う。なければ `.gitignore` に例外として書く（AGENTS.md の Git の ※1：ツールが置き場所を決めているもの）。
- ほかに開発のサーバーが作るファイルがないか、`git status --ignored` を store・admin・backend の起動の後に見て確かめる。

## 4. CI の時間（DS-24）

### 4.1 測った結果（DC-86）

2026-10-15 の CI（run 38696695289、develop の push、成功）の内訳：

| ジョブ | 時間 | 内訳 |
|--------|------|------|
| バックエンド | 7 分 7 秒 | ライセンスの検査 約 20 秒、jOOQ の生成 約 10 秒、コンパイルと OpenAPI 約 25 秒、単体テスト 26 秒、**統合テスト 4 分 13 秒** |
| 管理画面 | 1 分 5 秒 | テスト 34 秒 |
| 店舗画面 | 58 秒 | ビルド 14 秒 |
| AC とテストの対応 | 6 秒 | |
| E2E（バックエンドの後） | 6 分 9 秒 | 準備（Java・pnpm）28 秒、コンテナの起動 61 秒、ブラウザーの OS の依存 19 秒、DB と IdP の作り直し 13 秒、**サーバーの起動（バックエンドのコンパイル、管理画面・店舗画面のビルド）2 分 23 秒**、テスト 20 件 1 分 34 秒（うち支払の期限切れ 34 秒） |
| **全体** | **13 分 28 秒** | バックエンド ＋ E2E が直列 |

直近 5 回の成功の全体：12〜13.5 分。

### 4.2 選んだ方法（DC-95、DC-85）

| 方法 | 決定 | 見込み |
|------|------|--------|
| E2E のジョブの `needs` を外す | **入れる** | 全体 ≒ 長い方のジョブ（約 7 分） |
| 統合テストを並べる（`maxParallelForks`、ジョブの分割） | 入れない（測るだけ） | 段階 4 で、Spring の文脈の数とクラスごとの時間を測り、結果をこの節に足す。全体が 10 分を超えたときの次の手にする |
| E2E のサーバーの起動を早める | 入れない（測るだけ） | 同上 |
| Gradle の build cache を CI で共有する | 入れない | `setup-gradle` は既定で main のブランチだけがキャッシュを書く。MVP の間は develop に push するので効かない |

- `needs` を外す理由は spec の DC-95。E2E は自分のジョブでバックエンドをコンパイルしているので、バックエンドのジョブの結果を使っていない。
- 外すと、バックエンドのジョブが失敗しても E2E は流れる。失敗の原因は、各部分のジョブの結果を先に見る（`docs/dev/ci.md` に書く）。リポジトリは公開なので、流れる時間は費用にならない。
- DN-11：検査は減らさない。

段階 4 で測った結果（2026-10-16）：

| 測ったもの | 結果 |
|------------|------|
| `needs` を外した後の CI（run 38898476897） | 全体 7 分 34 秒。E2E はほかのジョブと同時に始まり 5 分 6 秒、バックエンドのジョブ 7 分 30 秒が最も長い |
| E2E のサーバーの起動の内訳（同じ run のログ） | 準備（DB と IdP の作り直し）35 秒の後、バックエンド 約 73 秒（Gradle、jOOQ の生成、コンパイル、Spring の起動）、管理画面のビルド 5 秒、店舗画面のビルド 9 秒。合わせて 約 90 秒。テスト 20 件 1 分 26 秒 |
| 統合テスト（手元の `./gradlew check` の結果の XML） | 133 クラス、595 件、クラスの時間の合計 300 秒。Spring の文脈の起動は 11 回（ログの `Started ... in` の数）。長いクラスは `EcSiteApplicationTests` 29 秒、`SmtpTimeoutTest` 23 秒（メールの時間切れを待つ）、`ShipmentMailTest` 17 秒 |

- 全体が 10 分以内になったので、統合テストを並べる方法と E2E のサーバーの起動を早める方法は入れない。全体が 10 分を超えたら、統合テストを `maxParallelForks` で並べる（Spring の文脈が 11 種類と少なく、並べた分だけ文脈の起動が増える）のを次の手にする。

### 4.3 記録（DC-85）

段階の最後と spec の見直しで、直近 3 回の CI の全体の時間を spec の末尾に記録する。

## 5. 書き方の規則の機械の検査（DS-25）

### 5.1 Javadoc の書き漏れ（DC-87）

| 項目 | 決定 |
|------|------|
| 道具 | Checkstyle（Gradle の `checkstyle` プラグイン）の `MissingJavadocMethod`・`MissingJavadocType`。`check` に入る |
| 対象 | `src/main/java` の public のクラスとメソッド。生成したコード（jOOQ、OpenAPI）と、上書き（`@Override`）は除く。テストは除く |
| ほかの規則 | 入れない（Javadoc の書き漏れだけ。ほかの規則は必要になったら足す） |
| 既存の漏れ | 段階 5 の最初のタスクで数を測る。100 を超えたら、直し方（まとめて直す、モジュールごとに分ける）を利用者に確かめる |
| 依存のライセンス | Checkstyle は LGPL。ビルドの道具で、アプリには入らない。`checkLicense` の対象に入るかを確かめ、入るなら方針（`scripts/ci/licenses/policy.json`）に理由を書いて許す |

理由：javac の `-Xdoclint:missing` は、生成したコードを除く設定が難しく、private にも当たる。Checkstyle は対象を規則ごとに絞れる。

公開のメソッドの範囲は、[コメントの書き方](../../process/comments.md)の Javadoc の規則に合わせる（段階 5 で照らし、食い違えば文書を先に直す）。

### 5.2 インスタンスを作らないクラス（DC-88）

| 項目 | 決定 |
|------|------|
| 書き方 | static のメンバーだけのクラスは `final` にし、コンストラクタを `private` にする（Lombok の `@NoArgsConstructor(access = AccessLevel.PRIVATE)` か、手で書く。どちらでもよい） |
| 書く場所 | バックエンドの書き方の文書（`docs/backend/coding.md`）の第 5 節 |
| 確かめ方 | ArchUnit の規則（`src/test/java/com/ecsite/architecture/`）：static でないメンバーを持たない最上位のクラス（enum・record・interface・注釈を除く）は、コンストラクタがすべて private |

理由：Checkstyle の `HideUtilityClassConstructor` はソースを読むので、Lombok の注釈で作るコンストラクタが見えず、誤って当たる。ArchUnit はコンパイルの後のクラスを読む。

テストは先に書き、今のコードで当たるクラスがあればレッドを確かめてから直す。当たらなければ、当たる例のクラス（テストの中）で規則が働くことを確かめる。

## 6. 文書の言語（DS-26、DC-89）

| 文書 | 直すこと |
|------|----------|
| `docs/process/review.md` 第 20 行 | 「中国語の字や言い回しが混ざっていない」を「日本語として自然に書いている」に改める |
| skill `docs` 第 31 行 | 「中国語の字や言い回しを混ぜない」を消す（「日本語だけで書き」は残す） |
| skill `docs` 第 40 行 | `.local/agent/check-zh.mjs` の行を消す（マシンに固有の検査は、各自の環境の文書に書く） |

- 段階 1 で `中国|check-zh|混入` を Git の現行の文書（`docs/specs/0*` を除く）と skill で grep し、ほかにもあれば同じく直す。
- 完了した spec（001〜010）の記録は変えない。

## 7. ポートの予約（DS-27）

### 7.1 予約から外す script（DC-90）

| 項目 | 決定 |
|------|------|
| script | `scripts/dev/windows/exclude-ports.ps1`（新しい script） |
| すること | ① 今の予約の範囲（`netsh interface ipv4 show excludedportrange protocol=tcp`）と、ローカルの環境のポートの一覧を照らして示す。② 変えることを示して確かめを求める。③ WinNAT を止め、各ポートを `netsh int ipv4 add excludedportrange protocol=tcp startport=<n> numberofports=1` で予約し（ほかのプログラムの予約から外す）、WinNAT を始める |
| 権限 | 管理者。冒頭のコメントと実行時の表示で明示し、管理者でなければ何もせずに終わる |
| ポートの一覧 | script の中の表（compose・バックエンド・画面・E2E のポート）。ポートを足したら、この表も直す（`docs/dev/ci.md` ではなく setup に書く） |
| 手順 | setup に「ポートが使えないとき」の節を足す：症状（`EACCES`、待ち受けているプロセスがない）、確かめ方、script の使い方、一時の対処（WinNAT の再起動） |

### 7.2 Mailpit の SMTP のポート（DC-96）

- 既定のポートを 1025 から **2525** に変える：`docker/compose.yaml`（`${MAILPIT_SMTP_PORT:-2525}:1025`）、`application-local.yml`、ほかに 1025 を既定にしている所（段階 3 で grep）。コンテナの中のポートは 1025 のまま。
- 環境変数 `MAILPIT_SMTP_PORT` で変えられる仕組みは残す。setup の 1025 の説明を直す。
- 理由：1025 は Windows のシステムのプログラムが使うことがあり、Windows の予約の範囲（49152 以降）とも離れた 2525 は、SMTP の代わりのポートとして広く使われている。

## 8. 手元の待ち時間と手間（DS-28、P2）

### 8.1 手元のビルドとテストの時間（DC-91）

段階 6 の最初のタスクで次を測り、結果と決定をこの節に足す。効果が測れたものだけを入れる。

| 測るもの | コマンド |
|----------|----------|
| 変更のないときの `check` | `./gradlew check --profile` |
| 1 ファイルを直した後の `compileJava` と `check` | 同上 |
| バックエンドの起動（`bootRun`） | 起動から `/actuator/health` が 200 になるまで |

| 候補 | 測ること |
|------|----------|
| Spring Boot DevTools（`developmentOnly`） | 起動中のバックエンドで `compileJava` した後、再起動して応答するまでの時間（完全な再起動と比べる） |
| Gradle の configuration cache | 変更のないときと 1 ファイルの変更の後の時間 |
| Testcontainers の使い回し（`testcontainers.reuse.enable`、手元だけ） | 統合テストの時間 |
| 統合テストの `maxParallelForks` | 同上（第 4.2 節の CI の測定と合わせる） |

- DevTools は開発のときだけの依存で、ビルドした jar に入らない（`developmentOnly`）。入れるなら、`docs/dev/setup.md` に使い方を書く。

2026-10-16 に、IntelliJ IDEA の MCP サーバー（エージェントが IDE の機能を呼ぶ口）で先に測った結果。DB に seed が入った状態、Gradle の daemon が起動済み：

| 方法 | 1 ファイルを直してから応答に出るまで |
|------|--------------------------------------|
| Gradle：`compileJava` ＋ `bootRun` を起動し直す | 約 15 秒（`compileJava` 4.2 秒、起動から health の 200 まで 10.8 秒。Spring の起動は 6.7 秒） |
| IntelliJ：デバッグで起動し、ビルドして HotSwap（クラスの置き換え） | 約 15〜20 秒（ビルドの後、置き換えまで数秒かかる） |
| IntelliJ で単体テストの 1 クラスを流す | 約 20 秒、結果の出力を返さない（Gradle は 6.9 秒で、結果を XML で読める） |

- 起動がすでに速いので、再起動しない仕組みで縮む時間は小さい。HotSwap はメソッドの中身の変更だけで、フィールド・メソッドの追加と設定の変更は再起動が要る。DevTools も同じ理由で効果が小さい見込み。段階 6 で DevTools を測り、差が 5 秒未満なら入れない。
- エージェントのビルドとテストは Gradle のままにする。IntelliJ の MCP は、呼び出しの関係、名前の変更、デバッガーに使う（マシンに固有の環境なので、Git の文書には書かない）。

### 8.2 機能紹介のページの数字（DC-92）

- `scripts/dev/showcase-counts.mjs`（新しい script）：spec の数（`docs/specs/` の直下）、AC の数（機能の spec の `**AC-n**`）、テストの数（Java の `@Test`・`@ParameterizedTest`、Vitest・Playwright の `test`・`it`）を出す。
- setup 第 6 節（機能紹介のページのスクリーンショット）から、撮り直すときに数字も直す手順として指す。

## 9. 段階

| 段階 | ファイル | 中身 | DC |
|------|----------|------|----|
| 1 | `01-numbering.md` | タスク番号の規則、番号の script、MVP の完成までの一覧、文書の言語 | 73、74、75、76、89 |
| 2 | `02-checks.md` | `check-all.sh`、CI のジョブの置き換え、e2e の lint、ac-check と hardcoded-text の直し、CI の文書、hook | 77、78、79、80、81、94 |
| 3 | `03-local-env.md` | `reset.sh`、正本のそろい、E2E の前提、作られるファイル、Mailpit のポート、ポートの予約の script | 82、83、84、90、93、96 |
| 4 | `04-ci-time.md` | E2E の `needs`、統合テストとサーバーの起動の測定 | 85、86、95 |
| 5 | `05-style-checks.md` | Checkstyle の Javadoc、既存の漏れの直し、インスタンスを作らないクラス | 87、88 |
| 6 | `06-local-time.md`（P2） | 手元の時間の測定と短縮、機能紹介の数字 | 91、92 |
| 7 | `07-review.md` | 見直し（[書き方の規則と見直し](../../process/review.md)）、CI の時間の記録、spec の完了 | すべて |

- 段階 1 を最初にする：段階 2 以降の tasks のタスク番号が新しい形式を使うため（tasks は始めから新しい形式で書き、段階 1 で規則と script を追いつかせる）。
- 段階 2 を段階 3 より先にする：hook と `check-all.sh` を、以降の commit で使うため。
- 段階 6 は P2。spec 012 に入る前に時間が足りなければ、Non-goals に移す（優先度 P2 の扱い）。

## 10. 確かめ方

| DC | 確かめ方 |
|----|----------|
| 73、74 | 見直しで読む（notation の規則、本 spec の tasks がその形式） |
| 75 | `spec-numbers.test.mjs`（新旧の形式の例）。`git diff` に spec 001〜010 の変更がない |
| 76 | 見直しで読む（`docs/specs/mvp.md` と README のリンク）。`doc-refs` が通る |
| 77 | `check-all.sh` を手元と CI で流す。検査の script を 1 つ仮に足して流れることを確かめ、消す |
| 78 | `check-frontend.sh` の出力に e2e の lint・typecheck が出る |
| 79 | `ac-check.test.mjs` の新しい例（レッドを確かめてから直す） |
| 80 | `hardcoded-text.test.mjs` の新しい例（同上） |
| 81 | 見直しで読む（`docs/dev/ci.md`、skill のリンク） |
| 94 | 検査に当たる変更を stage して commit し、hook で止まることを確かめる（commit はしない） |
| 82 | `reset.sh db` の後、`ec_site` が空で、`ec_site_e2e` と `keycloak` の database が残っている |
| 83 | `reset.sh all` の後、第 3.1 節の画面の確かめ |
| 84 | mailpit を止めて `e2e.sh` を流し、名前を示して止まることを確かめる |
| 85 | CI の直近 3 回の時間（spec の末尾） |
| 86 | 見直しで読む（本書 第 4 節） |
| 95 | ci.yml の E2E に `needs` がない。CI の実行で E2E がほかのジョブと同時に始まる |
| 87 | `./gradlew check` が通る。Javadoc を消した public のメソッドを仮に作って `check` が失敗することを確かめ、戻す |
| 88 | ArchUnit のテスト |
| 89 | 第 6 節の grep で、現行の文書と skill に当たりがない |
| 90 | 見直しで読む（script の権限の明示、変える前の表示）。利用者のマシンで一度流す |
| 96 | `MAILPIT_SMTP_PORT` を付けずに compose とバックエンドを起動し、出荷通知のメールが Mailpit に届く（E2E のメールのテストが通る） |
| 91 | 見直しで読む（本書 第 8.1 節の結果） |
| 92 | script の出力と、機能紹介のページの今の数字を照らす |
| 93 | 店舗画面の開発のサーバーを起動した後、`git status` に作られたファイルが出ない |
| DN-10 | 各段階の終わりに、既存のテスト（`./gradlew check`、`check-frontend.sh`、`e2e.sh`）を変えずに通す |
| DN-11 | 見直しで ci.yml の差分を読み、検査の手順が減っていない |
