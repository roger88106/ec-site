package com.ecsite.module.order.presentation;

import com.ecsite.common.error.ErrorDetail;
import com.ecsite.common.error.ValidationErrorMapping;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 注文の要求の形の誤りの、業務のエラーへの対応。<br />
 * お届け先の郵便番号と電話番号は、契約の形の検証で見つかっても、お届け先（ドメイン）の検証と同じ文を返す。
 */
@Component
class OrderValidationErrors implements ValidationErrorMapping {

    @Override
    public Map<String, ErrorDetail> errorsByField() {
        return Map.of(
                "shippingAddress.postalCode", ErrorDetail.of(OrderErrorCode.INVALID_POSTAL_CODE.getCode(),
                        List.of(ShippingAddress.POSTAL_CODE_DIGITS)),
                "shippingAddress.phone", ErrorDetail.of(OrderErrorCode.INVALID_PHONE_NUMBER.getCode(),
                        List.of(ShippingAddress.PHONE_MIN_DIGITS, ShippingAddress.PHONE_MAX_DIGITS)));
    }
}
