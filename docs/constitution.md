# EC Site Constitution

本文件是專案層級的長期規則。所有 spec、plan、tasks 與程式碼都必須遵守它。
與本文件衝突時，以本文件為準；要改規則，先改本文件（並說明原因），再改其他東西。

## 0. 如何閱讀本文件

每條規則帶有狀態標籤：

| 標籤 | 意義 |
|------|------|
| 【已定】 | 作者明確決定，不可擅自更動 |
| 【沿用】 | 沿用參考專案 MyIAM 的做法，除非有理由，否則照做 |
| 【提案】 | 目前的暫定方案，在第一份 plan 前確認或調整 |
| 【待定】 | 尚未決定，列於第 12 節 |

## 1. 專案定位

- 自家 B2C 電商網站，不是 B2B2C，不是多商家平台。【已定】
- 個人作品集專案，目標是**模擬正式產品的做法**，而非追求規模。【已定】
- 規模不大，但**核心流程必須完整**：瀏覽 → 購物車 → 下單 → 付款 → 出貨 → 物流狀態。【已定】
- 兩個使用者面向：
  - C 端（消費者）：商品瀏覽、購物車、結帳、金流、訂單與物流狀態查詢
  - B 端（經營者）：商品上架、價格修改、庫存、訂單管理、出貨提示、數據收集與簡易報表
- 目前階段：從零開始的 MVP。MVP 的原則是**能跑完整條核心流程的最小版本**，不是每個功能都做深。

## 2. 技術棧

### 後端

| 領域 | 採用 | 狀態 |
|------|------|------|
| 語言 / 執行環境 | Java 21 | 【沿用】 |
| 框架 | Spring Boot 4.0.x | 【沿用】開工前確認版本 |
| 模組化 | Spring Modulith、jMolecules | 【沿用】 |
| 資料庫 | PostgreSQL 18 | 【沿用】 |
| 資料存取 | jOOQ（程式碼生成） | 【提案】見第 6 節 |
| 快取 / 短期資料 | Redis（僅在確實需要時引入，MVP 不預設引入） | 【提案】 |
| 建置 | Gradle Kotlin DSL + version catalog，版本集中在 `gradle/libs.versions.toml` | 【沿用】 |
| 物件對應 | MapStruct、Lombok（設定沿用 MyIAM `lombok.config`） | 【沿用】 |
| 測試 | JUnit 5、AssertJ、Testcontainers、MockMvc | 【沿用】 |

### 前端

- 前後端分離，前端為獨立 SPA，使用 React / Next.js。【已定】
- C 端與 B 端都要有畫面。【已定】
- 細節（C 端與 B 端是否同一個 Next.js 專案、狀態管理、UI 套件）見第 12 節。【待定】

## 3. 倉庫結構

【提案】單一 repo（monorepo）。理由：SDD 以 spec 為唯一事實來源，一份 spec 往往同時牽動前後端；拆成多個 repo 會讓 spec 與實作分居兩地、版本難以對齊。

```
ec-site/
├── docs/
│   ├── constitution.md
│   ├── architecture.md          # 之後由 plan 累積
│   ├── modules/                 # 各模組的設計說明（含該模組採用的層級，見 4.2）
│   └── specs/
│       └── 001-<feature>/
│           ├── spec.md
│           ├── plan.md
│           └── tasks.md
├── backend/                     # Spring Boot（Gradle）
├── frontend/
│   ├── store/                   # C 端
│   └── admin/                   # B 端
└── docker-compose.yml
```

重新檢討的觸發條件：前後端的發版節奏明顯分離、或需要不同的 CI 與權限管理時，再考慮拆 repo。拆分前必須先更新本節。

## 4. 後端架構

### 4.1 模組化單體

- 保留模組化：內部以 Spring Modulith 切成多個模組。【已定】目前為單一部署單位。【沿用】
- 模組之間的依賴必須在各模組的 `package-info.java` 以 `@ApplicationModule(allowedDependencies = ...)` 宣告；未宣告的依賴由 `ModulithTest` 擋下。【沿用】
- 模組對外只暴露兩種東西：【沿用】
  - `api/`（`@NamedInterface("api")`）：同步呼叫。介面以「呼叫方需要的形狀」設計，呼叫方用防腐層（如 `XxxDirectory`）包起來，不把對方的型別帶進自己內部。
  - `event/`（`@NamedInterface("event")`）：非同步通知。狀態變化用事件傳遞，不用呼叫串接。事件發布沿用 Spring Modulith 的事件持久化（AFTER_COMMIT）。
- 預計模組：【提案】

| 模組 | 責任 |
|------|------|
| `catalog` | 商品、價格、上下架 |
| `inventory` | 庫存數量、預留與扣減 |
| `cart` | 購物車 |
| `order` | 訂單與其狀態流轉 |
| `payment` | 付款（外部金流的防腐層） |
| `shipping` | 出貨與物流狀態（外部物流的防腐層） |
| `reporting` | 數據收集與簡易報表 |

模組的新增、合併、拆分都屬於架構變更，需先更新本節與 `docs/modules/`。

### 4.2 模組內的架構層級（依模組選型）

不同模組可以採用不同層級，選擇依據是該模組的複雜度，而不是統一。【已定】每個模組採用哪一級，寫在 `docs/modules/<module>.md`，並說明理由。【提案】

| 層級 | 結構 | 適用情境 |
|------|------|----------|
| A. 完整 | DDD + Clean Architecture + CQRS（domain / application / infrastructure / presentation，作法同 MyIAM） | 有複雜不變條件、狀態機、與他模組有事件互動 |
| B. 簡化 | 三層（controller / service / repository），不拆 Command/Query | 以資料維護為主，規則簡單 |
| C. 事件儲存 | 以事件序列作為狀態來源（Event Sourcing） | 「狀態如何演變」本身就是價值，例如物流狀態歷程 |

暫定的分配（【提案】，最終在各模組的 plan 確認）：

| 模組 | 暫定層級 | 理由 |
|------|----------|------|
| `catalog` | A | 價格與上下架有規則，且是其他模組的基礎 |
| `inventory` | A | 預留 / 扣減有併發與不變條件 |
| `cart` | B | 規則簡單 |
| `order` | A | 狀態機與跨模組事件 |
| `payment` | A | 狀態機，且包著外部整合 |
| `shipping` | C | 物流狀態歷程是核心 |
| `reporting` | B | 以讀取與彙整為主 |

層級 A 的規則（沿用 MyIAM）：

- domain 不依賴任何框架與其他層；application 依賴 domain；infrastructure 實作 domain 的 port；presentation 呼叫 application。
- 領域物件用 jMolecules 註解標示（`@AggregateRoot`、`@ValueObject`、`@Identity`）。
- 聚合的形狀由領域決定，不由資料表決定。持久化採 `toSnapshot()` → Factory 重建。
- 聚合內的狀態變更透過方法進行，並在方法內登錄領域事件。
- application 層拋出的例外限定為 `BusinessException` / `SystemException`。

層級 C 目前只是被允許的選項；實際採用前，必須在該模組的 plan 說明儲存方式、快照策略與投影（read model）的做法，並更新本文件。

### 4.3 前台 / 後台（C 端 / B 端）的切分

【提案】

- 後端維持單一部署單位。C 端與 B 端共用同一套領域模組（商品、庫存、訂單是同一份資料，拆成兩個後端只會帶來同步問題）。
- 兩端在 **presentation 層分開**：
  - C 端 API：`/api/store/**`
  - B 端 API：`/api/admin/**`
  - Controller、Request / Response 各自獨立，不共用。B 端的回應常含 C 端不該看到的欄位（成本、庫存明細等）。
- 拆成兩個後端專案的觸發條件：兩端需要明顯不同的擴縮、部署頻率或安全邊界。拆分前必須先更新本節。

## 5. 模組間的整合原則

- 同步呼叫只用於「查詢」或「必須即時得到結果」的情境，其餘一律事件。
- 事件是已發生的事實，用過去式命名（如 `OrderPlaced`）。
- 跨模組不共用資料表：一個模組不得直接讀寫另一個模組的 table。【沿用】
- 跨模組的最終一致性是預設；需要強一致的地方（例如庫存預留）必須在 spec 中明確寫出，並在 plan 說明做法。

## 6. 持久化

- 【提案】沿用 jOOQ，SQL 為 schema 的唯一真相。
  - 理由：(1) 與 MyIAM 一致；(2) 完整 DDD 模組的聚合由 Factory 重建，與 JPA 的 entity 映射並不契合，jOOQ 讓「領域模型」與「資料表」保持分離；(3) 庫存這類併發場景需要精確控制 SQL（條件式更新、鎖），jOOQ 比較直接。
  - 層級 B 的模組若覺得 jOOQ 太繁瑣，可在該模組的 plan 提出例外，但仍必須遵守本節其餘規則。
- schema 的正本：`backend/docker/postgres/init/*.sql`；compose、jOOQ 生成、整合測試都使用同一份 SQL。【沿用】
- jOOQ 生成碼不進版控；`compileJava` 前以 Testcontainers 的拋棄式 DB 生成。【沿用】
- schema 以模組為單位（每個模組一個 PostgreSQL schema）。【沿用】
- 樂觀鎖：會被併發修改的聚合必須有 `version` 欄位。【沿用】
- 金額：
  - 以最小貨幣單位的整數儲存與計算，並帶貨幣代碼；禁止使用 `float` / `double`。【提案】
  - 以值物件（`Money`）表達，不在程式中到處傳裸數字。
  - 貨幣與稅率規則見第 12 節。【待定】
- 時間：一律以 UTC 的 `Instant` 儲存（`timestamptz`），顯示時才轉換時區。【沿用】
- 識別碼：UUID。【沿用】

## 7. API 規約

- RESTful JSON，路徑前綴見 4.3。
- 建立資源回 `201` 加 `Location`；無回傳內容的更新回 `204`。【沿用】
- 輸入驗證在 presentation 層用 Bean Validation；領域規則在 domain 內檢查。兩者都要有，不能只靠前端。
- 錯誤沿用 MyIAM 的體系：`ErrorCode`（含 `ErrorType`、i18n 訊息、公開層級）、`BusinessException`、`SystemException`，統一由 `GlobalExceptionHandler` 轉成回應。每個領域有自己的 `XxxErrorCode` enum。【沿用】
- 錯誤回應的 JSON 格式在第一份 plan 決定，之後所有模組一致。【待定】
- API 契約以 OpenAPI 描述，前端據此取得型別。工具選擇在 plan 決定。【待定】
- 分頁、排序、搜尋的參數慣例在第一份 plan 決定，之後所有列表 API 一致。【待定】

## 8. 認證與授權

【已定】MVP 階段**不實作**認證授權，但**保留日後串接 MyIAM 的餘地**。

為了讓日後串接是「補上」而不是「改寫」，現在就要遵守：

1. domain 與 application 不得依賴 Spring Security 的型別。需要「操作者是誰」時，以參數（例如 `actorId`）傳入。
2. 每個 `/api/admin/**` endpoint，在 spec 中標註所需的權限，命名沿用 MyIAM 風格 `resource:action`（例如 `product:write`、`inventory:write`）。日後接上時，機械式補上 `@PreAuthorize("hasAuthority('...')")` 即可。
3. C 端與 B 端的路徑前綴（4.3）就是日後授權規則的切分線。
4. 預計日後 EC 以 Resource Server 角色接 MyIAM（JWT）；此為方向，不是 MVP 範圍。

注意：MVP 的 `/api/admin/**` 沒有保護。**不得將 MVP 版本部署到公開網路。**

## 9. 外部整合（金流、物流）

- 外部服務一律放在 port 後面，由 infrastructure 實作；外部的型別、欄位、錯誤碼不得外洩到 domain 與 application。【沿用防腐層做法】
- 選擇順序：【已定】
  1. 有免費、能模擬真實行為的外部 API（sandbox / test mode）→ 串外部
  2. 沒有 → 內部 mock
- 內部 mock 與外部實作必須實作**同一個 port**，以 Spring profile 或設定切換，且 mock 的行為（成功、失敗、逾時、延遲回呼）要能在測試中被控制。
- 每個外部整合需在對應 spec 寫明：失敗時的行為、重試與冪等的處理、回呼（webhook）的驗證方式。

## 10. 測試

- 沿用 MyIAM 的兩層結構：【沿用】
  - `src/test`：單元測試。聚合與值物件的行為，只用 JUnit + AssertJ，不啟動 Spring。
  - `src/integrationTest`：`@SpringBootTest` + Testcontainers（PostgreSQL），Docker 必須。
  - 測試與 `main` 使用相同的 package 結構。
- 必備的整合測試種類：
  - Modulith 邊界測試
  - Repository 測試（持久化、重建、差異更新、事件發布）
  - 併發測試（庫存預留 / 扣減不得超賣）——僅限涉及併發不變條件的模組
  - 外部整合的 port 測試（mock 與真實實作行為一致）
- **每條驗收條件至少對應一個測試**，測試名稱或註解標註 AC 編號（見第 11 節）。
- E2E 在 MVP 不自動化，以 `.http` 檔手動驗證。前端測試策略見第 12 節。【待定】

## 11. SDD 工作流

1. **Constitution**（本文件）：長期規則。
2. **Spec**（`docs/specs/NNN-<feature>/spec.md`）：只寫「做什麼、為什麼」與驗收條件，**不寫實作**（不寫 class 名、資料表欄位、框架）。
3. **Plan**（`plan.md`）：依 spec 決定架構、資料模型、API 設計、模組層級；必須逐項對照本文件。
4. **Tasks**（`tasks.md`）：把 plan 拆成小而可驗證的任務，每個任務標註對應的 AC。
5. **實作**：依 tasks 進行。

規則：

- **Spec 先於程式。** 實作中發現 spec 有誤，先改 spec，再改程式。不允許只改程式。
- 一份 spec 對應一個可獨立驗證的功能切片。MVP 先做商品與庫存（spec 001），其餘依核心流程順序展開。
- spec 必須包含：背景與目標、User stories、驗收條件（編號 AC-1、AC-2…）、Non-goals、Open questions。
- Open questions 沒有清空前，不得進入 plan。
- 驗收條件必須可驗證：看得出「做完了」或「沒做完」。
- 每個 spec 與 plan 的 commit 要分開，commit 訊息說明為何改。

## 12. 待定事項

在第一份 plan（spec 001）之前，需要確認：

1. 目標市場與貨幣：日圓、新台幣，還是多幣別？稅（含稅 / 未稅、稅率）如何處理？
2. 文件與程式碼註解的語言：MyIAM 使用日文，本專案使用繁體中文、日文，或英文？
3. monorepo 結構（第 3 節）是否採納。
4. 持久化採 jOOQ（第 6 節）是否採納。
5. 前端：C 端與 B 端是否同一個 Next.js 專案；狀態管理、UI 套件、API 型別生成方式；前端測試策略。
6. 錯誤回應格式、分頁 / 排序慣例（第 7 節）。
7. Redis 在 MVP 是否需要（購物車、庫存預留的暫存是可能的用途）。
8. Spring Boot 與各依賴版本，開工前依當時最新穩定版重新確認。
