package com.ecsite.common.persistence;

import com.ecsite.common.error.CommonErrorCode;
import org.postgresql.util.PSQLException;

import java.util.Optional;

/**
 * DB の競合
 *
 * <p>例外の原因（cause）の連鎖にある PostgreSQL のエラーの SQLState で、再試行できる競合かを判定する。
 * 制約の違反の変換（{@link ConstraintViolationTranslator}）を通る書き込みも、通らない読み込みも、同じ判定にする。
 * <ul>
 *   <li>{@value #DEADLOCK_DETECTED}（デッドロック）：{@link CommonErrorCode#CONCURRENT_MODIFICATION}（409）</li>
 *   <li>{@value #LOCK_NOT_AVAILABLE}（ロック取得の失敗。ロック待ちのタイムアウトを含む）：{@link CommonErrorCode#RESOURCE_BUSY}（503）</li>
 * </ul>
 *
 * <p>直列化の失敗（40001）、文の実行のタイムアウト（57014）、接続の切断は、競合として扱わない（システムエラー）。
 *
 * @param errorCode 応答のエラーコード
 * @param sqlState  PostgreSQL のエラーの種類（SQLState）
 */
public record DbConflict(CommonErrorCode errorCode, String sqlState) {

    /**
     * デッドロックの SQLState
     */
    private static final String DEADLOCK_DETECTED = "40P01";

    /**
     * ロック取得の失敗の SQLState
     */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    /**
     * 例外の原因の連鎖から、DB の競合を探す。
     *
     * <p>jOOQ や Spring の例外に包まれていても、原因をたどって最初の PostgreSQL のエラーを見る。
     *
     * @param ex 例外
     * @return DB の競合。競合でなければ空
     */
    public static Optional<DbConflict> of(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException psqlException) {
                return of(psqlException.getSQLState());
            }
        }
        return Optional.empty();
    }

    /**
     * SQLState から、DB の競合を判定する。
     */
    private static Optional<DbConflict> of(String sqlState) {
        return switch (sqlState == null ? "" : sqlState) {
            case DEADLOCK_DETECTED -> Optional.of(new DbConflict(CommonErrorCode.CONCURRENT_MODIFICATION, sqlState));
            case LOCK_NOT_AVAILABLE -> Optional.of(new DbConflict(CommonErrorCode.RESOURCE_BUSY, sqlState));
            default -> Optional.empty();
        };
    }
}
