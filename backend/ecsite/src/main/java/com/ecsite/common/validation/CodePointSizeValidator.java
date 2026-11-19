package com.ecsite.common.validation;

import com.ecsite.common.model.value.BoundedText;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * 文字の項目の {@link Size} を、長さを code point で数えて検証する。
 *
 * <p>標準の実装は UTF-16 の要素の数で数えるため、サロゲートペアの文字が 2 文字に数えられる。
 * {@code String} の項目にはこの実装を使い、ドメインの上限（{@link BoundedText#length}）と同じ数え方にする。
 * サロゲートペアの文字を含む値も、上限以内なら受け付ける。null は有効（{@link Size} の標準と同じ）。
 *
 * <p>Bean Validation の ServiceLoader の仕組みで登録する
 * （{@code META-INF/services/jakarta.validation.ConstraintValidator}）。
 */
public class CodePointSizeValidator implements ConstraintValidator<Size, String> {

    /**
     * 下限の文字の数（code point）
     */
    private int min;

    /**
     * 上限の文字の数（code point）
     */
    private int max;

    @Override
    public void initialize(Size constraint) {
        min = constraint.min();
        max = constraint.max();
    }

    @Override
    public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = BoundedText.length(value);
        return min <= length && length <= max;
    }
}
