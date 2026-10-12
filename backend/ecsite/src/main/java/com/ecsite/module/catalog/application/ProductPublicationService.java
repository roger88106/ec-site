package com.ecsite.module.catalog.application;

import com.ecsite.module.catalog.application.model.command.ChangePublicationPeriodCommand;
import com.ecsite.module.catalog.domain.product.Product;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 商品の公開のコマンドサービス（公開、非公開、販売終了、公開の期間）
 */
@Service
@RequiredArgsConstructor
public class ProductPublicationService {

    /**
     * 商品リポジトリ
     */
    private final ProductRepository productRepository;

    /**
     * 商品の取り出しと状態の切り替え
     */
    private final ProductLoader productLoader;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 公開する（AC-29、31）。冪等。
     *
     * @param productId 商品 ID
     */
    @Transactional
    public void publishProduct(UUID productId) {
        productLoader.changeStatus(productId, Product::publish);
    }

    /**
     * 非公開にする（AC-30）。冪等。
     *
     * @param productId 商品 ID
     */
    @Transactional
    public void unpublishProduct(UUID productId) {
        productLoader.changeStatus(productId, Product::unpublish);
    }

    /**
     * 販売終了にする（AC-32）。冪等。
     *
     * @param productId 商品 ID
     */
    @Transactional
    public void discontinueProduct(UUID productId) {
        productLoader.changeStatus(productId, Product::discontinue);
    }

    /**
     * 公開期間を変更する（AC-34〜36）。
     *
     * @param command 公開期間の変更コマンド
     */
    @Transactional
    public void changePublicationPeriod(ChangePublicationPeriodCommand command) {
        var product = productLoader.get(command.productId(), command.version());

        product.changePublicationPeriod(PublicationPeriod.of(command.publishStart(), command.publishEnd()), clock.instant());

        productRepository.save(product);
    }
}
