# 段階 4：リポジトリの構成と workspace

- 前提：段階 3
- 完了条件：DC-18〜21 を満たし、移した後に CI がすべて通る。`docs/dev/` の手順で手元で起動でき、ローカルのデータ（DB、画像、Keycloak）が残っている。CI の所要時間が DN-3 の範囲（10 分 37 秒まで）。
- 参照：[plan](../plan.md) 第 5 節、第 2.2 節

## 確かめ（移す前）

- [x] **T4401** 前提を確かめる：ルートの `.env` の `COMPOSE_FILE` で `docker compose up -d` が `docker/compose.yaml` を使うこと、`name: ec-site` で volume の名前が変わらないこと、shadcn の CLI が workspace で動くこと。小さな試しで確かめ、だめなら代わりの方法を報告する。移す前のローカルのデータの件数（商品、注文、Keycloak の会員）を控える。

## 移動

- [ ] **T4402** `git mv` だけの commit（DN-2）：`backend/` → `backend/ecsite/`、`frontend/e2e/` → `e2e/`、`compose.yaml` → `docker/compose.yaml`、`compose/*` → `docker/*`、`keycloak/` → `docker/keycloak/`。
- [x] **T4403** 参照を直す（plan 第 5.2 節の表）：相対パス、compose の中のパスと `name: ec-site`、ルートの `.env`、テストの中のパス、`.gitignore`、CI の `working-directory`・キャッシュ・artifact。`./gradlew check`（`backend/ecsite`）、フロントの確かめ、E2E が手元で通る（DC-18、19）。
- [x] **T4404** 文書のパスを直す：constitution、README・`docs/dev/`、AGENTS.md、`docs/modules/*.md`、`docs/frontend/*.md`、skill `local-dev`。spec・plan・tasks（001〜003）は直さない（DC-21）。
- [x] **T4405** 手元で、控えたローカルのデータが残っていることを確かめる。IntelliJ の Gradle のプロジェクトの取り込み直しの手順を `docs/dev/setup.md` に書く（DC-19）。

## workspace と CI

- [ ] **T4406** ルートの `package.json`（`private`、`license: MIT`、`packageManager`、全体の script）、`pnpm-workspace.yaml`（`frontend/*`、`e2e`、`allowBuilds`）を作り、各プロジェクトの lock と `pnpm-workspace.yaml` を消して `pnpm install` する。依存の版の書き方を揃える（DC-20）。
- [ ] **T4407** `frontend/biome.base.jsonc`、`frontend/tsconfig.base.json` を作り、admin・store・e2e が `extends` で読む。各プロジェクトには違う所だけを残す。lint・型チェック・テスト・ビルドが通る（DC-20）。
- [ ] **T4408** CI：`.github/actions/setup-frontend/`（composite action）を作り、admin・store・e2e の job が使う。Playwright のブラウザーをキャッシュする。push して CI が通り、所要時間を DN-3 と比べて報告する。
