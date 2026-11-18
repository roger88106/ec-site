package com.ecsite.module.shipping.infrastructure.persistence;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.jooq.shipping.tables.records.ShipmentRecord;
import com.ecsite.support.RestoreFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.ecsite.jooq.shipping.Tables.SHIPMENT;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;

/**
 * 出荷を DB の値から戻すときの失敗の単体テスト
 *
 * <p>DB の値を戻せないときに、例外の出力に DB の値が出ないことだけを確認する。値を正しく戻すことは、Repository の
 * 統合テストが確認する。
 */
class ShipmentRestoreFailureTest {

    // ============================== Bean ==============================

    /**
     * 例外のメッセージに入ると分かる値
     */
    private final String value = RestoreFailures.value();

    // ============================== テスト ==============================

    @Test
    @DisplayName("配送業者が列挙にないとき、例外の出力に値が出ない")
    void hidesCarrier() {
        // 操作と確認：配送業者を列挙にない値にした行を読む
        assertFindHides(shipment -> shipment.setCarrier(value), IllegalArgumentException.class);
    }

    @Test
    @DisplayName("配送状態が列挙にないとき、例外の出力に値が出ない")
    void hidesStatus() {
        // 操作と確認：配送状態を列挙にない値にした行を読む
        assertFindHides(shipment -> shipment.setStatus(value), IllegalArgumentException.class);
    }

    @Test
    @DisplayName("追跡番号の形が違うとき、例外の出力に値が出ない")
    void hidesTrackingNumber() {
        // 操作と確認：追跡番号を数字でない値にした行を読む
        assertFindHides(shipment -> shipment.setTrackingNumber(value), BusinessException.class);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 行の一部を壊して出荷を読み、例外の出力に値が出ないことを確認する。
     *
     * @param breaking 行を壊す処理
     * @param causeType 復元の失敗の原因になるはずの例外の種類
     */
    private void assertFindHides(Consumer<ShipmentRecord> breaking, Class<? extends Throwable> causeType) {
        var shipment = RECORDS.newRecord(SHIPMENT);
        shipment.setCarrier("YAMATO");
        shipment.setTrackingNumber("123");
        shipment.setStatus("SHIPPED");
        breaking.accept(shipment);
        var dsl = new RestoreFailures().with("shipment", shipment).dsl();
        var repository = new JooqShipmentRepository(dsl, Clock.systemUTC());

        assertValueIsHidden(() -> repository.findAllByOrderIds(List.of(UUID.randomUUID())), value, causeType);
    }
}
