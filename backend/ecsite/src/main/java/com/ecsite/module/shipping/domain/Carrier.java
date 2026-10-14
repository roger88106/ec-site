package com.ecsite.module.shipping.domain;

/**
 * 配送業者（docs/backend/modules/shipping.md「配送業者」）。運営者が出荷のときに、決まった一覧から選ぶ。
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
    JAPAN_POST,
}
