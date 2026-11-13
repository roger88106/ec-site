package com.ecsite.common.persistence;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * DB の制約違反の業務のエラーへの変換
 *
 * <p>一意性や外部キーなど、同時に操作されたときに DB の制約でしか守れない規則の違反を、業務のエラーにする。
 *
 * <pre>{@code
 * private static final ConstraintViolationTranslator CONSTRAINTS = ConstraintViolationTranslator.of(Map.of(
 *         "product_product_code_key", () -> BusinessException.of(ProductErrorCode.PRODUCT_CODE_ALREADY_EXISTS)));
 *
 * CONSTRAINTS.run(() -> dsl.insertInto(PRODUCT)...execute());
 * int affected = CONSTRAINTS.call(() -> dsl.update(PRODUCT)...execute());
 * }</pre>
 */
public final class ConstraintViolationTranslator {

    /**
     * 制約名 → 業務のエラー
     */
    private final Map<String, Supplier<? extends BusinessException>> errorsByConstraint;

    /**
     * コンストラクタ。
     *
     * <p>{@link #of} で作る。
     *
     * @param errorsByConstraint 制約名 → 業務のエラーを作る関数
     */
    private ConstraintViolationTranslator(Map<String, Supplier<? extends BusinessException>> errorsByConstraint) {
        this.errorsByConstraint = Map.copyOf(errorsByConstraint);
    }

    /**
     * 制約名と業務のエラーの対応から作る。
     *
     * @param errorsByConstraint 制約名 → 業務のエラーを作る関数
     * @return {@link ConstraintViolationTranslator}
     */
    public static ConstraintViolationTranslator of(Map<String, Supplier<? extends BusinessException>> errorsByConstraint) {
        return new ConstraintViolationTranslator(errorsByConstraint);
    }

    /**
     * 書き込みを実行し、DB アクセスの例外を変換する。
     *
     * <p>Spring と jOOQ の DB アクセスの例外を、{@link #translate} で変換して投げる。それ以外の例外はそのまま投げる。
     *
     * @param write 書き込み
     */
    public void run(Runnable write) {
        try {
            write.run();
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw translate(e);
        }
    }

    /**
     * 書き込みを実行して結果を返し、DB アクセスの例外を変換する。
     *
     * <p>Spring と jOOQ の DB アクセスの例外を、{@link #translate} で変換して投げる。それ以外の例外はそのまま投げる。
     *
     * @param write 書き込み
     * @param <T>   結果の型
     * @return 書き込みの結果（更新した件数など）
     */
    public <T> T call(Supplier<T> write) {
        try {
            return write.get();
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw translate(e);
        }
    }

    /**
     * 例外を変換する。
     *
     * <p>対応が登録された制約の違反なら業務のエラーに、それ以外はシステムエラーにする。
     *
     * @param ex DB アクセスで発生した例外
     * @return 投げる例外
     */
    public RuntimeException translate(RuntimeException ex) {
        return violatedConstraint(ex)
                .map(errorsByConstraint::get)
                .<RuntimeException>map(Supplier::get)
                .orElseGet(() -> SystemException.of("DB アクセスでエラーが発生しました。", ex));
    }

    /**
     * 違反した制約の名前を取り出す。
     *
     * <p>jOOQ や Spring の例外に包まれていても、原因をたどって PostgreSQL のエラーを探す。
     *
     * @param ex 例外
     * @return 制約名。制約違反でなければ空
     */
    static Optional<String> violatedConstraint(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException psqlException) {
                return Optional.ofNullable(psqlException.getServerErrorMessage())
                        .map(ServerErrorMessage::getConstraint);
            }
        }
        return Optional.empty();
    }
}
