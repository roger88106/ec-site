package com.ecsite.module.notification.infrastructure.directory;

import com.ecsite.module.order.api.OrderApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 注文のメールアドレスを注文モジュールから得る腐敗防止層の単体テスト
 *
 * <p>注文の API の戻り値を、宛先か例外にする対応だけを確認する。注文の API は差し替える。
 */
class OrderApiDirectoryTest {

    // ============================== 定数 ==============================

    /**
     * 注文 ID
     */
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000572");

    /**
     * 注文のメールアドレス
     */
    private static final String EMAIL = "taro@example.com";

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-572-3 注文の API が値を返したら、その値を宛先として返す")
    void returnsEmailOfOrderApi() {
        // 準備：注文のメールアドレスを返す注文の API
        var directory = new OrderApiDirectory(orderApiReturning(Optional.of(EMAIL)));

        // 操作と確認：注文の API の値
        assertThat(directory.email(ORDER_ID)).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("DC-572-4 注文の API が空を返したら、注文 ID を入れた例外を投げる")
    void throwsWhenOrderNotFound() {
        // 準備：空を返す注文の API
        var directory = new OrderApiDirectory(orderApiReturning(Optional.empty()));

        // 操作と確認：注文 ID を含む例外
        assertThatThrownBy(() -> directory.email(ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORDER_ID.toString());
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文のメールアドレスの操作が、指定の値を返す注文の API を作る。
     *
     * @param email 返す値
     * @return 注文の API
     */
    private static OrderApi orderApiReturning(Optional<String> email) {
        var api = mock(OrderApi.class);
        when(api.findEmail(ORDER_ID)).thenReturn(email);
        return api;
    }
}
