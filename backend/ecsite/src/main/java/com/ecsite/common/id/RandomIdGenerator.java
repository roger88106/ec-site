package com.ecsite.common.id;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 乱数による識別子の生成
 *
 * <p>ランダムな UUID（バージョン 4）を返す。
 */
@Component
class RandomIdGenerator implements IdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
