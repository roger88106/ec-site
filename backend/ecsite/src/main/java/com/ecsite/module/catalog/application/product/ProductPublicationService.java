package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.command.ChangePublicationPeriodCommand;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 商品の公開のコマンドサービス
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductPublicationService {

    /**
     * 商品リポジトリ
     */
    private final ProductRepository productRepository;

    /**
     * 商品の取得と状態の切り替え
     */
    private final ProductLoader productLoader;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 公開する。
     *
     * <p>冪等。
     *
     * @param productId 商品 ID
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 販売終了の商品（{@code PRODUCT_DISCONTINUED}）
     * @throws BusinessException バリアントがない（{@code PRODUCT_HAS_NO_VARIANTS}）
     * @throws BusinessException 取得から保存までの間に他で更新された（{@code CONCURRENT_MODIFICATION}）
     */
    public void publishProduct(@NonNull UUID productId) {
        productLoader.changeStatus(productId, Product::publish);
    }

    /**
     * 非公開にする。
     *
     * <p>冪等。
     *
     * @param productId 商品 ID
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 販売終了の商品（{@code PRODUCT_DISCONTINUED}）
     * @throws BusinessException 取得から保存までの間に他で更新された（{@code CONCURRENT_MODIFICATION}）
     */
    public void unpublishProduct(@NonNull UUID productId) {
        productLoader.changeStatus(productId, Product::unpublish);
    }

    /**
     * 販売終了にする。
     *
     * <p>冪等。
     *
     * @param productId 商品 ID
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得から保存までの間に他で更新された（{@code CONCURRENT_MODIFICATION}）
     */
    public void discontinueProduct(@NonNull UUID productId) {
        productLoader.changeStatus(productId, Product::discontinue);
    }

    /**
     * 公開期間を変更する。
     *
     * @param command 公開期間の変更コマンド
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 開始が終了より前でない（{@code INVALID_PUBLICATION_PERIOD}）
     */
    public void changePublicationPeriod(@NonNull ChangePublicationPeriodCommand command) {
        // 商品取得
        var product = productLoader.get(command.productId(), command.version());

        // 公開期間変更
        product.changePublicationPeriod(PublicationPeriod.of(command.publishStart(), command.publishEnd()), clock.instant());

        // 商品保存
        productRepository.save(product);
    }
}
