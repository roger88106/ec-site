package com.ecsite.module.notification.application;

import com.ecsite.common.message.MessageResolver;
import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.notification.application.port.OrderDirectory;
import com.ecsite.module.order.event.OrderShipped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 出荷通知メールのサービスの単体テスト
 *
 * <p>出荷通知メールの宛先と内容の組み立てだけを確認する。宛先は、注文の腐敗防止層（テスト用の実装）から受ける。
 * 送ったメールの記録は、記録した注文 ID と宛先を集めて「まだ送っていない」と答え、送ったメールを受け取って、組み立てた内容を確認する。
 * 再送と 1 通だけ届くことは、通知の統合テストが確認する。
 */
class ShipmentMailCommandServiceTest {

    // ============================== 定数 ==============================

    /**
     * 出荷日時
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-08T01:00:00Z");

    /**
     * 注文 ID
     */
    private static final OrderId ORDER_ID = new OrderId(UUID.fromString("00000000-0000-0000-0000-000000000572"));

    /**
     * 注文番号
     */
    private static final String ORDER_NUMBER = "20261008-123456";

    /**
     * 注文のメールアドレス（注文の腐敗防止層が返す宛先）
     */
    private static final String EMAIL = "taro@example.com";

    /**
     * 送り状番号
     */
    private static final String TRACKING_NUMBER = "123456789012";

    // ============================== Bean ==============================

    /**
     * 送ったメール
     */
    private final List<Mailer.Mail> sent = new ArrayList<>();

    /**
     * 送ったメールとして記録した注文 ID と宛先
     */
    private final Map<OrderId, String> recorded = new HashMap<>();

    /**
     * 注文の腐敗防止層が宛先を返す注文（注文 ID と宛先）
     */
    private final Map<OrderId, String> emails = new HashMap<>();

    /**
     * 出荷通知メールのサービス
     */
    private ShipmentMailCommandService service;

    /**
     * 文言のファイルを読み、配送業者の表示名のキーを返すサービスを作る。
     *
     * <p>注文の腐敗防止層は、注文 ID に対応する宛先を返し、ない注文は例外を投げる。
     */
    @BeforeEach
    void setUp() {
        emails.put(ORDER_ID, EMAIL);
        OrderDirectory orderDirectory = orderId -> {
            var email = emails.get(orderId);
            if (email == null) {
                throw new IllegalStateException("注文がない：" + orderId);
            }
            return email;
        };
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        service = new ShipmentMailCommandService((orderId, kind, recipient, sentAt) -> {
                    recorded.put(orderId, recipient);
                    return true;
                }, sent::add,
                Clock.fixed(SHIPPED_AT, ZoneOffset.UTC),
                new MessageResolver(messageSource), carrier -> switch (carrier) {
                    case YAMATO -> "ecsite.shipping.carrier.yamato";
                    case SAGAWA -> "ecsite.shipping.carrier.sagawa";
                    case JAPAN_POST -> "ecsite.shipping.carrier.japan_post";
                }, orderDirectory);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("宛先と件名")
    class Header {

        @Test
        @DisplayName("AC-183、US-38、DC-572-3 宛先は、注文の腐敗防止層が返す注文のメールアドレス")
        void sendsToOrderEmail() {
            // 操作：ヤマト運輸の出荷を渡す
            var mail = sentMailOf(yamatoShipment());

            // 確認：注文の腐敗防止層が返した宛先（イベントには宛先がない）
            assertThat(mail.to()).isEqualTo(EMAIL);
            assertThat(recorded).containsEntry(ORDER_ID, EMAIL);
        }

        @Test
        @DisplayName("AC-183、US-38 件名に、注文番号が入る")
        void subjectHasOrderNumber() {
            // 操作：ヤマト運輸の出荷を渡す
            var mail = sentMailOf(yamatoShipment());

            // 確認：注文番号がある
            assertThat(mail.subject()).contains(ORDER_NUMBER);
        }
    }

    @Nested
    @DisplayName("本文")
    class Body {

        @Test
        @DisplayName("AC-183、US-38 本文に、注文番号と配送業者の表示名と送り状番号が入る")
        void bodyHasShipment() {
            // 操作：ヤマト運輸の出荷を渡す
            var mail = sentMailOf(yamatoShipment());

            // 確認：出荷の内容がある
            assertThat(mail.body()).contains(ORDER_NUMBER, "ヤマト運輸", TRACKING_NUMBER);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "SAGAWA, 佐川急便",
                "JAPAN_POST, 日本郵便",
        })
        @DisplayName("AC-183、US-38 本文の配送業者は、配送業者ごとの表示名")
        void usesCarrierName(OrderShipped.Carrier carrier, String name) {
            // 準備：配送業者の出荷
            var event = shipment(carrier);

            // 操作と確認：表示名がある
            assertThat(sentMailOf(event).body()).contains(name);
        }
    }

    @Nested
    @DisplayName("注文が見つからないとき")
    class OrderNotFound {

        @Test
        @DisplayName("DC-572-4 送ったメールの記録も送信もせず、例外を投げる")
        void neitherRecordsNorSends() {
            // 準備：注文の腐敗防止層が注文を見つけない
            emails.clear();

            // 操作と確認：例外を投げ、記録も送信もない
            assertThatThrownBy(() -> service.send(yamatoShipment())).isInstanceOf(IllegalStateException.class);
            assertThat(recorded).isEmpty();
            assertThat(sent).isEmpty();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * ヤマト運輸の出荷を、定数から作る。
     *
     * @return 注文の出荷のイベント
     */
    private static OrderShipped yamatoShipment() {
        return shipment(OrderShipped.Carrier.YAMATO);
    }

    /**
     * 配送業者の出荷を、定数から作る。
     *
     * @param carrier 配送業者
     * @return 注文の出荷のイベント
     */
    private static OrderShipped shipment(OrderShipped.Carrier carrier) {
        return OrderShipped.of(SHIPPED_AT, ORDER_ID, ORDER_NUMBER, carrier, TRACKING_NUMBER);
    }

    /**
     * 注文の出荷をサービスに渡し、送ったメールを返す。
     *
     * <p>メールが 1 通だけ送られたことも確認する。
     *
     * @param event 注文の出荷のイベント
     * @return 送ったメール
     */
    private Mailer.Mail sentMailOf(OrderShipped event) {
        sent.clear();
        service.send(event);
        assertThat(sent).hasSize(1);
        return sent.getFirst();
    }
}
