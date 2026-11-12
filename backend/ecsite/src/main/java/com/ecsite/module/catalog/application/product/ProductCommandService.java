package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.command.CreateProductCommand;
import com.ecsite.module.catalog.application.model.command.UpdateProductCommand;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.application.port.TaxDirectory;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.ProductImage;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

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
     * 商品の取得と状態の切り替え
     */
    private final ProductLoader productLoader;

    /**
     * 商品を登録する。
     *
     * @param command 商品の登録コマンド
     * @return 登録した商品の ID
     * @throws BusinessException 税区分に有効な税率がない（{@code TAX_CATEGORY_NOT_ASSIGNABLE}）
     * @throws BusinessException 所属カテゴリがない（{@code INVALID_CATEGORY_ID}）
     * @throws BusinessException 項目の値が正しくない（{@code INVALID_PRODUCT_CODE}、{@code INVALID_PRICE_EXCL_TAX}、{@code INVALID_PRODUCT_NAME}、{@code INVALID_PRODUCT_DESCRIPTION}、{@code INVALID_VARIANT_AXES}）
     * @throws BusinessException 商品品番が重複している（{@code PRODUCT_CODE_ALREADY_EXISTS}）
     */
    @Transactional
    public UUID createProduct(@NonNull CreateProductCommand command) {
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
                PriceExclTax.of(command.priceExclTax().yen()),
                command.categoryIds(),
                command.axes());

        // 商品登録 ※品番の重複は DB の一意制約で検出する
        productRepository.add(product);

        return product.id();
    }

    /**
     * 商品を編集する。
     *
     * @param command 商品の編集コマンド
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 税区分に有効な税率がない（{@code TAX_CATEGORY_NOT_ASSIGNABLE}）
     * @throws BusinessException 所属カテゴリがない（{@code INVALID_CATEGORY_ID}）
     * @throws BusinessException 項目の値が正しくない（{@code INVALID_PRICE_EXCL_TAX}、{@code INVALID_PRODUCT_NAME}、{@code INVALID_PRODUCT_DESCRIPTION}）
     */
    @Transactional
    public void updateProduct(@NonNull UpdateProductCommand command) {
        var now = clock.instant();

        // 商品取得
        var product = productLoader.get(command.productId(), command.version());

        // 税区分と所属カテゴリの確認 ※税区分は、変えるときだけ確認する
        if (!product.taxCategoryId().equals(command.taxCategoryId())) {
            verifyTaxCategoryAssignable(command.taxCategoryId(), now);
        }
        verifyCategoriesExist(command.categoryIds());

        // 商品編集
        product.update(command.name(), command.description(), command.taxCategoryId(),
                PriceExclTax.of(command.priceExclTax().yen()), command.categoryIds(), now);

        // 商品保存
        productRepository.save(product);
    }

    /**
     * 商品を削除する。
     *
     * <p>画像のアセットも手放す。
     *
     * @param productId 商品 ID
     * @param version   取得時のバージョン
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException C 側に表示されたことがある（{@code PRODUCT_ALREADY_SHOWN}）
     */
    @Transactional
    public void deleteProduct(@NonNull UUID productId, long version) {
        // 商品取得
        var product = productLoader.get(productId, version);

        // 削除できるかの確認
        product.delete(clock.instant());

        // 商品削除と、画像のアセットの解放
        productRepository.delete(product);
        mediaDirectory.release(product.images().stream().map(ProductImage::assetId).toList());
    }

// ============================== プライベートメソッド ==============================

    /**
     * 税区分が現時点で有効な税率を持つことを確認する。
     *
     * @param taxCategoryId 税区分 ID
     * @param now           現在時刻
     * @throws BusinessException 有効な税率がない（{@code TAX_CATEGORY_NOT_ASSIGNABLE}）
     */
    private void verifyTaxCategoryAssignable(UUID taxCategoryId, Instant now) {
        if (!taxDirectory.hasRateAt(taxCategoryId, now)) {
            throw BusinessException.of(ProductErrorCode.TAX_CATEGORY_NOT_ASSIGNABLE);
        }
    }

    /**
     * 所属カテゴリが全て存在することを確認する。
     *
     * @param categoryIds 所属カテゴリの ID
     * @throws BusinessException 存在しないカテゴリがある（{@code INVALID_CATEGORY_ID}）
     */
    private void verifyCategoriesExist(Collection<UUID> categoryIds) {
        // 確認の後に削除された場合は、保存のときに外部キーの違反として検出する
        if (!categoryRepository.existsAll(categoryIds)) {
            throw BusinessException.of(ProductErrorCode.INVALID_CATEGORY_ID);
        }
    }
}
