package com.ecsite.common.error;

import java.util.Map;

/**
 * 形の検証の誤りの、業務のエラーへの対応（docs/05-開発規則/backend-coding.md 第 3.3 節）。<br />
 * 業務の検証（ドメイン）が同じ誤りに業務のエラーを持つ項目について、各モジュールの presentation が bean として実装する。
 * 対応のある項目は、形の検証の種類ごとの文の代わりに、業務のエラーの文を返す。
 */
public interface ValidationErrorMapping {

    /**
     * 項目のパスごとの業務のエラー。
     *
     * @return リクエストの本文の項目のパス（例：{@code shippingAddress.postalCode}）から、業務のエラー（差し込む値を含む）への対応
     */
    Map<String, ErrorDetail> errorsByField();
}
