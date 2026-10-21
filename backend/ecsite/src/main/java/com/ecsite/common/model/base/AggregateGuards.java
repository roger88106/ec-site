package com.ecsite.common.model.base;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Supplier;

/**
 * 集約の版の比べ方と、集約を戻すときの失敗の扱い（バックエンドの書き方 第 3.5 節）。<br />
 * 版は、service が読んだ時点の版で更新の前に比べ、リポジトリが保存のときにもう一度比べる。
 * 前者は更新の処理を始める前に失敗させ、後者は読んだ後に他の人が保存した場合を見つける。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AggregateGuards {

    /**
     * 読んだ時点の版と今の版を比べる。違えば、他の人が先に更新している。
     *
     * @param current  今の版
     * @param expected 読んだ時点の版（リクエストの版）
     */
    public static void requireVersion(long current, long expected) {
        if (current != expected) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    /**
     * 保存した状態から集約を戻す。戻すときに例外が出れば、復元の失敗のシステムの例外にする。
     *
     * @param name     集約の名前（デバッグ用のメッセージに書く。例：`category`）
     * @param snapshot 保存した状態
     * @param restorer 集約を戻す処理
     * @param <T>      集約の型
     * @return 戻した集約
     */
    public static <T> T restore(String name, Object snapshot, Supplier<T> restorer) {
        try {
            return restorer.get();
        } catch (Exception e) {
            String debugMessage = """
                    %s restore error.
                    snapshot: %s
                    """.formatted(name, snapshot);
            throw SystemException.of(debugMessage, e, CommonErrorCode.RESTORE_ERROR);
        }
    }
}
