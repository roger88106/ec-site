package com.ecsite.contract;

import com.ecsite.module.media.domain.ImageFormat;
import com.ecsite.support.DomainConstants;
import org.jooq.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.ecsite.contract.OpenApiNodes.list;
import static com.ecsite.contract.OpenApiNodes.map;
import static com.ecsite.contract.OpenApiNodes.resolve;
import static com.ecsite.jooq.catalog.Tables.CATEGORY;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.VARIANT;
import static com.ecsite.jooq.catalog.Tables.VARIANT_AXIS_VALUE;
import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER_LINE;
import static com.ecsite.jooq.shipping.Tables.SHIPMENT;
import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 項目の制約が API の契約・ドメインの上限・DB の列の長さで一致することの契約テスト
 *
 * <p>バックエンドの書き方の「形の検証は presentation、業務の規則は domain」に沿う。
 *
 * <p>項目の表はこのクラスの {@link #fields()} と、個別のテストで照合する項目の定数に書く。契約に上限（{@code maxLength}・{@code maxItems}）のある項目を追加したら、
 * 表に追加するか、理由を付けて {@link #NOT_COMPARED} に追加する（追加しないと、逆向きの確認が失敗する）。
 */
class FieldConstraintContractTest {

    // ============================== 定数 ==============================

    /**
     * レポートの写しの明細の表（注文の明細の表と名前が同じなので、別の名前で持つ）
     */
    private static final com.ecsite.jooq.reporting.tables.SalesOrderLine REPORT_LINE =
            com.ecsite.jooq.reporting.Tables.SALES_ORDER_LINE;

    /**
     * 照合する項目の 1 行
     *
     * @param label    項目の論理名
     * @param contract 契約の場所（{@code admin.yaml#CreateProductRequest.name} など。配列の要素は {@code []}、
     *                 共通の引数は {@code admin.yaml#parameters/Size}）。複数あればすべて
     * @param domain   ドメインの上限の定数（{@code クラスの完全修飾名#定数名}）。なければ null
     * @param columns  DB の列。長さのない列（text、int）なら空
     */
    record Row(String label, List<String> contract, String domain, List<Field<?>> columns) {

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * 画像の大きさ（バイト）の項目
     */
    private static final Row IMAGE_BYTES = new Row("画像の大きさ（バイト）",
            List.of("admin.yaml#CreateImageUploadRequest.contentLength"),
            "com.ecsite.module.media.domain.ImageFormat#MAX_BYTES", List.of());

    /**
     * 商品の画像の枚数の項目
     */
    private static final Row IMAGE_COUNT = new Row("商品の画像の枚数", List.of("admin.yaml#ProductDetail.images"),
            "com.ecsite.module.catalog.domain.product.Product#MAX_IMAGES", List.of());

    /**
     * カテゴリの深さの項目
     */
    private static final Row CATEGORY_DEPTH = new Row("カテゴリの深さ",
            List.of("admin.yaml#CategoryNode.depth", "admin.yaml#Category.depth"),
            "com.ecsite.module.catalog.domain.category.Category#MAX_DEPTH", List.of());

    /**
     * 契約に上限（{@code maxLength}・{@code maxItems}）があるが、ドメインの上限・DB の列と照合しない場所
     *
     * <p>鍵は契約の場所（{@link Row#contract()} と同じ形。操作の引数は {@code admin.yaml#GET /products query:code}）、値は理由。
     */
    private static final Map<String, String> NOT_COMPARED = Map.ofEntries(
            entry("admin.yaml#GET /products query:code", "検索の条件（品番の前方一致）。保存しないので、ドメインの上限と DB の列がない"),
            entry("admin.yaml#GET /products query:name", "検索の条件（商品名の部分一致）。保存しないので、ドメインの上限と DB の列がない"),
            entry("admin.yaml#GET /inventory/stock-summaries query:productIds",
                    "一度に問い合わせる商品の数。保存しないので、ドメインの上限と DB の列がない"),
            entry("admin.yaml#GET /orders query:orderNumber", "検索の条件（注文番号の前方一致）。保存しないので、ドメインの上限と DB の列がない"),
            entry("store.yaml#GET /products/search query:q", "検索のキーワード。保存しないので、ドメインの上限と DB の列がない"));

    /**
     * 契約のファイルの名前 → 読み込んだ契約
     */
    private static final Map<String, Map<String, Object>> CONTRACTS = new HashMap<>();

    /**
     * 2 つの API の契約を読む。
     */
    @BeforeAll
    static void loadContracts() {
        for (String file : List.of("admin.yaml", "store.yaml")) {
            CONTRACTS.put(file, Contracts.openApi(file));
        }
    }

    /**
     * 照合する項目の表（個別のテストで照合する項目を除く）を返す。
     *
     * @return 項目の表
     */
    static Stream<Row> fields() {
        return Stream.of(
                new Row("税区分の名称", List.of("admin.yaml#CreateTaxCategoryRequest.name"),
                        "com.ecsite.module.tax.domain.taxcategory.TaxCategory#NAME_MAX_LENGTH", List.of(TAX_CATEGORY.NAME)),
                new Row("カテゴリの名称",
                        List.of("admin.yaml#CreateCategoryRequest.name", "admin.yaml#RenameCategoryRequest.name"),
                        "com.ecsite.module.catalog.domain.category.Category#NAME_MAX_LENGTH", List.of(CATEGORY.NAME)),
                new Row("商品品番", List.of("admin.yaml#ProductCode"),
                        "com.ecsite.module.catalog.domain.product.vo.CodeFormat#MAX_LENGTH",
                        List.of(PRODUCT.PRODUCT_CODE, SALES_ORDER_LINE.PRODUCT_CODE, REPORT_LINE.PRODUCT_CODE)),
                new Row("バリアント品番", List.of("admin.yaml#VariantCode"),
                        "com.ecsite.module.catalog.domain.product.vo.CodeFormat#MAX_LENGTH",
                        List.of(VARIANT.VARIANT_CODE, SALES_ORDER_LINE.VARIANT_CODE, REPORT_LINE.VARIANT_CODE)),
                new Row("商品名",
                        List.of("admin.yaml#CreateProductRequest.name", "admin.yaml#UpdateProductRequest.name"),
                        "com.ecsite.module.catalog.domain.product.Product#NAME_MAX_LENGTH",
                        List.of(PRODUCT.NAME, SALES_ORDER_LINE.PRODUCT_NAME, REPORT_LINE.PRODUCT_NAME)),
                new Row("商品の説明",
                        List.of("admin.yaml#CreateProductRequest.description", "admin.yaml#UpdateProductRequest.description"),
                        "com.ecsite.module.catalog.domain.product.Product#DESCRIPTION_MAX_LENGTH", List.of()),
                new Row("軸の数",
                        List.of("admin.yaml#CreateProductRequest.axes", "admin.yaml#ProductDetail.axes",
                                "admin.yaml#AddVariantRequest.axisValues"),
                        "com.ecsite.module.catalog.domain.product.Product#MAX_AXES", List.of()),
                new Row("軸の名前", List.of("admin.yaml#CreateProductRequest.axes[]"),
                        "com.ecsite.module.catalog.domain.product.Product#AXIS_NAME_MAX_LENGTH", List.of(PRODUCT_AXIS.NAME)),
                new Row("軸の値", List.of("admin.yaml#AddVariantRequest.axisValues[]"),
                        "com.ecsite.module.catalog.domain.product.Product#AXIS_VALUE_MAX_LENGTH",
                        List.of(VARIANT_AXIS_VALUE.VALUE)),
                new Row("在庫の調整のメモ", List.of("admin.yaml#AdjustStockRequest.memo"),
                        "com.ecsite.module.inventory.domain.stock.StockAdjustment#MEMO_MAX_LENGTH",
                        List.of(STOCK_ADJUSTMENT.MEMO)),
                new Row("キャンセルの理由",
                        List.of("admin.yaml#CancelOrderRequest.reason", "store.yaml#RequestOrderCancellationRequest.reason"),
                        "com.ecsite.module.order.domain.order.Order#REASON_MAX_LENGTH",
                        List.of(SALES_ORDER.CANCELLATION_REASON, SALES_ORDER.CANCELLATION_REQUEST_REASON)),
                new Row("送り状番号", List.of("admin.yaml#ShipOrderRequest.trackingNumber"),
                        "com.ecsite.module.shipping.domain.TrackingNumber#MAX_LENGTH", List.of(SHIPMENT.TRACKING_NUMBER)),
                new Row("お届け先の宛名", List.of("store.yaml#ShippingAddressInput.name"),
                        "com.ecsite.module.order.domain.order.vo.ShippingAddress#NAME_MAX_LENGTH",
                        List.of(SALES_ORDER.RECIPIENT_NAME)),
                new Row("お届け先の住所", List.of("store.yaml#ShippingAddressInput.address"),
                        "com.ecsite.module.order.domain.order.vo.ShippingAddress#ADDRESS_MAX_LENGTH",
                        List.of(SALES_ORDER.ADDRESS)),
                new Row("カートの品目の数量", List.of("store.yaml#Quantity"),
                        "com.ecsite.module.cart.domain.Cart#MAX_QUANTITY", List.of()),
                new Row("1 ページの件数", List.of("admin.yaml#parameters/Size", "store.yaml#parameters/Size"),
                        "com.ecsite.common.paging.PageRequest#MAX_SIZE", List.of()));
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("上限の一致")
    class Limits {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.contract.FieldConstraintContractTest#fields")
        @DisplayName("契約の上限、ドメインの上限、DB の列の長さが一致する")
        void limitsAgree(Row row) throws ReflectiveOperationException {
            // 操作と確認：表の項目の、3 か所の上限が一致する
            assertLimitsAgree(row);
        }

        @Test
        @DisplayName("AC-272 画像の大きさの上限が、契約の型の制約にあり、ドメインの上限と一致する")
        void imageBytesAgree() throws ReflectiveOperationException {
            // 操作と確認：画像の大きさの上限が一致する
            assertLimitsAgree(IMAGE_BYTES);
        }

        @Test
        @DisplayName("AC-272 商品の画像の枚数の上限が、契約の型の制約にあり、ドメインの上限と一致する")
        void imageCountAgrees() throws ReflectiveOperationException {
            // 操作と確認：画像の枚数の上限が一致する
            assertLimitsAgree(IMAGE_COUNT);
        }

        @Test
        @DisplayName("AC-273 カテゴリの深さの上限が、契約にあり、ドメインの上限と一致する")
        void categoryDepthAgrees() throws ReflectiveOperationException {
            // 操作と確認：カテゴリの深さの上限が一致する
            assertLimitsAgree(CATEGORY_DEPTH);
        }
    }

    @Nested
    @DisplayName("画像の形式")
    class ImageContentTypes {

        @Test
        @DisplayName("AC-272 画像の受け付けの形式の enum が、受け付ける画像の形式（MIME タイプ）と一致する")
        void imageContentTypesAgree() throws ReflectiveOperationException {
            // 準備：ドメインが受け付ける MIME タイプ
            var field = ImageFormat.class.getDeclaredField("contentTypes");
            field.setAccessible(true);
            List<String> accepted = new ArrayList<>();
            for (ImageFormat format : ImageFormat.values()) {
                for (Object type : (List<?>) field.get(format)) {
                    accepted.add((String) type);
                }
            }

            // 操作：契約の形式の enum を読む
            Map<String, Object> document = CONTRACTS.get("admin.yaml");
            Map<String, Object> request = map(map(map(document.get("components")).get("schemas")).get("CreateImageUploadRequest"));
            Object contract = map(map(request.get("properties")).get("contentType")).get("enum");

            // 確認：型の制約（enum）として書かれ、値が一致する
            assertThat(contract).as("契約の形式の enum").isInstanceOf(List.class);
            assertThat(new ArrayList<Object>((List<?>) contract)).containsExactlyInAnyOrderElementsOf(accepted);
        }
    }

    @Nested
    @DisplayName("表の漏れ（逆向き）")
    class Coverage {

        @Test
        @DisplayName("契約の maxLength・maxItems の全てが、照合する項目の表か、理由付きの除外にある")
        void allContractLimitsAreListed() {
            // 準備：表と除外の場所
            Set<String> listed = new TreeSet<>(NOT_COMPARED.keySet());
            allRows().forEach(row -> listed.addAll(row.contract()));

            // 操作：2 つの契約の maxLength・maxItems の場所を集める
            Set<String> limited = new TreeSet<>();
            CONTRACTS.forEach((file, document) -> limited.addAll(lengthLimitedLocations(file, document)));

            // 確認：集めた場所があり、全て表か除外にある
            assertThat(limited).isNotEmpty();
            assertThat(limited).as("表にも除外にもない場所").isSubsetOf(listed);
        }

        @Test
        @DisplayName("除外の全てが、契約の maxLength・maxItems のある場所（なくなった場所を除外に残さない）")
        void notComparedEntriesExist() {
            // 操作：2 つの契約の maxLength・maxItems の場所を集める
            Set<String> limited = new TreeSet<>();
            CONTRACTS.forEach((file, document) -> limited.addAll(lengthLimitedLocations(file, document)));

            // 確認：除外の全ての場所が、集めた場所にある
            assertThat(limited).containsAll(NOT_COMPARED.keySet());
        }

        @Test
        @DisplayName("場所を集める処理は、スキーマの項目、配列の要素、共通の引数、操作の引数の上限を見つけ、参照の先は参照した場所で数えない")
        void collectsLocations() {
            // 準備：各所に上限を持つ契約
            var document = OpenApiNodes.parse("""
                    paths:
                      /items:
                        get:
                          parameters:
                            - {name: q, in: query, schema: {type: string, maxLength: 100}}
                            - {$ref: '#/components/parameters/Size'}
                    components:
                      parameters:
                        Size: {name: size, in: query, schema: {type: string, maxLength: 3}}
                      schemas:
                        Code: {type: string, maxLength: 32}
                        Item:
                          type: object
                          properties:
                            code: {$ref: '#/components/schemas/Code'}
                            name: {type: string, maxLength: 50}
                            note: {type: string}
                            tags: {type: array, maxItems: 3, items: {type: string, maxLength: 30}}
                    """);

            // 操作：上限のある場所を集める
            var locations = lengthLimitedLocations("x.yaml", document);

            // 確認：上限を書いた 6 か所だけ（参照する Code の上限は、Code の場所で 1 回だけ数える）
            assertThat(locations).containsExactlyInAnyOrder(
                    "x.yaml#GET /items query:q",
                    "x.yaml#parameters/Size",
                    "x.yaml#Code",
                    "x.yaml#Item.name",
                    "x.yaml#Item.tags",
                    "x.yaml#Item.tags[]");
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 項目の上限が、契約・ドメイン・DB の列で一致することを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>契約の全ての場所に上限（文字は maxLength、配列は maxItems、数値は maximum）がある</li>
     *   <li>契約の全ての場所の上限が同じ</li>
     *   <li>ドメインの上限の定数があれば、契約の上限と同じ</li>
     *   <li>DB の列があれば、全ての列の長さが契約の上限と同じ</li>
     * </ul>
     *
     * @param row 照合する項目
     * @throws ReflectiveOperationException ドメインの定数を読めないとき
     */
    private static void assertLimitsAgree(Row row) throws ReflectiveOperationException {
        List<Integer> contractLimits = row.contract().stream().map(FieldConstraintContractTest::contractLimit).toList();
        assertThat(contractLimits).as("契約の上限").doesNotContainNull();
        int limit = contractLimits.getFirst();
        assertThat(contractLimits).as("契約の上限（場所ごと）").containsOnly(limit);

        if (row.domain() != null) {
            assertThat(DomainConstants.read(row.domain()).intValue()).as("ドメインの上限").isEqualTo(limit);
        }
        for (Field<?> column : row.columns()) {
            assertThat(column.getDataType().length()).as("DB の列 " + column).isEqualTo(limit);
        }
    }

    // ============================== 契約の読み方 ==============================

    /**
     * 照合する項目の表の全て（個別のテストで照合する項目を含む）を返す。
     *
     * @return 項目の表
     */
    private static Stream<Row> allRows() {
        return Stream.concat(fields(), Stream.of(IMAGE_BYTES, IMAGE_COUNT, CATEGORY_DEPTH));
    }

    /**
     * 契約の場所から、上限（文字は maxLength、配列は maxItems、数値は maximum）を取り出す。
     *
     * @param location 契約の場所
     * @return 上限。なければ null
     */
    private static Integer contractLimit(String location) {
        String[] fileAndPath = location.split("#", -1);
        Map<String, Object> document = CONTRACTS.get(fileAndPath[0]);
        Map<String, Object> components = map(document.get("components"));
        String path = fileAndPath[1];

        Map<String, Object> schema;
        if (path.startsWith("parameters/")) {
            schema = map(map(map(components.get("parameters")).get(path.substring("parameters/".length()))).get("schema"));
        } else {
            String[] parts = path.split("\\.", -1);
            schema = resolve(document, map(map(components.get("schemas")).get(parts[0])));
            for (int i = 1; i < parts.length; i++) {
                String property = parts[i].replace("[]", "");
                schema = resolve(document, map(map(schema.get("properties")).get(property)));
                if (parts[i].endsWith("[]")) {
                    schema = resolve(document, map(schema.get("items")));
                }
            }
        }
        // 文字は maxLength、配列は maxItems、数値は maximum
        Object limit = schema.containsKey("maxLength") ? schema.get("maxLength")
                : schema.containsKey("maxItems") ? schema.get("maxItems") : schema.get("maximum");
        return limit == null ? null : ((Number) limit).intValue();
    }

    /**
     * 契約のうち、{@code maxLength}・{@code maxItems} のある場所を全て集める。
     *
     * <p>共通のスキーマ、共通の引数、操作の引数・本文・応答のスキーマをたどる。参照（{@code $ref}）の先は、参照の先の場所で 1 回だけ数える。
     *
     * @param file     契約のファイルの名前（場所の接頭辞）
     * @param document 契約の全体
     * @return 場所（{@link Row#contract()} と同じ形）
     */
    private static Set<String> lengthLimitedLocations(String file, Map<String, Object> document) {
        Set<String> locations = new TreeSet<>();
        Map<String, Object> components = map(document.get("components"));
        map(components.get("schemas")).forEach((name, schema) ->
                collect(schema, file + "#" + name, locations));
        map(components.get("parameters")).forEach((name, parameter) ->
                collect(map(parameter).get("schema"), file + "#parameters/" + name, locations));
        for (var operation : OpenApiNodes.operations(document)) {
            String prefix = file + "#" + operation.name();
            List<Object> parameters = new ArrayList<>(list(operation.pathItem().get("parameters")));
            parameters.addAll(list(operation.node().get("parameters")));
            for (Object parameter : parameters) {
                Map<String, Object> p = map(parameter);
                collect(p.get("schema"), prefix + " " + p.get("in") + ":" + p.get("name"), locations);
            }
            for (Object media : map(map(operation.node().get("requestBody")).get("content")).values()) {
                collect(map(media).get("schema"), prefix + " body", locations);
            }
            map(operation.node().get("responses")).forEach((status, response) -> {
                for (Object media : map(map(response).get("content")).values()) {
                    collect(map(media).get("schema"), prefix + " response:" + status, locations);
                }
            });
        }
        return locations;
    }

    /**
     * スキーマをたどり、{@code maxLength}・{@code maxItems} のある場所を集める。
     *
     * <p>参照（{@code $ref}）はたどらない（参照の先は、共通のスキーマとして数える）。
     *
     * @param schema    スキーマ
     * @param location  スキーマの場所
     * @param locations 集める先
     */
    private static void collect(Object schema, String location, Set<String> locations) {
        Map<String, Object> s = map(schema);
        if (s.containsKey("$ref")) {
            return;
        }
        if (s.containsKey("maxLength") || s.containsKey("maxItems")) {
            locations.add(location);
        }
        for (String combinator : List.of("allOf", "oneOf", "anyOf")) {
            for (Object part : list(s.get(combinator))) {
                collect(part, location, locations);
            }
        }
        map(s.get("properties")).forEach((name, property) -> collect(property, location + "." + name, locations));
        if (s.get("items") != null) {
            collect(s.get("items"), location + "[]", locations);
        }
    }
}
