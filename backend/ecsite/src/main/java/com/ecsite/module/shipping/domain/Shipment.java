package com.ecsite.module.shipping.domain;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 出荷（docs/design/modules/shipping.md「出荷（集約）」）。<br />
 * 配送状態は、物流サービスの通知で、今より高い順位にだけ進む。
 */
@Getter
@Accessors(fluent = true)
public class Shipment {

    /**
     * 出荷 ID
     */
    private final UUID id;

    /**
     * 注文 ID（注文 1 つにつき出荷は 1 つ）
     */
    private final UUID orderId;

    /**
     * 配送業者
     */
    private final Carrier carrier;

    /**
     * 送り状番号
     */
    private final TrackingNumber trackingNumber;

    /**
     * 配送状態
     */
    private DeliveryStatus status;

    /**
     * 出荷日時（運営者が出荷を登録した日時）
     */
    private final Instant shippedAt;

    /**
     * 最後に通知を反映した日時。まだ通知を反映していなければ null
     */
    @Nullable
    @Getter(lombok.AccessLevel.NONE)
    private Instant lastNotifiedAt;

    /**
     * 項目をそのまま持つ。作るときは出荷の登録か、保存した状態からの復元を使う。
     */
    private Shipment(@NonNull UUID id, @NonNull UUID orderId, @NonNull Carrier carrier,
                     @NonNull TrackingNumber trackingNumber, @NonNull DeliveryStatus status,
                     @NonNull Instant shippedAt, Instant lastNotifiedAt) {
        this.id = id;
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.shippedAt = shippedAt;
        this.lastNotifiedAt = lastNotifiedAt;
    }

    /**
     * 出荷を作る。配送状態は「出荷済み」。
     *
     * @param id             出荷 ID
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param shippedAt      出荷日時
     * @return 出荷
     */
    public static Shipment create(UUID id, UUID orderId, Carrier carrier, TrackingNumber trackingNumber, Instant shippedAt) {
        return new Shipment(id, orderId, carrier, trackingNumber, DeliveryStatus.SHIPPED, shippedAt, null);
    }

    /**
     * 保存された出荷を組み立て直す。
     *
     * @param id             出荷 ID
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param status         配送状態
     * @param shippedAt      出荷日時
     * @param lastNotifiedAt 最後に通知を反映した日時。なければ null
     * @return 出荷
     */
    public static Shipment restore(UUID id, UUID orderId, Carrier carrier, TrackingNumber trackingNumber,
                                   DeliveryStatus status, Instant shippedAt, Instant lastNotifiedAt) {
        return new Shipment(id, orderId, carrier, trackingNumber, status, shippedAt, lastNotifiedAt);
    }

    /**
     * 最後に通知を反映した日時を返す。
     *
     * @return 最後に通知を反映した日時。まだ通知を反映していなければ空
     */
    public Optional<Instant> lastNotifiedAt() {
        return Optional.ofNullable(lastNotifiedAt);
    }

    /**
     * 通知の配送状態へ進める。今より高い順位でなければ、何もしない。
     *
     * @param next       通知の配送状態
     * @param notifiedAt 通知を反映する日時
     * @return 進めたら true。進めなければ false
     */
    public boolean advanceTo(@NonNull DeliveryStatus next, @NonNull Instant notifiedAt) {
        if (!status.canAdvanceTo(next)) {
            return false;
        }
        this.status = next;
        this.lastNotifiedAt = notifiedAt;
        return true;
    }
}
