package com.ecsite.common.error;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * エラータイプ
 *
 * <p>エラーの種類ごとに、応答の HTTP ステータスを持つ。
 * {@link #RESTORE_ERROR} より前は {@link BusinessException}、それ以降は {@link SystemException} で投げる。
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ErrorType {

    /**
     * 検証エラー
     *
     * <p>例：値オブジェクトの値が範囲の外、入力の形式の誤り
     */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

    /**
     * リソースが見つからないエラー
     */
    NOT_FOUND_ERROR(HttpStatus.NOT_FOUND),

    /**
     * 業務ルール違反エラー
     *
     * <p>例：バリアントのない商品を公開する
     */
    BUSINESS_RULE_ERROR(HttpStatus.BAD_REQUEST),

    /**
     * リソースの状態との競合エラー
     *
     * <p>例：一意であるべき値の重複、楽観排他
     */
    CONFLICT_ERROR(HttpStatus.CONFLICT),

    /**
     * 認証がないエラー
     *
     * <p>例：token がない、無効、期限切れ
     */
    UNAUTHENTICATED_ERROR(HttpStatus.UNAUTHORIZED),

    /**
     * 権限がないエラー
     *
     * <p>例：操作に必要な権限が token にない
     */
    FORBIDDEN_ERROR(HttpStatus.FORBIDDEN),

    /**
     * データ復元エラー
     *
     * <p>例：DB からドメインモデルを復元できない
     */
    RESTORE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * 外部のサービスに接続できないエラー
     *
     * <p>例：決済サービスが応えず、売上を確定できない（しばらくしてからやり直せる）。
     * メッセージは利用者に見せる。
     */
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY),

    /**
     * システムエラー
     *
     * <p>例：予期しないエラー
     */
    SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    /**
     * 応答の HTTP ステータス
     */
    private final HttpStatus status;
}
