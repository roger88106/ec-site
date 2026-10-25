package com.ecsite;

import com.jayway.jsonpath.JsonPath;

import java.util.UUID;

/**
 * 応答の本文（JSON）から値を読む補助。
 */
public final class TestJson {

    /**
     * 補助の関数だけを持つので、作らせない。
     */
    private TestJson() {
    }

    /**
     * 作成の応答の本文から、作ったものの ID（{@code $.id}）を読む。
     *
     * @param body 応答の本文
     * @return ID
     */
    public static UUID createdId(String body) {
        return uuid(body, "$.id");
    }

    /**
     * 応答の本文から、指定した位置の ID を読む。
     *
     * @param body 応答の本文
     * @param path JsonPath の式（例：{@code $.cartId}）
     * @return ID
     */
    public static UUID uuid(String body, String path) {
        return UUID.fromString(JsonPath.read(body, path));
    }
}
