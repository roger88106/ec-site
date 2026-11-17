package com.ecsite.module.shipping.domain;

import java.util.Locale;

/**
 * 配送業者
 */
public enum Carrier {

    /**
     * ヤマト運輸
     */
    YAMATO,

    /**
     * 佐川急便
     */
    SAGAWA,

    /**
     * 日本郵便
     */
    JAPAN_POST;

    /**
     * 表示名の文言のキーを返す。
     *
     * @return 文言のキー（{@code ecsite.shipping.carrier.<小文字の名前>}）
     */
    public String nameMessageKey() {
        return "ecsite.shipping.carrier." + name().toLowerCase(Locale.ROOT);
    }
}
