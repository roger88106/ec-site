package com.ecsite.config.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 認可で使う、要求のパスの取り出し。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RequestPaths {

    /**
     * 要求のパスから、コンテキストパスを除いたものを返す。OpenAPI の契約のパスと比べるために使う。
     *
     * @param request 要求
     * @return コンテキストパスを除いたパス
     */
    static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
