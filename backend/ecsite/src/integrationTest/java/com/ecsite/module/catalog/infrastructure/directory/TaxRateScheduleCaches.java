package com.ecsite.module.catalog.infrastructure.directory;

import org.springframework.context.ApplicationContext;

/**
 * 税率キャッシュ（{@link TaxRateScheduleCache}）を捨てる、統合テストの補助。<br />
 * キャッシュのクラスと破棄の操作は package-private なので、同じパッケージのこのクラスを通して呼ぶ。
 * テストは共通の時計を使い、多くのテストが同じ時刻に設定するので、時計を進めても有効期間が切れない。
 * 税率を変えるテストは、前のテストのキャッシュを読まないよう、準備で捨てる。
 */
public final class TaxRateScheduleCaches {

    private TaxRateScheduleCaches() {
    }

    /**
     * 税率キャッシュを捨てる。次の取得で税モジュールから取得し直す。
     *
     * @param context Spring のコンテキスト（テストに注入したもの）
     */
    public static void evict(ApplicationContext context) {
        context.getBean(TaxRateScheduleCache.class).evict();
    }
}
