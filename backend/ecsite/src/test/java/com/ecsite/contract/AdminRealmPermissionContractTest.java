package com.ecsite.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理 API の権限と運営者の realm の設定が一致することの契約テスト
 *
 * <p>Keycloak は起動せず、{@code admin.yaml} と realm の JSON（{@code docker/keycloak/realms/ec-admin-realm.json}）を読む。
 */
class AdminRealmPermissionContractTest {

    // ============================== 定数 ==============================

    /**
     * 権限の role を持つ client（管理 API）
     */
    private static final String API_CLIENT = "ec-admin-api";

    /**
     * 運営者がログインする client（token を発行する）
     */
    private static final String WEB_CLIENT = "ec-admin-web";

    /**
     * 権限ではなく、ログインしていればよい操作を表す値（admin.yaml の冒頭の説明）
     */
    private static final String AUTHENTICATED = "authenticated";

    /**
     * レポートの権限の接頭辞
     *
     * <p>閲覧者には付けず、役割ごとに決める。
     */
    private static final String REPORT_PREFIX = "report-";

    /**
     * 売上の推移のレポートの権限
     */
    private static final String SALES_TREND = "report-sales-trend:read";

    /**
     * バリアント別の売上のレポートの権限
     */
    private static final String VARIANT_SALES = "report-variant-sales:read";

    /**
     * 顧客の内訳のレポートの権限
     */
    private static final String CUSTOMER = "report-customer:read";

    /**
     * 契約で宣言した権限の一覧
     */
    private static Set<String> declaredPermissions;

    /**
     * 読み込んだ realm の定義
     */
    private static String realm;

    /**
     * 契約の権限と realm の定義を読む。
     */
    @BeforeAll
    static void load() {
        declaredPermissions = new TreeSet<>();
        for (var operation : OpenApiNodes.operations(Contracts.openApi("admin.yaml"))) {
            if (operation.node().get("x-required-permission") instanceof String permission
                    && !permission.equals(AUTHENTICATED)) {
                declaredPermissions.add(permission);
            }
        }
        realm = Contracts.realm("ec-admin-realm.json");
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("管理 API の client")
    class ApiClient {

        @Test
        @DisplayName("BR-12 admin.yaml の権限と、管理 API の client role が一致する")
        void clientRolesMatchDeclaredPermissions() {
            // 操作：管理 API の client role を読む
            List<String> roles = read("$.roles.client['" + API_CLIENT + "'][*].name");

            // 確認：契約の権限が空でなく、client role と同じ
            assertThat(declaredPermissions).isNotEmpty();
            assertThat(new TreeSet<>(roles)).isEqualTo(declaredPermissions);
        }
    }

    @Nested
    @DisplayName("職務の権限")
    class Roles {

        @Test
        @DisplayName("BR-12 職務の管理者（administrator）は、すべての権限を含む")
        void administratorHasAllPermissions() {
            // 操作と確認：管理者の職務の権限が、契約の全ての権限と同じ
            assertThat(new TreeSet<>(compositePermissions("administrator"))).isEqualTo(declaredPermissions);
        }

        @Test
        @DisplayName("BR-12 職務の閲覧者（viewer）は、レポートを除く参照（:read）の権限すべてを含み、ほかは含まない")
        void viewerHasAllReadPermissions() {
            // 準備：レポートを除く参照の権限
            var readPermissions = readPermissionsWithoutReports();

            // 操作：閲覧者の職務の権限を読む
            var viewer = compositePermissions("viewer");

            // 確認：参照の権限が空でなく、閲覧者の権限と同じ
            assertThat(readPermissions).isNotEmpty();
            assertThat(new TreeSet<>(viewer)).isEqualTo(readPermissions);
        }

        @Test
        @DisplayName("BR-28 管理職（manager）は、閲覧者の権限と、3 つのレポートの権限を含む")
        void managerHasAllReports() {
            // 準備：レポートを除く参照の権限と、3 つのレポートの権限
            var expected = readPermissionsWithoutReports();
            expected.addAll(List.of(SALES_TREND, VARIANT_SALES, CUSTOMER));

            // 操作と確認：管理職の職務の権限が、用意した権限と同じ
            assertThat(new TreeSet<>(compositePermissions("manager"))).isEqualTo(expected);
        }

        @Test
        @DisplayName("BR-28 営業（sales）は、閲覧者の権限と、売上の推移を除くレポートの権限を含む")
        void salesHasReportsExceptSalesTrend() {
            // 準備：レポートを除く参照の権限と、売上の推移を除く 2 つのレポートの権限
            var expected = readPermissionsWithoutReports();
            expected.addAll(List.of(VARIANT_SALES, CUSTOMER));

            // 操作と確認：営業の職務の権限が、用意した権限と同じ
            assertThat(new TreeSet<>(compositePermissions("sales"))).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("運営者がログインする client")
    class WebClient {

        @Test
        @DisplayName("token に、すべての権限が載りうる（client の scope の対応）")
        void webClientScopeIncludesAllPermissions() {
            // 準備：運営者がログインする client の fullScopeAllowed を読む
            List<Boolean> fullScopeAllowed = read("$.clients[?(@.clientId == '" + WEB_CLIENT + "')].fullScopeAllowed");

            // 確認（前提）：fullScopeAllowed が false なので、scope の対応にない role は token に載らない
            assertThat(fullScopeAllowed).containsExactly(false);

            // 操作：運営者がログインする client に対応する、管理 API の client role を読む
            List<List<String>> mapped = read("$.clientScopeMappings['" + API_CLIENT + "'][?(@.client == '" + WEB_CLIENT + "')].roles");

            // 確認：scope の対応が 1 つあり、全ての権限を含む
            assertThat(mapped).hasSize(1);
            assertThat(new TreeSet<>(mapped.getFirst())).isEqualTo(declaredPermissions);
        }
    }

    // ============================== 補助 ==============================

    /**
     * 契約の権限のうち、レポートを除く参照の権限を返す。
     *
     * @return 参照（{@code :read}）の権限のうち、レポートでないもの。呼ぶたびに新しい集合
     */
    private static Set<String> readPermissionsWithoutReports() {
        return new TreeSet<>(declaredPermissions.stream()
                .filter(p -> p.endsWith(":read") && !p.startsWith(REPORT_PREFIX))
                .toList());
    }

    /**
     * 職務（複合の role）が含む、管理 API の client role を返す。
     *
     * @param role 職務の名前
     * @return 職務が含む権限
     */
    private static List<String> compositePermissions(String role) {
        List<List<String>> composites = read("$.roles.realm[?(@.name == '" + role + "')].composites.client['" + API_CLIENT + "']");
        assertThat(composites).as("職務 %s", role).hasSize(1);
        return composites.getFirst();
    }

    /**
     * realm の定義を JSONPath で読む。
     *
     * @param path JSONPath
     * @param <T>  読む値の型
     * @return 読んだ値
     */
    private static <T> T read(String path) {
        return JsonPath.read(realm, path);
    }
}
