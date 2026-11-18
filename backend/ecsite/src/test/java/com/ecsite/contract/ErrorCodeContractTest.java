package com.ecsite.contract;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.support.ProductionClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.ecsite.contract.OpenApiNodes.list;
import static com.ecsite.contract.OpenApiNodes.map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * エラーのコードの契約テスト
 *
 * <p>API の規約の「エラーの本文」に沿う。API ごとに、全体の列挙（{@code ErrorCode}）が、操作ごとの一覧（{@code x-error-codes}）と共通のエラー
 * （{@code x-common-error-codes}）の和と一致し、バックエンドのエラーのコードと過不足なく一致することを確認する。
 */
class ErrorCodeContractTest {

    // ============================== 定数 ==============================

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

    // ============================== テスト ==============================

    @Nested
    @DisplayName("API ごとの全体の列挙")
    class PerApi {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"admin.yaml", "store.yaml"})
        @DisplayName("AC-274 全体の列挙が、操作ごとの一覧と共通のエラーの和と一致する")
        void enumIsUnionOfOperationsAndCommon(String file) {
            // 準備：操作ごとの一覧と共通のエラーの和
            Set<String> union = new TreeSet<>(commonOf(file));
            for (var operation : OpenApiNodes.operations(CONTRACTS.get(file))) {
                union.addAll(strings(operation.node().get("x-error-codes")));
            }

            // 操作と確認：全体の列挙が、用意した和と同じ
            assertThat(enumOf(file)).as("全体の列挙").isEqualTo(union);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"admin.yaml", "store.yaml"})
        @DisplayName("AC-274 全ての操作に x-error-codes がある（空の配列も可）")
        void everyOperationHasErrorCodes(String file) {
            // 準備：x-error-codes のない操作を集める
            List<String> missing = OpenApiNodes.operations(CONTRACTS.get(file)).stream()
                    .filter(operation -> !operation.node().containsKey("x-error-codes"))
                    .map(OpenApiNodes.Operation::name)
                    .toList();

            // 操作と確認：x-error-codes のない操作がない
            assertThat(missing).as("x-error-codes のない操作").isEmpty();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"admin.yaml", "store.yaml"})
        @DisplayName("AC-274 共通のエラーは、バックエンドの共通のエラーと同じ")
        void commonIsBackendCommon(String file) {
            // 準備：バックエンドの共通のエラーのコード
            Set<String> backend = Arrays.stream(CommonErrorCode.values())
                    .map(code -> code.getCode().code()).collect(Collectors.toCollection(TreeSet::new));

            // 操作と確認：契約の共通のエラーが、バックエンドの共通のエラーと同じ
            assertThat(commonOf(file)).as("共通のエラー").isEqualTo(backend);
        }
    }

    @Nested
    @DisplayName("バックエンドとの一致")
    class Backend {

        @Test
        @DisplayName("AC-275 バックエンドのすべてのエラーのコードが、どちらかの API の全体の列挙にあり、列挙のすべての値がバックエンドにある")
        void backendCodesAgreeWithContracts() {
            // 準備：バックエンドの全てのエラーのコード
            Set<String> backend = ProductionClasses.errorCodes().stream()
                    .map(constant -> ((ErrorCodeProvider) constant).getCode().code())
                    .collect(Collectors.toCollection(TreeSet::new));

            // 操作：2 つの API の全体の列挙を合わせる
            Set<String> contracts = new TreeSet<>(enumOf("admin.yaml"));
            contracts.addAll(enumOf("store.yaml"));

            // 確認：合わせた列挙が、バックエンドのエラーのコードと同じ
            assertThat(contracts).as("契約の全体の列挙（2 つの API の和）").isEqualTo(backend);
        }
    }

    // ============================== 補助 ==============================

    /**
     * API の全体の列挙を返す。
     *
     * @param file 契約のファイルの名前
     * @return 全体の列挙の値
     */
    private static Set<String> enumOf(String file) {
        return new TreeSet<>(strings(errorCodeSchema(file).get("enum")));
    }

    /**
     * API の共通のエラーを返す。
     *
     * @param file 契約のファイルの名前
     * @return 共通のエラーのコード
     */
    private static Set<String> commonOf(String file) {
        return new TreeSet<>(strings(errorCodeSchema(file).get("x-common-error-codes")));
    }

    /**
     * API の全体の列挙のスキーマを返す。
     *
     * @param file 契約のファイルの名前
     * @return スキーマ。なければ空
     */
    private static Map<String, Object> errorCodeSchema(String file) {
        return map(map(map(CONTRACTS.get(file).get("components")).get("schemas")).get("ErrorCode"));
    }

    /**
     * YAML の値を文字の一覧として返す。
     *
     * @param value YAML の値
     * @return 文字の一覧。null なら空
     */
    private static List<String> strings(Object value) {
        return list(value).stream().map(String::valueOf).toList();
    }
}
