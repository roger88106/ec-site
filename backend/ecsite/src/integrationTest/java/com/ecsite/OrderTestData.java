package com.ecsite;

import com.ecsite.module.order.application.payment.OrderPaymentService;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 統合テストで、C 側の API でカートに入れて注文し、mock の決済サービスで支払う補助（spec 002 のキャンセル・B 側の注文、spec 007 の支払のテストで使う）。<br />
 * 既定の会員（{@link TestSecurityConfig}）として、会員のカートで注文する。
 * 商品と在庫は、呼ぶ側が先に用意する。
 */
public class OrderTestData {

    /**
     * MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * mock の決済サービス
     */
    private final TestPaymentGateway gateway;

    /**
     * 注文の支払の結果のサービス
     */
    private final OrderPaymentService orderPaymentService;

    /**
     * 注文 ID → 支払った支払の画面 ID（支払の後は画面の URL を引けないため、覚えておく）
     */
    private final Map<UUID, String> paidPageIds = new HashMap<>();

    /**
     * コンストラクタ
     *
     * @param mockMvc             MockMvc
     * @param gateway             mock の決済サービス
     * @param orderPaymentService 注文の支払の結果のサービス
     */
    public OrderTestData(MockMvc mockMvc, TestPaymentGateway gateway, OrderPaymentService orderPaymentService) {
        this.mockMvc = mockMvc;
        this.gateway = gateway;
        this.orderPaymentService = orderPaymentService;
    }

    /**
     * 決まったお届け先（氏名：山田 太郎）で注文し、支払って「受付」にする。
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     * @return 注文 ID
     */
    public UUID placeOrder(UUID variantId, int quantity) throws Exception {
        return placeOrder(variantId, quantity, "山田 太郎");
    }

    /**
     * 氏名を指定して注文し、支払って「受付」にする。注文した品目はカートから除かれるので、続けて注文できる。
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     * @param name      お届け先の氏名
     * @return 注文 ID
     */
    public UUID placeOrder(UUID variantId, int quantity, String name) throws Exception {
        var orderId = placePendingOrder(variantId, quantity, name);
        pay(orderId);
        return orderId;
    }

    /**
     * 注文する（支払わない）。注文は「支払待ち」で、カートは変わらない。
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     * @return 注文 ID
     */
    public UUID placePendingOrder(UUID variantId, int quantity) throws Exception {
        return placePendingOrder(variantId, quantity, "山田 太郎");
    }

    /**
     * 注文の支払の画面を開く。
     *
     * @param orderId 注文 ID
     * @return 支払の画面 ID
     */
    public String openPaymentPage(UUID orderId) throws Exception {
        var body = mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * 支払の画面を開いて支払い、注文を「受付」にする。<br />
     * 与信の成功のイベントはコミットの後に届くので、ロールバックするテストでは届かない。
     * そのため、リスナーと同じ処理をここで呼ぶ（冪等なので、コミットするテストで後からイベントが届いても変わらない）。
     *
     * @param orderId 注文 ID
     */
    public void pay(UUID orderId) throws Exception {
        var pageId = openPaymentPage(orderId);
        paidPageIds.put(orderId, pageId);
        gateway.pay(pageId);
        orderPaymentService.accept(orderId);
    }

    /**
     * 支払った注文の、支払の画面 ID を返す（mock の与信の参照を求めるのに使う）。
     *
     * @param orderId この補助で支払った注文の ID
     * @return 支払の画面 ID
     */
    public String paidPageId(UUID orderId) {
        return paidPageIds.get(orderId);
    }

    /**
     * 注文が指定の状態になるのを待つ。
     *
     * @param orderId 注文 ID
     * @param status  注文の状態
     */
    public void awaitStatus(UUID orderId, String status) {
        await().atMost(TIMEOUT).until(() -> status.equals(JsonPath.read(mockMvc.perform(get("/api/store/orders/" + orderId))
                .andReturn().getResponse().getContentAsString(), "$.status")));
    }

    /**
     * 氏名を指定して注文する（支払わない）。
     */
    private UUID placePendingOrder(UUID variantId, int quantity, String name) throws Exception {
        // 既定の会員（TestSecurityConfig）の会員のカート
        var cartId = UUID.fromString(JsonPath.read(mockMvc.perform(get("/api/store/me/cart")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.cartId"));
        mockMvc.perform(post("/api/store/carts/" + cartId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)))
                .andExpect(status().isNoContent());
        var checkoutId = createdId(mockMvc.perform(startCheckoutRequest(cartId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var checkout = mockMvc.perform(get("/api/store/checkouts/" + checkoutId)).andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        var order = mockMvc.perform(placeOrderRequest(checkoutId, orderBody(total, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return createdId(order);
    }

    /**
     * 決まったお届け先で注文する本文（郵便番号は {@code 100-0001}）。<br />
     * お届け先の項目を確かめるテストは、確かめる項目を変えた本文をテストの中で書く。
     *
     * @param expectedTotal 確認した総額
     * @return 注文の要求の本文
     */
    public static String orderBody(long expectedTotal) {
        return orderBody(expectedTotal, "山田 太郎");
    }

    /**
     * 氏名を指定して、決まったお届け先で注文する本文。
     *
     * @param expectedTotal 確認した総額
     * @param name          お届け先の氏名
     * @return 注文の要求の本文
     */
    public static String orderBody(long expectedTotal, String name) {
        return """
                {"expectedTotal": %d, "shippingAddress": {"name": "%s", "postalCode": "100-0001",
                 "prefecture": "東京都", "address": "千代田区千代田 1-1", "phone": "03-1234-5678"}}"""
                .formatted(expectedTotal, name);
    }

    /**
     * カートから注文手続きを始める要求。token などは呼ぶ側で足す。
     *
     * @param cartId カート ID
     * @return 要求
     */
    public static MockHttpServletRequestBuilder startCheckoutRequest(UUID cartId) {
        return post("/api/store/carts/" + cartId + "/checkouts");
    }

    /**
     * 決まったお届け先で注文する要求。token などは呼ぶ側で足す。
     *
     * @param checkoutId    注文手続き ID
     * @param expectedTotal 確認した総額
     * @return 要求
     */
    public static MockHttpServletRequestBuilder placeOrderRequest(UUID checkoutId, long expectedTotal) {
        return placeOrderRequest(checkoutId, orderBody(expectedTotal));
    }

    /**
     * 本文を指定して注文する要求。
     *
     * @param checkoutId 注文手続き ID
     * @param body       注文の要求の本文
     * @return 要求
     */
    public static MockHttpServletRequestBuilder placeOrderRequest(UUID checkoutId, String body) {
        return post("/api/store/checkouts/" + checkoutId + "/order").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /**
     * 本文の ID を取り出す
     */
    private static UUID createdId(String body) {
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
