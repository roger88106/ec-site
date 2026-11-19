package com.ecsite.module.order.presentation;

import com.ecsite.module.order.api.OrderApi;
import com.ecsite.module.order.api.model.OrderSnapshot;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.http.TestJson;
import com.ecsite.support.security.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.SHIRT_QUANTITY;
import static com.ecsite.module.order.presentation.PlaceOrderFixture.TOTAL;
import static com.ecsite.support.data.OrderTestData.placeOrderRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注文時の年齢と性別の受け入れテスト
 *
 * <p>注文の成立のときに、会員の token の生年月日と性別から、注文に年齢と性別を記録することを確認する。
 * 年齢の計算の規則そのものは {@code BuyerProfileTest} が確認する。
 *
 * <p>準備は {@link PlaceOrderFixture}（T シャツ 2 枚のカート）。現在時刻は日本時間の 2026-10-08 00:00 で、
 * 1990-10-08 生まれはこの日に 36 歳になる。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class PlaceOrderBuyerProfileApiTest {

    // ============================== 定数 ==============================

    /**
     * 注文日にちょうど誕生日を迎える生年月日
     */
    private static final String BIRTHDATE = "1990-10-08";

    /**
     * {@link #BIRTHDATE} 生まれの、注文日の年齢
     */
    private static final short AGE = 36;

    /**
     * 後で token に載せる、変えた生年月日
     */
    private static final String CHANGED_BIRTHDATE = "2000-01-01";

    /**
     * {@link #CHANGED_BIRTHDATE} 生まれの、注文日の年齢
     */
    private static final short CHANGED_AGE = 26;

    // ============================== Bean ==============================

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    OrderApi orderApi;

    /**
     * 共有の準備
     */
    private PlaceOrderFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = PlaceOrderFixture.prepare(applicationContext);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("成立のときの記録")
    class AtPlacement {

        @Test
        @DisplayName("AC-212、222 成立のときの token の生年月日と性別から、注文日（日本時間）の年齢と性別を記録する")
        void recordsAgeAndGender() throws Exception {
            // 操作：注文日に 36 歳になる女性の会員として注文する
            var orderId = placeAs(TestTokens.member().birthdate(BIRTHDATE).gender("female"));

            // 確認：年齢が 36 歳、性別が女性
            assertThat(buyer(orderId)).containsExactly(AGE, "FEMALE");
        }

        @Test
        @DisplayName("AC-212、222 後で token の生年月日と性別が変わっても、成立済みの注文の年齢と性別は変わらない")
        void keepsRecordedProfileAfterTokenChange() throws Exception {
            // 準備：女性の会員として注文し、2 件目のための在庫を加える
            var first = placeAs(TestTokens.member().birthdate(BIRTHDATE).gender("female"));
            fixture.inventory.stock(fixture.shirtVariant(), fixture.shirt.id, SHIRT_QUANTITY);

            // 操作：生年月日と性別の変わった token で注文を表示し、2 件目を注文する
            var changed = TestTokens.member().birthdate(CHANGED_BIRTHDATE).gender("male");
            fixture.mockMvc.perform(get("/api/store/orders/" + first).with(changed.bearer())).andExpect(status().isOk());
            var second = placeAs(changed, first);

            // 確認（前提）：2 件目には変わった値が記録される
            assertThat(buyer(second)).containsExactly(CHANGED_AGE, "MALE");

            // 確認：1 件目は変わらない
            assertThat(buyer(first)).containsExactly(AGE, "FEMALE");
        }

        @Test
        @DisplayName("AC-212、222 別の生年月日と性別の token で次の注文をしても、注文の写しの API が返す 1 件目の年齢と性別は変わらない")
        void keepsReportedProfileAfterNextOrder() throws Exception {
            // 準備：女性の会員として注文し、2 件目のための在庫を加える
            var first = placeAs(TestTokens.member().birthdate(BIRTHDATE).gender("female"));
            fixture.inventory.stock(fixture.shirtVariant(), fixture.shirt.id, SHIRT_QUANTITY);

            // 操作：生年月日と性別の変わった token で 2 件目を注文する
            var second = placeAs(TestTokens.member().birthdate(CHANGED_BIRTHDATE).gender("male"), first);

            // 確認（前提）：2 件目の写しは、変わった値
            assertThat(reportedBuyer(second)).containsExactly((int) CHANGED_AGE, OrderSnapshot.Gender.MALE);

            // 確認：1 件目の写しは、注文のときの値のまま
            assertThat(reportedBuyer(first)).containsExactly((int) AGE, OrderSnapshot.Gender.FEMALE);
        }
    }

    @Nested
    @DisplayName("不明として記録する場合")
    class Unknown {

        @Test
        @DisplayName("AC-208、222 token に生年月日と性別がなければ、年齢と性別は不明（空）")
        void recordsUnknownWithoutClaims() throws Exception {
            // 操作：生年月日と性別のない token で注文する
            var orderId = placeAs(TestTokens.member());

            // 確認：年齢も性別も空
            assertThat(buyer(orderId)).containsExactly(null, null);
        }

        @Test
        @DisplayName("AC-213 未来の生年月日は、不明として記録する")
        void recordsUnknownForFutureBirthdate() throws Exception {
            // 操作：未来の生年月日の token で注文する
            var orderId = placeAs(TestTokens.member().birthdate("2030-01-01").gender("female"));

            // 確認：年齢だけが空
            assertThat(buyer(orderId)).containsExactly(null, "FEMALE");
        }

        @Test
        @DisplayName("BR-29 一覧にない性別は、不明として記録する")
        void recordsUnknownForUnlistedGender() throws Exception {
            // 操作：一覧にない性別の token で注文する
            var orderId = placeAs(TestTokens.member().birthdate(BIRTHDATE).gender("unknown"));

            // 確認：性別だけが空
            assertThat(buyer(orderId)).containsExactly(AGE, null);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * token を指定して、確認した注文の一覧を送り、T シャツ 2 枚を注文し、注文 ID を返す。
     *
     * @param token        会員の token
     * @param acknowledged 確認した支払待ちの注文の ID
     * @return 注文 ID
     * @throws Exception API の呼び出しに失敗したとき、または 201 でないとき
     */
    private UUID placeAs(TestTokens.Builder token, UUID... acknowledged) throws Exception {
        var checkoutId = fixture.orders.startCheckout(fixture.cartId, token.bearer(), List.of(acknowledged));
        var body = fixture.mockMvc.perform(placeOrderRequest(checkoutId, TOTAL).with(token.bearer()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * 注文に記録した年齢と性別を返す。
     *
     * @param orderId 注文 ID
     * @return 年齢と性別の並び
     */
    private List<Object> buyer(UUID orderId) {
        var record = fixture.dsl.select(SALES_ORDER.BUYER_AGE, SALES_ORDER.BUYER_GENDER).from(SALES_ORDER)
                .where(SALES_ORDER.ID.eq(orderId)).fetchSingle();
        return Arrays.asList(record.get(SALES_ORDER.BUYER_AGE), record.get(SALES_ORDER.BUYER_GENDER));
    }

    /**
     * 注文の写しの API が返す、年齢と性別を返す。
     *
     * @param orderId 注文 ID
     * @return 年齢と性別の並び
     */
    private List<Object> reportedBuyer(UUID orderId) {
        var snapshot = orderApi.find(orderId).orElseThrow();
        return Arrays.asList(snapshot.buyerAge(), snapshot.buyerGender());
    }
}
