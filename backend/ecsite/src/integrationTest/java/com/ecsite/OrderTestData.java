package com.ecsite;

import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 統合テストで、C 側の API でカートに入れて注文し、mock の決済サービスで支払う補助（キャンセル・B 側の注文・支払のテストで使う）。<br />
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
     * Spring のコンテキスト（注文の支払の結果を反映するために使う）
     */
    private final ApplicationContext applicationContext;

    /**
     * 注文 ID → 支払った支払画面 ID（支払の後は画面の URL を引けないため、覚えておく）
     */
    private final Map<UUID, String> paidPageIds = new HashMap<>();

    /**
     * 補助が会員として行う要求に付ける後処理（既定の会員なら何も付けない）
     */
    private final RequestPostProcessor member;

    /**
     * コンストラクタ（既定の会員として行う）
     *
     * @param mockMvc            MockMvc
     * @param gateway            mock の決済サービス
     * @param applicationContext Spring のコンテキスト（注文の支払の結果を反映するために使う）
     */
    public OrderTestData(MockMvc mockMvc, TestPaymentGateway gateway, ApplicationContext applicationContext) {
        this(mockMvc, gateway, applicationContext, request -> request);
    }

    /**
     * コンストラクタ（指定した会員として行う）。<br />
     * 巻き戻さないテストは、既定の会員のカートに品目を残すと他のテストに影響するので、テストごとの会員にする。
     *
     * @param mockMvc            MockMvc
     * @param gateway            mock の決済サービス
     * @param applicationContext Spring のコンテキスト（注文の支払の結果を反映するために使う）
     * @param member             会員の token などを付ける後処理
     */
    public OrderTestData(MockMvc mockMvc, TestPaymentGateway gateway, ApplicationContext applicationContext,
                         RequestPostProcessor member) {
        this.mockMvc = mockMvc;
        this.gateway = gateway;
        this.applicationContext = applicationContext;
        this.member = member;
    }

    /**
     * カートと注文手続きの補助だけを使うときのコンストラクタ（支払う補助は使えない）。
     *
     * @param mockMvc MockMvc
     */
    public OrderTestData(MockMvc mockMvc) {
        this(mockMvc, null, null);
    }

    /**
     * 会員のカートの ID を返す（注文手続きは会員のカートからだけ始められる）。
     *
     * @return カート ID
     */
    public UUID cart() throws Exception {
        return cart(member);
    }

    /**
     * 指定した会員の会員のカートの ID を返す。
     *
     * @param requester 会員の token などを付ける後処理
     * @return カート ID
     */
    public UUID cart(RequestPostProcessor requester) throws Exception {
        var body = mockMvc.perform(get("/api/store/me/cart").with(requester)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return TestJson.uuid(body, "$.cartId");
    }

    /**
     * 会員のカートに入れ、204 を確認する。
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @param quantity  数量
     */
    public void add(UUID cartId, UUID variantId, int quantity) throws Exception {
        add(cartId, variantId, quantity, member);
    }

    /**
     * 指定した要求者でカートに入れ、204 を確認する。
     *
     * @param cartId    カート ID
     * @param variantId バリアント ID
     * @param quantity  数量
     * @param requester token などを付ける後処理
     */
    public void add(UUID cartId, UUID variantId, int quantity, RequestPostProcessor requester) throws Exception {
        mockMvc.perform(post("/api/store/carts/" + cartId + "/items").with(requester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)))
                .andExpect(status().isNoContent());
    }

    /**
     * 会員として注文手続きを始め、201 を確認して、注文手続き ID を返す。
     *
     * @param cartId カート ID
     * @return 注文手続き ID
     */
    public UUID startCheckout(UUID cartId) throws Exception {
        return startCheckout(cartId, member);
    }

    /**
     * 指定した要求者で注文手続きを始め、201 を確認して、注文手続き ID を返す。
     *
     * @param cartId    カート ID
     * @param requester token などを付ける後処理
     * @return 注文手続き ID
     */
    public UUID startCheckout(UUID cartId, RequestPostProcessor requester) throws Exception {
        return TestJson.createdId(mockMvc.perform(startCheckoutRequest(cartId).with(requester))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
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
     * 注文の支払画面を開く。
     *
     * @param orderId 注文 ID
     * @return 支払画面 ID
     */
    public String openPaymentPage(UUID orderId) throws Exception {
        var body = mockMvc.perform(post("/api/store/orders/" + orderId + "/payment-page").with(member))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * 支払画面を開いて支払い、注文を「受付」にする。<br />
     * 与信の成功のイベントはコミットの後に届くので、ロールバックするテストでは届かない。
     * そのため、リスナーと同じ処理をここで呼ぶ（冪等なので、コミットするテストで後からイベントが届いても変わらない）。
     *
     * @param orderId 注文 ID
     */
    public void pay(UUID orderId) throws Exception {
        var pageId = openPaymentPage(orderId);
        paidPageIds.put(orderId, pageId);
        gateway.pay(pageId);
        OrderPayments.accept(applicationContext, orderId);
    }

    /**
     * 支払った注文の、支払画面 ID を返す（mock の与信の参照を求めるのに使う）。
     *
     * @param orderId この補助で支払った注文の ID
     * @return 支払画面 ID
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
        await().atMost(TIMEOUT).until(() -> status.equals(JsonPath.read(mockMvc.perform(get("/api/store/orders/" + orderId).with(member))
                .andReturn().getResponse().getContentAsString(), "$.status")));
    }

    /**
     * 氏名を指定して注文する（支払わない）。
     */
    private UUID placePendingOrder(UUID variantId, int quantity, String name) throws Exception {
        var cartId = cart();
        add(cartId, variantId, quantity);
        var checkoutId = startCheckout(cartId);
        var checkout = mockMvc.perform(get("/api/store/checkouts/" + checkoutId).with(member)).andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        var order = mockMvc.perform(placeOrderRequest(checkoutId, orderBody(total, name)).with(member))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(order);
    }

    /**
     * 決まったお届け先で注文する本文（郵便番号は {@code 100-0001}）。<br />
     * お届け先の項目を確認するテストは、確認する項目を変えた本文をテストの中で書く。
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
                 "prefectureCode": "13", "municipalityCode": "13101", "address": "千代田 1-1", "phone": "03-1234-5678"}}"""
                .formatted(expectedTotal, name);
    }

    /**
     * カートから注文手続きを始める要求。token などは呼ぶ側で追加する。
     *
     * @param cartId カート ID
     * @return 要求
     */
    public static MockHttpServletRequestBuilder startCheckoutRequest(UUID cartId) {
        return post("/api/store/carts/" + cartId + "/checkouts");
    }

    /**
     * 決まったお届け先で注文する要求。token などは呼ぶ側で追加する。
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
}
