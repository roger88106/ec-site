package com.ecsite.module.catalog.api;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.api.model.StoreProductSummary;
import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCaches;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * キーワード検索の統合テスト
 *
 * <p>商品は B 側の API で作って公開する。公開するごとに時計を 1 分進める（並びは新着順で固定）。テストごとにロールバックする。
 *
 * <p>商品名と説明には、テストごとの識別子を入れ、検索を自分の商品に絞る。
 */
@IntegrationTest
@Transactional
class StoreProductSearchTest {

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
     * 商品の税抜価格
     */
    private static final long PRICE_EXCL_TAX = 1000;

    /**
     * 税抜価格に税率 10% をかけた税込価格
     */
    private static final Money PRICE_INCL_TAX = Money.of(1100);

    /**
     * 1 ページ目に全てが入る指定
     */
    private static final PageRequest FIRST_PAGE = PageRequest.of(1, 20);

    // ============================== Bean ==============================

    @Autowired
    StoreCatalogApi api;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationContext context;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * テストごとの識別子
     *
     * <p>商品名に入れて、検索を自分の商品に絞る。他の語と重ならない英小文字と数字。
     */
    private String mark;

    @BeforeEach
    void setUp() throws Exception {
        mark = "mk" + UUID.randomUUID().toString().replace("-", "");
        catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("標準", STANDARD_RATE);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("キーワードの一致")
    class Matching {

        @Test
        @DisplayName("AC-58 商品名にキーワードを含む商品が返る")
        void matchesName() throws Exception {
            // 準備：商品名にキーワードを含む商品と、含まない商品
            var byName = published(mark + "綿のシャツ", "");
            published("パンツ", "ストレッチ素材");

            // 操作と確認：商品名にキーワードを含む商品だけが返る
            assertThat(searchIds(mark + "綿")).containsExactly(byName.id);
        }

        @Test
        @DisplayName("AC-58 説明にキーワードを含む商品が返る")
        void matchesDescription() throws Exception {
            // 準備：説明にキーワードを含む商品と、含まない商品
            var byDescription = published("T シャツ", "オーガニック " + mark + "綿を使用");
            published("パンツ", "ストレッチ素材");

            // 操作と確認：説明にキーワードを含む商品だけが返る
            assertThat(searchIds(mark + "綿")).containsExactly(byDescription.id);
        }

        @Test
        @DisplayName("商品名の英字の大文字・小文字は区別しない")
        void ignoresCaseInName() throws Exception {
            // 準備：商品名の英字が大文字を含む商品
            var product = published("Organic " + mark + "Cotton T シャツ", "");

            // 操作と確認：小文字のキーワードで見つかる
            assertThat(searchIds(mark + "cotton")).containsExactly(product.id);
        }

        @Test
        @DisplayName("説明の英字の大文字・小文字は区別しない")
        void ignoresCaseInDescription() throws Exception {
            // 準備：説明の英字が大文字の商品
            var product = published("T シャツ", "オーガニック " + mark + "COTTON を使用");

            // 操作と確認：小文字のキーワードで見つかる
            assertThat(searchIds(mark + "cotton")).containsExactly(product.id);
        }

        @Test
        @DisplayName("キーワード中の % は、ただの文字として扱う")
        void escapesPercent() throws Exception {
            // 準備：キーワードの % の位置に % がある商品と、ない商品
            var percent = published("綿 " + mark + "100% のシャツ", "");
            published("綿 " + mark + "100 のシャツ", "");

            // 操作と確認：% を含む商品だけが返る
            assertThat(searchIds(mark + "100%")).containsExactly(percent.id);
        }

        @Test
        @DisplayName("キーワード中の _ は、ただの文字として扱う")
        void escapesUnderscore() throws Exception {
            // 準備：識別子の後に _ がなく、別の 1 文字が続く商品
            published("綿 " + mark + "100 のシャツ", "");

            // 操作と確認：_ を任意の 1 文字とみなさず、見つからない
            assertThat(searchIds(mark + "_")).isEmpty();
        }
    }

    @Nested
    @DisplayName("結果の並びと内容")
    class Results {

        @Test
        @DisplayName("結果は新着順に並ぶ")
        void sortsByNewest() throws Exception {
            // 準備：キーワードを含む商品を 2 つ、順に公開する
            var older = published(mark + "シャツ 1", "");
            var newer = published(mark + "シャツ 2", "");

            // 操作と確認：後に公開した商品が先
            assertThat(searchIds(mark + "シャツ")).containsExactly(newer.id, older.id);
        }

        @Test
        @DisplayName("結果の各商品は、税込価格を持つ")
        void returnsPriceInclTax() throws Exception {
            // 準備：税率 10% の商品
            published(mark + "シャツ", "");

            // 操作：検索する
            var page = api.searchProducts(mark + "シャツ", FIRST_PAGE);

            // 確認：税抜価格に 10% をかけた税込価格
            assertThat(page.items().getFirst().priceInclTax()).isEqualTo(PRICE_INCL_TAX);
        }
    }

    @Nested
    @DisplayName("C 側に表示されない商品")
    class InvisibleProducts {

        @Test
        @DisplayName("AC-59 非公開の商品は検索されない")
        void excludesUnpublished() throws Exception {
            // 準備：表示される商品と、公開した後に非公開にした商品
            var visible = published(mark + "綿のシャツ", "");
            published(mark + "綿のパンツ", "").unpublish();

            // 操作と確認：表示される商品だけが返る
            assertOnlyVisibleFound(visible.id);
        }

        @Test
        @DisplayName("AC-59 販売を終了した商品は検索されない")
        void excludesDiscontinued() throws Exception {
            // 準備：表示される商品と、公開した後に販売を終了した商品
            var visible = published(mark + "綿のシャツ", "");
            published(mark + "綿の靴下", "").discontinue();

            // 操作と確認：表示される商品だけが返る
            assertOnlyVisibleFound(visible.id);
        }

        @Test
        @DisplayName("AC-59 公開期間の外の商品は検索されない")
        void excludesOutOfPeriod() throws Exception {
            // 準備：表示される商品と、公開開始日時が明日の商品
            var visible = published(mark + "綿のシャツ", "");
            catalog.product(taxCategoryId, mark + "綿の帽子", "", PRICE_EXCL_TAX, List.of())
                    .period(NOW.plus(Duration.ofDays(1)), null).publish();

            // 操作と確認：表示される商品だけが返る
            assertOnlyVisibleFound(visible.id);
        }

        @Test
        @DisplayName("有効な税率のない税区分の商品は検索されない")
        void excludesProductWithoutTaxRate() throws Exception {
            // 準備：表示される商品と、税区分に今は有効な税率がない商品。後者は、1 時間後に税率が始まる税区分で
            // その時刻に公開し、時計を税率の開始の 1 秒前に戻す（最後の税率は無期限なので、税率がないのは開始の前だけ）
            var visible = published(mark + "綿のシャツ", "");
            var startAt = clock.instant().plus(Duration.ofHours(1));
            var notYetEffective = catalog.taxCategory("将来", STANDARD_RATE, startAt);
            clock.setInstant(startAt);
            catalog.product(notYetEffective, mark + "綿の手袋", "", PRICE_EXCL_TAX, List.of()).publish();
            clock.setInstant(startAt.minusSeconds(1));
            TaxRateScheduleCaches.evict(context);

            // 操作と確認：表示される商品だけが返る
            assertOnlyVisibleFound(visible.id);
        }
    }

    @Nested
    @DisplayName("ページ")
    class Paging {

        @Test
        @DisplayName("AC-61 ページ単位で取得できる")
        void pages() throws Exception {
            // 準備：キーワードを含む商品を 5 つ、順に公開する
            var products = new ArrayList<CatalogTestData.TestProduct>();
            for (int i = 0; i < 5; i++) {
                products.add(published(mark + "シャツ " + i, ""));
            }

            // 操作：1 ページ 2 件の 2 ページ目を取得する
            var page = api.searchProducts(mark + "シャツ", PageRequest.of(2, 2));

            // 確認：新着順の 3、4 件目と、全体の件数・ページ数
            assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(products.get(2).id, products.get(1).id);
            assertThat(page.totalItems()).isEqualTo(5);
            assertThat(page.totalPages()).isEqualTo(3);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 識別子と「綿」のキーワードで検索し、表示される商品だけが返ることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>結果の商品が、表示される商品だけ</li>
     *   <li>全体の件数が 1</li>
     * </ul>
     *
     * @param visibleId 表示される商品の ID（実行ごとに作るので、定数にできない）
     */
    private void assertOnlyVisibleFound(UUID visibleId) {
        var page = api.searchProducts(mark + "綿", FIRST_PAGE);
        assertThat(page.items()).extracting(StoreProductSummary::id).containsExactly(visibleId);
        assertThat(page.totalItems()).isEqualTo(1);
    }

    // ============================== 準備の補助 ==============================

    /**
     * バリアントを 1 つ持つ商品を、カテゴリなしで登録して公開する。
     *
     * @param name        商品名
     * @param description 説明
     * @return 商品
     */
    private CatalogTestData.TestProduct published(String name, String description) throws Exception {
        return catalog.product(taxCategoryId, name, description, PRICE_EXCL_TAX, List.of()).publish();
    }

    // ============================== 操作の補助 ==============================

    /**
     * キーワードで検索し、1 ページ目の商品 ID を返す。
     *
     * @param keyword キーワード
     * @return 商品 ID（結果の並び）
     */
    private List<UUID> searchIds(String keyword) {
        return api.searchProducts(keyword, FIRST_PAGE).items().stream().map(StoreProductSummary::id).toList();
    }
}
