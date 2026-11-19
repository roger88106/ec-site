package com.ecsite.module.catalog.presentation;

import com.ecsite.module.catalog.domain.category.Category;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B 側の商品一覧の受け入れテスト
 *
 * <p>登録日時の新しい順を確認するため、商品を 1 つ登録するごとに時計を 1 分進める（{@link CatalogTestData#draft}）。
 * 他のテストの商品が見えないよう、一覧はテストごとに作るカテゴリで絞る。テストごとにロールバックする。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
@IntegrationTest
@Transactional
class ProductListApiTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/products";

    /**
     * 非公開の商品の品番
     */
    private static final String UNPUBLISHED_CODE = "A-001";

    /**
     * 非公開の商品の商品名
     */
    private static final String UNPUBLISHED_NAME = "非公開の商品";

    /**
     * 公開の商品の品番
     */
    private static final String PUBLISHED_CODE = "B-001";

    /**
     * 公開の商品の商品名
     */
    private static final String PUBLISHED_NAME = "公開の商品";

    /**
     * 販売終了の商品の品番
     */
    private static final String DISCONTINUED_CODE = "C-001";

    /**
     * 販売終了の商品の商品名
     */
    private static final String DISCONTINUED_NAME = "販売終了の商品";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * 衣類のカテゴリ
     *
     * <p>最上位。
     */
    private UUID clothing;

    /**
     * トップスのカテゴリ
     *
     * <p>衣類の子。
     */
    private UUID tops;

    /**
     * T シャツのカテゴリ
     *
     * <p>トップスの子。
     */
    private UUID tShirts;

    /**
     * 食品のカテゴリ
     *
     * <p>最上位。
     */
    private UUID food;

    /**
     * テストごとのカテゴリ
     *
     * <p>一覧をこのカテゴリで絞って、他のテストの商品が見えないようにする。
     */
    private UUID scope;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("標準 " + UUID.randomUUID().toString().substring(0, 8), "10");
        clothing = catalog.category("衣類 " + UUID.randomUUID(), null);
        tops = catalog.category("トップス", clothing);
        tShirts = catalog.category("T シャツ", tops);
        food = catalog.category("食品 " + UUID.randomUUID(), null);
        scope = catalog.category("範囲 " + UUID.randomUUID(), null);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("一覧の内容")
    class Contents {

        @Test
        @DisplayName("AC-47 状態に関係なく、全ての商品が返る")
        void listsProductsInAnyStatus() throws Exception {
            // 準備：非公開・公開・販売終了の商品
            var ids = productsInEachStatus();

            // 操作と確認：3 つの商品が全て返る
            listInScope()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids.unpublished(), ids.published(), ids.discontinued())))
                    .andExpect(jsonPath("$.totalItems").value(3));
        }

        @Test
        @DisplayName("商品は登録日時の新しい順に並ぶ")
        void listsNewestFirst() throws Exception {
            // 準備：非公開・公開・販売終了の順に登録した商品
            var ids = productsInEachStatus();

            // 操作と確認：登録の逆の順に返る
            listInScope().andExpect(jsonPath("$.items[*].id", contains(ids.discontinued(), ids.published(), ids.unpublished())));
        }

        @Test
        @DisplayName("AC-49 各商品の品番・商品名・状態・バリアント数が返る")
        void returnsSummaryFields() throws Exception {
            // 準備：非公開・公開・販売終了の商品
            productsInEachStatus();

            // 操作と確認：各商品の項目
            assertSummaryFields(listInScope());
        }
    }

    @Nested
    @DisplayName("品番の絞り込み")
    class FilterByCode {

        @Test
        @DisplayName("AC-48 商品品番の前方一致で絞り込める")
        void filtersByProductCode() throws Exception {
            // 準備：品番が ABC で始まる商品と、始まらない商品
            var abc = catalog.draft("ABC-001", taxCategoryId, "商品 1", scope).id.toString();
            catalog.draft("DEF-001", taxCategoryId, "商品 2", scope);
            catalog.draft("XABC-001", taxCategoryId, "前方一致しない", scope);

            // 操作と確認：ABC で始まる商品だけが返る
            listInScope("code", "ABC")
                    .andExpect(jsonPath("$.items[*].id", contains(abc)))
                    .andExpect(jsonPath("$.totalItems").value(1));
        }

        @Test
        @DisplayName("AC-48 バリアント品番の前方一致で絞り込める")
        void filtersByVariantCode() throws Exception {
            // 準備：バリアント品番が ABC で始まる商品と、始まらない商品
            var xyz = catalog.draft("XYZ-001", taxCategoryId, "商品 1", scope).variantCoded("ABC-VARIANT").id.toString();
            catalog.draft("DEF-001", taxCategoryId, "商品 2", scope).variantCoded("DEF-VARIANT");

            // 操作と確認：バリアント品番が ABC で始まる商品だけが返る
            listInScope("code", "ABC")
                    .andExpect(jsonPath("$.items[*].id", contains(xyz)))
                    .andExpect(jsonPath("$.totalItems").value(1));
        }

        @Test
        @DisplayName("DC-501-4 商品品番とバリアント品番の両方が一致し、複数のバリアントが一致しても、商品は 1 回だけ返る")
        void returnsProductOnceWhenProductAndVariantsMatch() throws Exception {
            // 準備：商品品番と 2 つのバリアント品番が、全て ABC で始まる商品と、始まらない商品
            var abc = catalog.draftWithAxes("ABC-001", taxCategoryId, "商品 1", List.of("色"), scope)
                    .variantCoded("ABC-001-R", "赤").variantCoded("ABC-001-B", "青").id.toString();
            catalog.draft("DEF-001", taxCategoryId, "商品 2", scope);

            // 操作と確認：商品が 1 回だけ返り、件数も 1
            listInScope("code", "ABC")
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[*].id", contains(abc)))
                    .andExpect(jsonPath("$.totalItems").value(1));
        }

        @Test
        @DisplayName("AC-48 品番の英小文字は英大文字として比べる")
        void treatsLowerCaseAsUpperCase() throws Exception {
            // 準備：商品品番とバリアント品番が ABC で始まる商品と、前方一致しない商品
            var abc = catalog.draft("ABC-001", taxCategoryId, "商品 1", scope).id.toString();
            var xyz = catalog.draft("XYZ-001", taxCategoryId, "商品 2", scope).variantCoded("ABC-VARIANT").id.toString();
            catalog.draft("DEF-001", taxCategoryId, "商品 3", scope);
            catalog.draft("XABC-001", taxCategoryId, "前方一致しない", scope);

            // 操作と確認：abc で、ABC で始まる 2 つの商品が返る
            listInScope("code", "abc")
                    .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(abc, xyz)))
                    .andExpect(jsonPath("$.totalItems").value(2));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"A%", "A_C"})
        @DisplayName("AC-48 品番の絞り込みの % と _ は、ワイルドカードでなく、ただの文字として前方一致で比べる")
        void doesNotTreatWildcardsInCodeAsWildcards(String code) throws Exception {
            // 準備：ワイルドカードとして扱えば一致する品番の商品
            catalog.draft("ABC-001", taxCategoryId, "商品 1", scope);

            // 操作と確認：一致しない
            listInScope("code", code)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(0));
        }
    }

    @Nested
    @DisplayName("商品名の絞り込み")
    class FilterByName {

        @Test
        @DisplayName("AC-48 商品名の部分一致で絞り込める")
        void filtersByName() throws Exception {
            // 準備：商品名の途中に T-Sh を含む商品と、含まない商品
            var shirt = catalog.draft("A-001", taxCategoryId, "Basic T-Shirt", scope).id.toString();
            catalog.draft("A-002", taxCategoryId, "パンツ", scope);

            // 操作と確認：T-Sh を含む商品だけが返る
            listInScope("name", "T-Sh").andExpect(jsonPath("$.items[*].id", contains(shirt)));
        }

        @Test
        @DisplayName("AC-48 商品名の英字の大文字・小文字は区別しない")
        void ignoresCase() throws Exception {
            // 準備：Shirt・SHIRT を含む商品と、含まない商品
            var shirt = catalog.draft("A-001", taxCategoryId, "Basic T-Shirt", scope).id.toString();
            var poloShirt = catalog.draft("A-002", taxCategoryId, "ポロ SHIRT", scope).id.toString();
            catalog.draft("A-003", taxCategoryId, "パンツ", scope);

            // 操作と確認：shirt で、両方の商品が返る
            listInScope("name", "shirt").andExpect(jsonPath("$.items[*].id", containsInAnyOrder(shirt, poloShirt)));
        }

        @Test
        @DisplayName("商品名の絞り込みの % は、ただの文字として扱う")
        void treatsPercentAsLiteral() throws Exception {
            // 準備：% を含む商品名と、% の位置に任意の文字があれば一致する商品名
            var percent = catalog.draft("A-001", taxCategoryId, "100% 綿", scope).id.toString();
            catalog.draft("A-002", taxCategoryId, "綿 100", scope);

            // 操作と確認：0% を含む商品だけが返る
            listInScope("name", "0%").andExpect(jsonPath("$.items[*].id", contains(percent)));
        }

        @Test
        @DisplayName("商品名の絞り込みの _ は、ただの文字として扱う")
        void treatsUnderscoreAsLiteral() throws Exception {
            // 準備：_ を含まない商品名
            catalog.draft("A-001", taxCategoryId, "100% 綿", scope);
            catalog.draft("A-002", taxCategoryId, "綿 100", scope);

            // 操作と確認：_ を含む商品はないので、空
            listInScope("name", "_").andExpect(jsonPath("$.items").isEmpty());
        }
    }

    @Nested
    @DisplayName("状態とカテゴリの絞り込み")
    class FilterByStatusAndCategory {

        @Test
        @DisplayName("AC-48 状態で絞り込める")
        void filtersByStatus() throws Exception {
            // 準備：非公開の商品と、販売終了の商品
            catalog.draft("A-001", taxCategoryId, UNPUBLISHED_NAME, scope);
            var discontinued = catalog.draft("A-002", taxCategoryId, DISCONTINUED_NAME, scope).id.toString();
            discontinue(discontinued);

            // 操作と確認：販売終了の商品だけが返る
            listInScope("status", "DISCONTINUED").andExpect(jsonPath("$.items[*].id", contains(discontinued)));
        }

        @Test
        @DisplayName("AC-48 カテゴリで絞ると、子孫のカテゴリに所属する商品も含む")
        void includesDescendantCategories() throws Exception {
            // 準備：衣類・T シャツ（衣類の孫）・食品・カテゴリなしの商品
            var inClothing = catalog.draft("A-001", taxCategoryId, "衣類の商品", clothing).id.toString();
            var inTShirts = catalog.draft("A-002", taxCategoryId, "T シャツの商品", tShirts).id.toString();
            catalog.draft("A-003", taxCategoryId, "食品の商品", food);
            catalog.draft("A-004", taxCategoryId, "カテゴリなしの商品");

            // 操作と確認：衣類で絞ると、衣類と T シャツの商品が返る
            mockMvc.perform(get(BASE).param("categoryId", clothing.toString()))
                    .andExpect(jsonPath("$.items[*].id", contains(inTShirts, inClothing)));
        }

        @Test
        @DisplayName("AC-48 カテゴリで絞ると、親のカテゴリの商品は含まない")
        void excludesAncestorCategories() throws Exception {
            // 準備：衣類と、T シャツ（トップスの子）の商品
            catalog.draft("A-001", taxCategoryId, "衣類の商品", clothing);
            var inTShirts = catalog.draft("A-002", taxCategoryId, "T シャツの商品", tShirts).id.toString();

            // 操作と確認：トップスで絞ると、T シャツの商品だけが返る
            mockMvc.perform(get(BASE).param("categoryId", tops.toString()))
                    .andExpect(jsonPath("$.items[*].id", contains(inTShirts)));
        }

        @Test
        @DisplayName("AC-282 ドメインの上限の深さまでつないだカテゴリの、いちばん深いカテゴリの商品が、いちばん上のカテゴリの絞り込みで出る")
        void filtersByTopCategoryAtMaxDepth() throws Exception {
            // 準備：ドメインの上限の深さまでつないだカテゴリの、いちばん深いカテゴリの商品
            var field = Category.class.getDeclaredField("MAX_DEPTH");
            field.setAccessible(true);
            int maxDepth = field.getInt(null);
            var top = catalog.category("深さ 1", null);
            var deepest = top;
            for (int depth = 2; depth <= maxDepth; depth++) {
                deepest = catalog.category("深さ " + depth, deepest);
            }
            var product = catalog.draft("A-001", taxCategoryId, "いちばん深いカテゴリの商品", deepest).id.toString();

            // 操作と確認：いちばん上のカテゴリで絞ると、その商品が返る
            mockMvc.perform(get(BASE).param("categoryId", top.toString()))
                    .andExpect(jsonPath("$.items[*].id", contains(product)));
        }

        @Test
        @DisplayName("絞り込みを組み合わせられる")
        void combinesFilters() throws Exception {
            // 準備：品番・商品名の片方だけが一致する商品と、両方が一致する商品
            var target = catalog.draft("ABC-001", taxCategoryId, "T シャツ", clothing).id.toString();
            catalog.draft("ABC-002", taxCategoryId, "パンツ", clothing);
            catalog.draft("XYZ-001", taxCategoryId, "T シャツ", clothing);

            // 操作と確認：品番・商品名・カテゴリの全てに一致する商品だけが返る
            mockMvc.perform(get(BASE).param("code", "ABC").param("name", "シャツ").param("categoryId", clothing.toString()))
                    .andExpect(jsonPath("$.items[*].id", contains(target)));
        }
    }

    @Nested
    @DisplayName("ページ")
    class Paging {

        @Test
        @DisplayName("AC-50 指定したページの商品が返る")
        void returnsRequestedPage() throws Exception {
            // 準備：5 つの商品
            var ids = fiveProducts();

            // 操作と確認：1 ページ 2 件の 2 ページ目は、新しい順の 3・4 番目
            listSecondPageOfTwo().andExpect(jsonPath("$.items[*].id", contains(ids[2], ids[1])));
        }

        @Test
        @DisplayName("AC-50 ページの番号・件数・総件数・総ページ数が返る")
        void returnsPageInfo() throws Exception {
            // 準備：5 つの商品
            fiveProducts();

            // 操作と確認：1 ページ 2 件の 2 ページ目の情報
            assertSecondPageOfTwoInfo(listSecondPageOfTwo());
        }

        @Test
        @DisplayName("1 ページの件数が 100 を超えれば 400")
        void rejectsTooLargePageSize() throws Exception {
            // 操作と確認：101 件は、リクエストの誤りで拒否される
            mockMvc.perform(get(BASE).param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, 100})
        @DisplayName("1 ページの件数が範囲の端なら取得できる")
        void acceptsPageSizeAtBoundary(int size) throws Exception {
            // 操作と確認：取得できる
            mockMvc.perform(get(BASE).param("size", String.valueOf(size))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("1 ページの件数が 0 なら 400")
        void rejectsZeroPageSize() throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            mockMvc.perform(get(BASE).param("size", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("ページの番号は 1 から指定できる")
        void acceptsFirstPage() throws Exception {
            // 操作と確認：取得できる
            mockMvc.perform(get(BASE).param("page", "1")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("ページの番号が 0 なら 400")
        void rejectsZeroPage() throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            mockMvc.perform(get(BASE).param("page", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("絞り込みの条件の文字数")
    class FilterLengths {

        @Test
        @DisplayName("品番の条件は 32 文字まで指定できる")
        void acceptsCodeUpToMaximum() throws Exception {
            // 操作と確認：取得できる
            mockMvc.perform(get(BASE).param("code", "A".repeat(32))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("品番の条件が 33 文字なら 400")
        void rejectsCodeOverMaximum() throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            mockMvc.perform(get(BASE).param("code", "A".repeat(33)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("商品名の条件は 200 文字まで指定できる")
        void acceptsNameUpToMaximum() throws Exception {
            // 操作と確認：取得できる
            mockMvc.perform(get(BASE).param("name", "あ".repeat(200))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("商品名の条件が 201 文字なら 400")
        void rejectsNameOverMaximum() throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            mockMvc.perform(get(BASE).param("name", "あ".repeat(201)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 一覧の各商品の項目が、{@link #productsInEachStatus()} で作った商品のものであることを確認する。
     *
     * <p>確認すること（期待値は定数。新しい順）：
     * <ul>
     *   <li>品番</li>
     *   <li>商品名</li>
     *   <li>状態</li>
     *   <li>バリアント数</li>
     * </ul>
     *
     * @param list 一覧の応答
     */
    private static void assertSummaryFields(ResultActions list) throws Exception {
        list.andExpect(jsonPath("$.items[*].productCode", contains(DISCONTINUED_CODE, PUBLISHED_CODE, UNPUBLISHED_CODE)))
                .andExpect(jsonPath("$.items[*].name", contains(DISCONTINUED_NAME, PUBLISHED_NAME, UNPUBLISHED_NAME)))
                .andExpect(jsonPath("$.items[*].status", contains("DISCONTINUED", "PUBLISHED", "UNPUBLISHED")))
                .andExpect(jsonPath("$.items[*].variantCount", contains(0, 1, 0)));
    }

    /**
     * 5 件の商品を 1 ページ 2 件で分けた、2 ページ目の情報であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>ステータスが 200</li>
     *   <li>ページの番号が 2</li>
     *   <li>1 ページの件数が 2</li>
     *   <li>総件数が 5</li>
     *   <li>総ページ数が 3</li>
     * </ul>
     *
     * @param page ページの応答
     */
    private static void assertSecondPageOfTwoInfo(ResultActions page) throws Exception {
        page.andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 状態ごとの商品の ID
     *
     * @param unpublished  非公開の商品（バリアントなし）
     * @param published    公開の商品（バリアント 1 つ）
     * @param discontinued 販売終了の商品（バリアントなし）
     */
    private record StatusProducts(String unpublished, String published, String discontinued) {
    }

    /**
     * テストごとのカテゴリに、非公開・公開・販売終了の商品を、この順に登録する。
     *
     * @return 商品の ID
     */
    private StatusProducts productsInEachStatus() throws Exception {
        var unpublished = catalog.draft(UNPUBLISHED_CODE, taxCategoryId, UNPUBLISHED_NAME, scope).id.toString();
        var published = catalog.draft(PUBLISHED_CODE, taxCategoryId, PUBLISHED_NAME, scope).variant().publish().id.toString();
        var discontinued = catalog.draft(DISCONTINUED_CODE, taxCategoryId, DISCONTINUED_NAME, scope).id.toString();
        discontinue(discontinued);
        return new StatusProducts(unpublished, published, discontinued);
    }

    /**
     * テストごとのカテゴリに、商品を 5 つ登録する。
     *
     * @return 商品の ID（登録の順）
     */
    private String[] fiveProducts() throws Exception {
        var ids = new String[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = catalog.draft("A-00" + i, taxCategoryId, "商品 " + i, scope).id.toString();
        }
        return ids;
    }

    /**
     * 商品を販売終了にする。
     *
     * @param id 商品 ID
     */
    private void discontinue(String id) throws Exception {
        mockMvc.perform(put(BASE + "/" + id + "/discontinue")).andExpect(status().isNoContent());
    }

    /**
     * テストごとのカテゴリで絞った一覧を取得する。
     *
     * @return 応答
     */
    private ResultActions listInScope() throws Exception {
        return mockMvc.perform(get(BASE).param("categoryId", scope.toString()));
    }

    /**
     * テストごとのカテゴリと、もう 1 つの条件で絞った一覧を取得する。
     *
     * @param name  条件の名前
     * @param value 条件の値
     * @return 応答
     */
    private ResultActions listInScope(String name, String value) throws Exception {
        return mockMvc.perform(get(BASE).param(name, value).param("categoryId", scope.toString()));
    }

    /**
     * テストごとのカテゴリで絞った一覧の、1 ページ 2 件の 2 ページ目を取得する。
     *
     * @return 応答
     */
    private ResultActions listSecondPageOfTwo() throws Exception {
        return mockMvc.perform(get(BASE).param("categoryId", scope.toString()).param("page", "2").param("size", "2"));
    }
}
