package com.ecsite.config.event;

import java.time.Instant;

/**
 * イベントの発行の記録の検査の例：補助の外で、本番の設定クラスの記録を数える（規則に合わない）
 *
 * <p>{@code EventPublicationRecords} は package-private なので、同じパッケージに置く。
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 * テストのクラスの一覧（{@code TestCodeClasses}）から外してあり、{@code EventPublicationReadTest} の側で読み込む。
 */
public final class ReadsEventPublicationRecords {

    /**
     * インスタンスを作らない。
     */
    private ReadsEventPublicationRecords() {
    }

    /**
     * 未完了のイベントを数える。
     *
     * @param records 記録を読み書きする設定クラス
     * @return 件数
     */
    public static int count(EventPublicationRecords records) {
        return records.countIncompleteNotStopped();
    }

    /**
     * 完了したイベントを消す（名前が find でも count でも始まらない）。
     *
     * @param records 記録を読み書きする設定クラス
     * @return 消した件数
     */
    public static int delete(EventPublicationRecords records) {
        return records.deleteCompletedBefore(Instant.EPOCH);
    }
}
