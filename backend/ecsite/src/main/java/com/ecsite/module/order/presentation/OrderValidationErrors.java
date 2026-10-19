package com.ecsite.module.order.presentation;

import com.ecsite.common.error.ErrorDetail;
import com.ecsite.common.error.ValidationErrorMapping;
import com.ecsite.module.order.domain.order.OrderErrorCode;
import org.springframework.stereotype.Component;

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
                "shippingAddress.postalCode", ErrorDetail.of(OrderErrorCode.INVALID_POSTAL_CODE.getCode()),
                "shippingAddress.phone", ErrorDetail.of(OrderErrorCode.INVALID_PHONE_NUMBER.getCode()));
    }
}
