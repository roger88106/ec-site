package com.ecsite.module.catalog.api;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.api.OrderableVariant.AxisValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * カート・注文向けの商品カタログの API の統合テスト。<br />
 * 商品は B 側の API で作る。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class OrderCatalogApiTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired
    OrderCatalogApi api;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    private CatalogTestData data;

    private UUID standard;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        data = new CatalogTestData(mockMvc, clock);
        standard = data.taxCategory("標準", "10");
    }

    @Test
    @DisplayName("AC-92 品番・商品名・軸の名前と値・税抜価格・税区分を返す。表示されるか、販売停止かも返す")
    void returnsVariantInformation() throws Exception {
        var product = data.product(standard, "T シャツ", 1800, "色", "サイズ")
                .variant("白", "M").variant("黒", "L").publish().suspend(1);

        var variants = api.findVariants(product.variantIds);

        assertThat(variants).hasSize(2);
        assertThat(variants.get(product.variantIds.getFirst())).satisfies(variant -> {
            assertThat(variant.variantId()).isEqualTo(product.variantIds.getFirst());
            assertThat(variant.productId()).isEqualTo(product.id);
            assertThat(variant.productCode()).isEqualTo(product.productCode);
            assertThat(variant.variantCode()).isEqualTo(product.variantCodes.getFirst());
            assertThat(variant.productName()).isEqualTo("T シャツ");
            assertThat(variant.axisValues()).containsExactly(new AxisValue("色", "白"), new AxisValue("サイズ", "M"));
            assertThat(variant.priceExclTax()).isEqualTo(Money.of(1800));
            assertThat(variant.taxCategoryId()).isEqualTo(standard);
            assertThat(variant.imageUrl()).isNull();
            assertThat(variant.visible()).isTrue();
            assertThat(variant.salesSuspended()).isFalse();
        });
        assertThat(variants.get(product.variantIds.get(1)).salesSuspended()).isTrue();
    }

    @Test
    @DisplayName("AC-80 C 側に表示されない商品のバリアントは、表示されないと返す。軸のない商品の軸の値は空")
    void returnsInvisibleVariants() throws Exception {
        var unpublished = data.product(standard, "非公開", 1000);
        var hidden = data.product(standard, "非公開にした", 1000).publish().unpublish();

        var variants = api.findVariants(List.of(unpublished.variantIds.getFirst(), hidden.variantIds.getFirst()));

        assertThat(variants.values()).extracting(OrderableVariant::visible).containsOnly(false);
        assertThat(variants.get(unpublished.variantIds.getFirst()).axisValues()).isEmpty();
    }

    @Test
    @DisplayName("存在しないバリアントは含めない")
    void excludesUnknownVariants() throws Exception {
        var product = data.product(standard, "商品", 1000).publish();
        var unknown = UUID.randomUUID();

        var variants = api.findVariants(List.of(product.variantIds.getFirst(), unknown));

        assertThat(variants).containsOnlyKeys(product.variantIds.getFirst());
        assertThat(api.findVariants(List.of())).isEmpty();
    }

    @Test
    @DisplayName("AC-38 画像はバリアントの画像、なければ商品の代表画像の URL")
    void returnsVariantImageOrRepresentative() throws Exception {
        var product = data.product(standard, "画像あり", 1000, "色")
                .variant("白").variant("黒").image().image().variantImage(1, 1).publish();

        var variants = api.findVariants(product.variantIds);

        assertThat(variants.get(product.variantIds.getFirst()).imageUrl()).isEqualTo(product.imageUrls.getFirst());
        assertThat(variants.get(product.variantIds.get(1)).imageUrl()).isEqualTo(product.imageUrls.get(1));
    }
}
