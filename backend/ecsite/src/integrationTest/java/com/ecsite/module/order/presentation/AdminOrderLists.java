package com.ecsite.module.order.presentation;

import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理の注文の一覧を、条件に合う全てのページから集めて確認するテストの補助。<br />
 * 他のテストの注文が一覧に混ざっても、自分の注文を ID で識別して確認できる。
 */
final class AdminOrderLists {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/orders";

    /**
     * API を呼ぶ MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * コンストラクタ
     *
     * @param mockMvc API を呼ぶ MockMvc
     */
    AdminOrderLists(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /**
     * 条件に合う注文を、全てのページから集めて、一覧の順に返す
     *
     * @param params 条件の名前と値を交互に並べたもの
     */
    List<Map<String, Object>> listAll(String... params) throws Exception {
        var items = new ArrayList<Map<String, Object>>();
        int totalPages = 1;
        for (int page = 1; page <= totalPages; page++) {
            var request = get(BASE).param("page", String.valueOf(page)).param("size", "100");
            for (int i = 0; i < params.length; i += 2) {
                request.param(params[i], params[i + 1]);
            }
            var body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            totalPages = JsonPath.read(body, "$.totalPages");
            List<Map<String, Object>> pageItems = JsonPath.read(body, "$.items");
            items.addAll(pageItems);
        }
        return items;
    }

    /**
     * 行のうち、指定した注文のものの ID を、行の順に返す
     */
    static List<String> idsOf(List<Map<String, Object>> items, UUID... orderIds) {
        var wanted = Arrays.stream(orderIds).map(UUID::toString).collect(Collectors.toSet());
        return items.stream().map(item -> (String) item.get("id")).filter(wanted::contains).toList();
    }

    /**
     * 指定した注文の行
     */
    static Map<String, Object> itemOf(List<Map<String, Object>> items, UUID orderId) {
        return items.stream().filter(item -> orderId.toString().equals(item.get("id"))).findFirst().orElseThrow();
    }
}
