package com.ecsite.module.inventory.application;

import com.ecsite.module.inventory.application.model.command.AdjustStockCommand;
import com.ecsite.module.inventory.application.model.view.StockAdjustmentView;
import com.ecsite.module.inventory.domain.stock.StockAdjustment;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 在庫のコマンドサービス
 */
@Service
@RequiredArgsConstructor
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
     */
    @Transactional
    public StockAdjustmentView adjustStock(AdjustStockCommand command) {
        // 在庫調整作成 ※増減量とメモの検証
        var adjustment = StockAdjustment.create(command.delta(), command.reason(),
                command.memo(), command.operator(), clock.instant());

        // 差分の加算と履歴の記録 ※負になるか、在庫がなければ業務のエラー
        int quantityAfter = stockItemRepository.adjust(command.variantId(), adjustment);

        return new StockAdjustmentView(adjustment.id(), adjustment.delta(), adjustment.reason(),
                adjustment.memo(), null, null, quantityAfter, adjustment.adjustedAt(), command.operator().name());
    }
}
