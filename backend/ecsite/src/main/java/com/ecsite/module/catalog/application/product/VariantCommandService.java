package com.ecsite.module.catalog.application.product;

import com.ecsite.module.catalog.application.model.command.AddVariantCommand;
import com.ecsite.module.catalog.application.model.command.ReorderVariantsCommand;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 商品のバリアントのコマンドサービス（追加、削除、並べ替え、販売の停止・再開）
 */
@Service
@RequiredArgsConstructor
public class VariantCommandService {

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
     * バリアントを登録する。
     *
     * @param command バリアントの登録コマンド
     * @return 登録したバリアントの ID
     */
    @Transactional
    public UUID addVariant(AddVariantCommand command) {
        var product = productLoader.get(command.productId(), command.version());

        var variantId = product.addVariant(VariantCode.of(command.variantCode()), command.axisValues(), clock.instant());

        // 商品保存 ※別の商品とのバリアント品番の重複は DB の一意制約で検出する
        productRepository.save(product);

        return variantId;
    }

    /**
     * バリアントを削除する。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @param version   取得時の商品のバージョン
     */
    @Transactional
    public void deleteVariant(UUID productId, UUID variantId, long version) {
        var product = productLoader.get(productId, version);

        product.deleteVariant(variantId, clock.instant());

        productRepository.save(product);
    }

    /**
     * バリアントの並び順を変更する。
     *
     * @param command バリアントの並び順の変更コマンド
     */
    @Transactional
    public void reorderVariants(ReorderVariantsCommand command) {
        var product = productLoader.get(command.productId(), command.version());

        product.reorderVariants(command.variantIds());

        productRepository.save(product);
    }

    /**
     * バリアントを販売停止にする。冪等。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     */
    @Transactional
    public void suspendVariant(UUID productId, UUID variantId) {
        productLoader.changeStatus(productId, (product, now) -> product.suspendVariant(variantId));
    }

    /**
     * バリアントの販売停止を解除する。冪等。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     */
    @Transactional
    public void resumeVariant(UUID productId, UUID variantId) {
        productLoader.changeStatus(productId, (product, now) -> product.resumeVariant(variantId));
    }
}
