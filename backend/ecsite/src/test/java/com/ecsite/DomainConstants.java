package com.ecsite;

/**
 * ドメインの定数の読み取り
 *
 * <p>ドメインの定数をテストから読む。package-private の定数を読むため、反射を使う。
 */
public final class DomainConstants {

    /**
     * インスタンスを作らない。
     */
    private DomainConstants() {
    }

    /**
     * 定数の値を読む。
     *
     * @param location {@code クラスの完全修飾名#定数名}
     * @return 定数の値
     * @throws ReflectiveOperationException クラスか定数がない
     */
    public static Number read(String location) throws ReflectiveOperationException {
        String[] classAndField = location.split("#", -1);
        var field = Class.forName(classAndField[0]).getDeclaredField(classAndField[1]);
        field.setAccessible(true);
        return (Number) field.get(null);
    }
}
