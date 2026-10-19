-- 列挙を文字で持つ列のうち、取りうる値の制約がなかった列に制約を足す（spec 016 の AC-279）。
-- 値は各モジュールの列挙の名前。列挙と制約が一致することは、統合テスト（EnumColumnConstraintTest）で確かめる。

ALTER TABLE payment.payment ADD CONSTRAINT payment_status_check
    CHECK (status IN ('UNPAID', 'AUTHORIZED', 'VOID_PENDING', 'VOIDED', 'NOT_PAID', 'CAPTURED',
                      'AUTHORIZATION_EXPIRED', 'REFUND_PENDING', 'REFUNDED'));

ALTER TABLE shipping.shipment ADD CONSTRAINT shipment_carrier_check
    CHECK (carrier IN ('YAMATO', 'SAGAWA', 'JAPAN_POST'));
ALTER TABLE shipping.shipment ADD CONSTRAINT shipment_status_check
    CHECK (status IN ('SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'RETURNED'));

ALTER TABLE reporting.sales_order ADD CONSTRAINT sales_order_cancellation_kind_check
    CHECK (cancellation_kind IN ('REQUEST_APPROVED', 'OPERATOR', 'PAYMENT_EXPIRED'));
ALTER TABLE reporting.sales_order ADD CONSTRAINT sales_order_buyer_gender_check
    CHECK (buyer_gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED'));

-- 状態の履歴は、出来事に応じて注文の状態か配送状態を持つ
ALTER TABLE reporting.status_history ADD CONSTRAINT status_history_from_status_check
    CHECK (from_status IN ('PENDING_PAYMENT', 'ACCEPTED', 'CANCELLATION_REQUESTED', 'CANCELLED', 'SHIPPED',
                           'IN_TRANSIT', 'DELIVERED', 'RETURNED'));
ALTER TABLE reporting.status_history ADD CONSTRAINT status_history_to_status_check
    CHECK (to_status IN ('PENDING_PAYMENT', 'ACCEPTED', 'CANCELLATION_REQUESTED', 'CANCELLED', 'SHIPPED',
                         'IN_TRANSIT', 'DELIVERED', 'RETURNED'));
ALTER TABLE reporting.status_history ADD CONSTRAINT status_history_cancellation_kind_check
    CHECK (cancellation_kind IN ('REQUEST_APPROVED', 'OPERATOR', 'PAYMENT_EXPIRED'));

ALTER TABLE media.asset ADD CONSTRAINT asset_format_check
    CHECK (format IN ('JPEG', 'PNG', 'WEBP', 'BMP', 'HEIF', 'AVIF'));
