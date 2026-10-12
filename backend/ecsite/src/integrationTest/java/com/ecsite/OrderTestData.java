package com.ecsite;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 統合テストで、C 側の API でカートに入れて注文する補助（spec 002 のキャンセル・B 側の注文のテストで使う）。<br />
 * 既定の会員（{@link TestSecurityConfig}）として、会員のカートで注文する。
 * 商品と在庫は、呼ぶ側が先に用意する。
 */
public class OrderTestData {

    /**
     * MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * コンストラクタ
     *
     * @param mockMvc MockMvc
     */
    public OrderTestData(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /**
     * 決まったお届け先（氏名：山田 太郎）で注文する。
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     * @return 注文 ID
     */
    public UUID placeOrder(UUID variantId, int quantity) throws Exception {
        return placeOrder(variantId, quantity, "山田 太郎");
    }

    /**
     * 氏名を指定して注文する。
     *
     * @param variantId バリアント ID
     * @param quantity  数量
     * @param name      お届け先の氏名
     * @return 注文 ID
     */
    public UUID placeOrder(UUID variantId, int quantity, String name) throws Exception {
        // 既定の会員（TestSecurityConfig）の会員のカート。注文の後は空になるので、続けて注文できる
        var cartId = UUID.fromString(JsonPath.read(mockMvc.perform(get("/api/store/me/cart")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.cartId"));
        mockMvc.perform(post("/api/store/carts/" + cartId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"variantId": "%s", "quantity": %d}""".formatted(variantId, quantity)))
                .andExpect(status().isNoContent());
        var checkoutId = createdId(mockMvc.perform(post("/api/store/carts/" + cartId + "/checkouts"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var checkout = mockMvc.perform(get("/api/store/checkouts/" + checkoutId)).andReturn().getResponse().getContentAsString();
        long total = JsonPath.<Number>read(checkout, "$.amounts.total").longValue();
        var order = mockMvc.perform(post("/api/store/checkouts/" + checkoutId + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedTotal": %d, "shippingAddress": {"name": "%s",
                                 "postalCode": "1000001", "prefecture": "東京都", "address": "千代田区千代田 1-1",
                                 "phone": "0312345678"}}""".formatted(total, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return createdId(order);
    }

    /**
     * 本文の ID を取り出す
     */
    private static UUID createdId(String body) {
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
