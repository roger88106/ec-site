package com.ecsite.architecture;

/**
 * 構成の検査で、複数の規則が例外として扱うクラスの名前
 *
 * <p>規則ごとに書くと、片方だけ変えたときに食い違うので、ここに 1 つだけ書く。
 */
final class ArchitectureExceptions {

    /**
     * presentation の外に置く例外の {@code @RestController}
     *
     * <p>どのモジュールにも属さない GET /me。役割は presentation 層と同じ。
     */
    static final String ME_CONTROLLER = "com.ecsite.config.security.MeController";

    /**
     * インスタンスを作らない。
     */
    private ArchitectureExceptions() {
    }
}
