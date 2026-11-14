package com.ecsite;

import com.ecsite.module.order.application.payment.OrderPayments;
import com.ecsite.module.payment.event.PaymentAuthorized;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文のテストデータ
 *
 * <p>統合テストで、C 側の API でカートに入れて注文し、mock の決済サービスで支払う。
 * 既定の会員（{@link TestSecurityConfig}）として、会員のカートで注文する。
 * 商品と在庫は、呼ぶ側が先に用意する。
 */
public class OrderTestData {

    /**
     * 既定のお届け先の氏名
     */
    public static final String RECIPIENT_NAME = "山田 太郎";

    /**
     * 既定のお届け先の郵便番号
     */
    public static final String POSTAL_CODE = "100-0001";

    /**
     * 既定のお届け先の都道府県コード（東京都）
     */
    public static final String PREFECTURE_CODE = "13";

    /**
     * 既定のお届け先の市区町村コード（千代田区）
     */
    public static final String MUNICIPALITY_CODE = "13101";

    /**
     * 既定のお届け先の住所（市区町村より後）
     */
    public static final String ADDRESS = "千代田 1-1";

    /**
     * 既定のお届け先の電話番号
     */
    public static final String PHONE = "03-1234-5678";

    /**
     * 送料（税込、円）<br />
     * 注文の設定（{@code OrderProperties.shippingFee}）の既定値。{@code application.yml} と統合テストの設定は上書きしない。
     */
    public static final long SHIPPING_FEE = 1000L;

    /**
     * MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * mock の決済サービス
     */
    private final TestPaymentGateway gateway;

    /**
     * Spring のコンテキスト
     *
     * <p>注文の支払の結果を反映するために使う。
     */
    private final ApplicationContext applicationContext;

    /**
     * 注文 ID → 支払った支払画面 ID
     *
     * <p>支払の後は画面の URL を引けないため、覚えておく。
     */
    private final Map<UUID, String> paidPageIds = new HashMap<>();

    /**
     * 会員のリクエストの後処理
     *
     * <p>補助が会員として行うリクエストに付ける。既定の会員なら何も付けない。
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
     * コンストラクタ（指定した会員として行う）。
     *
     * <p>巻き戻さないテストは、既定の会員のカートに品目を残すと他のテストに影響するので、テストごとの会員にする。
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
     * 会員として、確認した注文の ID を送って注文手続きを始め、201 を確認して、注文手続き ID を返す。
     *
     * @param cartId               カート ID
     * @param acknowledgedOrderIds 確認した支払待ちの注文の ID
     * @return 注文手続き ID
     */
    public UUID startCheckout(UUID cartId, Collection<UUID> acknowledgedOrderIds) throws Exception {
        return startCheckout(cartId, member, acknowledgedOrderIds);
    }

    /**
     * 指定した要求者で、確認した注文の ID を送って注文手続きを始め、201 を確認して、注文手続き ID を返す。
     *
     * @param cartId               カート ID
     * @param requester            token などを付ける後処理
     * @param acknowledgedOrderIds 確認した支払待ちの注文の ID
     * @return 注文手続き ID
     */
    public UUID startCheckout(UUID cartId, RequestPostProcessor requester, Collection<UUID> acknowledgedOrderIds)
            throws Exception {
        return TestJson.createdId(mockMvc.perform(startCheckoutRequest(cartId, acknowledgedOrderIds).with(requester))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /**
     * 会員として、注文手続きの総額を読んで注文し、201 を確認して、注文 ID を返す。
     *
     * @param checkoutId 注文手続き ID
     * @return 注文 ID
     */
    public UUID placeFromCheckout(UUID checkoutId) throws Exception {
        return placeFromCheckout(checkoutId, member);
    }

    /**
     * 指定した要求者で、注文手続きの総額を読んで注文し、201 を確認して、注文 ID を返す。
     *
     * @param checkoutId 注文手続き ID
     * @param requester  token などを付ける後処理
     * @return 注文 ID
     */
    public UUID placeFromCheckout(UUID checkoutId, RequestPostProcessor requester) throws Exception {
        return placeFromCheckout(checkoutId, requester, RECIPIENT_NAME);
    }

    /**
     * 決まったお届け先（氏名：山田 太郎）で注文し、支払って「受付」にする。
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     * @return 注文 ID
     */
    public UUID placeOrder(UUID variantId, int quantity) throws Exception {
        return placeOrder(variantId, quantity, RECIPIENT_NAME);
    }

    /**
     * 氏名を指定して注文し、支払って「受付」にする。
     *
     * <p>注文した品目はカートから除かれるので、続けて注文できる。
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
     * 注文する（支払わない）。
     *
     * <p>注文は「支払待ち」で、カートは変わらない。
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     * @return 注文 ID
     */
    public UUID placePendingOrder(UUID variantId, int quantity) throws Exception {
        return placePendingOrder(variantId, quantity, RECIPIENT_NAME);
    }

    /**
     * 会員のカートで、バリアントごとに 1 個ずつの支払待ちの注文を続けて作り、その ID を作った順に返す（支払わない）。
     *
     * <p>2 件目からは、それまでに作った支払待ちの注文を確認して注文手続きを始める。会員は、ほかに支払待ちの注文を持っていないこと。
     * 支払待ちの注文はカートの品目を残すので、続けて注文できるように、注文した品目をカートから除く。
     * 除いた品目は、後で与信の成功やキャンセルがあっても、カートの内容（バージョン）を変えない。
     * カートは、呼ぶ前に空にしておく（残っている品目も注文に入るため）。商品と在庫は、呼ぶ側が先に用意する。
     *
     * @param cartId     カート ID
     * @param variantIds バリアント ID（注文ごとに 1 つ）
     * @return 作った注文の ID（作った順。呼ぶ側で変えられる一覧）
     */
    public List<UUID> placePendingOrders(UUID cartId, List<UUID> variantIds) throws Exception {
        var pendingIds = new ArrayList<UUID>();
        for (var variantId : variantIds) {
            add(cartId, variantId, 1);
            pendingIds.add(placeFromCheckout(startCheckout(cartId, pendingIds)));
            mockMvc.perform(delete("/api/store/carts/" + cartId + "/items/" + variantId).with(member))
                    .andExpect(status().isNoContent());
        }
        return pendingIds;
    }

    /**
     * 会員が持つ、支払待ちの注文の件数を返す（支払の期限を過ぎ、まだキャンセルされていない注文も数える）。
     *
     * @param dsl      DB の操作
     * @param memberId 会員 ID
     * @return 支払待ちの注文の件数
     */
    public static int pendingOrderCount(DSLContext dsl, UUID memberId) {
        return dsl.fetchCount(SALES_ORDER, SALES_ORDER.MEMBER_ID.eq(memberId).and(SALES_ORDER.STATUS.eq("PENDING_PAYMENT")));
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
     * 支払画面を開いて支払い、注文を「受付」にする。
     *
     * <p>与信の成功のイベントはコミットの後に届くので、ロールバックするテストでは届かない。
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
        return placeFromCheckout(startCheckout(cartId), member, name);
    }

    /**
     * 注文手続きの総額を読み、氏名を指定して注文し、201 を確認して、注文 ID を返す。
     */
    private UUID placeFromCheckout(UUID checkoutId, RequestPostProcessor requester, String name) throws Exception {
        var checkout = mockMvc.perform(get("/api/store/checkouts/" + checkoutId).with(requester))
                .andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        var order = mockMvc.perform(placeOrderRequest(checkoutId, orderBody(total, name)).with(requester))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return TestJson.createdId(order);
    }

    /**
     * 注文の与信の成功のイベント（{@link PaymentAuthorized}）を選ぶ条件を返す。<br />
     * コミットするテストで、支払った後に、与信の成功のリスナーが注文を受け付け直すのを待つときに、
     * {@code asyncEvents.awaitHandled(OrderTestData.paymentAuthorized(orderId))} のように使う。
     * 待たずに出荷やキャンセルをすると、リスナーの更新と楽観ロックで衝突することがある。
     *
     * @param orderId 注文 ID
     * @return イベントの条件
     */
    public static Predicate<Object> paymentAuthorized(UUID orderId) {
        return event -> event instanceof PaymentAuthorized authorized && authorized.orderId().equals(orderId);
    }

    /**
     * 決まったお届け先（{@link #RECIPIENT_NAME} などの定数）で注文する本文。
     *
     * <p>お届け先の項目を確認するテストは、確認する項目を変えた本文をテストの中で書く。
     *
     * @param expectedTotal 確認した総額
     * @return 注文のリクエストの本文
     */
    public static String orderBody(long expectedTotal) {
        return orderBody(expectedTotal, RECIPIENT_NAME);
    }

    /**
     * 氏名を指定して、決まったお届け先で注文する本文。
     *
     * @param expectedTotal 確認した総額
     * @param name          お届け先の氏名
     * @return 注文のリクエストの本文
     */
    public static String orderBody(long expectedTotal, String name) {
        return """
                {"expectedTotal": %d, "shippingAddress": {"name": "%s", "postalCode": "%s",
                 "prefectureCode": "%s", "municipalityCode": "%s", "address": "%s", "phone": "%s"}}"""
                .formatted(expectedTotal, name, POSTAL_CODE, PREFECTURE_CODE, MUNICIPALITY_CODE, ADDRESS, PHONE);
    }

    /**
     * カートから注文手続きを始めるリクエスト。
     *
     * <p>token などは呼ぶ側で追加する。
     *
     * @param cartId カート ID
     * @return リクエスト
     */
    public static MockHttpServletRequestBuilder startCheckoutRequest(UUID cartId) {
        return post("/api/store/carts/" + cartId + "/checkouts");
    }

    /**
     * 確認した注文の ID の一覧を送って、カートから注文手続きを始めるリクエスト。
     *
     * <p>token などは呼ぶ側で追加する。
     *
     * @param cartId               カート ID
     * @param acknowledgedOrderIds 確認した支払待ちの注文の ID
     * @return リクエスト
     */
    public static MockHttpServletRequestBuilder startCheckoutRequest(UUID cartId, Collection<UUID> acknowledgedOrderIds) {
        var ids = acknowledgedOrderIds.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", "));
        return startCheckoutRequest(cartId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"acknowledgedOrderIds\": [" + ids + "]}");
    }

    /**
     * 決まったお届け先で注文するリクエスト。
     *
     * <p>token などは呼ぶ側で追加する。
     *
     * @param checkoutId    注文手続き ID
     * @param expectedTotal 確認した総額
     * @return リクエスト
     */
    public static MockHttpServletRequestBuilder placeOrderRequest(UUID checkoutId, long expectedTotal) {
        return placeOrderRequest(checkoutId, orderBody(expectedTotal));
    }

    /**
     * 本文を指定して注文するリクエスト。
     *
     * @param checkoutId 注文手続き ID
     * @param body       注文のリクエストの本文
     * @return リクエスト
     */
    public static MockHttpServletRequestBuilder placeOrderRequest(UUID checkoutId, String body) {
        return post("/api/store/checkouts/" + checkoutId + "/order").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
