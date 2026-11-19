-- 未払いの期限の部分索引を、期限切れの支払の検索用と、与信の反映を待つ支払の検索・件数の集計用に分ける（spec 549）
-- 今の索引 payment_unpaid_deadline_idx は、与信の反映を待ち始めた時刻のある行も含む。期限切れの支払の検索は、その行を読んでから除く。
-- 与信の反映を待つ支払の検索と、与信の反映を長く待つ支払の件数の集計には、待ち始めた時刻の索引がない。
-- 支払 ID を含めるのは、並び順（期限・支払 ID、待ち始めた時刻・支払 ID）のまま索引を読み、件数の上限で止めるため。
-- 今の索引を使う検索は期限切れの支払の検索だけで、新しい索引がその条件を全て含むので、削除する。
-- 索引の削除と追加なので、前のリリースのアプリもそのまま動く。
DROP INDEX payment.payment_unpaid_deadline_idx;

-- 期限切れの支払の検索用（与信の反映を待ち始めた時刻がない行だけ）
CREATE INDEX payment_unpaid_deadline_id_idx ON payment.payment (deadline, id)
    WHERE status = 'UNPAID' AND awaiting_authorization_since IS NULL;
-- 与信の反映を待つ支払の検索と、件数の集計用（待ち始めた時刻がある行だけ）
CREATE INDEX payment_unpaid_awaiting_authorization_since_id_idx ON payment.payment (awaiting_authorization_since, id)
    WHERE status = 'UNPAID' AND awaiting_authorization_since IS NOT NULL;
