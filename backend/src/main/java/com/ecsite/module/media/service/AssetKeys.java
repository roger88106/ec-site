package com.ecsite.module.media.service;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ファイルのキーの決まり（plan 001 第 7.1 節）。キーの形はここでだけ決める。
 */
final class AssetKeys {

    /**
     * 範囲に使える文字。キーの区切り（/）を含めさせない
     */
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /**
     * インスタンスを作らせない（static のメソッドだけを持つ）
     */
    private AssetKeys() {
    }

    /**
     * 受付のバケットのキー。範囲を含め、別の範囲の受付を確定できないようにする
     *
     * @param scope    範囲
     * @param uploadId 受付 ID
     * @return {範囲}/{uploadId}
     * @throws IllegalArgumentException 範囲に使えない文字がある
     */
    static String upload(String scope, UUID uploadId) {
        if (scope == null || !SCOPE.matcher(scope).matches()) {
            throw new IllegalArgumentException("範囲は英数字とハイフンで 1〜64 文字にする：" + scope);
        }
        return scope + "/" + uploadId;
    }

    /**
     * 配信のバケットのキー。アセット ID で作り、上書きしない
     *
     * @param assetId アセット ID
     * @param format  形式
     * @return assets/{assetId}.{拡張子}
     */
    static String published(UUID assetId, ImageFormat format) {
        return "assets/" + assetId + "." + format.extension();
    }
}
