package com.ecsite.common.model.base;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 集約のバージョンの比べ方と、集約を戻すときの失敗の扱い（バックエンドの書き方の「更新の競合は version」）。<br />
 * バージョンは、service が読んだ時点のバージョンで更新の前に比べ、リポジトリが保存のときにもう一度比べる。
 * 前者は更新の処理を始める前に失敗させ、後者は読んだ後に他の人が保存した場合を見つける。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AggregateGuards {

    /**
     * 読んだ時点のバージョンと今のバージョンを比べる。違えば、他の人が先に更新している。
     *
     * @param current  今のバージョン
     * @param expected 読んだ時点のバージョン（リクエストのバージョン）
     */
    public static void requireVersion(long current, long expected) {
        if (current != expected) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    /**
     * 保存した状態から集約を戻す。戻すときに例外が出れば、復元の失敗のシステムの例外にする。<br />
     * 保存した状態は集約の全ての値を含む（集約によっては個人情報を含みうる）ので、メッセージには集約の名前と原因の例外の種類だけを書く。
     * 原因の例外のメッセージにも値が入りうるので、原因は、元の例外の種類とスタックトレースだけを持つ例外に置き換える。
     * 原因の原因と抑制した例外も同じく置き換える。この例外は、HTTP の要求のほか、定期のジョブとイベントのリスナーでも、連鎖ごとログに出るため。
     * 集約の ID も書かない（この補助は共通の部品で、後に加わる集約の ID が利用者を指す値になりうるため）。要求との対応は、ログのトレース ID で追う。
     *
     * @param name     集約の名前（デバッグ用のメッセージに書く。例：`category`）
     * @param restorer 集約を戻す処理
     * @param <T>      集約の型
     * @return 戻した集約
     */
    public static <T> T restore(String name, Supplier<T> restorer) {
        try {
            return restorer.get();
        } catch (Exception e) {
            String debugMessage = "%s restore error: %s".formatted(name, e.getClass().getName());
            throw SystemException.of(debugMessage, redact(e, Collections.newSetFromMap(new IdentityHashMap<>())),
                    CommonErrorCode.RESTORE_ERROR);
        }
    }

    /**
     * 例外を、元の例外の種類とスタックトレースだけを持つ例外に置き換える。原因と抑制した例外も、再帰的に置き換える。
     *
     * @param throwable 置き換える例外
     * @param visited   置き換えた例外（輪になった連鎖で止まるため）
     * @return 置き換えた例外。例外がないか、既に置き換えた例外なら null
     */
    private static @Nullable RedactedException redact(@Nullable Throwable throwable, Set<Throwable> visited) {
        if (throwable == null || !visited.add(throwable)) {
            return null;
        }
        var redacted = new RedactedException(throwable.getClass().getName(), redact(throwable.getCause(), visited));
        redacted.setStackTrace(throwable.getStackTrace());
        for (var suppressed : throwable.getSuppressed()) {
            var redactedSuppressed = redact(suppressed, visited);
            if (redactedSuppressed != null) {
                redacted.addSuppressed(redactedSuppressed);
            }
        }
        return redacted;
    }

    /**
     * 元の例外の種類をメッセージに持ち、元の例外のメッセージを持たない例外。スタックトレースは元の例外のものを入れる。
     */
    private static final class RedactedException extends RuntimeException {

        /**
         * シリアライズのバージョン
         */
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * コンストラクタ。
         *
         * @param typeName 元の例外の種類（クラスの完全修飾名）
         * @param cause    置き換えた原因。なければ null
         */
        private RedactedException(String typeName, @Nullable RedactedException cause) {
            super(typeName, cause, true, true);
        }
    }
}
