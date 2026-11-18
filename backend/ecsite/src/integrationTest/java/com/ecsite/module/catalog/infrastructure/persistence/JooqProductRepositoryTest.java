package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.ProductImage;
import com.ecsite.module.catalog.domain.product.Variant;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.PublicationStatus;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品のリポジトリの統合テスト
 *
 * <p>バリアントを含む保存・再構築・差分更新、バージョン不一致、イベントの発行を確認する。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class JooqProductRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 税区分 ID
     *
     * <p>税モジュールの ID なので、存在しなくてよい。
     */
    private static final UUID TAX_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 変更後の商品名
     */
    private static final String UPDATED_NAME = "ポロシャツ";

    /**
     * 変更後の説明
     */
    private static final String UPDATED_DESCRIPTION = "綿 50%";

    /**
     * 変更後の税抜価格
     */
    private static final PriceExclTax UPDATED_PRICE = PriceExclTax.of(2000);

    /**
     * 商品名の上限（文字）
     */
    private static final int NAME_MAX_LENGTH = 200;

    /**
     * 説明の上限（文字）
     */
    private static final int DESCRIPTION_MAX_LENGTH = 10_000;

    /**
     * 軸の名前の上限（文字）
     */
    private static final int AXIS_NAME_MAX_LENGTH = 30;

    /**
     * 軸の値の上限（文字）
     */
    private static final int AXIS_VALUE_MAX_LENGTH = 50;

    /**
     * 上限の 32 文字の商品品番
     */
    private static final String MAX_PRODUCT_CODE = "P".repeat(32);

    /**
     * 上限の 32 文字のバリアント品番
     */
    private static final String MAX_VARIANT_CODE = "V".repeat(32);

    /**
     * 商品品番
     */
    private static final String PRODUCT_CODE = "ABC-001";

    /**
     * 商品名
     */
    private static final String NAME = "T シャツ";

    /**
     * 説明
     */
    private static final String DESCRIPTION = "綿 100%";

    /**
     * 税抜価格
     */
    private static final PriceExclTax PRICE = PriceExclTax.of(1000);

    /**
     * 1 枚目の画像のアセット ID
     *
     * <p>メディアのモジュールの ID なので、存在しなくてよい。
     */
    private static final UUID FIRST_ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000005001");

    /**
     * 2 枚目の画像のアセット ID
     */
    private static final UUID SECOND_ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000005002");

    // ============================== Bean ==============================

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

    /**
     * カテゴリ「衣類」の ID
     */
    private UUID clothing;

    /**
     * カテゴリ「食品」の ID
     */
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

    // ============================== テスト ==============================

    @Nested
    @DisplayName("保存と再構築")
    class AddAndRestore {

        @Test
        @DisplayName("軸・所属カテゴリ・バリアントを含めて保存し、再構築できる")
        void addsAndRestores() {
            // 準備：軸 2 つとバリアント 2 つを持つ商品
            var product = newProduct(PRODUCT_CODE, "色", "サイズ");
            var white = product.addVariant(VariantCode.of("ABC-001-WH-M"), List.of("白", "M"), NOW);
            var black = product.addVariant(VariantCode.of("ABC-001-BK-M"), List.of("黒", "M"), NOW);

            // 操作：保存する
            repository.add(product);

            // 確認：再構築した商品が、作った値を全て持つ
            assertRestoredWithVariants(repository.findById(product.id()).orElseThrow(), product.id(), white, black);
        }

        @Test
        @DisplayName("存在しない商品は見つからない")
        void returnsEmptyForUnknownId() {
            // 操作と確認：空
            assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("差分の更新")
    class SaveDifferences {

        @Test
        @DisplayName("基本情報の変更を保存できる")
        void savesBasicInformation() {
            // 準備：保存した商品を読み、商品名・説明・税抜価格を変える
            var product = newProduct("ABC-001");
            repository.add(product);
            var loaded = repository.findById(product.id()).orElseThrow();
            loaded.update(UPDATED_NAME, UPDATED_DESCRIPTION, TAX_CATEGORY_ID, UPDATED_PRICE, Set.of(clothing), NOW);

            // 操作：保存する
            repository.save(loaded);

            // 確認：変えた値で再構築される
            var restored = repository.findById(product.id()).orElseThrow();
            assertThat(restored.name()).isEqualTo(UPDATED_NAME);
            assertThat(restored.description()).isEqualTo(UPDATED_DESCRIPTION);
            assertThat(restored.priceExclTax()).isEqualTo(UPDATED_PRICE);
        }

        @Test
        @DisplayName("所属カテゴリの変更を保存できる")
        void savesCategories() {
            // 準備：「衣類」に所属する商品を読み、所属を「食品」に変える
            var product = newProduct("ABC-001");
            repository.add(product);
            var loaded = repository.findById(product.id()).orElseThrow();
            loaded.update(UPDATED_NAME, UPDATED_DESCRIPTION, TAX_CATEGORY_ID, UPDATED_PRICE, Set.of(food), NOW);

            // 操作：保存する
            repository.save(loaded);

            // 確認：所属は「食品」だけ
            assertThat(repository.findById(product.id()).orElseThrow().categoryIds()).containsExactly(food);
        }

        @Test
        @DisplayName("バリアントの追加・削除・並び替えを、まとめて差分で保存できる")
        void savesVariantChanges() {
            // 準備：白・黒のバリアントを持つ商品を読み、白を削除し、赤を加え、赤・黒の順に並べ替える
            var product = newProduct("ABC-001", "色");
            var white = product.addVariant(VariantCode.of("V-WH"), List.of("白"), NOW);
            var black = product.addVariant(VariantCode.of("V-BK"), List.of("黒"), NOW);
            repository.add(product);
            var loaded = repository.findById(product.id()).orElseThrow();
            loaded.deleteVariant(white, NOW);
            var red = loaded.addVariant(VariantCode.of("V-RD"), List.of("赤"), NOW);
            loaded.reorderVariants(List.of(red, black));

            // 操作：保存する
            repository.save(loaded);

            // 確認：赤・黒の順に再構築され、加えた赤の軸の値も保存されている
            var restored = repository.findById(product.id()).orElseThrow();
            assertThat(restored.variants()).extracting(Variant::id).containsExactly(red, black);
            assertThat(restored.variants().getFirst().axisValues()).containsExactly("赤");
        }

        @Test
        @DisplayName("公開の状態と初回表示日時を保存できる")
        void savesPublication() {
            // 準備：バリアントを持つ非公開の商品を読み、公開する
            var product = newProduct("ABC-001");
            product.addVariant(VariantCode.of("V1"), List.of(), NOW);
            repository.add(product);
            var loaded = repository.findById(product.id()).orElseThrow();
            loaded.publish(NOW);

            // 操作：保存する
            repository.save(loaded);

            // 確認：公開で、初回表示日時は公開した時刻
            var restored = repository.findById(product.id()).orElseThrow();
            assertThat(restored.status()).isEqualTo(PublicationStatus.PUBLISHED);
            assertThat(restored.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("基本情報とバリアントと公開を変えても、1 回の保存でバージョンが 1 だけ上がる")
        void incrementsVersion() {
            // 準備：保存した商品（バージョン 0）を読み、基本情報を変え、バリアントを入れ替えて、公開する
            var product = newProduct("ABC-001", "色");
            var white = product.addVariant(VariantCode.of("V-WH"), List.of("白"), NOW);
            repository.add(product);
            var loaded = repository.findById(product.id()).orElseThrow();
            loaded.update(UPDATED_NAME, UPDATED_DESCRIPTION, TAX_CATEGORY_ID, UPDATED_PRICE, Set.of(food), NOW);
            loaded.addVariant(VariantCode.of("V-RD"), List.of("赤"), NOW);
            loaded.deleteVariant(white, NOW);
            loaded.publish(NOW);

            // 操作：保存する
            repository.save(loaded);

            // 確認：バージョンが 1
            assertThat(repository.findById(product.id()).orElseThrow().version()).isEqualTo(1);
        }

        @Test
        @DisplayName("取得後に他で更新されていたら、バージョン不一致で保存できない")
        void detectsVersionMismatch() {
            // 準備：同じ商品を 2 回読み、片方を先に保存する
            var product = newProduct("ABC-001");
            repository.add(product);
            var mine = repository.findById(product.id()).orElseThrow();
            var theirs = repository.findById(product.id()).orElseThrow();
            theirs.addVariant(VariantCode.of("V1"), List.of(), NOW);
            repository.save(theirs);
            mine.update(UPDATED_NAME, "", TAX_CATEGORY_ID, UPDATED_PRICE, Set.of(), NOW);

            // 操作と確認：古いバージョンの保存は、業務のエラー CONCURRENT_MODIFICATION で拒否される
            assertBusinessError(() -> repository.save(mine), CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    @Nested
    @DisplayName("制約の違反")
    class ConstraintViolations {

        @Test
        @DisplayName("AC-14 商品品番が重複すれば、一意制約の違反が業務のエラーになる")
        void rejectsDuplicatedProductCode() {
            // 準備：商品品番 ABC-001 の商品を保存する
            repository.add(newProduct("ABC-001"));

            // 操作と確認：同じ品番の商品の保存は、業務のエラー PRODUCT_CODE_ALREADY_EXISTS で拒否される
            assertBusinessError(() -> repository.add(newProduct("ABC-001")), ProductErrorCode.PRODUCT_CODE_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("AC-21 別の商品とバリアント品番が重複すれば、一意制約の違反が業務のエラーになる")
        void rejectsDuplicatedVariantCodeAcrossProducts() {
            // 準備：バリアント品番 V1 を持つ商品と、別の商品に同じ品番のバリアントを加えたもの
            var first = newProduct("ABC-001");
            first.addVariant(VariantCode.of("V1"), List.of(), NOW);
            repository.add(first);
            var second = newProduct("ABC-002");
            repository.add(second);
            second.addVariant(VariantCode.of("V1"), List.of(), NOW);

            // 操作と確認：業務のエラー VARIANT_CODE_ALREADY_EXISTS で拒否される
            assertBusinessError(() -> repository.save(second), ProductErrorCode.VARIANT_CODE_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("存在しないカテゴリへの所属は、外部キーの違反が業務のエラーになる")
        void rejectsUnknownCategory() {
            // 準備：存在しないカテゴリに所属する商品
            var product = factory.create(ProductCode.of("ABC-001"), "T シャツ", "", TAX_CATEGORY_ID, PriceExclTax.of(1000),
                    Set.of(UUID.randomUUID()), List.of());

            // 操作と確認：業務のエラー INVALID_CATEGORY_ID で拒否される
            assertBusinessError(() -> repository.add(product), ProductErrorCode.INVALID_CATEGORY_ID);
        }
    }

    @Nested
    @DisplayName("削除")
    class Delete {

        @Test
        @DisplayName("削除した商品は見つからない")
        void deletesProduct() {
            // 準備：バリアントを持つ商品を保存し、削除の操作をする
            var product = newProduct("ABC-001", "色");
            product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            repository.add(product);
            product.delete(NOW);

            // 操作：削除する
            repository.delete(product);

            // 確認：見つからない
            assertThat(repository.findById(product.id())).isEmpty();
        }

        @Test
        @DisplayName("削除すると、バリアントなど商品の一部も消える")
        void deletesParts() {
            // 準備：バリアント品番 V1 を持つ商品を保存して削除する
            var product = newProduct("ABC-001", "色");
            product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            repository.add(product);
            product.delete(NOW);
            repository.delete(product);

            // 操作：同じバリアント品番を持つ、別の商品を保存する
            var other = newProduct("ABC-002", "色");
            other.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            repository.add(other);

            // 確認：一意制約の違反にならず、保存されている
            assertThat(repository.findById(other.id())).isPresent();
        }

        @Test
        @DisplayName("取得した後に他で更新された商品の削除は、競合のエラーになり、商品は残る")
        void rejectsDeletingStaleProduct() {
            // 準備：同じ商品を 2 回読み、片方で更新して保存する
            var product = newProduct("ABC-001");
            repository.add(product);
            var mine = repository.findById(product.id()).orElseThrow();
            var theirs = repository.findById(product.id()).orElseThrow();
            theirs.update(UPDATED_NAME, "", TAX_CATEGORY_ID, UPDATED_PRICE, Set.of(), NOW);
            repository.save(theirs);
            mine.delete(NOW);

            // 操作と確認：古い方の削除は、業務のエラー CONCURRENT_MODIFICATION で拒否される
            assertBusinessError(() -> repository.delete(mine), CommonErrorCode.CONCURRENT_MODIFICATION);

            // 確認：商品は残る
            assertThat(repository.findById(product.id())).isPresent();
        }
    }

    @Nested
    @DisplayName("上限の値の保存")
    class MaxValues {

        @Test
        @DisplayName("文字数と品番の上限の値（サロゲートペアを含む）を保存して、同じ値で読み戻せる")
        void savesMaxValues() {
            // 準備：商品名・説明・軸の名前・軸の値・商品品番・バリアント品番が、どれも上限ちょうどの商品
            var product = factory.create(ProductCode.of(MAX_PRODUCT_CODE), "𩸽".repeat(NAME_MAX_LENGTH),
                    "𩸽".repeat(DESCRIPTION_MAX_LENGTH), TAX_CATEGORY_ID, PriceExclTax.of(1000), Set.of(clothing),
                    List.of("𩸽".repeat(AXIS_NAME_MAX_LENGTH)));
            product.addVariant(VariantCode.of(MAX_VARIANT_CODE), List.of("𩸽".repeat(AXIS_VALUE_MAX_LENGTH)), NOW);

            // 操作：保存して再構築する
            repository.add(product);
            var restored = repository.findById(product.id()).orElseThrow();

            // 確認：上限ちょうどの値が、全て同じ値で読み戻せる
            assertMaxValuesRestored(restored);
        }
    }

    @Nested
    @DisplayName("画像")
    class Images {

        @Test
        @DisplayName("画像とバリアントの画像の指定を含めて保存し、再構築できる")
        void addsAndRestoresImages() {
            // 準備：画像 2 枚を持ち、バリアントに 2 枚目を指定した商品
            var first = UUID.randomUUID();
            var second = UUID.randomUUID();
            var product = withVariantImage(first, second);

            // 操作：保存する
            repository.add(product);

            // 確認：画像は並び順どおりで、バリアントは 2 枚目を指す
            var restored = repository.findById(product.id()).orElseThrow();
            assertThat(restored.images()).usingRecursiveFieldByFieldElementComparator().containsExactly(
                    new ProductImage(first, FIRST_ASSET_ID, 1), new ProductImage(second, SECOND_ASSET_ID, 2));
            assertThat(restored.variants().getFirst().imageId()).isEqualTo(second);
        }

        @Test
        @DisplayName("画像の追加・並び替え・削除を、まとめて差分で保存できる")
        void savesImageChanges() {
            // 準備：画像 2 枚を持つ商品を保存して読み、3 枚目を加え、3・2・1 枚目の順に並べ替え、2 枚目を削除する
            var product = newProduct("ABC-001", "色");
            product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            var first = UUID.randomUUID();
            var firstAsset = UUID.randomUUID();
            var second = UUID.randomUUID();
            product.addImage(first, firstAsset);
            product.addImage(second, UUID.randomUUID());
            repository.add(product);
            var loaded = repository.findById(product.id()).orElseThrow();
            var third = UUID.randomUUID();
            var thirdAsset = UUID.randomUUID();
            loaded.addImage(third, thirdAsset);
            loaded.reorderImages(List.of(third, second, first));
            loaded.deleteImage(second);

            // 操作：保存する
            repository.save(loaded);

            // 確認：3 枚目・1 枚目の順に、それぞれのアセットとともに再構築される
            var restored = repository.findById(product.id()).orElseThrow();
            assertThat(restored.images()).extracting(ProductImage::id).containsExactly(third, first);
            assertThat(restored.images()).extracting(ProductImage::assetId).containsExactly(thirdAsset, firstAsset);
        }

        @Test
        @DisplayName("削除した画像を指定していたバリアントは、指定が外れる")
        void clearsVariantImageOfDeletedImage() {
            // 準備：バリアントに 2 枚目の画像を指定した商品を保存して読み、2 枚目を削除する
            var second = UUID.randomUUID();
            var product = withVariantImage(UUID.randomUUID(), second);
            repository.add(product);
            var loaded = repository.findById(product.id()).orElseThrow();
            loaded.deleteImage(second);

            // 操作：保存する
            repository.save(loaded);

            // 確認：バリアントの画像の指定がない
            assertThat(repository.findById(product.id()).orElseThrow().variants().getFirst().imageId()).isNull();
        }
    }

    @Nested
    @DisplayName("イベントの発行")
    class Events {

        @Test
        @DisplayName("保存の後に、集約が登録したバリアントの登録のイベントを発行する")
        void publishesVariantRegistered() {
            // 準備：バリアントを加えた商品
            var product = newProduct("ABC-001");
            var variantId = product.addVariant(VariantCode.of("V1"), List.of(), NOW);

            // 操作：保存する
            repository.add(product);

            // 確認：この商品とバリアントの、登録のイベントが 1 件
            assertThat(events.stream(VariantRegistered.class))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.productId()).isEqualTo(product.id());
                        assertThat(event.variantId()).isEqualTo(variantId);
                    });
        }

        @Test
        @DisplayName("削除の後に、集約が登録した商品の削除のイベントを発行する")
        void publishesProductDeleted() {
            // 準備：保存した商品に、削除の操作をする
            var product = newProduct("ABC-001");
            product.addVariant(VariantCode.of("V1"), List.of(), NOW);
            repository.add(product);
            product.delete(NOW);

            // 操作：削除する
            repository.delete(product);

            // 確認：この商品の、削除のイベントが 1 件
            assertThat(events.stream(ProductDeleted.class))
                    .singleElement()
                    .satisfies(event -> assertThat(event.productId()).isEqualTo(product.id()));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 再構築した商品が、{@link #newProduct} の値と、色・サイズの 2 つのバリアント（白 M、黒 M）を持つことを確認する。
     *
     * <p>ID は保存のたびに作るので引数で受け取る。それ以外の期待値は定数。確認すること：
     * <ul>
     *   <li>ID、商品品番、商品名、説明、税区分 ID、税抜価格</li>
     *   <li>所属カテゴリが「衣類」だけ</li>
     *   <li>軸（並びを含む）</li>
     *   <li>非公開で、初回表示日時がなく、公開の期間が無期限</li>
     *   <li>画像がない</li>
     *   <li>バリアント（ID、品番、軸の値、並び順、販売中、画像の指定なし）</li>
     * </ul>
     *
     * @param restored  再構築した商品
     * @param productId 商品 ID
     * @param white     白 M のバリアントの ID
     * @param black     黒 M のバリアントの ID
     */
    private void assertRestoredWithVariants(Product restored, UUID productId, UUID white, UUID black) {
        assertThat(restored.id()).isEqualTo(productId);
        assertThat(restored.productCode()).isEqualTo(ProductCode.of(PRODUCT_CODE));
        assertThat(restored.name()).isEqualTo(NAME);
        assertThat(restored.description()).isEqualTo(DESCRIPTION);
        assertThat(restored.taxCategoryId()).isEqualTo(TAX_CATEGORY_ID);
        assertThat(restored.priceExclTax()).isEqualTo(PRICE);
        assertThat(restored.categoryIds()).containsExactly(clothing);
        assertThat(restored.axes()).containsExactly("色", "サイズ");
        assertThat(restored.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        assertThat(restored.firstShownAt()).isNull();
        assertThat(restored.publicationPeriod()).isEqualTo(PublicationPeriod.UNLIMITED);
        assertThat(restored.images()).isEmpty();
        assertThat(restored.variants()).usingRecursiveFieldByFieldElementComparator().containsExactly(
                new Variant(white, VariantCode.of("ABC-001-WH-M"), List.of("白", "M"), 1, false, null),
                new Variant(black, VariantCode.of("ABC-001-BK-M"), List.of("黒", "M"), 2, false, null));
    }

    /**
     * 再構築した商品が、上限ちょうどの値を同じ値で持つことを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>商品品番</li>
     *   <li>商品名と説明</li>
     *   <li>軸の名前</li>
     *   <li>バリアント品番と軸の値</li>
     * </ul>
     *
     * @param restored 再構築した商品
     */
    private static void assertMaxValuesRestored(Product restored) {
        assertThat(restored.productCode()).isEqualTo(ProductCode.of(MAX_PRODUCT_CODE));
        assertThat(restored.name()).isEqualTo("𩸽".repeat(NAME_MAX_LENGTH));
        assertThat(restored.description()).isEqualTo("𩸽".repeat(DESCRIPTION_MAX_LENGTH));
        assertThat(restored.axes()).containsExactly("𩸽".repeat(AXIS_NAME_MAX_LENGTH));
        assertThat(restored.variants()).singleElement().satisfies(variant -> {
            assertThat(variant.variantCode()).isEqualTo(VariantCode.of(MAX_VARIANT_CODE));
            assertThat(variant.axisValues()).containsExactly("𩸽".repeat(AXIS_VALUE_MAX_LENGTH));
        });
    }

    // ============================== 準備の補助 ==============================

    /**
     * 「衣類」に所属する、非公開の商品を作る（保存はしない）。
     *
     * @param code 商品品番
     * @param axes 軸の名前
     * @return 商品
     */
    private Product newProduct(String code, String... axes) {
        return factory.create(ProductCode.of(code), NAME, DESCRIPTION, TAX_CATEGORY_ID, PRICE, Set.of(clothing), List.of(axes));
    }

    /**
     * 色の軸とバリアント 1 つ、画像 2 枚を持ち、バリアントに 2 枚目の画像を指定した商品を作る（保存はしない）。
     *
     * <p>画像の ID は保存のたびに作るので引数で受け取る。アセット ID は定数。
     *
     * @param firstImageId  1 枚目の画像の ID
     * @param secondImageId 2 枚目の画像の ID（バリアントに指定する）
     * @return 商品
     */
    private Product withVariantImage(UUID firstImageId, UUID secondImageId) {
        var product = newProduct(PRODUCT_CODE, "色");
        var variantId = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
        product.addImage(firstImageId, FIRST_ASSET_ID);
        product.addImage(secondImageId, SECOND_ASSET_ID);
        product.setVariantImage(variantId, secondImageId);
        return product;
    }
}
