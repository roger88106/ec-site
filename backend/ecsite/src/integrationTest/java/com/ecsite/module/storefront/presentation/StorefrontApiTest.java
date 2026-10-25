package com.ecsite.module.storefront.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCache;
import com.ecsite.module.inventory.api.StockReservationApi;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    /**
     * API のパス
     */
    private static final String BASE = "/api/store";

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    TaxRateScheduleCache taxRateScheduleCache;

    @Autowired
    StockItemRepository stockItemRepository;

    private InventoryTestData inventory;

    @Autowired
    StockReservationApi stockReservationApi;

    @Autowired
    DSLContext dsl;

    private CatalogTestData catalog;

    private UUID standard;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        clock.setInstant(NOW);
        taxRateScheduleCache.evict();
        catalog = new CatalogTestData(mockMvc, clock);
        standard = catalog.taxCategory("標準", "10");
    }

    @Nested
    @DisplayName("AC-51、52、53、54、55、56、57 商品一覧")
    class ListProducts {

        @Test
        @DisplayName("AC-51、29、30、32 表示される商品だけが出る（非公開・販売終了の商品は出ない）")
        void listsOnlyVisibleProducts() throws Exception {
            var category = newCategory();
            var visible = catalog.product(standard, "公開の商品", 1000, List.of(category)).publish();
            catalog.product(standard, "非公開の商品", 1000, List.of(category)).publish().unpublish();
            catalog.product(standard, "販売終了の商品", 1000, List.of(category)).publish().discontinue();
            catalog.product(standard, "一度も公開していない商品", 1000, List.of(category));

            mockMvc.perform(listIn(category))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains(visible.id.toString())))
                    .andExpect(jsonPath("$.totalItems").value(1));
        }

        @Test
        @DisplayName("AC-55 商品名と、税込・税抜の価格が返る。画像がなければ代表画像は null")
        void returnsNameAndPrice() throws Exception {
            var category = newCategory();
            catalog.product(standard, "T シャツ", 1800, List.of(category)).publish();

            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[0].name").value("T シャツ"))
                    .andExpect(jsonPath("$.items[0].price.inclTax").value(1980))
                    .andExpect(jsonPath("$.items[0].price.exclTax").value(1800))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(nullValue()));
        }

        @Test
        @DisplayName("AC-55、37 代表画像は、商品の画像の並びの先頭の URL")
        void returnsRepresentativeImage() throws Exception {
            var category = newCategory();
            var mark = newMark();
            var product = catalog.product(standard, mark + "画像のある商品", 1000, List.of(category)).image().image().publish();

            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(product.imageUrls.getFirst()));
            mockMvc.perform(get(BASE + "/products/search").param("q", mark))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(product.imageUrls.getFirst()));
        }

        @Test
        @DisplayName("AC-35、54 公開期間の外では出ず、開始日時に出て終了日時に消える。新着順は開始日時で並ぶ")
        void listsOnlyWithinPublicationPeriod() throws Exception {
            var start = NOW.plus(Duration.ofDays(1));
            var end = NOW.plus(Duration.ofDays(2));
            var category = newCategory();
            var reserved = catalog.product(standard, "予約の商品", 1000, List.of(category)).period(start, end).publish();
            var plain = catalog.product(standard, "通常の商品", 1000, List.of(category)).publish();

            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[*].id", contains(plain.id.toString())));
            mockMvc.perform(get(BASE + "/products/" + reserved.id)).andExpect(status().isNotFound());

            // 運営者の操作なしに、開始日時で表示される
            clock.setInstant(start);
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[*].id", contains(reserved.id.toString(), plain.id.toString())));
            mockMvc.perform(get(BASE + "/products/" + reserved.id)).andExpect(status().isOk());

            // 終了日時ちょうどは含まない
            clock.setInstant(end);
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[*].id", contains(plain.id.toString())));
            mockMvc.perform(get(BASE + "/products/" + reserved.id)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-56 全バリアントが在庫なしの商品も出て、在庫なしと示される。在庫がまだないバリアントも在庫なし")
        void marksOutOfStock() throws Exception {
            var category = newCategory();
            var inStock = catalog.product(standard, "在庫あり", 1000, List.of(category), "色").variant("白").variant("黒").publish();
            stock(inStock.variantIds.getFirst(), 0);
            stock(inStock.variantIds.get(1), 3);
            var soldOut = catalog.product(standard, "在庫なし", 1000, List.of(category), "色").variant("白").variant("黒").publish();
            stock(soldOut.variantIds.getFirst(), 0);
            // 2 つ目のバリアントの在庫は、まだない

            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[*].id", contains(soldOut.id.toString(), inStock.id.toString())))
                    .andExpect(jsonPath("$.items[*].inStock", contains(false, true)));
        }

        @Test
        @DisplayName("AC-56、28 販売停止のバリアントだけに在庫がある商品は、在庫なしと示される")
        void ignoresSuspendedVariantsForStock() throws Exception {
            var category = newCategory();
            var product = catalog.product(standard, "販売停止", 1000, List.of(category), "色").variant("白").variant("黒").publish();
            stock(product.variantIds.getFirst(), 5);
            stock(product.variantIds.get(1), 0);
            product.suspend(0);

            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[0].inStock").value(false));
        }

        @Test
        @DisplayName("AC-53、54 税込価格の範囲で絞り込み、価格の順に並べられる")
        void filtersAndSortsByPrice() throws Exception {
            var category = newCategory();
            catalog.product(standard, "A", 900, List.of(category)).publish();
            var b = catalog.product(standard, "B", 1000, List.of(category)).publish();
            var c = catalog.product(standard, "C", 2000, List.of(category)).publish();

            mockMvc.perform(listIn(category).param("minPrice", "1100").param("maxPrice", "2200").param("sort", "price_desc"))
                    .andExpect(jsonPath("$.items[*].id", contains(c.id.toString(), b.id.toString())));
            mockMvc.perform(listIn(category).param("minPrice", "1100").param("maxPrice", "2200").param("sort", "price_asc"))
                    .andExpect(jsonPath("$.items[*].id", contains(b.id.toString(), c.id.toString())));
        }

        @Test
        @DisplayName("AC-57 ページ単位で取得できる")
        void pages() throws Exception {
            var category = newCategory();
            var ids = new String[5];
            for (int i = 0; i < 5; i++) {
                ids[i] = catalog.product(standard, "商品 " + i, 1000, List.of(category)).publish().id.toString();
            }

            mockMvc.perform(listIn(category).param("page", "2").param("size", "2"))
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
    @DisplayName("AC-58、59、61 キーワード検索")
    class Search {

        @Test
        @DisplayName("AC-58、59 商品名または説明にキーワードを含む、表示される商品が出る")
        void searches() throws Exception {
            var mark = newMark();
            var byName = catalog.product(standard, mark + "綿のシャツ", 1000).publish();
            catalog.product(standard, mark + "綿のパンツ", 1000).publish().unpublish();
            catalog.product(standard, mark + "パンツ", 1000).publish();

            mockMvc.perform(get(BASE + "/products/search").param("q", mark + "綿"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains(byName.id.toString())))
                    .andExpect(jsonPath("$.items[0].price.inclTax").value(1100))
                    .andExpect(jsonPath("$.items[0].inStock").value(false));
        }

        @Test
        @DisplayName("AC-61 ページ単位で取得できる")
        void pages() throws Exception {
            var mark = newMark();
            var ids = new String[3];
            for (int i = 0; i < 3; i++) {
                ids[i] = catalog.product(standard, mark + "シャツ " + i, 1000).publish().id.toString();
            }

            mockMvc.perform(get(BASE + "/products/search").param("q", mark + "シャツ").param("page", "2").param("size", "2"))
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
    @DisplayName("AC-62、63、64、65、66 商品の詳細")
    class Detail {

        @Test
        @DisplayName("AC-62、63、64、28 価格、軸と選択肢、バリアントごとの在庫の有無が返る。販売停止・在庫切れのバリアントも返る")
        void returnsDetail() throws Exception {
            var product = catalog.product(standard, "T シャツ", 1800, "色", "サイズ")
                    .variant("白", "M").variant("白", "L").variant("黒", "M").publish();
            stock(product.variantIds.getFirst(), 2);
            stock(product.variantIds.get(1), 0);
            stock(product.variantIds.get(2), 4);
            product.suspend(2);

            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(product.id.toString()))
                    .andExpect(jsonPath("$.name").value("T シャツ"))
                    .andExpect(jsonPath("$.description").value("説明"))
                    .andExpect(jsonPath("$.price.inclTax").value(1980))
                    .andExpect(jsonPath("$.price.exclTax").value(1800))
                    .andExpect(jsonPath("$.imageUrls").isEmpty())
                    .andExpect(jsonPath("$.axes[*].name", contains("色", "サイズ")))
                    .andExpect(jsonPath("$.axes[0].values", contains("白", "黒")))
                    .andExpect(jsonPath("$.axes[1].values", contains("M", "L")))
                    .andExpect(jsonPath("$.variants[*].id", contains(product.variantIds.stream().map(UUID::toString).toArray())))
                    .andExpect(jsonPath("$.variants[0].axisValues", contains("白", "M")))
                    .andExpect(jsonPath("$.variants[*].inStock", contains(true, false, true)))
                    .andExpect(jsonPath("$.variants[*].salesSuspended", contains(false, false, true)))
                    .andExpect(jsonPath("$.variants[0].imageUrl").value(nullValue()))
                    // 数量は返さない（AC-64）
                    .andExpect(jsonPath("$.variants[0].quantity").doesNotExist());
        }

        @Test
        @DisplayName("BR-11 在庫数がすべて引き当てられたバリアントは在庫なしと示される")
        void treatsReservedStockAsOutOfStock() throws Exception {
            var product = catalog.product(standard, "T シャツ", 1800, "色").variant("白").variant("黒").publish();
            stock(product.variantIds.getFirst(), 1);
            stock(product.variantIds.get(1), 1);
            stockReservationApi.reserve(UUID.randomUUID(), Map.of(product.variantIds.getFirst(), 1),
                    clock.instant().plus(Duration.ofMinutes(15)));

            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(jsonPath("$.variants[*].inStock", contains(false, true)));
        }

        @Test
        @DisplayName("AC-37、38 画像の URL が並び順で返る。バリアントの画像は、指定した画像の URL で、指定がなければ null")
        void returnsImages() throws Exception {
            var product = catalog.product(standard, "画像", 1000, "色").variant("白").variant("黒").image().image();
            product.variantImage(1, 1).publish();

            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(jsonPath("$.imageUrls", contains(product.imageUrls.toArray())))
                    .andExpect(jsonPath("$.variants[0].imageUrl").value(nullValue()))
                    .andExpect(jsonPath("$.variants[1].imageUrl").value(product.imageUrls.get(1)));
        }

        @Test
        @DisplayName("AC-65 表示されない商品は、存在しない商品と同じく 404")
        void returnsNotFoundForInvisibleProduct() throws Exception {
            var unpublished = catalog.product(standard, "非公開", 1000).publish().unpublish();

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
            var product = catalog.product(standard, "T シャツ", 1000).publish();
            dsl.update(PRODUCT).set(PRODUCT.PRODUCT_CODE, "RENAMED-001")
                    .where(PRODUCT.ID.eq(product.id)).execute();

            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(product.id.toString()));
        }
    }

    @Nested
    @DisplayName("カテゴリ")
    class Categories {

        @Test
        @DisplayName("カテゴリの木が、運営者が決めた並び順で返る")
        void listsCategoryTree() throws Exception {
            var rootName = "衣類 " + UUID.randomUUID();
            var clothing = catalog.category(rootName, null);
            var foodName = "食品 " + UUID.randomUUID();
            catalog.category(foodName, null);
            catalog.category("食品", clothing);
            catalog.category("トップス", clothing);

            var node = "$.items[?(@.name=='%s')]".formatted(rootName);
            var body = mockMvc.perform(get(BASE + "/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(node + ".children[*].name", contains("食品", "トップス")))
                    .andExpect(jsonPath(node + ".depth").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            // 他のテストの最上位のカテゴリが間に入っても、自分の 2 つの相対の順序は並び順（登録順）どおりになる
            List<String> rootNames = JsonPath.read(body, "$.items[*].name");
            assertThat(rootNames.indexOf(rootName)).isLessThan(rootNames.indexOf(foodName));
        }
    }

    // ============================== 補助 ==============================

    /**
     * テストごとのカテゴリを作る（一覧をこのカテゴリで絞って、他のテストの商品が見えないようにする）
     */
    private UUID newCategory() throws Exception {
        return catalog.category("カテゴリ " + UUID.randomUUID(), null);
    }

    /**
     * 商品名の先頭に付ける、テストごとの印（検索をこの印で絞る。他の語と重ならない英数字）
     */
    private static String newMark() {
        return "mk" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * カテゴリで絞った商品一覧の要求
     */
    private static MockHttpServletRequestBuilder listIn(UUID category) {
        return get(BASE + "/products").param("categoryId", category.toString());
    }

    /**
     * バリアントの在庫を作り、在庫数を設定する（入荷として調整する）
     */
    private void stock(UUID id, int quantity) {
        var productId = UUID.randomUUID();
        if (quantity > 0) {
            inventory.stock(id, productId, quantity);
        } else {
            inventory.create(id, productId);
        }
    }
}
