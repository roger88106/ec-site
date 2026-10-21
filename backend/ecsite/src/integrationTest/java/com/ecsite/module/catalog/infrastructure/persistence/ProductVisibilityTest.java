package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.PublicationStatus;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 側に表示される条件の統合テスト（T601、BR-2）。<br />
 * 条件はドメイン（{@link Product#isVisibleAt}）と SQL（{@link ProductVisibility}）の 2 か所にあるので、
 * 公開状態と公開期間のすべての組み合わせで、両者が一致することを確認する。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class ProductVisibilityTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 1 日
     */
    private static final Duration ONE_DAY = Duration.ofDays(1);

    @Autowired
    ProductRepository repository;

    @Autowired
    ProductFactory factory;

    @Autowired
    DSLContext dsl;

    @Test
    @DisplayName("BR-2 公開状態と公開期間のすべての組み合わせで、ドメインの判定と SQL の条件が一致する")
    void domainAndSqlAgree() {
        var past = NOW.minus(ONE_DAY);
        var future = NOW.plus(ONE_DAY);
        // 開始・終了それぞれ：なし、過去、現在ちょうど、未来
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

        var visibleByDomain = products.stream()
                .filter(product -> product.isVisibleAt(NOW))
                .map(Product::id)
                .collect(Collectors.toSet());
        Set<UUID> visibleBySql = dsl.select(PRODUCT.ID)
                .from(PRODUCT)
                .where(PRODUCT.ID.in(products.stream().map(Product::id).toList()))
                .and(ProductVisibility.visibleAt(NOW))
                .fetchSet(PRODUCT.ID);

        assertThat(visibleBySql).isEqualTo(visibleByDomain);
        // 組み合わせに、表示されるものとされないものの両方が含まれている
        assertThat(visibleByDomain).isNotEmpty().hasSizeLessThan(products.size());
    }

    @Test
    @DisplayName("BR-2 公開開始日時ちょうどから表示され、公開終了日時ちょうどで表示されなくなる")
    void boundaries() {
        var startsNow = restored(1, PublicationStatus.PUBLISHED, PublicationPeriod.of(NOW, null));
        var endsNow = restored(2, PublicationStatus.PUBLISHED, PublicationPeriod.of(null, NOW));
        Arrays.asList(startsNow, endsNow).forEach(repository::add);

        assertThat(dsl.select(PRODUCT.ID).from(PRODUCT)
                .where(PRODUCT.ID.in(startsNow.id(), endsNow.id()))
                .and(ProductVisibility.visibleAt(NOW))
                .fetch(PRODUCT.ID))
                .containsExactly(startsNow.id());
    }

    // ============================== 補助 ==============================

    /**
     * 状態と公開期間を指定した商品（バリアントなしで状態を決めるため、スナップショットから作る）
     */
    private Product restored(int sequence, PublicationStatus status, PublicationPeriod period) {
        var created = factory.create(ProductCode.of("VIS-" + sequence), "商品 " + sequence, "",
                UUID.randomUUID(), PriceExclTax.of(1000), Set.of(), List.of());
        var snapshot = created.toSnapshot();
        return factory.restore(new Product.Snapshot(snapshot.id(), snapshot.productCode(), snapshot.name(),
                snapshot.description(), snapshot.taxCategoryId(), snapshot.priceExclTax(), snapshot.categoryIds(),
                snapshot.axes(), status, null, period, snapshot.images(), snapshot.variants(), snapshot.version()));
    }
}
