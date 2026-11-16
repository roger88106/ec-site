package com.ecsite.module.catalog.api;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.api.model.StoreProductQuery;
import com.ecsite.module.catalog.api.model.StoreProductSort;
import com.ecsite.module.catalog.api.model.StoreProductSummary;
import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCaches;
import com.ecsite.module.tax.api.TaxRateApi;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 側向けの商品照会の統合テスト
 *
 * <p>商品は B 側の API で作って公開する。新着順を確認するため、公開するごとに時計を 1 分進める。
 * 税率の一覧のキャッシュは、前のテストのものが残らないよう、テストごとに捨てる。テストごとにロールバックする。
 *
 * <p>一覧は、テストごとに作るカテゴリで絞り、他のテストの商品が見えないようにする。
 */
@IntegrationTest
@Transactional
class StoreCatalogApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 税区分「標準」の税率（%）
     */
    private static final String STANDARD_RATE = "10";

    /**
     * 税区分「軽減」の税率（%）
     */
    private static final String REDUCED_RATE = "8";

    /**
     * 税区分「端数」の税率（%）
     *
     * <p>税込価格に 1 円未満の端数が出やすい率。
     */
    private static final String ODD_RATE = "7.25";

    /**
     * 税込価格の一致を確認する税抜価格
     *
     * <p>1 円、桁の変わり目の前後と、大きな金額。
     */
    private static final long[] BOUNDARY_PRICES = {1, 9, 10, 11, 99, 100, 101, 999, 1234, 9999, 99_999, 1_000_001};

    /**
     * 商品の既定の税抜価格
     */
    private static final long PRICE_EXCL_TAX = 1000;

    /**
     * 1 ページ目に全てが入る指定
     */
    private static final PageRequest FIRST_PAGE = PageRequest.of(1, 20);

    /**
     * 税率の切り替え・開始の、今からの時間
     */
    private static final Duration RATE_CHANGE_AFTER = Duration.ofHours(1);

    /**
     * 税抜価格 {@value #PRICE_EXCL_TAX} 円の、税率 {@value #STANDARD_RATE}% の税込価格
     */
    private static final long PRICE_INCL_STANDARD = 1100;

    /**
     * 税抜価格 {@value #PRICE_EXCL_TAX} 円の、税率 {@value #REDUCED_RATE}% の税込価格
     */
    private static final long PRICE_INCL_REDUCED = 1080;

    // ============================== Bean ==============================

    @Autowired
    StoreCatalogApi api;

    @Autowired
    TaxRateApi taxRateApi;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationContext context;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * 税区分「標準」の ID
     */
    private UUID standard;

    /**
     * 税区分「軽減」の ID
     */
    private UUID reduced;

    /**
     * テストごとのカテゴリ
     *
     * <p>一覧をこのカテゴリで絞って、他のテストの商品が見えないようにする。
     */
    private UUID category;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogTestData(mockMvc, clock);
        standard = catalog.taxCategory("標準 " + UUID.randomUUID().toString().substring(0, 8), STANDARD_RATE);
        reduced = catalog.taxCategory("軽減", REDUCED_RATE);
        category = catalog.category("カテゴリ " + UUID.randomUUID(), null);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("税込価格")
    class PriceInclTax {

        @Test
        @DisplayName("BR-1 税込価格の計算が、Java（税モジュール）と SQL で、端数の境界値でも一致する")
        void priceInclTaxAgreesWithJava() throws Exception {
            // 準備：3 つの税率それぞれで、境界の税抜価格の商品を公開し、税モジュールで税込価格を計算しておく
            var odd = catalog.taxCategory("端数", ODD_RATE);
            var expected = new ArrayList<Long>();
            for (var taxCategory : List.of(standard, reduced, odd)) {
                var rate = taxRateApi.rateAt(taxCategory, NOW).orElseThrow();
                for (var price : BOUNDARY_PRICES) {
                    published(taxCategory, price);
                    expected.add(javaPriceInclTax(price, rate));
                }
            }

            // 操作：一覧を新着順で取得する
            var page = api.listProducts(query(null, null, StoreProductSort.NEWEST), PageRequest.of(1, 100));

            // 確認：新着順は公開した順の逆なので、逆にして並べた税込価格が、税モジュールの計算と一致する
            var actual = page.items().stream().map(summary -> summary.priceInclTax().yen()).toList().reversed();
            assertThat(actual).containsExactlyElementsOf(expected);
        }
    }

    @Nested
    @DisplayName("価格の範囲での絞り込み")
    class PriceRange {

        @Test
        @DisplayName("AC-53 税込価格が下限以上・上限以下の商品だけが含まれる")
        void filtersByPriceRange() throws Exception {
            // 準備：税込 990・1,100・2,160・2,200 円の商品
            published(standard, 900);
            var lower = published(standard, 1000);
            var upper = published(reduced, 2000);
            published(standard, 2000);

            // 操作：税込 1,100〜2,160 円で絞り、安い順に取得する
            var page = api.listProducts(query(Money.of(1100), Money.of(2160), StoreProductSort.PRICE_ASC), FIRST_PAGE);

            // 確認：下限ちょうど・上限ちょうどの商品だけが含まれる
            assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(lower.id, upper.id);
            assertThat(page.items()).extracting(StoreProductSummary::priceExclTax).containsExactly(Money.of(1000), Money.of(2000));
        }

        @Test
        @DisplayName("AC-53 税抜価格が同じ商品でも、税込価格で範囲に入るかが決まる")
        void filtersByPriceInclTax() throws Exception {
            // 準備：税抜 2,000 円で、税込 2,160 円（軽減）と 2,200 円（標準）の商品
            var reducedProduct = published(reduced, 2000);
            published(standard, 2000);

            // 操作：税込 1,100〜2,160 円で絞る
            var page = api.listProducts(query(Money.of(1100), Money.of(2160), StoreProductSort.PRICE_ASC), FIRST_PAGE);

            // 確認：税込 2,160 円の商品だけが含まれる
            assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(reducedProduct.id);
        }

        @Test
        @DisplayName("AC-53 下限だけを指定すると、税込価格が下限以上の商品が含まれる")
        void filtersByMinPriceOnly() throws Exception {
            // 準備：税込 990・1,100・2,200 円の商品
            published(standard, 900);
            var atMin = published(standard, 1000);
            var aboveMin = published(standard, 2000);

            // 操作：税込 1,100 円以上で絞り、安い順に取得する
            var page = api.listProducts(query(Money.of(1100), null, StoreProductSort.PRICE_ASC), FIRST_PAGE);

            // 確認：税込 1,100 円以上の商品が含まれる
            assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(atMin.id, aboveMin.id);
        }

        @Test
        @DisplayName("AC-53 上限だけを指定すると、税込価格が上限以下の商品が含まれる")
        void filtersByMaxPriceOnly() throws Exception {
            // 準備：税込 990・1,100・2,200 円の商品
            var belowMax = published(standard, 900);
            var atMax = published(standard, 1000);
            published(standard, 2000);

            // 操作：税込 1,100 円以下で絞り、安い順に取得する
            var page = api.listProducts(query(null, Money.of(1100), StoreProductSort.PRICE_ASC), FIRST_PAGE);

            // 確認：税込 1,100 円以下の商品が含まれる
            assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(belowMax.id, atMax.id);
        }
    }

    @Nested
    @DisplayName("並び順")
    class Sorting {

        @Test
        @DisplayName("AC-54 価格の安い順は、税抜でなく税込価格で並ぶ")
        void sortsByPriceInclTaxAscending() throws Exception {
            // 準備：税抜は 990 < 1,000 だが、税込は 1,089（10%）> 1,080（8%）になる 2 つの商品
            var cheaperExclTax = published(standard, 990);
            var cheaperInclTax = published(reduced, 1000);

            // 操作と確認：税込の安い順
            assertThat(listIds(StoreProductSort.PRICE_ASC)).containsExactly(cheaperInclTax.id, cheaperExclTax.id);
        }

        @Test
        @DisplayName("AC-54 価格の高い順は、税抜でなく税込価格で並ぶ")
        void sortsByPriceInclTaxDescending() throws Exception {
            // 準備：税抜は 990 < 1,000 だが、税込は 1,089（10%）> 1,080（8%）になる 2 つの商品
            var cheaperExclTax = published(standard, 990);
            var cheaperInclTax = published(reduced, 1000);

            // 操作と確認：税込の高い順
            assertThat(listIds(StoreProductSort.PRICE_DESC)).containsExactly(cheaperExclTax.id, cheaperInclTax.id);
        }

        @Test
        @DisplayName("AC-54 新着順は、初回表示日時の新しい順")
        void sortsByFirstShownAt() throws Exception {
            // 準備：2 つの商品を順に公開する
            var first = published(standard, PRICE_EXCL_TAX);
            var second = published(standard, PRICE_EXCL_TAX);

            // 操作と確認：後に公開した商品が先
            assertThat(listIds(StoreProductSort.NEWEST)).containsExactly(second.id, first.id);
        }

        @Test
        @DisplayName("AC-54 再公開しても、新着順の順位は上がらない")
        void keepsRankAfterRepublishing() throws Exception {
            // 準備：2 つの商品を順に公開し、先の商品を非公開にして、1 分後に再び公開する
            var first = published(standard, PRICE_EXCL_TAX);
            var second = published(standard, PRICE_EXCL_TAX);
            first.unpublish();
            clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
            first.publish();

            // 操作と確認：先の商品は、最初に表示された日時の順のまま後ろ
            assertThat(listIds(StoreProductSort.NEWEST)).containsExactly(second.id, first.id);
        }

        @Test
        @DisplayName("並びの基準が同じ商品は ID の順になり、ページをまたいでも重複・欠落しない")
        void pagesWithoutDuplicatesOrGaps() throws Exception {
            // 準備：同じ価格の商品を 7 つ公開する
            var ids = new ArrayList<UUID>();
            for (int i = 0; i < 7; i++) {
                ids.add(published(standard, PRICE_EXCL_TAX).id);
            }

            // 操作：安い順で、1 ページ 3 件の 3 ページを続けて取得する
            var collected = IntStream.rangeClosed(1, 3).boxed()
                    .flatMap(page -> api.listProducts(query(null, null, StoreProductSort.PRICE_ASC), PageRequest.of(page, 3)).items().stream())
                    .map(StoreProductSummary::id)
                    .toList();

            // 確認：重複がなく、ID の順に全てが並ぶ
            // PostgreSQL の uuid の順は文字列の辞書順（Java の UUID#compareTo は符号付きで比べるので使えない）
            assertThat(collected).doesNotHaveDuplicates()
                    .containsExactlyElementsOf(ids.stream().sorted(Comparator.comparing(UUID::toString)).toList());
        }
    }

    @Nested
    @DisplayName("C 側に表示されない商品")
    class InvisibleProducts {

        @Test
        @DisplayName("AC-51 非公開の商品は含まれない")
        void excludesUnpublished() throws Exception {
            // 準備：表示される商品と、公開した後に非公開にした商品
            var visible = published(standard, PRICE_EXCL_TAX);
            published(standard, PRICE_EXCL_TAX).unpublish();

            // 操作と確認：表示される商品だけが含まれる
            assertOnlyVisibleListed(visible.id);
        }

        @Test
        @DisplayName("AC-51 販売を終了した商品は含まれない")
        void excludesDiscontinued() throws Exception {
            // 準備：表示される商品と、公開した後に販売を終了した商品
            var visible = published(standard, PRICE_EXCL_TAX);
            published(standard, PRICE_EXCL_TAX).discontinue();

            // 操作と確認：表示される商品だけが含まれる
            assertOnlyVisibleListed(visible.id);
        }

        @Test
        @DisplayName("AC-51 公開期間の外の商品は含まれない")
        void excludesOutOfPeriod() throws Exception {
            // 準備：表示される商品と、公開開始日時が明日の商品
            var visible = published(standard, PRICE_EXCL_TAX);
            catalog.product(standard, "商品", PRICE_EXCL_TAX, List.of(category)).period(NOW.plus(Duration.ofDays(1)), null).publish();

            // 操作と確認：表示される商品だけが含まれる
            assertOnlyVisibleListed(visible.id);
        }

        @Test
        @DisplayName("有効な税率のない税区分の商品は含まれない")
        void excludesProductWithoutTaxRate() throws Exception {
            // 準備：表示される商品と、税区分に今は有効な税率がない商品
            var visible = published(standard, PRICE_EXCL_TAX);
            publishedBeforeRateStarts();

            // 操作と確認：表示される商品だけが含まれる
            assertOnlyVisibleListed(visible.id);
        }

        @Test
        @DisplayName("有効な税率のない税区分の商品は、詳細も見つからない")
        void hidesDetailOfProductWithoutTaxRate() throws Exception {
            // 準備：税区分に今は有効な税率がない商品
            var product = publishedBeforeRateStarts();

            // 操作と確認：詳細は空
            assertThat(api.findProduct(product.id)).isEmpty();
        }
    }

    @Nested
    @DisplayName("予定の税率への切り替え")
    class RateSwitch {

        @Test
        @DisplayName("切り替えの直前は、旧い税率の税込価格")
        void usesOldRateJustBeforeSwitch() throws Exception {
            // 準備：税区分「標準」の税率を、1 時間後から軽減と同じ率に切り替える予定にし、キャッシュを捨てる
            published(standard, PRICE_EXCL_TAX);
            var switchAt = scheduleSwitch();

            // 操作：切り替えの 1 ナノ秒前に一覧を取得する
            clock.setInstant(switchAt.minusNanos(1));
            var item = api.listProducts(query(null, null, StoreProductSort.NEWEST), FIRST_PAGE).items().getFirst();

            // 確認：旧い税率の税込価格
            assertThat(item.priceInclTax()).isEqualTo(Money.of(PRICE_INCL_STANDARD));
        }

        @Test
        @DisplayName("切り替えの時刻ちょうどから、キャッシュを捨てなくても新しい税率の税込価格になる")
        void usesNewRateAtSwitchWithoutEvicting() throws Exception {
            // 準備：切り替えの予定を登録し、直前に一覧を取得して、予定を含む税率の一覧をキャッシュに入れる
            published(standard, PRICE_EXCL_TAX);
            var switchAt = scheduleSwitch();
            clock.setInstant(switchAt.minusNanos(1));
            api.listProducts(query(null, null, StoreProductSort.NEWEST), FIRST_PAGE);

            // 操作：切り替えの時刻ちょうどに、キャッシュを捨てずに一覧を取得する
            clock.setInstant(switchAt);
            var item = api.listProducts(query(null, null, StoreProductSort.NEWEST), FIRST_PAGE).items().getFirst();

            // 確認：新しい税率の税込価格
            assertThat(item.priceInclTax()).isEqualTo(Money.of(PRICE_INCL_REDUCED));
        }
    }

    @Nested
    @DisplayName("カテゴリでの絞り込み")
    class CategoryFilter {

        @Test
        @DisplayName("AC-52 指定したカテゴリの商品だけが含まれる")
        void filtersByCategory() throws Exception {
            // 準備：「衣類」の商品と、「食品」の商品
            var clothing = catalog.category("衣類 " + UUID.randomUUID(), null);
            var food = catalog.category("食品 " + UUID.randomUUID(), null);
            var inClothing = publishedIn(clothing);
            publishedIn(food);

            // 操作：「衣類」で絞って取得する
            var page = api.listProducts(new StoreProductQuery(clothing, null, null, StoreProductSort.NEWEST), FIRST_PAGE);

            // 確認：「衣類」の商品だけ
            assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(inClothing);
        }

        @Test
        @DisplayName("AC-52 子孫のカテゴリの商品も含まれる")
        void includesDescendants() throws Exception {
            // 準備：「衣類」と、その孫の「T シャツ」の商品
            var clothing = catalog.category("衣類 " + UUID.randomUUID(), null);
            var tops = catalog.category("トップス", clothing);
            var tShirts = catalog.category("T シャツ", tops);
            var inClothing = publishedIn(clothing);
            var inTShirts = publishedIn(tShirts);

            // 操作：「衣類」で絞って取得する
            var page = api.listProducts(new StoreProductQuery(clothing, null, null, StoreProductSort.NEWEST), FIRST_PAGE);

            // 確認：孫のカテゴリの商品も、新着順で含まれる
            assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(inTShirts, inClothing);
        }
    }

    @Nested
    @DisplayName("一覧の項目")
    class Items {

        @Test
        @DisplayName("一覧の各商品は、販売中のバリアントの ID を持つ（在庫の有無の判定に使う）")
        void returnsSellableVariantIds() throws Exception {
            // 準備：バリアントを 1 つ持つ商品
            var product = catalog.product(standard, "商品 1", PRICE_EXCL_TAX, List.of(category)).publish();

            // 操作：一覧の先頭の商品を取得する
            var item = api.listProducts(query(null, null, StoreProductSort.NEWEST), FIRST_PAGE).items().getFirst();

            // 確認：その商品で、販売中のバリアントの ID を持つ
            assertSellableVariants(item, product.id, product.variantIds.getFirst());
        }

        @Test
        @DisplayName("販売停止のバリアントの ID は、販売中のバリアントに含まれない")
        void excludesSuspendedVariantIds() throws Exception {
            // 準備：2 つのバリアントのうち、2 つ目を販売停止にした商品
            var product = catalog.product(standard, "商品 1", PRICE_EXCL_TAX, List.of(category), "色")
                    .variant("白").variant("黒").publish().suspend(1);

            // 操作：一覧の先頭の商品を取得する
            var item = api.listProducts(query(null, null, StoreProductSort.NEWEST), FIRST_PAGE).items().getFirst();

            // 確認：販売中のバリアントは 1 つ目だけ
            assertSellableVariants(item, product.id, product.variantIds.getFirst());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * テストのカテゴリの一覧に、表示される商品だけが含まれることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>一覧の商品が、表示される商品だけ</li>
     *   <li>全体の件数が 1</li>
     * </ul>
     *
     * @param visibleId 表示される商品の ID（実行ごとに作るので、定数にできない）
     */
    private void assertOnlyVisibleListed(UUID visibleId) {
        var page = api.listProducts(query(null, null, StoreProductSort.NEWEST), FIRST_PAGE);
        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(visibleId);
        assertThat(page.totalItems()).isEqualTo(1);
    }

    /**
     * 一覧の商品「商品 1」が、販売中のバリアントの ID を 1 つだけ持つことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>商品 ID</li>
     *   <li>商品名が「商品 1」</li>
     *   <li>販売中のバリアントが 1 つ</li>
     *   <li>販売中のバリアントの ID</li>
     * </ul>
     *
     * @param item              一覧の商品
     * @param productId         商品 ID（実行ごとに作るので、定数にできない）
     * @param sellableVariantId 販売中のバリアントの ID（実行ごとに作るので、定数にできない）
     */
    private static void assertSellableVariants(StoreProductSummary item, UUID productId, UUID sellableVariantId) {
        assertThat(item.id()).isEqualTo(productId);
        assertThat(item.name()).isEqualTo("商品 1");
        assertThat(item.sellableVariantIds()).hasSize(1);
        assertThat(item.sellableVariantIds()).containsExactly(sellableVariantId);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 税区分に今は有効な税率がない商品を、テストのカテゴリに作って公開する。
     *
     * <p>今から {@link #RATE_CHANGE_AFTER} 後に税率が始まる税区分で、その時刻に商品を登録して公開した後、時計を税率の開始の前に戻す
     * （{@link CatalogTestData#taxCategoryStartingAfter}）。
     *
     * @return 商品
     */
    private CatalogTestData.TestProduct publishedBeforeRateStarts() throws Exception {
        var rateStart = catalog.taxCategoryStartingAfter(STANDARD_RATE, RATE_CHANGE_AFTER);
        var product = published(rateStart.taxCategoryId(), PRICE_EXCL_TAX);
        catalog.rewindBeforeRateStart(rateStart.startAt(), context);
        return product;
    }

    /**
     * テストのカテゴリに、バリアントを 1 つ持つ商品を登録して公開する。
     *
     * @param taxCategoryId 税区分 ID
     * @param priceExclTax  税抜価格
     * @return 商品
     */
    private CatalogTestData.TestProduct published(UUID taxCategoryId, long priceExclTax) throws Exception {
        return catalog.product(taxCategoryId, "商品", priceExclTax, List.of(category)).publish();
    }

    /**
     * 指定したカテゴリに、税区分「標準」でバリアントを 1 つ持つ商品を登録して公開する。
     *
     * @param categoryId カテゴリ ID
     * @return 商品 ID
     */
    private UUID publishedIn(UUID categoryId) throws Exception {
        return catalog.product(standard, "商品", "", PRICE_EXCL_TAX, List.of(categoryId)).publish().id;
    }

    /**
     * テストのカテゴリで絞る、C 側の一覧の絞り込みと並び順を作る。
     *
     * @param minPrice 税込価格の下限（なければ null）
     * @param maxPrice 税込価格の上限（なければ null）
     * @param sort     並び順
     * @return 絞り込みと並び順
     */
    private StoreProductQuery query(Money minPrice, Money maxPrice, StoreProductSort sort) {
        return new StoreProductQuery(category, minPrice, maxPrice, sort);
    }

    /**
     * 税モジュールで税込価格を計算する。
     *
     * @param price 税抜価格
     * @param rate  税率（%）
     * @return 税込価格（円）
     */
    private long javaPriceInclTax(long price, BigDecimal rate) {
        return taxRateApi.priceInclTax(Money.of(price), rate).yen();
    }

    // ============================== 操作の補助 ==============================

    /**
     * 税区分「標準」の税率を、今から {@link #RATE_CHANGE_AFTER} 後に {@value #REDUCED_RATE}% へ切り替える予定にし、税率のキャッシュを捨てる。
     *
     * @return 切り替えの時刻
     */
    private Instant scheduleSwitch() throws Exception {
        var switchAt = clock.instant().plus(RATE_CHANGE_AFTER);
        catalog.changeRate(standard, REDUCED_RATE, switchAt);
        TaxRateScheduleCaches.evict(context);
        return switchAt;
    }

    /**
     * テストのカテゴリの一覧を、指定した順で取得し、商品 ID を返す。
     *
     * @param sort 並び順
     * @return 商品 ID（一覧の並び）
     */
    private List<UUID> listIds(StoreProductSort sort) {
        return api.listProducts(query(null, null, sort), FIRST_PAGE).items().stream().map(StoreProductSummary::id).toList();
    }
}
