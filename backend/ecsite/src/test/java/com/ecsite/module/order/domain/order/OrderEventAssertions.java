package com.ecsite.module.order.domain.order;

import com.ecsite.module.order.event.OrderStatusChanged;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文の状態の変化のイベントの確認
 *
 * <p>注文の状態の遷移の単体テスト（支払・出荷・キャンセル）が、遷移で登録されたイベントの中身を確認するのに使う。
 */
public final class OrderEventAssertions {

    /**
     * インスタンスを作らない。
     */
    private OrderEventAssertions() {
    }

    /**
     * 状態の変化のイベントが、期待の中身で 1 件だけ登録されていることを確認する。
     *
     * <p>他の種類のイベント（注文の出荷など）は数えない。確認すること：
     * <ul>
     *   <li>状態の変化のイベントが 1 件だけ</li>
     *   <li>注文 ID がこの注文</li>
     *   <li>起きた日時</li>
     *   <li>前の状態と後の状態</li>
     *   <li>キャンセルの種類（キャンセル以外は null）</li>
     *   <li>在庫に戻したか（キャンセル以外は null）</li>
     * </ul>
     *
     * @param order            確認する注文
     * @param occurredAt       起きた日時
     * @param from             前の状態
     * @param to               後の状態
     * @param cancellationKind キャンセルの種類
     * @param restocked        在庫に戻したか
     */
    public static void assertStatusChangedEventOnly(Order order, Instant occurredAt, OrderStatusChanged.Status from,
                                                    OrderStatusChanged.Status to,
                                                    OrderStatusChanged.@Nullable CancellationKind cancellationKind,
                                                    @Nullable Boolean restocked) {
        assertThat(order.pullEvents()).filteredOn(OrderStatusChanged.class::isInstance).singleElement()
                .isInstanceOfSatisfying(OrderStatusChanged.class, event -> {
                    assertThat(event.orderId()).isEqualTo(order.id());
                    assertThat(event.occurredAt()).isEqualTo(occurredAt);
                    assertThat(event.from()).isEqualTo(from);
                    assertThat(event.to()).isEqualTo(to);
                    assertThat(event.cancellationKind()).isEqualTo(cancellationKind);
                    assertThat(event.restocked()).isEqualTo(restocked);
                });
    }

    /**
     * キャンセルへの状態の変化のイベントだけが登録されていることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>登録されたイベントが 1 件だけで、種類が {@link OrderStatusChanged}</li>
     *   <li>後の状態がキャンセル</li>
     * </ul>
     *
     * @param order 確認する注文
     */
    public static void assertCancelledEventOnly(Order order) {
        assertThat(order.pullEvents()).singleElement().isInstanceOfSatisfying(OrderStatusChanged.class,
                event -> assertThat(event.to()).isEqualTo(OrderStatusChanged.Status.CANCELLED));
    }
}
