package com.ecsite.module.storefront.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.CatalogTestData.TestProduct;
import com.ecsite.IntegrationTest;
import com.ecsite.InventoryTestData;
import com.ecsite.MutableClock;
import com.ecsite.module.inventory.api.StockReservationApi;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 店舗 API の受け入れテスト
 *
 * <p>商品一覧・キーワード検索・商品の詳細・カテゴリの木を、店舗の API を通して確認する。
 * 一覧と検索の条件と並びの細かい規則（境界の値、同じ並びの基準の順、新着順の基準）は {@code StoreCatalogApiTest}・
 * {@code StoreProductSearchTest}、変更の反映の速さは {@code StorefrontFreshnessTest}、カートの表示は {@code StorefrontCartTest} が確認する。
 *
 * <p>商品は B 側の API で作って公開する。在庫はバリアント登録のイベント（コミット後に動く）では作られないので、
 * 在庫モジュールのリポジトリで直接作る。公開するごとに時計を 1 分進める。
 * 一覧はテストごとのカテゴリで、検索はテストごとの識別子で絞り、他のテストの商品が見えないようにする。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class StorefrontApiTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String BASE = "/api/store";

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 予約公開の開始日時（NOW の 1 日後）
     */
    private static final Instant PUBLISH_START = NOW.plus(Duration.ofDays(1));

    /**
     * 予約公開の終了日時（NOW の 2 日後。この日時ちょうどは含まない）
     */
    private static final Instant PUBLISH_END = NOW.plus(Duration.ofDays(2));

    /**
     * 詳細を確認する商品の商品名
     */
    private static final String SHIRT_NAME = "T シャツ";

    /**
     * 詳細を確認する商品の税抜価格
     */
    private static final int SHIRT_PRICE_EXCL_TAX = 1800;

    /**
     * 詳細を確認する商品の税込価格（税率 10%）
     */
    private static final int SHIRT_PRICE_INCL_TAX = 1980;

    /**
     * 商品の補助（{@link CatalogTestData}）が、説明を指定しない商品に登録する説明
     */
    private static final String DEFAULT_DESCRIPTION = "説明";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * テストの時計
     */
    @Autowired
    MutableClock clock;

    /**
     * 在庫のリポジトリ
     */
    @Autowired
    StockItemRepository stockItemRepository;

    /**
     * 在庫の引当の API
     */
    @Autowired
    StockReservationApi stockReservationApi;

    /**
     * DB を書き換える jOOQ
     */
    @Autowired
    DSLContext dsl;

    /**
     * 在庫を作る補助
     */
    private InventoryTestData inventory;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * 税率 10% の税区分の ID
     */
    private UUID standard;

    @BeforeEach
    void setUp() throws Exception {
        inventory = new InventoryTestData(stockItemRepository, NOW);
        catalog = new CatalogTestData(mockMvc, clock);
        standard = catalog.taxCategory("標準", "10");
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("一覧に出る商品")
    class Visibility {

        @Test
        @DisplayName("AC-29、51 公開した商品は一覧に出る")
        void listsPublishedProduct() throws Exception {
            // 準備：公開した商品
            var category = newCategory();
            var visible = catalog.product(standard, "公開の商品", 1000, List.of(category)).publish();

            // 操作と確認：その商品が出る
            assertListsOnly(category, visible);
        }

        @Test
        @DisplayName("AC-30、51 非公開にした商品は一覧に出ない")
        void hidesUnpublishedProduct() throws Exception {
            // 準備：公開した商品と、公開してから非公開にした商品
            var category = newCategory();
            var visible = catalog.product(standard, "公開の商品", 1000, List.of(category)).publish();
            catalog.product(standard, "非公開の商品", 1000, List.of(category)).publish().unpublish();

            // 操作と確認：公開した商品だけが出る
            assertListsOnly(category, visible);
        }

        @Test
        @DisplayName("AC-32、51 販売終了の商品は一覧に出ない")
        void hidesDiscontinuedProduct() throws Exception {
            // 準備：公開した商品と、公開してから販売を終了した商品
            var category = newCategory();
            var visible = catalog.product(standard, "公開の商品", 1000, List.of(category)).publish();
            catalog.product(standard, "販売終了の商品", 1000, List.of(category)).publish().discontinue();

            // 操作と確認：公開した商品だけが出る
            assertListsOnly(category, visible);
        }

        @Test
        @DisplayName("AC-51 一度も公開していない商品は一覧に出ない")
        void hidesNeverPublishedProduct() throws Exception {
            // 準備：公開した商品と、一度も公開していない商品
            var category = newCategory();
            var visible = catalog.product(standard, "公開の商品", 1000, List.of(category)).publish();
            catalog.product(standard, "一度も公開していない商品", 1000, List.of(category));

            // 操作と確認：公開した商品だけが出る
            assertListsOnly(category, visible);
        }

        @Test
        @DisplayName("AC-52 カテゴリで絞ると、子孫のカテゴリの商品も出て、ほかのカテゴリの商品は出ない")
        void filtersByCategoryIncludingDescendants() throws Exception {
            // 準備：親のカテゴリ・子のカテゴリ・ほかのカテゴリに 1 つずつの商品
            var parent = newCategory();
            var child = catalog.category("子 " + UUID.randomUUID(), parent);
            var other = newCategory();
            var inParent = catalog.product(standard, "親の商品", 1000, List.of(parent)).publish();
            var inChild = catalog.product(standard, "子の商品", 1000, List.of(child)).publish();
            catalog.product(standard, "ほかの商品", 1000, List.of(other)).publish();

            // 操作と確認：親で絞ると、親と子の商品が出る
            mockMvc.perform(listIn(parent))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(inParent.id.toString(), inChild.id.toString())));
        }
    }

    @Nested
    @DisplayName("一覧の項目")
    class ListItems {

        @Test
        @DisplayName("AC-55 商品名と、税込・税抜の価格が返る")
        void returnsNameAndPrice() throws Exception {
            // 準備：税抜 1,800 円の商品
            var category = newCategory();
            catalog.product(standard, SHIRT_NAME, SHIRT_PRICE_EXCL_TAX, List.of(category)).publish();

            // 操作と確認：商品名と、税込 1,980 円・税抜 1,800 円
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[0].name").value(SHIRT_NAME))
                    .andExpect(jsonPath("$.items[0].price.inclTax").value(SHIRT_PRICE_INCL_TAX))
                    .andExpect(jsonPath("$.items[0].price.exclTax").value(SHIRT_PRICE_EXCL_TAX));
        }

        @Test
        @DisplayName("AC-55 画像のない商品の代表画像は null")
        void returnsNullImageWithoutImages() throws Exception {
            // 準備：画像のない商品
            var category = newCategory();
            catalog.product(standard, SHIRT_NAME, SHIRT_PRICE_EXCL_TAX, List.of(category)).publish();

            // 操作と確認：代表画像は null
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(nullValue()));
        }

        @Test
        @DisplayName("AC-37、55 一覧の代表画像は、商品の画像の並びの先頭の URL")
        void returnsRepresentativeImageInList() throws Exception {
            // 準備：画像を 2 枚登録した商品
            var category = newCategory();
            var product = catalog.product(standard, "画像のある商品", 1000, List.of(category)).image().image().publish();

            // 操作と確認：先頭の画像の URL
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(product.imageUrls.getFirst()));
        }

        @Test
        @DisplayName("AC-37、55 検索の結果の代表画像も、商品の画像の並びの先頭の URL")
        void returnsRepresentativeImageInSearch() throws Exception {
            // 準備：画像を 2 枚登録した商品
            var mark = newMark();
            var product = catalog.product(standard, mark + "画像のある商品", 1000).image().image().publish();

            // 操作と確認：先頭の画像の URL
            mockMvc.perform(search(mark))
                    .andExpect(jsonPath("$.items[0].imageUrl").value(product.imageUrls.getFirst()));
        }
    }

    @Nested
    @DisplayName("公開期間")
    class PublicationPeriod {

        @Test
        @DisplayName("AC-35 開始日時の前は、一覧に出ない")
        void hidesFromListBeforeStart() throws Exception {
            // 準備：予約公開の商品と通常の商品
            var category = newCategory();
            var products = periodProducts(category);

            // 操作と確認：通常の商品だけが出る
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[*].id", contains(products.plain().id.toString())));
        }

        @Test
        @DisplayName("AC-35 開始日時の前は、詳細が見つからない")
        void hidesDetailBeforeStart() throws Exception {
            // 準備：予約公開の商品
            var products = periodProducts(newCategory());

            // 操作と確認：見つからない
            mockMvc.perform(get(BASE + "/products/" + products.reserved().id)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-35、54 開始日時になると、操作なしで一覧に出て、新着順では開始日時で並ぶ")
        void listsAtStart() throws Exception {
            // 準備：通常の商品より後の日時に開始する予約公開の商品
            var category = newCategory();
            var products = periodProducts(category);

            // 操作：開始日時になる
            clock.setInstant(PUBLISH_START);

            // 確認：開始日時の新しい予約公開の商品が先に出る
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[*].id",
                            contains(products.reserved().id.toString(), products.plain().id.toString())));
        }

        @Test
        @DisplayName("AC-35 開始日時になると、操作なしで詳細が見られる")
        void showsDetailAtStart() throws Exception {
            // 準備：予約公開の商品
            var products = periodProducts(newCategory());

            // 操作：開始日時になる
            clock.setInstant(PUBLISH_START);

            // 確認：詳細が見られる
            mockMvc.perform(get(BASE + "/products/" + products.reserved().id)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-35 終了日時ちょうどで、一覧から消える")
        void hidesFromListAtEnd() throws Exception {
            // 準備：予約公開の商品と通常の商品
            var category = newCategory();
            var products = periodProducts(category);

            // 操作：終了日時ちょうどになる
            clock.setInstant(PUBLISH_END);

            // 確認：通常の商品だけが出る
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[*].id", contains(products.plain().id.toString())));
        }

        @Test
        @DisplayName("AC-35 終了日時ちょうどで、詳細が見つからなくなる")
        void hidesDetailAtEnd() throws Exception {
            // 準備：予約公開の商品
            var products = periodProducts(newCategory());

            // 操作：終了日時ちょうどになる
            clock.setInstant(PUBLISH_END);

            // 確認：見つからない
            mockMvc.perform(get(BASE + "/products/" + products.reserved().id)).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("一覧の在庫の表示")
    class ListStock {

        @Test
        @DisplayName("AC-56 全バリアントが在庫なしの商品も一覧に出て、在庫なしと示される")
        void marksOutOfStock() throws Exception {
            // 準備：1 つのバリアントに在庫のある商品と、在庫 0 と在庫のまだないバリアントだけの商品
            var category = newCategory();
            var inStock = catalog.product(standard, "在庫あり", 1000, List.of(category), "色").variant("白").variant("黒").publish();
            inventory.stock(inStock.variantIds.getFirst(), 0);
            inventory.stock(inStock.variantIds.get(1), 3);
            var soldOut = catalog.product(standard, "在庫なし", 1000, List.of(category), "色").variant("白").variant("黒").publish();
            inventory.stock(soldOut.variantIds.getFirst(), 0);
            // 2 つ目のバリアントの在庫は、まだない

            // 操作と確認：両方が出て、在庫なしの商品は在庫なし、在庫ありの商品は在庫あり
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[*].id", contains(soldOut.id.toString(), inStock.id.toString())))
                    .andExpect(jsonPath("$.items[*].inStock", contains(false, true)));
        }

        @Test
        @DisplayName("AC-56 在庫がまだ作られていないバリアントは、在庫なしとして数える")
        void treatsMissingStockAsOutOfStock() throws Exception {
            // 準備：在庫をまだ作っていないバリアントだけの商品
            var category = newCategory();
            catalog.product(standard, "在庫の記録なし", 1000, List.of(category)).publish();

            // 操作と確認：在庫なし
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[0].inStock").value(false));
        }

        @Test
        @DisplayName("AC-28、56 販売停止のバリアントだけに在庫がある商品は、在庫なしと示される")
        void ignoresSuspendedVariantsForStock() throws Exception {
            // 準備：在庫のあるバリアントを販売停止にし、残りは在庫 0 の商品
            var category = newCategory();
            var product = catalog.product(standard, "販売停止", 1000, List.of(category), "色").variant("白").variant("黒").publish();
            inventory.stock(product.variantIds.getFirst(), 5);
            inventory.stock(product.variantIds.get(1), 0);
            product.suspend(0);

            // 操作と確認：在庫なし
            mockMvc.perform(listIn(category))
                    .andExpect(jsonPath("$.items[0].inStock").value(false));
        }
    }

    @Nested
    @DisplayName("価格による絞り込みと並び")
    class Price {

        @Test
        @DisplayName("AC-53 税込価格の範囲で絞り込める")
        void filtersByPriceInclTax() throws Exception {
            // 準備：税込 990 円・1,100 円・2,200 円の商品
            var category = newCategory();
            var products = threePricedProducts(category);

            // 操作と確認：税込 1,100〜2,200 円で絞ると、990 円の商品は出ない（安い順）
            mockMvc.perform(listIn(category).param("minPrice", "1100").param("maxPrice", "2200").param("sort", "price_asc"))
                    .andExpect(jsonPath("$.items[*].id", contains(products.get(1).id.toString(), products.get(2).id.toString())));
        }

        @Test
        @DisplayName("AC-54 価格の高い順に並べられる")
        void sortsByPriceDesc() throws Exception {
            // 準備：税込 990 円・1,100 円・2,200 円の商品
            var category = newCategory();
            var products = threePricedProducts(category);

            // 操作と確認：税込 1,100〜2,200 円で絞り、高い順
            mockMvc.perform(listIn(category).param("minPrice", "1100").param("maxPrice", "2200").param("sort", "price_desc"))
                    .andExpect(jsonPath("$.items[*].id", contains(products.get(2).id.toString(), products.get(1).id.toString())));
        }

        @Test
        @DisplayName("並び順が決められた値でなければ、リクエストの誤り")
        void rejectsUnknownSort() throws Exception {
            // 操作と確認：決められていない並び順は 400
            mockMvc.perform(get(BASE + "/products").param("sort", "popular"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("一覧のページ")
    class ListPages {

        @Test
        @DisplayName("AC-57 指定したページの商品が返る")
        void returnsRequestedPage() throws Exception {
            // 準備：5 つの商品（新着順では後に作ったものが先）
            var category = newCategory();
            var ids = fiveProductIds(category);

            // 操作と確認：2 件ずつの 2 ページ目は、新着順で 3 番目と 4 番目（作った順で 3 番目と 2 番目）
            mockMvc.perform(listIn(category).param("page", "2").param("size", "2"))
                    .andExpect(jsonPath("$.items[*].id", contains(ids.get(2), ids.get(1))));
        }

        @Test
        @DisplayName("AC-57 ページの番号・大きさ・全件数・ページ数が返る")
        void returnsPageInfo() throws Exception {
            // 準備：5 つの商品
            var category = newCategory();
            fiveProductIds(category);

            // 操作と確認：2 件ずつの 2 ページ目で、全 5 件・3 ページ
            mockMvc.perform(listIn(category).param("page", "2").param("size", "2"))
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalItems").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }
    }

    @Nested
    @DisplayName("キーワード検索")
    class Search {

        @Test
        @DisplayName("AC-58 商品名にキーワードを含む商品が出て、含まない商品は出ない")
        void searchesByName() throws Exception {
            // 準備：キーワードを含む商品と、含まない商品
            var mark = newMark();
            var byName = catalog.product(standard, mark + "綿のシャツ", 1000).publish();
            catalog.product(standard, mark + "パンツ", 1000).publish();

            // 操作と確認：含む商品だけが出る
            mockMvc.perform(search(mark + "綿"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains(byName.id.toString())));
        }

        @Test
        @DisplayName("AC-58 説明にだけキーワードを含む商品も出る")
        void searchesByDescription() throws Exception {
            // 準備：商品名でなく説明にキーワードを含む商品
            var mark = newMark();
            var byDescription = catalog.product(standard, "シャツ", mark + "綿の生地", 1000, List.of()).publish();

            // 操作と確認：その商品が出る
            mockMvc.perform(search(mark))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", contains(byDescription.id.toString())));
        }

        @Test
        @DisplayName("AC-59 キーワードを含んでも、表示されない商品は出ない")
        void excludesInvisibleProducts() throws Exception {
            // 準備：キーワードを含む、公開した商品と非公開にした商品
            var mark = newMark();
            var visible = catalog.product(standard, mark + "綿のシャツ", 1000).publish();
            catalog.product(standard, mark + "綿のパンツ", 1000).publish().unpublish();

            // 操作と確認：公開した商品だけが出る
            mockMvc.perform(search(mark + "綿"))
                    .andExpect(jsonPath("$.items[*].id", contains(visible.id.toString())));
        }

        @Test
        @DisplayName("AC-58 検索の結果の商品は、税込価格と在庫の有無を持つ")
        void returnsPriceAndStock() throws Exception {
            // 準備：税抜 1,000 円で、在庫を作っていない商品
            var mark = newMark();
            catalog.product(standard, mark + "綿のシャツ", 1000).publish();

            // 操作と確認：税込 1,100 円で、在庫なし
            mockMvc.perform(search(mark))
                    .andExpect(jsonPath("$.items[0].price.inclTax").value(1100))
                    .andExpect(jsonPath("$.items[0].inStock").value(false));
        }

        @Test
        @DisplayName("AC-61 ページ単位で取得できる")
        void pages() throws Exception {
            // 準備：キーワードを含む 3 つの商品（新着順では後に作ったものが先）
            var mark = newMark();
            var ids = new String[3];
            for (int i = 0; i < 3; i++) {
                ids[i] = catalog.product(standard, mark + "シャツ " + i, 1000).publish().id.toString();
            }

            // 操作と確認：2 件ずつの 2 ページ目は、最初に作った商品で、全 3 件
            mockMvc.perform(search(mark + "シャツ").param("page", "2").param("size", "2"))
                    .andExpect(jsonPath("$.items[*].id", contains(ids[0])))
                    .andExpect(jsonPath("$.totalItems").value(3));
        }

        @Test
        @DisplayName("キーワードがなければ、リクエストの誤り")
        void requiresKeyword() throws Exception {
            // 操作と確認：400
            mockMvc.perform(get(BASE + "/products/search"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("商品の詳細")
    class Detail {

        @Test
        @DisplayName("AC-62 商品の ID・商品名・説明・税込と税抜の価格が返る")
        void returnsProductInfo() throws Exception {
            // 準備：3 つのバリアントを持つ商品
            var product = shirtWithThreeVariants();

            // 操作と確認：登録した値
            detail(product)
                    .andExpect(jsonPath("$.id").value(product.id.toString()))
                    .andExpect(jsonPath("$.name").value(SHIRT_NAME))
                    .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
                    .andExpect(jsonPath("$.price.inclTax").value(SHIRT_PRICE_INCL_TAX))
                    .andExpect(jsonPath("$.price.exclTax").value(SHIRT_PRICE_EXCL_TAX));
        }

        @Test
        @DisplayName("AC-62 画像のない商品は、画像の URL が空で、バリアントの画像も null")
        void returnsNoImages() throws Exception {
            // 準備：画像のない商品
            var product = shirtWithThreeVariants();

            // 操作と確認：画像の URL は空、バリアントの画像は null
            detail(product)
                    .andExpect(jsonPath("$.imageUrls").isEmpty())
                    .andExpect(jsonPath("$.variants[0].imageUrl").value(nullValue()));
        }

        @Test
        @DisplayName("AC-62 軸と、軸ごとの選択肢が登録順に返る")
        void returnsAxes() throws Exception {
            // 準備：色とサイズの軸を持つ商品
            var product = shirtWithThreeVariants();

            // 操作と確認：色（白・黒）、サイズ（M・L）の順
            detail(product)
                    .andExpect(jsonPath("$.axes[*].name", contains("色", "サイズ")))
                    .andExpect(jsonPath("$.axes[0].values", contains("白", "黒")))
                    .andExpect(jsonPath("$.axes[1].values", contains("M", "L")));
        }

        @Test
        @DisplayName("AC-28、62 販売停止・在庫切れのバリアントも含めて、全てのバリアントが軸の値と共に返る")
        void returnsAllVariants() throws Exception {
            // 準備：在庫切れのバリアントと販売停止のバリアントを含む商品
            var product = shirtWithThreeVariants();

            // 操作と確認：3 つのバリアントが登録順に返り、先頭は白・M
            detail(product)
                    .andExpect(jsonPath("$.variants[*].id", contains(product.variantIds.stream().map(UUID::toString).toArray())))
                    .andExpect(jsonPath("$.variants[0].axisValues", contains("白", "M")));
        }

        @Test
        @DisplayName("AC-63、64 バリアントごとに在庫の有無が返る")
        void returnsStockPerVariant() throws Exception {
            // 準備：在庫 2・0・4 のバリアントを持つ商品
            var product = shirtWithThreeVariants();

            // 操作と確認：在庫あり・在庫なし・在庫あり
            detail(product)
                    .andExpect(jsonPath("$.variants[*].inStock", contains(true, false, true)));
        }

        @Test
        @DisplayName("AC-64 在庫の数量は返さない")
        void doesNotReturnQuantity() throws Exception {
            // 準備：在庫のあるバリアントを持つ商品
            var product = shirtWithThreeVariants();

            // 操作と確認：数量の項目がない
            detail(product)
                    .andExpect(jsonPath("$.variants[0].quantity").doesNotExist());
        }

        @Test
        @DisplayName("AC-28 販売停止のバリアントは、販売停止と示される")
        void marksSuspendedVariant() throws Exception {
            // 準備：3 つ目のバリアントを販売停止にした商品
            var product = shirtWithThreeVariants();

            // 操作と確認：3 つ目だけが販売停止
            detail(product)
                    .andExpect(jsonPath("$.variants[*].salesSuspended", contains(false, false, true)));
        }

        @Test
        @DisplayName("BR-11 在庫数がすべて引き当てられたバリアントは、在庫なしと示される")
        void treatsReservedStockAsOutOfStock() throws Exception {
            // 準備：在庫 1 ずつのバリアントのうち、白の在庫を全て引き当てた商品
            var product = catalog.product(standard, SHIRT_NAME, SHIRT_PRICE_EXCL_TAX, "色").variant("白").variant("黒").publish();
            inventory.stock(product.variantIds.getFirst(), 1);
            inventory.stock(product.variantIds.get(1), 1);
            stockReservationApi.reserve(UUID.randomUUID(), Map.of(product.variantIds.getFirst(), 1),
                    clock.instant().plus(Duration.ofMinutes(15)));

            // 操作と確認：白は在庫なし、黒は在庫あり
            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(jsonPath("$.variants[*].inStock", contains(false, true)));
        }

        @Test
        @DisplayName("AC-65 表示されない商品は見つからない")
        void returnsNotFoundForInvisibleProduct() throws Exception {
            // 準備：公開してから非公開にした商品
            var unpublished = catalog.product(standard, "非公開", 1000).publish().unpublish();

            // 操作と確認：存在しない商品と同じ、見つからないというエラー
            mockMvc.perform(get(BASE + "/products/" + unpublished.id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("AC-65 存在しない商品は見つからない")
        void returnsNotFoundForUnknownProduct() throws Exception {
            // 操作と確認：見つからないというエラー
            mockMvc.perform(get(BASE + "/products/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("AC-66 URL は商品 ID なので、品番を付け替えても変わらない")
        void urlDoesNotDependOnProductCode() throws Exception {
            // 準備：公開した後に品番を付け替えた商品
            var product = catalog.product(standard, SHIRT_NAME, 1000).publish();
            dsl.update(PRODUCT).set(PRODUCT.PRODUCT_CODE, "RENAMED-001")
                    .where(PRODUCT.ID.eq(product.id)).execute();

            // 操作と確認：同じ URL で見られる
            mockMvc.perform(get(BASE + "/products/" + product.id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(product.id.toString()));
        }
    }

    @Nested
    @DisplayName("商品の詳細の画像")
    class DetailImages {

        @Test
        @DisplayName("AC-37 商品の画像の URL が、並び順で返る")
        void returnsImagesInOrder() throws Exception {
            // 準備：画像を 2 枚登録した商品
            var product = productWithVariantImage();

            // 操作と確認：登録した順
            detail(product)
                    .andExpect(jsonPath("$.imageUrls", contains(product.imageUrls.toArray())));
        }

        @Test
        @DisplayName("AC-38 画像を指定したバリアントは、その画像の URL を返す")
        void returnsVariantImage() throws Exception {
            // 準備：2 つ目のバリアントに 2 枚目の画像を指定した商品
            var product = productWithVariantImage();

            // 操作と確認：2 つ目のバリアントは 2 枚目の画像
            detail(product)
                    .andExpect(jsonPath("$.variants[1].imageUrl").value(product.imageUrls.get(1)));
        }

        @Test
        @DisplayName("AC-38 画像を指定していないバリアントの画像は null")
        void returnsNullForVariantWithoutImage() throws Exception {
            // 準備：1 つ目のバリアントに画像を指定していない商品
            var product = productWithVariantImage();

            // 操作と確認：1 つ目のバリアントは null
            detail(product)
                    .andExpect(jsonPath("$.variants[0].imageUrl").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("カテゴリ")
    class Categories {

        @Test
        @DisplayName("最上位のカテゴリは、登録順に並ぶ")
        void listsRootsInOrder() throws Exception {
            // 準備：最上位のカテゴリを 2 つ
            var tree = categoryTree();

            // 操作：カテゴリの木を取得する
            var body = mockMvc.perform(get(BASE + "/categories"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // 確認：他のテストの最上位のカテゴリが間に入っても、自分の 2 つは登録順
            List<String> rootNames = JsonPath.read(body, "$.items[*].name");
            assertThat(rootNames.indexOf(tree.clothing())).isLessThan(rootNames.indexOf(tree.food()));
        }

        @Test
        @DisplayName("子のカテゴリは、登録順に親の中に含まれる")
        void listsChildrenInOrder() throws Exception {
            // 準備：子を 2 つ持つ最上位のカテゴリ
            var tree = categoryTree();

            // 操作と確認：子は登録順
            mockMvc.perform(get(BASE + "/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(nodeOf(tree.clothing()) + ".children[*].name", contains("食品", "トップス")));
        }

        @Test
        @DisplayName("カテゴリの深さは返さない")
        void doesNotReturnDepth() throws Exception {
            // 準備：最上位のカテゴリ
            var tree = categoryTree();

            // 操作と確認：深さの項目がない
            mockMvc.perform(get(BASE + "/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(nodeOf(tree.clothing()) + ".depth").doesNotExist());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * カテゴリで絞った一覧に、指定した商品だけが出ることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>応答が 200</li>
     *   <li>一覧の商品 ID が、指定した商品だけ</li>
     *   <li>全件数が 1</li>
     * </ul>
     *
     * @param category カテゴリ ID
     * @param visible  出る商品
     */
    private void assertListsOnly(UUID category, TestProduct visible) throws Exception {
        mockMvc.perform(listIn(category))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(visible.id.toString())))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 公開期間を指定した商品と、指定しない商品
     *
     * @param reserved 予約公開の商品（{@link #PUBLISH_START} から {@link #PUBLISH_END} の前まで）
     * @param plain    公開期間を指定しない商品
     */
    private record PeriodProducts(TestProduct reserved, TestProduct plain) {
    }

    /**
     * 自分のカテゴリの名前
     *
     * @param clothing 最上位のカテゴリ「衣類」の名前（子は「食品」「トップス」の順）
     * @param food     最上位のカテゴリ「食品」の名前（衣類の後に登録する）
     */
    private record CategoryTree(String clothing, String food) {
    }

    /**
     * 予約公開の商品と、通常の商品を、この順に作って公開する。
     *
     * @param category カテゴリ ID
     * @return 2 つの商品
     */
    private PeriodProducts periodProducts(UUID category) throws Exception {
        var reserved = catalog.product(standard, "予約の商品", 1000, List.of(category)).period(PUBLISH_START, PUBLISH_END).publish();
        var plain = catalog.product(standard, "通常の商品", 1000, List.of(category)).publish();
        return new PeriodProducts(reserved, plain);
    }

    /**
     * 税抜 900 円・1,000 円・2,000 円（税込 990 円・1,100 円・2,200 円）の商品を、この順に作って公開する。
     *
     * @param category カテゴリ ID
     * @return 作った順の商品
     */
    private List<TestProduct> threePricedProducts(UUID category) throws Exception {
        var a = catalog.product(standard, "A", 900, List.of(category)).publish();
        var b = catalog.product(standard, "B", 1000, List.of(category)).publish();
        var c = catalog.product(standard, "C", 2000, List.of(category)).publish();
        return List.of(a, b, c);
    }

    /**
     * 5 つの商品を作って公開する。
     *
     * @param category カテゴリ ID
     * @return 作った順の商品 ID
     */
    private List<String> fiveProductIds(UUID category) throws Exception {
        var ids = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            ids.add(catalog.product(standard, "商品 " + i, 1000, List.of(category)).publish().id.toString());
        }
        return ids;
    }

    /**
     * 色とサイズの軸で 3 つのバリアント（白・M、白・L、黒・M）を持つ、税抜 1,800 円の商品を作って公開する。
     *
     * <p>在庫は 2・0・4 で、3 つ目のバリアントを販売停止にする。画像は登録しない。
     *
     * @return 商品
     */
    private TestProduct shirtWithThreeVariants() throws Exception {
        var product = catalog.product(standard, SHIRT_NAME, SHIRT_PRICE_EXCL_TAX, "色", "サイズ")
                .variant("白", "M").variant("白", "L").variant("黒", "M").publish();
        inventory.stock(product.variantIds.getFirst(), 2);
        inventory.stock(product.variantIds.get(1), 0);
        inventory.stock(product.variantIds.get(2), 4);
        product.suspend(2);
        return product;
    }

    /**
     * 画像を 2 枚登録し、2 つ目のバリアントに 2 枚目の画像を指定した商品を作って公開する。
     *
     * @return 商品
     */
    private TestProduct productWithVariantImage() throws Exception {
        var product = catalog.product(standard, "画像", 1000, "色").variant("白").variant("黒").image().image();
        return product.variantImage(1, 1).publish();
    }

    /**
     * 最上位のカテゴリ「衣類」と「食品」をこの順に登録し、衣類に子「食品」「トップス」をこの順に登録する。
     *
     * <p>最上位の名前は、他のテストと重ならないように識別子を付ける。
     *
     * @return 最上位のカテゴリの名前
     */
    private CategoryTree categoryTree() throws Exception {
        var clothingName = "衣類 " + UUID.randomUUID();
        var clothing = catalog.category(clothingName, null);
        var foodName = "食品 " + UUID.randomUUID();
        catalog.category(foodName, null);
        catalog.category("食品", clothing);
        catalog.category("トップス", clothing);
        return new CategoryTree(clothingName, foodName);
    }

    /**
     * テストごとのカテゴリを作る（一覧をこのカテゴリで絞って、他のテストの商品が見えないようにする）。
     *
     * @return カテゴリ ID
     */
    private UUID newCategory() throws Exception {
        return catalog.category("カテゴリ " + UUID.randomUUID(), null);
    }

    /**
     * 商品名の先頭に付ける、テストごとの識別子を作る（検索をこの識別子で絞る。他の語と重ならない英数字）。
     *
     * @return 識別子
     */
    private static String newMark() {
        return "mk" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * カテゴリで絞った商品一覧のリクエストを作る。
     *
     * @param category カテゴリ ID
     * @return リクエスト
     */
    private static MockHttpServletRequestBuilder listIn(UUID category) {
        return get(BASE + "/products").param("categoryId", category.toString());
    }

    /**
     * キーワード検索のリクエストを作る。
     *
     * @param keyword キーワード
     * @return リクエスト
     */
    private static MockHttpServletRequestBuilder search(String keyword) {
        return get(BASE + "/products/search").param("q", keyword);
    }

    /**
     * 商品の詳細を取得し、200 であることを確認する。
     *
     * @param product 商品
     * @return 応答
     */
    private ResultActions detail(TestProduct product) throws Exception {
        return mockMvc.perform(get(BASE + "/products/" + product.id)).andExpect(status().isOk());
    }

    /**
     * カテゴリの木の応答から、名前で最上位のカテゴリを選ぶ JSONPath を作る。
     *
     * @param rootName 最上位のカテゴリの名前
     * @return JSONPath
     */
    private static String nodeOf(String rootName) {
        return "$.items[?(@.name=='%s')]".formatted(rootName);
    }
}
