package com.ecsite;

import com.ecsite.module.catalog.domain.product.PublicationStatus;
import com.ecsite.module.inventory.domain.stock.StockAdjustmentReason;
import com.ecsite.module.media.domain.ImageFormat;
import com.ecsite.module.notification.application.port.SentMailRepository;
import com.ecsite.module.order.domain.order.CancellationKind;
import com.ecsite.module.order.domain.order.Gender;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.reporting.domain.snapshot.DeliveryStatus;
import com.ecsite.module.reporting.domain.snapshot.HistoryKind;
import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import com.ecsite.module.reporting.domain.snapshot.ReportCancellationKind;
import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import com.ecsite.module.shipping.domain.Carrier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * 列挙を文字で持つ列の制約と列挙の一致の検証（T016-09-06、AC-279）。
 */
@IntegrationTest
class EnumColumnConstraintTest {

    /**
     * 列挙を文字で持つ列（schema.テーブル.列）→ 取りうる値の列挙。複数の列挙のどれかの値を持つ列は、すべてを並べる
     */
    private static final Map<String, List<Class<? extends Enum<?>>>> ENUM_COLUMNS = Map.ofEntries(
            entry("catalog.product.status", List.of(PublicationStatus.class)),
            entry("inventory.stock_adjustment.reason", List.of(StockAdjustmentReason.class)),
            entry("media.asset.format", List.of(ImageFormat.class)),
            entry("notification.sent_mail.kind", List.of(SentMailRepository.Kind.class)),
            entry("ordering.sales_order.status", List.of(OrderStatus.class)),
            entry("ordering.sales_order.cancellation_kind", List.of(CancellationKind.class)),
            entry("ordering.sales_order.cancellation_request_result", List.of(CancellationRequest.Result.class)),
            entry("ordering.sales_order.buyer_gender", List.of(Gender.class)),
            entry("payment.payment.status", List.of(PaymentStatus.class)),
            entry("reporting.sales_order.status", List.of(ReportOrderStatus.class)),
            entry("reporting.sales_order.delivery_status", List.of(DeliveryStatus.class)),
            entry("reporting.sales_order.cancellation_kind", List.of(ReportCancellationKind.class)),
            entry("reporting.sales_order.buyer_gender", List.of(ReportBuyerGender.class)),
            entry("reporting.status_history.kind", List.of(HistoryKind.class)),
            // 状態の履歴は、出来事に応じて注文の状態か配送状態を持つ
            entry("reporting.status_history.from_status", List.of(ReportOrderStatus.class, DeliveryStatus.class)),
            entry("reporting.status_history.to_status", List.of(ReportOrderStatus.class, DeliveryStatus.class)),
            entry("reporting.status_history.cancellation_kind", List.of(ReportCancellationKind.class)),
            entry("shipping.shipment.carrier", List.of(Carrier.class)),
            entry("shipping.shipment.status", List.of(com.ecsite.module.shipping.domain.DeliveryStatus.class)));

    /**
     * 名前で列挙とわかる列の名前の形（状態、種類、配送業者、性別、結果、形式、理由）
     */
    private static final Pattern ENUM_LIKE_NAME = Pattern.compile("(^|_)(status|kind|carrier|gender|result|format|reason)$");

    /**
     * 名前は列挙に見えるが、列挙ではない列
     */
    private static final Set<String> NOT_ENUM_COLUMNS = Set.of(
            // 自由な文で書くキャンセルの理由
            "ordering.sales_order.cancellation_reason",
            "ordering.sales_order.cancellation_request_reason",
            // Spring Modulith が書くイベントの発行の記録
            "common.event_publication.status");

    /**
     * 値を並べる形の制約（{@code IN (...)} と {@code = '...'}）
     */
    private static final Pattern VALUE_LIST = Pattern.compile("= ANY \\(\\(?ARRAY\\[|::text = '");

    /**
     * 制約の中の文字の値
     */
    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("AC-279 列挙の列の制約の値が、列挙の値と一致する")
    void constraintValuesMatchEnums() {
        var constrained = constrainedValues();

        // 食い違う列をすべて出す
        assertSoftly(softly -> ENUM_COLUMNS.forEach((column, enums) -> {
            var expected = enums.stream()
                    .flatMap(type -> Arrays.stream(type.getEnumConstants()).map(Enum::name))
                    .collect(Collectors.toCollection(TreeSet::new));
            softly.assertThat(constrained.get(column)).as(column).isEqualTo(expected);
        }));
    }

    @Test
    @DisplayName("AC-279 値を並べる制約の列と、名前で列挙とわかる列が、すべて対応の表にある")
    void everyEnumColumnIsListed() {
        var enumLike = jdbcTemplate.queryForList("""
                SELECT table_schema || '.' || table_name || '.' || column_name
                  FROM information_schema.columns
                 WHERE data_type IN ('character varying', 'text')
                   AND table_schema NOT IN ('pg_catalog', 'information_schema')
                """, String.class).stream()
                .filter(column -> ENUM_LIKE_NAME.matcher(column.substring(column.lastIndexOf('.') + 1)).find());
        var columns = Stream.concat(enumLike, constrainedValues().keySet().stream())
                .filter(column -> !NOT_ENUM_COLUMNS.contains(column))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(columns).isSubsetOf(ENUM_COLUMNS.keySet());
    }

    /**
     * 1 つの列の、値を並べる形の制約の値を読む。
     *
     * @return 列（schema.テーブル.列）→ 制約の値
     */
    private Map<String, Set<String>> constrainedValues() {
        var rows = jdbcTemplate.queryForList("""
                SELECT n.nspname || '.' || c.relname || '.' || a.attname AS column_name,
                       pg_get_constraintdef(con.oid) AS definition
                  FROM pg_constraint con
                  JOIN pg_class c ON c.oid = con.conrelid
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                  JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = con.conkey[1]
                 WHERE con.contype = 'c'
                   AND array_length(con.conkey, 1) = 1
                """);
        var result = new HashMap<String, Set<String>>();
        rows.forEach(row -> {
            var definition = (String) row.get("definition");
            if (!VALUE_LIST.matcher(definition).find()) {
                return;
            }
            var values = result.computeIfAbsent((String) row.get("column_name"), key -> new TreeSet<>());
            LITERAL.matcher(definition).results().forEach(match -> values.add(match.group(1)));
        });
        return result;
    }
}
