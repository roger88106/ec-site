package com.ecsite.architecture.sample.testcode;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.inventory.domain.stock.port.StockItemRepository;
import com.ecsite.module.payment.api.PaymentApi;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * テストデータの作成の検査の例：補助の外で、在庫と支払を作る（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CreatesTestData {

    /**
     * インスタンスを作らない。
     */
    private CreatesTestData() {
    }

    /**
     * 在庫を、型を書かない変数（var）から作る。
     *
     * @param repository 在庫の Repository
     */
    static void createStock(StockItemRepository repository) {
        var stocks = repository;
        stocks.createIfAbsent(UUID.randomUUID(), UUID.randomUUID());
    }

    /**
     * 在庫の作成を、メソッド参照で渡す。
     *
     * @param repository 在庫の Repository
     * @return 在庫を作る関数
     */
    static BiConsumer<UUID, UUID> createStockLater(StockItemRepository repository) {
        return repository::createIfAbsent;
    }

    /**
     * 支払を作る。
     *
     * @param payments 支払の API
     * @param amount   金額
     */
    static void createPayment(PaymentApi payments, Money amount) {
        payments.create(UUID.randomUUID(), amount, Instant.EPOCH);
    }
}
