package com.ecsite.common.error;

/**
 * エラータイプ。<br />
 * 業務系の例外（{@link com.ecsite.common.error.exception.BusinessException}）の HTTP ステータスを決める。
 */
public enum ErrorType {

// ==================== ビジネス系 ====================
    /**
     * 検証エラー（400）
     * <p>使用例：</p>
     * <ul>
     *     <li>Domain 層の VO 初期化でエラー発生した場合</li>
     *     <li>入力値のフォーマットチェックでエラー発生する場合</li>
     * </ul>
     */
    VALIDATION_ERROR,

    /**
     * リソースが見つからないエラー（404）
     * <p>使用例：</p>
     * <ul>
     *     <li>リソースが存在しない場合</li>
     * </ul>
     */
    NOT_FOUND_ERROR,

    /**
     * 業務ルール違反エラー（400）
     * <p>使用例：</p>
     * <ul>
     *     <li>バリアントのない商品を公開しようとした場合</li>
     * </ul>
     */
    BUSINESS_RULE_ERROR,

    /**
     * リソースの状態と競合エラー（409）
     * <p>使用例：</p>
     * <ul>
     *     <li>一意であるべき値が重複した場合</li>
     *     <li>楽観排他が発生した場合</li>
     * </ul>
     */
    CONFLICT_ERROR,

// ==================== システム系 ====================

    /**
     * データ復元エラー（500）
     * <p>使用例：</p>
     * <ul>
     *     <li>DB からドメインモデルを復元する際にエラーが発生した場合</li>
     * </ul>
     */
    RESTORE_ERROR,

    /**
     * システムエラー（500）
     * <p>使用例：</p>
     * <ul>
     *     <li>予期しないシステムエラーが発生した場合</li>
     * </ul>
     */
    SYSTEM_ERROR
}
