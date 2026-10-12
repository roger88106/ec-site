package com.ecsite.module.catalog.application;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.command.AddVariantCommand;
import com.ecsite.module.catalog.application.model.command.ChangePublicationPeriodCommand;
import com.ecsite.module.catalog.application.model.command.CreateProductCommand;
import com.ecsite.module.catalog.application.model.command.ReorderVariantsCommand;
import com.ecsite.module.catalog.application.model.command.UpdateProductCommand;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.ProductImage;
import com.ecsite.module.catalog.domain.product.port.MediaDirectory;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.port.TaxDirectory;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * 商品のコマンドサービス
 */
@Service
@RequiredArgsConstructor
public class ProductCommandService {

    /**
     * 商品リポジトリ
     */
    private final ProductRepository productRepository;

    /**
     * 商品ファクトリ
     */
    private final ProductFactory productFactory;

    /**
     * カテゴリリポジトリ（所属カテゴリの存在確認）
     */
    private final CategoryRepository categoryRepository;

    /**
     * 税の腐敗防止層
     */
    private final TaxDirectory taxDirectory;

    /**
     * メディアの腐敗防止層（画像のアセットを手放す）
     */
    private final MediaDirectory mediaDirectory;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 商品を登録する（AC-6、13〜17、25）。
     *
     * @param command 商品の登録コマンド
     * @return 登録した商品の ID
     */
    @Transactional
    public UUID createProduct(CreateProductCommand command) {
        var now = clock.instant();

        // 税区分と所属カテゴリの確認
        verifyTaxCategoryAssignable(command.taxCategoryId(), now);
        verifyCategoriesExist(command.categoryIds());

        // 商品作成
        var product = productFactory.create(
                ProductCode.of(command.productCode()),
                command.name(),
                command.description(),
                command.taxCategoryId(),
                PriceExclTax.of(command.priceExclTax()),
                command.categoryIds(),
                command.axes());

        // 商品登録 ※品番の重複は DB の一意制約で検出する
        productRepository.add(product);

        return product.id();
    }

    /**
     * 商品を編集する（AC-6、18、19、25、26）。
     *
     * @param command 商品の編集コマンド
     */
    @Transactional
    public void updateProduct(UpdateProductCommand command) {
        var now = clock.instant();
        var product = getProduct(command.productId(), command.version());

        // 税区分は、変えるときだけ確認する
        if (!product.taxCategoryId().equals(command.taxCategoryId())) {
            verifyTaxCategoryAssignable(command.taxCategoryId(), now);
        }
        verifyCategoriesExist(command.categoryIds());

        // 商品編集
        product.update(command.name(), command.description(), command.taxCategoryId(),
                PriceExclTax.of(command.priceExclTax()), command.categoryIds(), now);

        productRepository.save(product);
    }

    /**
     * 商品を削除する（AC-33）。画像のアセットも手放す（配信のファイルは、コミットの後にメディアモジュールが消す）。
     *
     * @param productId 商品 ID
     * @param version   取得時のバージョン
     */
    @Transactional
    public void deleteProduct(UUID productId, long version) {
        var product = getProduct(productId, version);

        product.delete(clock.instant());

        productRepository.delete(product);
        mediaDirectory.release(product.images().stream().map(ProductImage::assetId).toList());
    }

    /**
     * バリアントを登録する（AC-20〜24）。
     *
     * @param command バリアントの登録コマンド
     * @return 登録したバリアントの ID
     */
    @Transactional
    public UUID addVariant(AddVariantCommand command) {
        var product = getProduct(command.productId(), command.version());

        var variantId = product.addVariant(VariantCode.of(command.variantCode()), command.axisValues(), clock.instant());

        // 商品保存 ※別の商品とのバリアント品番の重複は DB の一意制約で検出する
        productRepository.save(product);

        return variantId;
    }

    /**
     * バリアントを削除する（AC-71）。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @param version   取得時の商品のバージョン
     */
    @Transactional
    public void deleteVariant(UUID productId, UUID variantId, long version) {
        var product = getProduct(productId, version);

        product.deleteVariant(variantId, clock.instant());

        productRepository.save(product);
    }

    /**
     * バリアントの並び順を変更する（AC-27）。
     *
     * @param command バリアントの並び順の変更コマンド
     */
    @Transactional
    public void reorderVariants(ReorderVariantsCommand command) {
        var product = getProduct(command.productId(), command.version());

        product.reorderVariants(command.variantIds());

        productRepository.save(product);
    }

    /**
     * 公開する（AC-29、31）。冪等。
     *
     * @param productId 商品 ID
     */
    @Transactional
    public void publishProduct(UUID productId) {
        changeStatus(productId, Product::publish);
    }

    /**
     * 非公開にする（AC-30）。冪等。
     *
     * @param productId 商品 ID
     */
    @Transactional
    public void unpublishProduct(UUID productId) {
        changeStatus(productId, Product::unpublish);
    }

    /**
     * 販売終了にする（AC-32）。冪等。
     *
     * @param productId 商品 ID
     */
    @Transactional
    public void discontinueProduct(UUID productId) {
        changeStatus(productId, Product::discontinue);
    }

    /**
     * 公開期間を変更する（AC-34〜36）。
     *
     * @param command 公開期間の変更コマンド
     */
    @Transactional
    public void changePublicationPeriod(ChangePublicationPeriodCommand command) {
        var product = getProduct(command.productId(), command.version());

        product.changePublicationPeriod(PublicationPeriod.of(command.publishStart(), command.publishEnd()), clock.instant());

        productRepository.save(product);
    }

    /**
     * バリアントを販売停止にする（AC-28）。冪等。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     */
    @Transactional
    public void suspendVariant(UUID productId, UUID variantId) {
        changeStatus(productId, (product, now) -> product.suspendVariant(variantId));
    }

    /**
     * バリアントの販売停止を解除する（AC-28）。冪等。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     */
    @Transactional
    public void resumeVariant(UUID productId, UUID variantId) {
        changeStatus(productId, (product, now) -> product.resumeVariant(variantId));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 状態（公開状態、バリアントの販売停止）を切り替える。<br />
     * 状態の切り替えはバージョンを受け取らない（constitution 9）。すでにその状態なら保存しないので、バージョンも上がらない。
     * 取得から保存までの間に他で更新されていれば、保存時に競合になる。
     *
     * @param productId 商品 ID
     * @param operation 切り替えの操作。状態が変わったら true を返す
     */
    private void changeStatus(UUID productId, BiPredicate<Product, Instant> operation) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));

        if (operation.test(product, clock.instant())) {
            productRepository.save(product);
        }
    }

    /**
     * 商品を取得し、取得時のバージョンと一致することを確かめる。
     *
     * @param productId 商品 ID
     * @param version   取得時のバージョン
     * @return 商品
     */
    private Product getProduct(UUID productId, long version) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));

        // 取得時のバージョンと一致しなければ、他の人が先に更新している
        if (product.version() != version) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        return product;
    }

    /**
     * 税区分が現時点で有効な税率を持つことを確かめる（AC-6）。
     *
     * @param taxCategoryId 税区分 ID
     * @param now           現在時刻
     */
    private void verifyTaxCategoryAssignable(UUID taxCategoryId, Instant now) {
        if (!taxDirectory.hasRateAt(taxCategoryId, now)) {
            throw BusinessException.of(ProductErrorCode.TAX_CATEGORY_NOT_ASSIGNABLE);
        }
    }

    /**
     * 所属カテゴリがすべて存在することを確かめる。<br />
     * 確認の後に削除された場合は、保存時に外部キーの違反として検出する。
     *
     * @param categoryIds 所属カテゴリの ID
     */
    private void verifyCategoriesExist(Collection<UUID> categoryIds) {
        if (!categoryRepository.existsAll(categoryIds)) {
            throw BusinessException.of(ProductErrorCode.INVALID_CATEGORY_ID);
        }
    }
}
