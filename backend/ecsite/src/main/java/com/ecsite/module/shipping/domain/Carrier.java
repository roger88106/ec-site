package com.ecsite.module.shipping.domain;

import java.util.Locale;

/**
 * 配送業者（docs/design/modules/shipping.md「配送業者」）。運営者が出荷のときに、決まった一覧から選ぶ。
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
     * 表示名の文言のキー。<br />
     * キーの形は出荷モジュールの中のここだけで決める。通知のメールは公開 API から、mock の画面はこの操作から得る。
     *
     * @return 文言のキー（{@code ecsite.shipping.carrier.<小文字の名前>}）
     */
    public String nameMessageKey() {
        return "ecsite.shipping.carrier." + name().toLowerCase(Locale.ROOT);
    }
}
