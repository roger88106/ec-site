package com.ecsite.common.id;

import java.util.UUID;

/**
 * 識別子の生成
 */
public interface IdGenerator {

    /**
     * 新しい識別子を作る。
     *
     * @return UUID
     */
    UUID generate();
}
