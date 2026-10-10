package com.ecsite.module.storefront.controller;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.TestImages;
import com.ecsite.module.catalog.infrastructure.TaxRateScheduleCache;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗 API の受け入れテスト（T607、US-10〜12）。<br />
 * 商品は B 側の API で作って公開する。在庫はバリアント登録のイベント（コミット後に動く）では作られないので、
 * 在庫モジュールのリポジトリで直接作る。公開するごとに時計を 1 分進める。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class StorefrontApiTest {

    private static final String BASE = "/api/store";

    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * PNG（登録のときに imgproxy で変換するので、本物の画像）
     */
    private static final byte[] PNG = TestImages.png();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    TaxRateScheduleCache taxRateScheduleCache;

    @Autowired
    StockItemRepository stockItemRepository;

    @Autowired
    DSLContext dsl;

    private String standard;

    private int sequence;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        taxRateScheduleCache.evict();
        standard = createdId(post("/api/admin/tax-categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "標準"}"""));
        mockMvc.perform(put("/api/admin/tax-categories/" + standard + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "rates": [{"ratePercent": 10}]}"""))
                .andExpect(status().isNoContent());
    }

    @Nested
    @DisplayName("AC-51〜57 商品一覧")
    class ListProducts {

        @Test
        @DisplayName("AC-51、29、30、32 表示される商品だけが出る（非公開・販売終了の商品は出ない）")
        void listsOnlyVisibleProducts() throws Exception {
            var visible = product("公開の商品", 1000).publish();
            product("非公開の商品", 1000).publish().unpublish();
            product("販売終了の商品", 1000).publish().discontinue();
            product("一度も公開していない商品", 1000);

            mockMvc.perform(get(BASE + "/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains(visible.id)))
                    .andExpect(jsonPath("$.totalItems").value(1));
        }

        @Test
        @DisplayName("AC-55 商品名と、税込・税抜の価格が返る。画像がなければ代表画像は null")
        void returnsNameAndPrice() throws Exception {
            product("T シャツ", 1800).publish();

            mockMvc.perform(get(BASE + "/products"))
                    .andExpect(jsonPath("$.items[0].name").value("T シャツ"))
                    .andExpect(jsonPath("$.items[0].price.inclTax").value(1980))
                    .andExpect(jsonPath("$.items[0].price.exclTax").value(1800))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(nullValue()));
        }

        @Test
        @DisplayName("AC-55、37 代表画像は、商品の画像の並びの先頭の URL")
        void returnsRepresentativeImage() throws Exception {
            var product = product("画像のある商品", 1000).image().image().publish();

            mockMvc.perform(get(BASE + "/products"))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(product.imageUrls.getFirst()));
            mockMvc.perform(get(BASE + "/products/search").param("q", "画像のある"))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(product.imageUrls.getFirst()));
        }

        @Test
        @DisplayName("AC-35、54 公開期間の外では出ず、開始日時に出て終了日時に消える。新着順は開始日時で並ぶ")
        void listsOnlyWithinPublicationPeriod() throws Exception {
            var start = NOW.plus(Duration.ofDays(1));
            var end = NOW.plus(Duration.ofDays(2));
            var reserved = product("予約の商品", 1000).period(start, end).publish();
            var plain = product("通常の商品", 1000).publish();

            mockMvc.perform(get(BASE + "/products"))
                    .andExpect(jsonPath("$.items[*].id", contains(plain.id)));
            mockMvc.perform(get(BASE + "/products/" + reserved.id)).andExpect(status().isNotFound());

            // 運営者の操作なしに、開始日時で表示される
            clock.setInstant(start);
            mockMvc.perform(get(BASE + "/products"))
                    .andExpect(jsonPath("$.items[*].id", contains(reserved.id, plain.id)));
            mockMvc.perform(get(BASE + "/products/" + reserved.id)).andExpect(status().isOk());

            // 終了日時ちょうどは含まない
            clock.setInstant(end);
            mockMvc.perform(get(BASE + "/products"))
                    .andExpect(jsonPath("$.items[*].id", contains(plain.id)));
            mockMvc.perform(get(BASE + "/products/" + reserved.id)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-56 全バリアントが在庫なしの商品も出て、在庫なしと示される。在庫がまだないバリアントも在庫なし")
        void marksOutOfStock() throws Exception {
            var inStock = product("在庫あり", 1000, "色").variant("白").variant("黒").publish();
            stock(inStock.variantIds.get(0), 0);
            stock(inStock.variantIds.get(1), 3);
            var soldOut = product("在庫なし", 1000, "色").variant("白").variant("黒").publish();
            stock(soldOut.variantIds.get(0), 0);
            // 2 つ目のバリアントの在庫は、まだない

            mockMvc.perform(get(BASE + "/products"))
                    .andExpect(jsonPath("$.items[*].id", contains(soldOut.id, inStock.id)))
                    .andExpect(jsonPath("$.items[*].inStock", contains(false, true)));
        }

        @Test
        @DisplayName("AC-56、28 販売停止のバリアントだけに在庫がある商品は、在庫なしと示される")
        void ignoresSuspendedVariantsForStock() throws Exception {
            var product = product("販売停止", 1000, "色").variant("白").variant("黒").publish();
            stock(product.variantIds.get(0), 5);
            stock(product.variantIds.get(1), 0);
            suspend(product.id, product.variantIds.get(0));

            mockMvc.perform(get(BASE + "/products"))
                    .andExpect(jsonPath("$.items[0].inStock").value(false));
        }

        @Test
        @DisplayName("AC-53、54 税込価格の範囲で絞り込み、価格の順に並べられる")
        void filtersAndSortsByPrice() throws Exception {
            product("A", 900).publish();
            var b = product("B", 1000).publish();
            var c = product("C", 2000).publish();

            mockMvc.perform(get(BASE + "/products").param("minPrice", "1100").param("maxPrice", "2200").param("sort", "price_desc"))
                    .andExpect(jsonPath("$.items[*].id", contains(c.id, b.id)));
        }

        @Test
        @DisplayName("AC-57 ページ単位で取得できる")
        void pages() throws Exception {
            var ids = new String[5];
            for (int i = 0; i < 5; i++) {
                ids[i] = product("商品 " + i, 1000).publish().id;
            }

            mockMvc.perform(get(BASE + "/products").param("page", "2").param("size", "2"))
                    .andExpect(jsonPath("$.items[*].id", contains(ids[2], ids[1])))
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalItems").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }

        @Test
        @DisplayName("並び順が決められた値でなければ 400")
        void rejectsUnknownSort() throws Exception {
            mockMvc.perform(get(BASE + "/products").param("sort", "popular"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("AC-58〜61 キーワード検索")
    class Search {

        @Test
        @DisplayName("AC-58、59 商品名または説明にキーワードを含む、表示される商品が出る")
        void searches() throws Exception {
            var byName = product("綿のシャツ", 1000).publish();
            product("綿のパンツ", 1000).publish().unpublish();
            product("パンツ", 1000).publish();

            mockMvc.perform(get(BASE + "/products/search").param("q", "綿"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains(byName.id)))
                    .andExpect(jsonPath("$.items[0].price.inclTax").value(1100))
                    .andExpect(jsonPath("$.items[0].inStock").value(false));
        }

        @Test
        @DisplayName("AC-61 ページ単位で取得できる")
        void pages() throws Exception {
            var ids = new String[3];
            for (int i = 0; i < 3; i++) {
                ids[i] = product("シャツ " + i, 1000).publish().id;
            }

            mockMvc.perform(get(BASE + "/products/search").param("q", "シャツ").param("page", "2").param("size", "2"))
                    .andExpect(jsonPath("$.items[*].id", contains(ids[0])))
                    .andExpect(jsonPath("$.totalItems").value(3));
        }

        @Test
        @DisplayName("キーワードがなければ 400")
        void requiresKeyword() throws Exception {
            mockMvc.perform(get(BASE + "/products/search"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("AC-62〜66 商品の詳細")
    class Detail {

        @Test
        @DisplayName("AC-62〜64、28 価格、軸と選択肢、バリアントごとの在庫の有無が返る。販売停止・在庫切れのバリアントも返る")
        void returnsDetail() throws Exception {
            var product = product("T シャツ", 1800, "色", "サイズ")
                    .variant("白", "M").variant("白", "L").variant("黒", "M").publish();
            stock(product.variantIds.get(0), 2);
            stock(product.variantIds.get(1), 0);
            stock(product.variantIds.get(2), 4);
            suspend(product.id, product.variantIds.get(2));

            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(product.id))
                    .andExpect(jsonPath("$.name").value("T シャツ"))
                    .andExpect(jsonPath("$.description").value("説明"))
                    .andExpect(jsonPath("$.price.inclTax").value(1980))
                    .andExpect(jsonPath("$.price.exclTax").value(1800))
                    .andExpect(jsonPath("$.imageUrls").isEmpty())
                    .andExpect(jsonPath("$.axes[*].name", contains("色", "サイズ")))
                    .andExpect(jsonPath("$.axes[0].values", contains("白", "黒")))
                    .andExpect(jsonPath("$.axes[1].values", contains("M", "L")))
                    .andExpect(jsonPath("$.variants[*].id", contains(product.variantIds.toArray())))
                    .andExpect(jsonPath("$.variants[0].axisValues", contains("白", "M")))
                    .andExpect(jsonPath("$.variants[*].inStock", contains(true, false, true)))
                    .andExpect(jsonPath("$.variants[*].salesSuspended", contains(false, false, true)))
                    .andExpect(jsonPath("$.variants[0].imageUrl").value(nullValue()))
                    // 数量は返さない（AC-64）
                    .andExpect(jsonPath("$.variants[0].quantity").doesNotExist());
        }

        @Test
        @DisplayName("AC-37、38 画像の URL が並び順で返る。バリアントの画像は、指定した画像の URL で、指定がなければ null")
        void returnsImages() throws Exception {
            var product = product("画像", 1000, "色").variant("白").variant("黒").image().image();
            product.variantImage(1, 1).publish();

            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(jsonPath("$.imageUrls", contains(product.imageUrls.toArray())))
                    .andExpect(jsonPath("$.variants[0].imageUrl").value(nullValue()))
                    .andExpect(jsonPath("$.variants[1].imageUrl").value(product.imageUrls.get(1)));
        }

        @Test
        @DisplayName("AC-65 表示されない商品は、存在しない商品と同じく 404")
        void returnsNotFoundForInvisibleProduct() throws Exception {
            var unpublished = product("非公開", 1000).publish().unpublish();

            mockMvc.perform(get(BASE + "/products/" + unpublished.id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
            mockMvc.perform(get(BASE + "/products/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("AC-66 URL は商品 ID なので、品番を付け替えても変わらない")
        void urlDoesNotDependOnProductCode() throws Exception {
            var product = product("T シャツ", 1000).publish();
            dsl.update(PRODUCT).set(PRODUCT.PRODUCT_CODE, "RENAMED-001")
                    .where(PRODUCT.ID.eq(UUID.fromString(product.id))).execute();

            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(product.id));
        }
    }

    @Nested
    @DisplayName("カテゴリ")
    class Categories {

        @Test
        @DisplayName("カテゴリの木が、運営者が決めた並び順で返る")
        void listsCategoryTree() throws Exception {
            var clothing = createCategory("衣類", null);
            createCategory("食品", null);
            createCategory("トップス", clothing);

            mockMvc.perform(get(BASE + "/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].name", contains("衣類", "食品")))
                    .andExpect(jsonPath("$.items[0].children[*].name", contains("トップス")))
                    .andExpect(jsonPath("$.items[0].depth").doesNotExist());
        }
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
     * 軸を指定して商品を登録する。軸がなければ、バリアントを 1 つ持つ
     */
    private TestProduct product(String name, long price, String... axes) throws Exception {
        sequence++;
        var axesJson = Arrays.stream(axes).map("\"%s\""::formatted).collect(Collectors.joining(", "));
        var id = createdId(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"productCode": "ST-%d", "name": "%s", "description": "説明", "taxCategoryId": "%s",
                 "priceExclTax": %d, "axes": [%s]}""".formatted(sequence, name, standard, price, axesJson)));
        var product = new TestProduct(id);
        if (axes.length == 0) {
            product.variant();
        }
        return product;
    }

    private void stock(String variantId, int quantity) {
        var id = UUID.fromString(variantId);
        stockItemRepository.createIfAbsent(id, UUID.randomUUID());
        if (quantity > 0) {
            stockItemRepository.adjust(id, StockAdjustment.create(quantity, StockAdjustmentReason.ARRIVAL, null, NOW));
        }
    }

    private void suspend(String productId, String variantId) throws Exception {
        mockMvc.perform(put("/api/admin/products/" + productId + "/variants/" + variantId + "/suspend"))
                .andExpect(status().isNoContent());
    }

    /**
     * テストで作った商品。バリアントの登録・状態の切り替えを続けて書けるようにする
     */
    private class TestProduct {

        private final String id;

        private final List<String> variantIds = new ArrayList<>();

        private final List<String> imageIds = new ArrayList<>();

        private final List<String> imageUrls = new ArrayList<>();

        private long version;

        TestProduct(String id) {
            this.id = id;
        }

        TestProduct variant(String... axisValues) throws Exception {
            var values = Arrays.stream(axisValues).map("\"%s\""::formatted).collect(Collectors.joining(", "));
            var variantId = createdId(post("/api/admin/products/" + id + "/variants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"variantCode": "ST-%d-%d", "axisValues": [%s], "version": %d}"""
                            .formatted(sequence, variantIds.size() + 1, values, version)));
            variantIds.add(variantId);
            version++;
            return this;
        }

        TestProduct image() throws Exception {
            var upload = mockMvc.perform(post("/api/admin/products/" + id + "/image-uploads")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"contentType": "image/png", "contentLength": %d}""".formatted(PNG.length)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            var request = HttpRequest.newBuilder(URI.create(JsonPath.read(upload, "$.uploadUrl")))
                    .header("Content-Type", "image/png")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG));
            assertThat(HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                    .isEqualTo(200);
            mockMvc.perform(post("/api/admin/products/" + id + "/images")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"uploadId": "%s", "version": %d}""".formatted(JsonPath.<String>read(upload, "$.uploadId"), version)))
                    .andExpect(status().isCreated());
            version++;
            var detail = mockMvc.perform(get("/api/admin/products/" + id)).andReturn().getResponse().getContentAsString();
            imageUrls.clear();
            imageUrls.addAll(JsonPath.read(detail, "$.images[*].url"));
            imageIds.clear();
            imageIds.addAll(JsonPath.read(detail, "$.images[*].id"));
            return this;
        }

        TestProduct variantImage(int variantIndex, int imageIndex) throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/variants/" + variantIds.get(variantIndex) + "/image")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageId": "%s", "version": %d}""".formatted(imageIds.get(imageIndex), version)))
                    .andExpect(status().isNoContent());
            version++;
            return this;
        }

        TestProduct publish() throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/publish")).andExpect(status().isNoContent());
            clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
            return this;
        }

        TestProduct unpublish() throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/unpublish")).andExpect(status().isNoContent());
            return this;
        }

        TestProduct discontinue() throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/discontinue")).andExpect(status().isNoContent());
            return this;
        }

        TestProduct period(Instant publishStart, Instant publishEnd) throws Exception {
            var body = mockMvc.perform(get("/api/admin/products/" + id)).andReturn().getResponse().getContentAsString();
            long current = ((Number) JsonPath.read(body, "$.version")).longValue();
            mockMvc.perform(put("/api/admin/products/" + id + "/publication-period")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"publishStart": %s, "publishEnd": %s, "version": %d}"""
                                    .formatted(jsonInstant(publishStart), jsonInstant(publishEnd), current)))
                    .andExpect(status().isNoContent());
            return this;
        }

        private static String jsonInstant(Instant instant) {
            return instant == null ? "null" : "\"" + instant + "\"";
        }
    }
}
