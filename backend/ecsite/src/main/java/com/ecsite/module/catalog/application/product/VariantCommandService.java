package com.ecsite.module.catalog.application.product;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.command.AddVariantCommand;
import com.ecsite.module.catalog.application.model.command.ReorderVariantsCommand;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 商品のバリアントのコマンドサービス
 */
@Service
@RequiredArgsConstructor
public class VariantCommandService {

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
     * バリアントを登録する。
     *
     * @param command バリアントの登録コマンド
     * @return 登録したバリアントの ID
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 項目の値が正しくない（{@code INVALID_VARIANT_CODE}、{@code INVALID_AXIS_VALUES}）
     * @throws BusinessException 軸を持たない商品に 2 つ目のバリアントを登録しようとした（{@code SINGLE_VARIANT_ONLY}）
     * @throws BusinessException 品番か軸の値の組み合わせが重複している（{@code VARIANT_CODE_ALREADY_EXISTS}、{@code VARIANT_AXIS_VALUES_ALREADY_EXISTS}）
     */
    @Transactional
    public UUID addVariant(@NonNull AddVariantCommand command) {
        // 商品取得
        var product = productLoader.get(command.productId(), command.version());

        // バリアント登録
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
     * @throws BusinessException 商品かバリアントがない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException C 側に表示されたことがある（{@code PRODUCT_ALREADY_SHOWN}）
     * @throws BusinessException 公開中の商品の最後のバリアント（{@code PRODUCT_HAS_NO_VARIANTS}）
     */
    @Transactional
    public void deleteVariant(@NonNull UUID productId, @NonNull UUID variantId, long version) {
        // 商品取得
        var product = productLoader.get(productId, version);

        // バリアント削除
        product.deleteVariant(variantId, clock.instant());

        // 商品保存
        productRepository.save(product);
    }

    /**
     * バリアントの並び順を変更する。
     *
     * @param command バリアントの並び順の変更コマンド
     * @throws BusinessException 商品がない（{@code NOT_FOUND}）
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 全てのバリアントと過不足なく一致しない（{@code INVALID_VARIANT_ORDER}）
     */
    @Transactional
    public void reorderVariants(@NonNull ReorderVariantsCommand command) {
        // 商品取得
        var product = productLoader.get(command.productId(), command.version());

        // 並び順変更
        product.reorderVariants(command.variantIds());

        // 商品保存
        productRepository.save(product);
    }

    /**
     * バリアントを販売停止にする。
     *
     * <p>冪等。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @throws BusinessException 商品かバリアントがない（{@code NOT_FOUND}）
     * @throws BusinessException 取得から保存までの間に他で更新された（{@code CONCURRENT_MODIFICATION}）
     */
    @Transactional
    public void suspendVariant(@NonNull UUID productId, @NonNull UUID variantId) {
        productLoader.changeStatus(productId, (product, now) -> product.suspendVariant(variantId));
    }

    /**
     * バリアントの販売停止を解除する。
     *
     * <p>冪等。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @throws BusinessException 商品かバリアントがない（{@code NOT_FOUND}）
     * @throws BusinessException 取得から保存までの間に他で更新された（{@code CONCURRENT_MODIFICATION}）
     */
    @Transactional
    public void resumeVariant(@NonNull UUID productId, @NonNull UUID variantId) {
        productLoader.changeStatus(productId, (product, now) -> product.resumeVariant(variantId));
    }
}
