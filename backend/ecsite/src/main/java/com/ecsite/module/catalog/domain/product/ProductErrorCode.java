package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.ErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.common.error.ErrorType;
import lombok.Getter;

/**
 * 商品のエラーコード
 */
public enum ProductErrorCode implements ErrorCodeProvider {

    /**
     * 商品品番の形式が正しくない
     */
    INVALID_PRODUCT_CODE(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * バリアント品番の形式が正しくない
     */
    INVALID_VARIANT_CODE(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 税抜価格が 1 円未満
     */
    INVALID_PRICE_EXCL_TAX(ErrorType.VALIDATION_ERROR),
    /**
     * 商品名が 1 文字以上 200 文字以内でない
     */
    INVALID_PRODUCT_NAME(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 説明が 10,000 文字を超えている
     */
    INVALID_PRODUCT_DESCRIPTION(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * バリアント軸が 3 つを超えている、名前が 1 文字以上 30 文字以内でない、または重複している
     */
    INVALID_VARIANT_AXES(ErrorType.VALIDATION_ERROR, "maxAxes", "max"),
    /**
     * 軸の値が商品の軸と同じ数でない、または 1 文字以上 50 文字以内でない
     */
    INVALID_AXIS_VALUES(ErrorType.VALIDATION_ERROR, "max"),
    /**
     * 公開開始日時が公開終了日時より前でない
     */
    INVALID_PUBLICATION_PERIOD(ErrorType.VALIDATION_ERROR),
    /**
     * 並び順の変更で送られたバリアントが、商品のバリアントと過不足なく一致しない
     */
    INVALID_VARIANT_ORDER(ErrorType.VALIDATION_ERROR),
    /**
     * 並び順の変更で送られた画像が、商品の画像と過不足なく一致しない
     */
    INVALID_IMAGE_ORDER(ErrorType.VALIDATION_ERROR),
    /**
     * バリアントに、その商品にない画像を指定した
     */
    INVALID_VARIANT_IMAGE(ErrorType.VALIDATION_ERROR),
    /**
     * 商品の画像が上限（20 枚）に達している
     */
    TOO_MANY_PRODUCT_IMAGES(ErrorType.BUSINESS_RULE_ERROR, "max"),
    /**
     * 所属カテゴリに存在しないカテゴリが指定された
     */
    INVALID_CATEGORY_ID(ErrorType.VALIDATION_ERROR),
    /**
     * 税区分が存在しないか、現時点で有効な税率を持たない
     */
    TAX_CATEGORY_NOT_ASSIGNABLE(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 商品品番が重複している
     */
    PRODUCT_CODE_ALREADY_EXISTS(ErrorType.CONFLICT_ERROR),
    /**
     * バリアント品番が重複している
     */
    VARIANT_CODE_ALREADY_EXISTS(ErrorType.CONFLICT_ERROR),
    /**
     * 商品の中で軸の値の組み合わせが重複している
     */
    VARIANT_AXIS_VALUES_ALREADY_EXISTS(ErrorType.CONFLICT_ERROR),
    /**
     * 軸を持たない商品に 2 つ目のバリアントを登録しようとした
     */
    SINGLE_VARIANT_ONLY(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * バリアントのない商品を公開しようとした、または公開中の商品の最後のバリアントを削除しようとした
     */
    PRODUCT_HAS_NO_VARIANTS(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * 販売終了の商品を公開・非公開にしようとした
     */
    PRODUCT_DISCONTINUED(ErrorType.BUSINESS_RULE_ERROR),
    /**
     * C 側に表示されたことのある商品・バリアントを削除しようとした
     */
    PRODUCT_ALREADY_SHOWN(ErrorType.BUSINESS_RULE_ERROR),
    ;

    /**
     * エラーコード
     */
    @Getter
    private final ErrorCode code;

    /**
     * コンストラクタ
     *
     * @param errorType エラータイプ
     * @param paramKeys 文に差し込む値の名前
     */
    ProductErrorCode(ErrorType errorType, String... paramKeys) {
        this.code = ErrorCodeProvider.errorCode("catalog", this.name(), errorType, paramKeys);
    }

}
