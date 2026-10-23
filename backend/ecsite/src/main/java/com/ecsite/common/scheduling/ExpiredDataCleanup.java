package com.ecsite.common.scheduling;

import org.slf4j.Logger;

import java.util.function.IntSupplier;

/**
 * 期限切れのデータの片付けの共通の処理（docs/design/common.md）。<br />
 * カート、在庫の引当、注文手続きの片付けのジョブが使う。
 * 件数の上限ずつ消し、消した件数が上限と等しい間は繰り返す。
 * {@code @Scheduled} と {@code @SchedulerLock} は注釈の定数しか取れないので、ジョブに残す。
 */
public final class ExpiredDataCleanup {

    /**
     * 作らせない
     */
    private ExpiredDataCleanup() {
    }

    /**
     * 件数の上限ずつ消すのを、消した件数が上限に満たなくなるまで繰り返し、合計の件数をログに出す。<br />
     * 合計が 1 件以上なら info、0 件なら debug で出す。ログの文は {@code {}を消した：{} 件}（名詞は引数で渡す）で、渡したロガーに出す。
     *
     * @param log        ログを出すロガー（呼ぶジョブのもの）
     * @param noun       消すものを表す名詞（例：期限切れのカート）
     * @param batchSize  1 回に消す件数の上限
     * @param deleteOnce 上限までを 1 回消して、消した件数を返す処理（1 回ごとに別のトランザクション）
     */
    public static void run(Logger log, String noun, int batchSize, IntSupplier deleteOnce) {
        long total = 0;
        int deleted;
        do {
            deleted = deleteOnce.getAsInt();
            total += deleted;
        } while (deleted == batchSize);
        if (total > 0) {
            log.info("{}を消した：{} 件", noun, total);
        } else {
            log.debug("{}を消した：0 件", noun);
        }
    }
}
