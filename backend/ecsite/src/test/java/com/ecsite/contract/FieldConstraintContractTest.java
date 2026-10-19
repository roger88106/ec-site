package com.ecsite.contract;

import com.ecsite.module.media.domain.ImageFormat;
import org.jooq.Field;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.ecsite.jooq.catalog.Tables.CATEGORY;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT_AXIS;
import static com.ecsite.jooq.catalog.Tables.VARIANT;
import static com.ecsite.jooq.catalog.Tables.VARIANT_AXIS_VALUE;
import static com.ecsite.jooq.inventory.Tables.STOCK_ADJUSTMENT;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER_LINE;
import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同じ項目の制約が、API の契約・ドメインの上限・DB の列の長さで一致することを確かめる契約テスト
 * （docs/backend/coding.md 第 3.1 節、spec 005 DC-32）。<br />
 * 項目の表はこのクラスの {@link #fields()} に書く。上限のある項目を足したら、表にも足す。
 */
class FieldConstraintContractTest {

    /**
     * OpenAPI の置き場所。Gradle の test task が設定する。IDE から直接実行した場合は backend/ecsite/ からの相対パス
     */
    private static final Path OPENAPI_DIR = Path.of(System.getProperty("openapi.dir", "../../api/openapi"));

    /**
     * 契約のファイルの名前 → 読み込んだ契約
     */
    private static final Map<String, Map<String, Object>> CONTRACTS = new HashMap<>();

    /**
     * 照らす項目の 1 行。
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
     * 照らす項目の表を返す。
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
                        List.of(PRODUCT.PRODUCT_CODE, SALES_ORDER_LINE.PRODUCT_CODE)),
                new Row("バリアント品番", List.of("admin.yaml#VariantCode"),
                        "com.ecsite.module.catalog.domain.product.vo.CodeFormat#MAX_LENGTH",
                        List.of(VARIANT.VARIANT_CODE, SALES_ORDER_LINE.VARIANT_CODE)),
                new Row("商品名",
                        List.of("admin.yaml#CreateProductRequest.name", "admin.yaml#UpdateProductRequest.name"),
                        "com.ecsite.module.catalog.domain.product.Product#NAME_MAX_LENGTH",
                        List.of(PRODUCT.NAME, SALES_ORDER_LINE.PRODUCT_NAME)),
                new Row("商品の説明",
                        List.of("admin.yaml#CreateProductRequest.description", "admin.yaml#UpdateProductRequest.description"),
                        "com.ecsite.module.catalog.domain.product.Product#DESCRIPTION_MAX_LENGTH", List.of()),
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
                new Row("お届け先の宛名", List.of("store.yaml#ShippingAddressInput.name"),
                        "com.ecsite.module.order.domain.order.vo.ShippingAddress#NAME_MAX_LENGTH",
                        List.of(SALES_ORDER.RECIPIENT_NAME)),
                new Row("お届け先の住所", List.of("store.yaml#ShippingAddressInput.address"),
                        "com.ecsite.module.order.domain.order.vo.ShippingAddress#ADDRESS_MAX_LENGTH",
                        List.of(SALES_ORDER.ADDRESS)),
                new Row("カートの品目の数量", List.of("store.yaml#Quantity"),
                        "com.ecsite.module.cart.domain.Cart#MAX_QUANTITY", List.of()),
                new Row("1 ページの件数", List.of("admin.yaml#parameters/Size", "store.yaml#parameters/Size"),
                        "com.ecsite.common.paging.PageRequest#MAX_SIZE", List.of()),
                new Row("画像の大きさ（バイト）", List.of("admin.yaml#CreateImageUploadRequest.contentLength"),
                        "com.ecsite.module.media.domain.ImageFormat#MAX_BYTES", List.of()),
                new Row("商品の画像の枚数", List.of("admin.yaml#ProductDetail.images"),
                        "com.ecsite.module.catalog.domain.product.Product#MAX_IMAGES", List.of()),
                new Row("カテゴリの深さ", List.of("admin.yaml#CategoryNode.depth", "admin.yaml#Category.depth"),
                        "com.ecsite.module.catalog.domain.category.Category#MAX_DEPTH", List.of()));
    }

    @Test
    @DisplayName("AC-272 画像の受け付けの形式の enum が、受け付ける画像の形式（MIME タイプ）と一致する")
    void imageContentTypesAgree() throws ReflectiveOperationException {
        Map<String, Object> document = CONTRACTS.get("admin.yaml");
        Map<String, Object> request = map(map(map(document.get("components")).get("schemas")).get("CreateImageUploadRequest"));
        Object contract = map(map(request.get("properties")).get("contentType")).get("enum");

        var field = ImageFormat.class.getDeclaredField("contentTypes");
        field.setAccessible(true);
        List<String> accepted = new ArrayList<>();
        for (ImageFormat format : ImageFormat.values()) {
            for (Object type : (List<?>) field.get(format)) {
                accepted.add((String) type);
            }
        }

        assertThat(contract).as("契約の形式の enum").isInstanceOf(List.class);
        List<Object> values = new ArrayList<>((List<?>) contract);
        assertThat(values).containsExactlyInAnyOrderElementsOf(accepted);
    }

    /**
     * 2 つの API の契約を読む
     */
    @BeforeAll
    static void loadContracts() throws IOException {
        for (String file : List.of("admin.yaml", "store.yaml")) {
            CONTRACTS.put(file, new Yaml().load(Files.readString(OPENAPI_DIR.resolve(file))));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fields")
    @DisplayName("AC-272、273 契約の上限、ドメインの上限、DB の列の長さが一致する")
    void limitsAgree(Row row) throws ReflectiveOperationException {
        // 契約の場所ごとの上限（文字は maxLength、数値は maximum）
        List<Integer> contractLimits = row.contract().stream().map(FieldConstraintContractTest::contractLimit).toList();
        assertThat(contractLimits).as("契約の上限").doesNotContainNull();
        int limit = contractLimits.getFirst();
        assertThat(contractLimits).as("契約の上限（場所ごと）").containsOnly(limit);

        if (row.domain() != null) {
            assertThat(domainLimit(row.domain())).as("ドメインの上限").isEqualTo(limit);
        }
        for (Field<?> column : row.columns()) {
            assertThat(column.getDataType().length()).as("DB の列 " + column).isEqualTo(limit);
        }
    }

    /**
     * 契約の場所から、上限（文字は maxLength、数値は maximum）を取り出す。
     *
     * @param location 契約の場所
     * @return 上限。なければ null
     */
    private static Integer contractLimit(String location) {
        String[] fileAndPath = location.split("#");
        Map<String, Object> document = CONTRACTS.get(fileAndPath[0]);
        Map<String, Object> components = map(document.get("components"));
        String path = fileAndPath[1];

        Map<String, Object> schema;
        if (path.startsWith("parameters/")) {
            schema = map(map(map(components.get("parameters")).get(path.substring("parameters/".length()))).get("schema"));
        } else {
            String[] parts = path.split("\\.");
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
     * ドメインの上限の定数の値を読む（package-private の定数を、テストから読むため反射を使う）。
     *
     * @param location {@code クラスの完全修飾名#定数名}
     * @return 定数の値
     * @throws ReflectiveOperationException クラスか定数がない
     */
    private static int domainLimit(String location) throws ReflectiveOperationException {
        String[] classAndField = location.split("#");
        var field = Class.forName(classAndField[0]).getDeclaredField(classAndField[1]);
        field.setAccessible(true);
        return ((Number) field.get(null)).intValue();
    }

    /**
     * {@code $ref}（同じファイルの中への参照）をたどった先を返す。
     *
     * @param document 契約の全体
     * @param node     スキーマ
     * @return 参照でなければそのもの、参照なら参照の先
     */
    private static Map<String, Object> resolve(Map<String, Object> document, Map<String, Object> node) {
        Map<String, Object> current = node;
        while (current.get("$ref") instanceof String ref) {
            Object target = document;
            for (String key : ref.substring(2).split("/")) {
                target = map(target).get(key);
            }
            current = map(target);
        }
        return current;
    }

    /**
     * YAML の値を Map として返す。
     *
     * @param value YAML の値
     * @return Map。null なら空
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }
}
