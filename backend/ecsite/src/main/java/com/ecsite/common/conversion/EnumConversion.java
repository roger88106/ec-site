package com.ecsite.common.conversion;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 定数の名前の集合が同じ列挙の間の変換
 *
 * <p>名前で変換してよいのは、この変換のメソッドの中だけである。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EnumConversion {

    /**
     * 変換元の定数と同じ名前の、変換先の定数を返す。
     *
     * @param source     変換元の定数。null は受け取らない
     * @param targetType 変換先の列挙の型
     * @param <S>        変換元の列挙
     * @param <T>        変換先の列挙
     * @return 変換先の定数
     * @throws IllegalArgumentException 変換先に同名の定数がないとき
     */
    public static <S extends Enum<S>, T extends Enum<T>> T byName(S source, Class<T> targetType) {
        var constantName = source.name();
        try {
            return Enum.valueOf(targetType, constantName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "変換先に同名の定数がない：変換元 " + source.getDeclaringClass().getName()
                            + "、変換先 " + targetType.getName() + "、定数 " + constantName, e);
        }
    }
}
