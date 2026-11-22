package com.ecsite.config.event;

import com.ecsite.common.model.base.DomainEvent;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.cart.event.CartChanged;
import com.ecsite.module.catalog.event.ProductPriceChanged;
import com.ecsite.module.order.event.OrderStatusChanged;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.shipping.event.DeliveryStatusChanged;
import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * イベントの記録の JSON の形の統合テスト
 *
 * <p>識別子を持つイベントが、アプリの {@code JsonMapper}（識別子の型の JSON のモジュールを登録したもの）で、
 * 識別子を UUID の文字列にした決まった形の JSON になり、その JSON から同じイベントを読めることを確認する。
 * 識別子の型に替える前後で、記録の形が変わらず、既に記録したイベントを読めることの根拠になる。
 *
 * <p>各モジュールのイベントの代表を 1 つずつ確認する。
 */
@IntegrationTest
class EventRecordJsonTest {

    // ============================== 定数 ==============================

    /**
     * イベント ID
     */
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    /**
     * 注文・商品・カートなど、イベントの対象の ID
     */
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    /**
     * 発生時刻
     */
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 期待の JSON の、全てのイベントに共通の先頭（イベント ID と発生時刻）
     */
    private static final String COMMON = "{\"eventId\":\"11111111-1111-4111-8111-111111111111\",\"occurredAt\":\"2026-10-07T15:00:00Z\",";

    // ============================== Bean ==============================

    @Autowired
    JsonMapper jsonMapper;

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("events")
    @DisplayName("イベントは、識別子を UUID の文字列にした決まった形の JSON に書ける")
    void writesEventWithUuidStrings(DomainEvent event, String expectedJson) {
        // 操作：アプリの JsonMapper で書く
        String json = jsonMapper.writeValueAsString(event);

        // 確認：識別子が UUID の文字列の、決まった形になる
        assertThat(json).isEqualTo(expectedJson);
    }

    @ParameterizedTest(name = "「{0}」")
    @MethodSource("events")
    @DisplayName("決まった形の JSON から、同じイベントを読める")
    void readsEventFromJson(DomainEvent event, String expectedJson) {
        // 操作：決まった形の JSON を読む
        DomainEvent read = jsonMapper.readValue(expectedJson, event.getClass());

        // 確認：同じイベントを読める
        assertThat(read).isEqualTo(event);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 識別子を持つイベントと、書いた JSON の形を返す。
     *
     * @return イベント（名前を付ける）と、期待の JSON
     */
    static Stream<Arguments> events() {
        String targetId = "\"22222222-2222-4222-8222-222222222222\"";
        return Stream.of(
                row("CartChanged", new CartChanged(EVENT_ID, OCCURRED_AT, TARGET_ID, 7L),
                        COMMON + "\"cartId\":" + targetId + ",\"cartVersion\":7}"),
                row("ProductPriceChanged", new ProductPriceChanged(EVENT_ID, OCCURRED_AT, new ProductId(TARGET_ID), Money.of(1000)),
                        COMMON + "\"productId\":" + targetId + ",\"priceExclTax\":{\"yen\":1000}}"),
                row("OrderStatusChanged", new OrderStatusChanged(EVENT_ID, OCCURRED_AT, TARGET_ID, null,
                                OrderStatusChanged.Status.ACCEPTED, null, null),
                        COMMON + "\"orderId\":" + targetId + ",\"from\":null,\"to\":\"ACCEPTED\",\"cancellationKind\":null,\"restocked\":null}"),
                row("PaymentAuthorized", new PaymentAuthorized(EVENT_ID, OCCURRED_AT, TARGET_ID),
                        COMMON + "\"orderId\":" + targetId + "}"),
                row("DeliveryStatusChanged", new DeliveryStatusChanged(EVENT_ID, OCCURRED_AT, TARGET_ID,
                                DeliveryStatusChanged.Status.SHIPPED, DeliveryStatusChanged.Status.IN_TRANSIT),
                        COMMON + "\"orderId\":" + targetId + ",\"from\":\"SHIPPED\",\"to\":\"IN_TRANSIT\"}"));
    }

    /**
     * 名前を付けたイベントと期待の JSON の組を作る。
     *
     * @param name  名前
     * @param event イベント
     * @param json  期待の JSON
     * @return テストの引数
     */
    private static Arguments row(String name, DomainEvent event, String json) {
        return Arguments.of(Named.of(name, event), json);
    }
}
