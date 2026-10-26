# Spec 195：TypeScript 7 と js-yaml 5 に上げる

ステータス：草案

## 1. 背景と目的

Dependabot が作った 2 つの major の更新の pull request は、CI が失敗したので閉じた。どちらもフロントの依存で、コードか設定の修正が必要になる。1→10 の改善の第 5 批として、1 つの spec で扱う。

| pull request | 更新 | 失敗した CI | 失敗の原因 |
|--------------|------|-------------|------------|
| #121 | TypeScript 6.0.3 → 7.0.2 | 管理画面、店舗画面、E2E | API の型の生成（openapi-typescript）が起動の時に落ちる ※1 |
| #122 | js-yaml 4.3.2 → 5.4.2 | 管理画面、店舗画面、E2E、バックエンド | 入力の制約の書き出しの script が、なくなった既定の export を読む ※2 |

※1：TypeScript 7 は Go で書き直したバージョンで、JavaScript のコンパイラーの API をまだ持たない（TypeScript Go の README で API は「not ready」）。openapi-typescript は、その API で型を組み立てる。

※2：js-yaml 5 は ESM の既定の export をなくし、名前付きの export だけにした（js-yaml の移行の文書 v4 → v5）。バックエンドの失敗（AC-296 の統合テスト 1 件）は、js-yaml を使わないので別の一時的な失敗とみる。実装の後の CI で、再び起きないことを確認する。

追跡の issue は [#195](https://github.com/roger88106/ec-site/issues/195)、扱う issue は [#126](https://github.com/roger88106/ec-site/issues/126)（TypeScript 7）と [#127](https://github.com/roger88106/ec-site/issues/127)（js-yaml 5）、作業ブランチは `195-deps-major` である。本 spec は整理の spec で、規模は M である（利用者が 2026-10-26 に承認した）。API の契約、データベース、AC を変えず、変えるのは依存の指定、設定と script の数行だが、管理画面、店舗画面、共有のパッケージ、E2E の 4 つにまたがり、第 6 節の決定が必要になるので S ではない。

## 2. スコープ

| # | 目的 | 対象 | 優先度 | issue |
|---|------|------|--------|-------|
| DS-195-1 | 型チェックとビルドを TypeScript 7 で行い、TypeScript の JavaScript の API を使うツールも動く | D | P1 | #126 |
| DS-195-2 | 共有のパッケージの script が js-yaml 5 で動く | D | P1 | #127 |
| DS-195-3 | Dependabot と文書が、上げた後の構成に合う | D | P1 | #126、#127 |

## 3. 目的と完了条件

- 1 つの DC に条件を 1 つ書く。
- DC の確認方法は、規模 M には plan がないので、tasks で決める（spec 183 と同じ扱い）。

### DS-195-1 TypeScript 7（D / P1）

| DC | 完了の条件 |
|----|------------|
| **DC-195-1** | 管理画面、店舗画面、共有のパッケージ、E2E の型チェックが、TypeScript 7 のコンパイラーで行われる |
| **DC-195-2** | API の型の生成（管理画面、店舗画面、E2E）が動き、生成した型が上げる前と同じである |
| **DC-195-3** | 店舗画面のビルド（Next.js）が、型チェックを含めて通る |
| **DC-195-4** | 管理画面のビルドが通る |
| **DC-195-5** | TypeScript 7 で新しく出た型の誤りと、7 で廃止された設定を、型チェックを弱めずに直す ※1 |
| **DC-195-6** | 管理画面、店舗画面、E2E の CI が通る |

※1：型チェックを弱めるとは、`strict` などの検査の設定を外すこと、型の誤りを `any`・`@ts-ignore`・`@ts-expect-error` で黙らせることをいう。

### DS-195-2 js-yaml 5（D / P1）

| DC | 完了の条件 |
|----|------------|
| **DC-195-7** | 入力の制約の書き出しの script が、js-yaml 5 の名前付きの export で YAML を読む |
| **DC-195-8** | 書き出した入力の制約の定数（管理画面、店舗画面）が、上げる前と同じである |
| **DC-195-9** | 共有のパッケージの script のテストが通る |

### DS-195-3 Dependabot と文書（D / P1）

| DC | 完了の条件 |
|----|------------|
| **DC-195-10** | Dependabot の設定が、第 6 節の 1 で決めた構成の依存（TypeScript 6 を残すなら、その指定）に合い、上げられない major の更新の pull request を作らない |
| **DC-195-11** | 依存の構成（TypeScript の 2 つのバージョンを使い分けるなら、その理由と、6 を外す条件）が、文書かコメントにある |
| **DC-195-12** | TypeScript のバージョンを書いた今の文書とコメントが、上げた後のバージョンに合う |

## 4. 守る制約

- **DN-195-1** 消費者・運営者に見える振る舞い、API の契約、データベースを変えない。
- **DN-195-2** 型チェックとビルドの検査を弱めない（DC-195-5 の ※1）。
- **DN-195-3** 並行する spec（192 のフロントの型の別名と `cn`、193 の店舗画面、194 の E2E）と変えるファイルが重なりうる。並行の可否は plan の段階で決める。規模 M には plan がないので、実装の前の照合（[開発の進め方](../../rules/workflow.md) 第 5.6 節）で、互いの変えるファイルを照合して決め、結果を tasks と追跡の issue に書く。
- **DN-195-4** 新しく書くコメントに、spec・tasks の番号を書かない（[コメントの書き方](../../rules/comments.md) 第 1 節）。

## 5. Non-goals

| 項目 | 理由 |
|------|------|
| TypeScript 7 の新しい機能や速さに合わせた設定の再検討 | 上げるのに必要な修正だけにする。速さの比較は後でよい |
| openapi-typescript を別のツールに替える | 第 6 節の 1 の案 C。上げるのにツールの入れ替えまでは必要ない |
| IDE（IntelliJ IDEA）の TypeScript の設定 | 各自の環境の設定で、リポジトリの構成ではない。困ることがあれば `/.local/agent/environment.md` に書く |

## 6. Open questions

### 6.1 TypeScript の JavaScript の API を使うツールをどう動かすか

TypeScript 7 は JavaScript の API を持たないので、API を使うツールは 7 では動かない。今のリポジトリで API を使うのは、次の 2 つである。

| ツール | API の使い方 | TypeScript 7 での動き |
|------|--------------|-----------------------|
| openapi-typescript（API の型の生成） | 型の構文木を API で組み立てる | 動かない（#121 で確認）。TypeScript 7 に合うバージョンがあるかは、実装の前に確認する |
| Next.js（店舗画面のビルドの型チェック） | 既定は、プロジェクトの `tsc` のコマンドを呼ぶ。設定で API に替えられる | Next.js の文書では、コマンドで型チェックするので 7 で動く。今のバージョン（16.4.2）がそうかは、実装の前に確認する |

| 案 | 内容 |
|----|------|
| 案 A（推奨） | `typescript` を 7 に上げ、openapi-typescript にだけ TypeScript 6 を渡す（pnpm の依存の上書き） |
| 案 B | TypeScript の公式の推奨の形。`typescript` を 6 のまま残し、7 を別の名前（`@typescript/native` など）で入れて、型チェックのコマンドを 7 で呼ぶ |
| 案 C | openapi-typescript を、API を使わない型の生成のツールに替える |

- 案 A の理由：型チェック、Next.js のビルド、エディターが同じ `typescript`（7）を使い、6 を使う所が 1 か所だけになる。openapi-typescript が 7 に合えば、上書きを消すだけで 6 を外せる。
- 案 A の手間と不確かさ：pnpm の上書きが、openapi-typescript の peer の依存に効くかを実装の前に確認する。効かなければ案 B にする（第 3 節の DC は案によらず同じ）。
- 案 B の理由：TypeScript の 7.0 の発表と、TypeScript Go の拡張機能が推奨する形。API を使うツールが増えても、6 がそのまま使える。
- 案 B の手間：2 つのバージョンが `tsc` という同じ名前のコマンドを持つので、型チェックの script が 7 のコマンドを場所で指定する必要がある。Next.js は 6 の `tsc` を呼ぶので、店舗画面のビルドの型チェックは 6 のままになる。Dependabot は `typescript` の major を上げないように除外する。
- 案 C は、生成した型の形が変わり、管理画面、店舗画面、E2E の使う所を直す範囲が広い。上げるだけの spec には大きい。

### 6.2 js-yaml 5 の読み方

| 案 | 内容 |
|----|------|
| 案 A（推奨） | 名前付きの export（`load`）を読む |
| 案 B | 名前空間の import（`import * as yaml`）にし、今の呼び方（`yaml.load`）のまま使う |

- 案 A の理由：js-yaml の移行の文書が、新しいコードに名前付きの export を勧める。使うのは 1 か所の読み込みだけなので、直す量は案 B と変わらない。

## 7. tasks

第 6 節の推奨の案（6.1 は案 A、6.2 は案 A）で書く。spec の確認で別の案に決まったら、確定の前に直す。

- `[P]` のタスクはない。全てのタスクが `pnpm-lock.yaml` を変えるため。
- 新しいテストは書かない。振る舞いを変えず、DC は今のテスト、型チェック、ビルド、生成したファイルの比較で確認する。
- 各段階の最後のレビューは、profile「厚め」で行う（[エージェントの作業フロー](../../rules/subagents.md) 第 4 節）。重要な観点に「型チェックを弱めていないか」（DN-195-2）を含める。

### 段階 1：js-yaml 5 と TypeScript 7

- 前提：spec の確定、実装の前の照合（並行の可否を含む。DN-195-3）
- 完了条件：DC-195-1〜12 を満たし、`check-all.sh` と、管理画面、店舗画面、共有のパッケージ、E2E の lint・型チェック・テスト・ビルドが通る。
- 参照：第 3 節、第 6 節

- [ ] **T195-01-01** js-yaml を 5 に上げ、入力の制約の書き出しの script を名前付きの export で読む（DC-195-7〜9）。
  - 依存：なし
  - 範囲：`frontend/shared/package.json`、`frontend/shared/scripts/export-constraints.mjs`、`pnpm-lock.yaml`
  - 確認方法：DC-195-7 は script を読む（レビュー）。DC-195-8 は、上げる前後で管理画面と店舗画面の `generate:api` を流し、生成した定数のファイルに差がないことを確認する。DC-195-9 は共有のパッケージのテスト。
- [ ] **T195-01-02** 実装の前に、TypeScript 7 に合う openapi-typescript のバージョンがあるか、今の Next.js が型チェックにコマンドを使うか、pnpm の上書きが openapi-typescript に TypeScript 6 を渡せるかを確認し、結果をこのタスクに書く。
  - 依存：なし
  - 範囲：調べるだけ（コードを変えない）
  - openapi-typescript が 7 に合うなら上書きは必要ない。上書きが効かないなら案 B にする。どちらも、T195-01-03 の範囲と確認方法を直してから進める
- [ ] **T195-01-03** TypeScript を 7 に上げ、openapi-typescript に TypeScript 6 を渡し、7 で出た型の誤りと廃止された設定を直す（DC-195-1〜6）。
  - 依存：T195-01-01（同じ `pnpm-lock.yaml`）、T195-01-02
  - 範囲：`package.json`（ルート。上書き）、`frontend/admin/package.json`、`frontend/store/package.json`、`frontend/shared/package.json`、`e2e/package.json`、`pnpm-lock.yaml`、`frontend/tsconfig.base.json` と各 tsconfig（必要なとき）、型の誤りが出たソース
  - 確認方法：DC-195-1 は、各パッケージの型チェックが使う `tsc` が 7 であること（`tsc --version`）。DC-195-2 は、上げる前後で 3 つの `generate:api` が生成した型のファイルに差がないこと。DC-195-3、4 は各ビルド。DC-195-5 はレビューで読む（差分に検査の設定を外す所、`any`・`@ts-ignore`・`@ts-expect-error` の追加がないこと）。DC-195-6 は push の後の CI。
- [ ] **T195-01-04** Dependabot の設定を構成に合わせ、TypeScript のバージョンを書いた文書とコメントを直す（DC-195-10〜12）。
  - 依存：T195-01-03
  - 範囲：`.github/dependabot.yml`、`frontend/tsconfig.base.json`（バージョンを書いたコメント）、上書きの理由を書く所、TypeScript のバージョンを書いた文書（`git grep -n "TypeScript 6"` で探す）
  - 確認方法：レビューで読む。DC-195-10 は、上書きの TypeScript 6 の major を Dependabot が上げないことを設定で読む。
- [ ] **T195-01-05** 段階の最後のレビュー（profile「厚め」）を行い、指摘に対応する。
  - 依存：T195-01-01〜04

### 段階 2：完了

- 前提：段階 1
- 完了条件：spec の最後のレビューの指摘に対応し、spec をアーカイブに移している。
- 参照：[エージェントの作業フロー](../../rules/subagents.md) 第 4 節

- [ ] **T195-02-01** spec の最後のレビュー（profile「厚め」）と、pull request の前のレビューを行い、指摘に対応する。
  - 依存：T195-01-05
- [ ] **T195-02-02** 後回しにする作業と起票の案（第 8 節）を最新にし、spec を `docs/specs/archive/1to10/` に移す。全てのチェックボックスを `[x]` にする。
  - 依存：T195-02-01
  - 範囲：`docs/specs/`、spec へのリンクを持つ文書

## 8. 後回しにする作業と起票の案

作業の中で見つけたものは、この節に加える。
