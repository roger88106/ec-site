package com.ecsite.contract;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorCodeProvider;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * エラーのコードの契約テスト（docs/api/conventions.md 第 5 節）。<br />
 * API ごとに、全体の列挙（{@code ErrorCode}）が、操作ごとの一覧（{@code x-error-codes}）と共通のエラー
 * （{@code x-common-error-codes}）の和と一致し、バックエンドのエラーのコードと過不足なく一致することを確かめる。
 */
class ErrorCodeContractTest {

    /**
     * OpenAPI の置き場所。Gradle の test task が設定する。IDE から直接実行した場合は backend/ecsite/ からの相対パス
     */
    private static final Path OPENAPI_DIR = Path.of(System.getProperty("openapi.dir", "../../api/openapi"));

    /**
     * 操作の HTTP メソッド
     */
    private static final Set<String> METHODS = Set.of("get", "post", "put", "delete", "patch");

    /**
     * 契約のファイルの名前 → 読み込んだ契約
     */
    private static final Map<String, Map<String, Object>> CONTRACTS = new HashMap<>();

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
    @ValueSource(strings = {"admin.yaml", "store.yaml"})
    @DisplayName("AC-274 全体の列挙が、操作ごとの一覧と共通のエラーの和と一致し、共通のエラーはバックエンドの共通のエラーと同じ")
    void enumIsUnionOfOperationsAndCommon(String file) {
        Set<String> operations = new TreeSet<>();
        map(CONTRACTS.get(file).get("paths")).values().forEach(path -> map(path).forEach((method, operation) -> {
            if (METHODS.contains(method)) {
                operations.addAll(strings(map(operation).get("x-error-codes")));
            }
        }));
        Set<String> common = new TreeSet<>(strings(errorCodeSchema(file).get("x-common-error-codes")));
        Set<String> union = new TreeSet<>(operations);
        union.addAll(common);

        assertThat(common).as("共通のエラー").isEqualTo(Arrays.stream(CommonErrorCode.values())
                .map(code -> code.getCode().code()).collect(Collectors.toCollection(TreeSet::new)));
        assertThat(enumOf(file)).as("全体の列挙").isEqualTo(union);
    }

    @Test
    @DisplayName("AC-275 バックエンドのすべてのエラーのコードが、どちらかの API の全体の列挙にあり、列挙のすべての値がバックエンドにある")
    void backendCodesAgreeWithContracts() {
        Set<String> backend = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ecsite")
                .stream()
                .filter(javaClass -> javaClass.isEnum() && javaClass.isAssignableTo(ErrorCodeProvider.class))
                .map(JavaClass::reflect)
                .flatMap(type -> Arrays.stream(type.getEnumConstants()))
                .map(constant -> ((ErrorCodeProvider) constant).getCode().code())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> contracts = new TreeSet<>(enumOf("admin.yaml"));
        contracts.addAll(enumOf("store.yaml"));

        assertThat(contracts).as("契約の全体の列挙（2 つの API の和）").isEqualTo(backend);
    }

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
        return value == null ? List.of() : ((List<?>) value).stream().map(String::valueOf).toList();
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
