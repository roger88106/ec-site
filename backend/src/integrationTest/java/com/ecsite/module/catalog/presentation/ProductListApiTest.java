package com.ecsite.module.catalog.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B 側の商品一覧の受け入れテスト（T501、US-9）。<br />
 * 登録日時の新しい順を確かめるため、商品を 1 つ登録するごとに時計を 1 分進める。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class ProductListApiTest {

    private static final String BASE = "/api/admin/products";

    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    private String taxCategoryId;

    private String clothing;

    private String tops;

    private String tShirts;

    private String food;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        taxCategoryId = createdId(post("/api/admin/tax-categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "標準"}"""));
        mockMvc.perform(put("/api/admin/tax-categories/" + taxCategoryId + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "rates": [{"ratePercent": 10}]}"""))
                .andExpect(status().isNoContent());
        clothing = createCategory("衣類", null);
        tops = createCategory("トップス", clothing);
        tShirts = createCategory("T シャツ", tops);
        food = createCategory("食品", null);
    }

    @Test
    @DisplayName("AC-47、49 状態に関係なくすべての商品が、登録日時の新しい順に、品番・名前・状態・バリアント数とともに返る")
    void listsAllProductsNewestFirst() throws Exception {
        var unpublished = createProduct("A-001", "非公開の商品");
        var published = createProduct("B-001", "公開の商品");
        addVariant(published, "B-001-1", 0);
        mockMvc.perform(put(BASE + "/" + published + "/publish")).andExpect(status().isNoContent());
        var discontinued = createProduct("C-001", "販売終了の商品");
        mockMvc.perform(put(BASE + "/" + discontinued + "/discontinue")).andExpect(status().isNoContent());

        mockMvc.perform(get(BASE))
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
        var abc = createProduct("ABC-001", "商品 1");
        var xyz = createProduct("XYZ-001", "商品 2");
        addVariant(xyz, "ABC-VARIANT", 0);
        createProduct("DEF-001", "商品 3");
        createProduct("XABC-001", "前方一致しない");

        mockMvc.perform(get(BASE).param("code", "abc"))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(abc, xyz)))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    @DisplayName("AC-48 商品名の部分一致で絞り込める。英字の大文字・小文字は区別しない")
    void filtersByName() throws Exception {
        var shirt = createProduct("A-001", "Basic T-Shirt");
        var poloShirt = createProduct("A-002", "ポロ SHIRT");
        createProduct("A-003", "パンツ");

        mockMvc.perform(get(BASE).param("name", "shirt"))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(shirt, poloShirt)));
    }

    @Test
    @DisplayName("絞り込みの文字列中の % と _ は、ただの文字として扱う")
    void escapesWildcards() throws Exception {
        var percent = createProduct("A-001", "100% 綿");
        createProduct("A-002", "綿 100");

        mockMvc.perform(get(BASE).param("name", "0%"))
                .andExpect(jsonPath("$.items[*].id", contains(percent)));
        mockMvc.perform(get(BASE).param("name", "_"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("AC-48 状態で絞り込める")
    void filtersByStatus() throws Exception {
        createProduct("A-001", "非公開の商品");
        var discontinued = createProduct("A-002", "販売終了の商品");
        mockMvc.perform(put(BASE + "/" + discontinued + "/discontinue")).andExpect(status().isNoContent());

        mockMvc.perform(get(BASE).param("status", "DISCONTINUED"))
                .andExpect(jsonPath("$.items[*].id", contains(discontinued)));
    }

    @Test
    @DisplayName("AC-48 カテゴリで絞り込める。子孫のカテゴリに所属する商品も含む")
    void filtersByCategoryIncludingDescendants() throws Exception {
        var inClothing = createProduct("A-001", "衣類の商品", clothing);
        var inTShirts = createProduct("A-002", "T シャツの商品", tShirts);
        createProduct("A-003", "食品の商品", food);
        createProduct("A-004", "カテゴリなしの商品");

        mockMvc.perform(get(BASE).param("categoryId", clothing))
                .andExpect(jsonPath("$.items[*].id", contains(inTShirts, inClothing)));
        mockMvc.perform(get(BASE).param("categoryId", tops))
                .andExpect(jsonPath("$.items[*].id", contains(inTShirts)));
    }

    @Test
    @DisplayName("絞り込みを組み合わせられる")
    void combinesFilters() throws Exception {
        var target = createProduct("ABC-001", "T シャツ", clothing);
        createProduct("ABC-002", "パンツ", clothing);
        createProduct("XYZ-001", "T シャツ", clothing);

        mockMvc.perform(get(BASE).param("code", "ABC").param("name", "シャツ").param("categoryId", clothing))
                .andExpect(jsonPath("$.items[*].id", contains(target)));
    }

    @Test
    @DisplayName("AC-50 ページ単位で取得できる")
    void pages() throws Exception {
        var ids = new String[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = createProduct("A-00" + i, "商品 " + i);
        }

        mockMvc.perform(get(BASE).param("page", "2").param("size", "2"))
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

    // ============================== 補助 ==============================

    private String createdId(RequestBuilder request) throws Exception {
        var body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createCategory(String name, String parentId) throws Exception {
        var parent = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        return createdId(post("/api/admin/categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "%s", "parentId": %s}""".formatted(name, parent)));
    }

    /**
     * 商品を登録し、時計を 1 分進める（登録日時をずらす）
     */
    private String createProduct(String code, String name, String... categoryIds) throws Exception {
        var categories = String.join(", ", Arrays.stream(categoryIds).map("\"%s\""::formatted).toList());
        var id = createdId(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                {"productCode": "%s", "name": "%s", "description": "", "taxCategoryId": "%s",
                 "priceExclTax": 1000, "categoryIds": [%s]}""".formatted(code, name, taxCategoryId, categories)));
        clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
        return id;
    }

    private void addVariant(String productId, String code, long version) throws Exception {
        mockMvc.perform(post(BASE + "/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantCode": "%s", "axisValues": [], "version": %d}""".formatted(code, version)))
                .andExpect(status().isCreated());
    }
}
