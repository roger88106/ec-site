package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.PublicationStatus;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 側に表示される条件の統合テスト
 *
 * <p>条件はドメイン（{@link Product#isVisibleAt}）と SQL（{@link ProductVisibility}）の 2 か所にある。
 * そこで、公開状態と公開期間の全ての組み合わせで、両者が一致することを確認する。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class ProductVisibilityTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 1 日
     */
    private static final Duration ONE_DAY = Duration.ofDays(1);

    // ============================== Bean ==============================

    @Autowired
    ProductRepository repository;

    @Autowired
    ProductFactory factory;

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("ドメインと SQL の一致")
    class DomainAndSql {

        @Test
        @DisplayName("BR-2 公開状態と公開期間の全ての組み合わせで、ドメインの判定と SQL の条件が一致する")
        void domainAndSqlAgree() {
            // 準備：公開状態ごとに、開始・終了（なし、過去、現在ちょうど、未来）の組み合わせの商品を保存する。開始が終了以後の組み合わせは除く
            var past = NOW.minus(ONE_DAY);
            var future = NOW.plus(ONE_DAY);
            Instant[] points = {null, past, NOW, future};
            var products = new ArrayList<Product>();
            int sequence = 0;
            for (var status : PublicationStatus.values()) {
                for (var start : points) {
                    for (var end : points) {
                        if (start != null && end != null && !start.isBefore(end)) {
                            continue;
                        }
                        var product = restored(++sequence, status, PublicationPeriod.of(start, end));
                        repository.add(product);
                        products.add(product);
                    }
                }
            }

            // 操作：ドメインの判定と SQL の条件で、それぞれ表示される商品を求める
            var visibleByDomain = products.stream()
                    .filter(product -> product.isVisibleAt(NOW))
                    .map(Product::id)
                    .collect(Collectors.toSet());
            Set<UUID> visibleBySql = dsl.select(PRODUCT.ID)
                    .from(PRODUCT)
                    .where(PRODUCT.ID.in(products.stream().map(Product::id).toList()))
                    .and(ProductVisibility.visibleAt(NOW))
                    .fetchSet(PRODUCT.ID);

            // 確認（前提）：組み合わせに、表示されるものとされないものの両方が含まれている
            assertThat(visibleByDomain).isNotEmpty().hasSizeLessThan(products.size());

            // 確認：両者が一致する
            assertThat(visibleBySql).isEqualTo(visibleByDomain);
        }
    }

    @Nested
    @DisplayName("公開期間の境界")
    class Boundaries {

        @Test
        @DisplayName("BR-2 公開開始日時ちょうどから表示される")
        void visibleFromPublishStart() {
            // 準備：公開開始日時が現在ちょうどの、公開中の商品
            var startsNow = restored(1, PublicationStatus.PUBLISHED, PublicationPeriod.of(NOW, null));
            repository.add(startsNow);

            // 操作と確認：SQL の条件で表示される
            assertThat(visibleIds(startsNow.id())).containsExactly(startsNow.id());
        }

        @Test
        @DisplayName("BR-2 公開終了日時ちょうどで表示されなくなる")
        void invisibleFromPublishEnd() {
            // 準備：公開終了日時が現在ちょうどの、公開中の商品
            var endsNow = restored(2, PublicationStatus.PUBLISHED, PublicationPeriod.of(null, NOW));
            repository.add(endsNow);

            // 操作と確認：SQL の条件で表示されない
            assertThat(visibleIds(endsNow.id())).isEmpty();
        }
    }

    // ============================== 操作の補助 ==============================

    /**
     * SQL の条件で、指定した商品が現在時刻に表示されるなら、その ID を返す。
     *
     * @param productId 商品 ID
     * @return 表示されるなら商品 ID の 1 件、表示されないなら空
     */
    private List<UUID> visibleIds(UUID productId) {
        return dsl.select(PRODUCT.ID).from(PRODUCT)
                .where(PRODUCT.ID.eq(productId))
                .and(ProductVisibility.visibleAt(NOW))
                .fetch(PRODUCT.ID);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 状態と公開期間を指定した商品を作る（保存はしない）。
     *
     * <p>バリアントなしで状態を決めるため、スナップショットから作る。公開中の商品は、初めて表示した日時を持たせる（公開の商品の整合のため）。
     *
     * @param sequence 品番と商品名に付ける連番
     * @param status   公開状態
     * @param period   公開期間
     * @return 商品
     */
    private Product restored(int sequence, PublicationStatus status, PublicationPeriod period) {
        var created = factory.create(ProductCode.of("VIS-" + sequence), "商品 " + sequence, "",
                UUID.randomUUID(), PriceExclTax.of(1000), Set.of(), List.of());
        var snapshot = created.toSnapshot();
        return factory.restore(new Product.Snapshot(snapshot.id(), snapshot.productCode(), snapshot.name(),
                snapshot.description(), snapshot.taxCategoryId(), snapshot.priceExclTax(), snapshot.categoryIds(),
                snapshot.axes(), status, status == PublicationStatus.PUBLISHED ? NOW : null, period, snapshot.images(),
                snapshot.variants(), snapshot.version()));
    }
}
