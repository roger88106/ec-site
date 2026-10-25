package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.ProductImage;
import com.ecsite.module.catalog.domain.product.PublicationStatus;
import com.ecsite.module.catalog.domain.product.Variant;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品のリポジトリの統合テスト。<br />
 * バリアントを含む保存・再構築・差分更新、バージョン不一致、イベントの発行を確認する。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class JooqProductRepositoryTest {

    /**
     * 現在：2026-10-08 00:00（日本時間）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 税区分 ID（税モジュールの ID なので、存在しなくてよい）
     */
    private static final UUID TAX_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    ProductRepository repository;

    @Autowired
    ProductFactory factory;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    CategoryFactory categoryFactory;

    @Autowired
    ApplicationEvents events;

    private UUID clothing;

    private UUID food;

    @BeforeEach
    void createCategories() {
        var clothingCategory = categoryFactory.create(null, "衣類", 1);
        var foodCategory = categoryFactory.create(null, "食品", 2);
        categoryRepository.add(clothingCategory);
        categoryRepository.add(foodCategory);
        clothing = clothingCategory.id();
        food = foodCategory.id();
    }

    @Test
    @DisplayName("軸・所属カテゴリ・バリアントを含めて保存し、再構築できる")
    void addsAndRestores() {
        var product = newProduct("ABC-001", "色", "サイズ");
        product.addVariant(VariantCode.of("ABC-001-WH-M"), List.of("白", "M"), NOW);
        product.addVariant(VariantCode.of("ABC-001-BK-M"), List.of("黒", "M"), NOW);

        repository.add(product);

        var restored = repository.findById(product.id()).orElseThrow();
        assertThat(restored.toSnapshot()).isEqualTo(product.toSnapshot());
    }

    @Test
    @DisplayName("存在しない商品は見つからない")
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("差分で更新する：バリアントの追加・削除・並び替え、項目と所属カテゴリの変更。保存するとバージョンが上がる")
    void savesDifferences() {
        var product = newProduct("ABC-001", "色");
        var white = product.addVariant(VariantCode.of("V-WH"), List.of("白"), NOW);
        var black = product.addVariant(VariantCode.of("V-BK"), List.of("黒"), NOW);
        repository.add(product);

        var loaded = repository.findById(product.id()).orElseThrow();
        loaded.update("ポロシャツ", "綿 50%", TAX_CATEGORY_ID, PriceExclTax.of(2000), Set.of(food), NOW);
        loaded.deleteVariant(white, NOW);
        var red = loaded.addVariant(VariantCode.of("V-RD"), List.of("赤"), NOW);
        loaded.reorderVariants(List.of(red, black));
        loaded.publish(NOW);
        repository.save(loaded);

        var restored = repository.findById(product.id()).orElseThrow();
        assertThat(restored.name()).isEqualTo("ポロシャツ");
        assertThat(restored.priceExclTax()).isEqualTo(PriceExclTax.of(2000));
        assertThat(restored.categoryIds()).containsExactly(food);
        assertThat(restored.variants()).extracting(Variant::id).containsExactly(red, black);
        assertThat(restored.variants().getFirst().axisValues()).containsExactly("赤");
        assertThat(restored.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(restored.firstShownAt()).isEqualTo(NOW);
        assertThat(restored.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("取得後に他で更新されていたら、バージョン不一致で保存できない")
    void detectsVersionMismatch() {
        var product = newProduct("ABC-001");
        repository.add(product);
        var mine = repository.findById(product.id()).orElseThrow();
        var theirs = repository.findById(product.id()).orElseThrow();

        theirs.addVariant(VariantCode.of("V1"), List.of(), NOW);
        repository.save(theirs);

        mine.update("ポロシャツ", "", TAX_CATEGORY_ID, PriceExclTax.of(2000), Set.of(), NOW);
        assertBusinessError(() -> repository.save(mine), CommonErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("AC-14 商品品番が重複すれば、一意制約の違反が業務のエラーになる")
    void rejectsDuplicatedProductCode() {
        repository.add(newProduct("ABC-001"));

        assertBusinessError(() -> repository.add(newProduct("ABC-001")), ProductErrorCode.PRODUCT_CODE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("AC-21 別の商品とバリアント品番が重複すれば、一意制約の違反が業務のエラーになる")
    void rejectsDuplicatedVariantCodeAcrossProducts() {
        var first = newProduct("ABC-001");
        first.addVariant(VariantCode.of("V1"), List.of(), NOW);
        repository.add(first);

        var second = newProduct("ABC-002");
        repository.add(second);
        second.addVariant(VariantCode.of("V1"), List.of(), NOW);

        assertBusinessError(() -> repository.save(second), ProductErrorCode.VARIANT_CODE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("存在しないカテゴリへの所属は、外部キーの違反が業務のエラーになる")
    void rejectsUnknownCategory() {
        var product = factory.create(ProductCode.of("ABC-001"), "T シャツ", "", TAX_CATEGORY_ID, PriceExclTax.of(1000),
                Set.of(UUID.randomUUID()), List.of());

        assertBusinessError(() -> repository.add(product), ProductErrorCode.INVALID_CATEGORY_ID);
    }

    @Test
    @DisplayName("削除すると、バリアントなど商品の一部も消える")
    void deletesWithParts() {
        var product = newProduct("ABC-001", "色");
        product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
        repository.add(product);

        product.delete(NOW);
        repository.delete(product);

        assertThat(repository.findById(product.id())).isEmpty();
        // 同じバリアント品番を、別の商品で使える
        var other = newProduct("ABC-002", "色");
        other.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
        repository.add(other);
    }

    @Test
    @DisplayName("画像を差分で保存する：追加・並び替え・削除、バリアントの画像の指定。削除した画像の指定は外れる")
    void savesImages() {
        var product = newProduct("ABC-001", "色");
        var variantId = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var firstAsset = UUID.randomUUID();
        product.addImage(first, firstAsset);
        product.addImage(second, UUID.randomUUID());
        product.setVariantImage(variantId, second);
        repository.add(product);

        var added = repository.findById(product.id()).orElseThrow();
        assertThat(added.toSnapshot()).isEqualTo(product.toSnapshot());

        var third = UUID.randomUUID();
        var thirdAsset = UUID.randomUUID();
        added.addImage(third, thirdAsset);
        added.reorderImages(List.of(third, second, first));
        added.deleteImage(second);
        repository.save(added);

        var restored = repository.findById(product.id()).orElseThrow();
        assertThat(restored.images()).extracting(ProductImage::id).containsExactly(third, first);
        assertThat(restored.images()).extracting(ProductImage::assetId).containsExactly(thirdAsset, firstAsset);
        assertThat(restored.variants().getFirst().imageId()).isNull();
    }

    @Test
    @DisplayName("保存・削除の後に、集約が登録したイベントを発行する")
    void publishesEvents() {
        var product = newProduct("ABC-001");
        var variantId = product.addVariant(VariantCode.of("V1"), List.of(), NOW);
        repository.add(product);

        product.delete(NOW);
        repository.delete(product);

        assertThat(events.stream(VariantRegistered.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.productId()).isEqualTo(product.id());
                    assertThat(event.variantId()).isEqualTo(variantId);
                });
        assertThat(events.stream(ProductDeleted.class))
                .singleElement()
                .satisfies(event -> assertThat(event.productId()).isEqualTo(product.id()));
    }

    // ============================== 補助 ==============================

    /**
     * 所属カテゴリが 1 つの、非公開の商品を作る（保存はしない）
     */
    private Product newProduct(String code, String... axes) {
        return factory.create(ProductCode.of(code), "T シャツ", "綿 100%", TAX_CATEGORY_ID, PriceExclTax.of(1000),
                Set.of(clothing), List.of(axes));
    }
}
