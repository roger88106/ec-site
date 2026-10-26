package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.module.catalog.domain.category.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B 側の商品一覧の受け入れテスト。<br />
 * 登録日時の新しい順を確認するため、商品を 1 つ登録するごとに時計を 1 分進める。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class ProductListApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/products";

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

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

    private UUID clothing;

    private UUID tops;

    private UUID tShirts;

    private UUID food;

    /**
     * テストごとのカテゴリ（一覧をこのカテゴリで絞って、他のテストの商品が見えないようにする）
     */
    private UUID scope;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("標準", "10");
        clothing = catalog.category("衣類", null);
        tops = catalog.category("トップス", clothing);
        tShirts = catalog.category("T シャツ", tops);
        food = catalog.category("食品", null);
        scope = catalog.category("範囲 " + UUID.randomUUID(), null);
    }

    @Test
    @DisplayName("AC-47、49 状態に関係なくすべての商品が、登録日時の新しい順に、品番・名前・状態・バリアント数とともに返る")
    void listsAllProductsNewestFirst() throws Exception {
        var unpublished = catalog.draft("A-001", taxCategoryId, "非公開の商品", scope).id.toString();
        var published = catalog.draft("B-001", taxCategoryId, "公開の商品", scope).variant().publish().id.toString();
        var discontinued = catalog.draft("C-001", taxCategoryId, "販売終了の商品", scope).id.toString();
        mockMvc.perform(put(BASE + "/" + discontinued + "/discontinue")).andExpect(status().isNoContent());

        mockMvc.perform(get(BASE).param("categoryId", scope.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(discontinued, published, unpublished)))
                .andExpect(jsonPath("$.items[*].productCode", contains("C-001", "B-001", "A-001")))
                .andExpect(jsonPath("$.items[*].name", contains("販売終了の商品", "公開の商品", "非公開の商品")))
                .andExpect(jsonPath("$.items[*].status", contains("DISCONTINUED", "PUBLISHED", "UNPUBLISHED")))
                .andExpect(jsonPath("$.items[*].variantCount", contains(0, 1, 0)))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    @DisplayName("AC-48 商品品番またはバリアント品番の前方一致で絞り込める。英小文字は大文字として扱う")
    void filtersByCode() throws Exception {
        var abc = catalog.draft("ABC-001", taxCategoryId, "商品 1", scope).id.toString();
        var xyz = catalog.draft("XYZ-001", taxCategoryId, "商品 2", scope).variantCoded("ABC-VARIANT").id.toString();
        catalog.draft("DEF-001", taxCategoryId, "商品 3", scope);
        catalog.draft("XABC-001", taxCategoryId, "前方一致しない", scope);

        mockMvc.perform(get(BASE).param("code", "abc").param("categoryId", scope.toString()))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(abc, xyz)))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    @DisplayName("AC-48 商品名の部分一致で絞り込める。英字の大文字・小文字は区別しない")
    void filtersByName() throws Exception {
        var shirt = catalog.draft("A-001", taxCategoryId, "Basic T-Shirt", scope).id.toString();
        var poloShirt = catalog.draft("A-002", taxCategoryId, "ポロ SHIRT", scope).id.toString();
        catalog.draft("A-003", taxCategoryId, "パンツ", scope);

        mockMvc.perform(get(BASE).param("name", "shirt").param("categoryId", scope.toString()))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(shirt, poloShirt)));
    }

    @Test
    @DisplayName("絞り込みの文字列中の % と _ は、ただの文字として扱う")
    void escapesWildcards() throws Exception {
        var percent = catalog.draft("A-001", taxCategoryId, "100% 綿", scope).id.toString();
        catalog.draft("A-002", taxCategoryId, "綿 100", scope);

        mockMvc.perform(get(BASE).param("name", "0%").param("categoryId", scope.toString()))
                .andExpect(jsonPath("$.items[*].id", contains(percent)));
        mockMvc.perform(get(BASE).param("name", "_").param("categoryId", scope.toString()))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("AC-48 状態で絞り込める")
    void filtersByStatus() throws Exception {
        catalog.draft("A-001", taxCategoryId, "非公開の商品", scope);
        var discontinued = catalog.draft("A-002", taxCategoryId, "販売終了の商品", scope).id.toString();
        mockMvc.perform(put(BASE + "/" + discontinued + "/discontinue")).andExpect(status().isNoContent());

        mockMvc.perform(get(BASE).param("status", "DISCONTINUED").param("categoryId", scope.toString()))
                .andExpect(jsonPath("$.items[*].id", contains(discontinued)));
    }

    @Test
    @DisplayName("AC-48 カテゴリで絞り込める。子孫のカテゴリに所属する商品も含む")
    void filtersByCategoryIncludingDescendants() throws Exception {
        var inClothing = catalog.draft("A-001", taxCategoryId, "衣類の商品", clothing).id.toString();
        var inTShirts = catalog.draft("A-002", taxCategoryId, "T シャツの商品", tShirts).id.toString();
        catalog.draft("A-003", taxCategoryId, "食品の商品", food);
        catalog.draft("A-004", taxCategoryId, "カテゴリなしの商品");

        mockMvc.perform(get(BASE).param("categoryId", clothing.toString()))
                .andExpect(jsonPath("$.items[*].id", contains(inTShirts, inClothing)));
        mockMvc.perform(get(BASE).param("categoryId", tops.toString()))
                .andExpect(jsonPath("$.items[*].id", contains(inTShirts)));
    }

    @Test
    @DisplayName("AC-282 ドメインの上限の深さまでつないだカテゴリの、いちばん深いカテゴリの商品が、いちばん上のカテゴリの絞り込みで出る")
    void filtersByTopCategoryAtMaxDepth() throws Exception {
        var field = Category.class.getDeclaredField("MAX_DEPTH");
        field.setAccessible(true);
        int maxDepth = field.getInt(null);
        var top = catalog.category("深さ 1", null);
        var deepest = top;
        for (int depth = 2; depth <= maxDepth; depth++) {
            deepest = catalog.category("深さ " + depth, deepest);
        }
        var product = catalog.draft("A-001", taxCategoryId, "いちばん深いカテゴリの商品", deepest).id.toString();

        mockMvc.perform(get(BASE).param("categoryId", top.toString()))
                .andExpect(jsonPath("$.items[*].id", contains(product)));
    }

    @Test
    @DisplayName("絞り込みを組み合わせられる")
    void combinesFilters() throws Exception {
        var target = catalog.draft("ABC-001", taxCategoryId, "T シャツ", clothing).id.toString();
        catalog.draft("ABC-002", taxCategoryId, "パンツ", clothing);
        catalog.draft("XYZ-001", taxCategoryId, "T シャツ", clothing);

        mockMvc.perform(get(BASE).param("code", "ABC").param("name", "シャツ").param("categoryId", clothing.toString()))
                .andExpect(jsonPath("$.items[*].id", contains(target)));
    }

    @Test
    @DisplayName("AC-50 ページ単位で取得できる")
    void pages() throws Exception {
        var ids = new String[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = catalog.draft("A-00" + i, taxCategoryId, "商品 " + i, scope).id.toString();
        }

        mockMvc.perform(get(BASE).param("categoryId", scope.toString()).param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(ids[2], ids[1])))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    @DisplayName("1 ページの件数が 100 を超えれば 400")
    void rejectsTooLargePageSize() throws Exception {
        mockMvc.perform(get(BASE).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
