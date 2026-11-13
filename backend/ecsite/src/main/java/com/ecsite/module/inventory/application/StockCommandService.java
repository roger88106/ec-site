package com.ecsite.module.inventory.application;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.inventory.application.model.command.AdjustStockCommand;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 在庫のコマンドサービス
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockCommandService {

    /**
     * 在庫リポジトリ
     */
    private final StockItemRepository stockItemRepository;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 在庫を調整する。
     *
     * @param command 在庫の調整コマンド
     * @return 記録した在庫調整
     * @throws BusinessException 増減量・調整理由・メモが検証に合わない、調整後の在庫数が負になるか上限を超える、または在庫がない（{@code NOT_FOUND}）
     */
    public StockAdjustmentView adjustStock(@NonNull AdjustStockCommand command) {
        // 在庫調整作成 ※増減量とメモの検証
        var adjustment = StockAdjustment.create(command.delta(), command.reason(),
                command.memo(), command.operator(), clock.instant());

        // 差分の加算と履歴の記録 ※負になる、上限を超える、在庫がないなら例外
        int quantityAfter = stockItemRepository.adjust(command.variantId(), adjustment);

        return new StockAdjustmentView(adjustment.id(), adjustment.delta(), adjustment.reason(),
                adjustment.memo(), null, null, quantityAfter, adjustment.adjustedAt(), command.operator().name());
    }

    /**
     * 在庫数 0 の在庫を作る。
     *
     * <p>すでにあれば何もしない。
     *
     * @param variantId バリアント ID
     * @param productId 商品 ID
     */
    public void createIfAbsent(@NonNull UUID variantId, @NonNull UUID productId) {
        stockItemRepository.createIfAbsent(variantId, productId);
    }

    /**
     * バリアントの在庫と調整履歴を消す。
     *
     * <p>なければ何もしない。
     *
     * @param variantId バリアント ID
     */
    public void deleteByVariant(@NonNull UUID variantId) {
        stockItemRepository.deleteByVariantId(variantId);
    }

    /**
     * 商品のすべての在庫と調整履歴を消す。
     *
     * <p>なければ何もしない。
     *
     * @param productId 商品 ID
     */
    public void deleteByProduct(@NonNull UUID productId) {
        stockItemRepository.deleteByProductId(productId);
    }
}
