package com.ecsite.module.catalog.application;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.command.CreateProductCommand;
import com.ecsite.module.catalog.application.model.command.UpdateProductCommand;
import com.ecsite.module.catalog.application.port.MediaDirectory;
import com.ecsite.module.catalog.application.port.TaxDirectory;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.ProductImage;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * 商品のコマンドサービス（登録、更新、削除）。<br />
 * バリアントは {@link VariantCommandService}、公開は {@link ProductPublicationService}、画像は {@link ProductImageService}。
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
     * 商品の取り出しと状態の切り替え
     */
    private final ProductLoader productLoader;

    /**
     * 商品を登録する。
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
     */
    @Transactional
    public void updateProduct(UpdateProductCommand command) {
        var now = clock.instant();
        var product = productLoader.get(command.productId(), command.version());

        // 税区分は、変えるときだけ確認する
        if (!product.taxCategoryId().equals(command.taxCategoryId())) {
            verifyTaxCategoryAssignable(command.taxCategoryId(), now);
        }
        verifyCategoriesExist(command.categoryIds());

        // 商品編集
        product.update(command.name(), command.description(), command.taxCategoryId(),
                PriceExclTax.of(command.priceExclTax().yen()), command.categoryIds(), now);

        productRepository.save(product);
    }

    /**
     * 商品を削除する。画像のアセットも手放す（配信のファイルは、コミットの後にメディアモジュールが消す）。
     *
     * @param productId 商品 ID
     * @param version   取得時のバージョン
     */
    @Transactional
    public void deleteProduct(UUID productId, long version) {
        var product = productLoader.get(productId, version);

        product.delete(clock.instant());

        productRepository.delete(product);
        mediaDirectory.release(product.images().stream().map(ProductImage::assetId).toList());
    }

// ============================== プライベートメソッド ==============================

    /**
     * 税区分が現時点で有効な税率を持つことを確かめる。
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
