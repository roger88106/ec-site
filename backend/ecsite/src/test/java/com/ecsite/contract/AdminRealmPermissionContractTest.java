package com.ecsite.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理 API の権限と、運営者の realm の設定が一致することを確認する契約テスト。<br />
 * Keycloak は起動せず、{@code admin.yaml} と realm の JSON（{@code docker/keycloak/realms/ec-admin-realm.json}）を読む。
 */
class AdminRealmPermissionContractTest {

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
     * レポートの権限の接頭辞。閲覧者には付けず、役割ごとに決める
     */
    private static final String REPORT_PREFIX = "report-";

    private static Set<String> declaredPermissions;
    private static String realm;

    // YAML を読んだ汎用の Map を、契約（OpenAPI）の形が決まっているので、型を当てはめて読む
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() {
        Map<String, Object> spec = Contracts.openApi("admin.yaml");
        var paths = (Map<String, Map<String, Object>>) spec.get("paths");
        declaredPermissions = new TreeSet<>();
        paths.values().forEach(item -> item.values().forEach(value -> {
            if (value instanceof Map<?, ?> operation && operation.get("x-required-permission") instanceof String permission
                    && !permission.equals(AUTHENTICATED)) {
                declaredPermissions.add(permission);
            }
        }));
        realm = Contracts.realm("ec-admin-realm.json");
    }

    @Test
    @DisplayName("BR-12 admin.yaml の権限と、管理 API の client role が一致する")
    void clientRolesMatchDeclaredPermissions() {
        assertThat(declaredPermissions).isNotEmpty();
        List<String> roles = read("$.roles.client['" + API_CLIENT + "'][*].name");
        assertThat(new TreeSet<>(roles))
                .isEqualTo(declaredPermissions);
    }

    @Test
    @DisplayName("BR-12 職務の管理者（administrator）は、すべての権限を含む")
    void administratorHasAllPermissions() {
        assertThat(new TreeSet<>(compositePermissions("administrator"))).isEqualTo(declaredPermissions);
    }

    @Test
    @DisplayName("BR-12 職務の閲覧者（viewer）は、レポートを除く参照（:read）の権限すべてを含み、ほかは含まない")
    void viewerHasAllReadPermissions() {
        var readPermissions = readPermissionsWithoutReports();

        assertThat(readPermissions).isNotEmpty();
        assertThat(new TreeSet<>(compositePermissions("viewer"))).isEqualTo(readPermissions);
    }

    @Test
    @DisplayName("BR-28 管理職（manager）は、閲覧者の権限と、3 つのレポートの権限を含む")
    void managerHasAllReports() {
        var expected = readPermissionsWithoutReports();
        expected.addAll(List.of("report-sales-trend:read", "report-variant-sales:read", "report-customer:read"));

        assertThat(new TreeSet<>(compositePermissions("manager"))).isEqualTo(expected);
    }

    @Test
    @DisplayName("BR-28 営業（sales）は、閲覧者の権限と、売上の推移を除くレポートの権限を含む")
    void salesHasReportsExceptSalesTrend() {
        var expected = readPermissionsWithoutReports();
        expected.addAll(List.of("report-variant-sales:read", "report-customer:read"));

        assertThat(new TreeSet<>(compositePermissions("sales"))).isEqualTo(expected);
    }

    @Test
    @DisplayName("運営者がログインする client の token に、すべての権限が載りうる（client の scope の対応）")
    void webClientScopeIncludesAllPermissions() {
        // ec-admin-web は fullScopeAllowed が false なので、scope の対応にない role は token に載らない
        List<Boolean> fullScopeAllowed = read("$.clients[?(@.clientId == '" + WEB_CLIENT + "')].fullScopeAllowed");
        assertThat(fullScopeAllowed).containsExactly(false);
        List<List<String>> mapped = read("$.clientScopeMappings['" + API_CLIENT + "'][?(@.client == '" + WEB_CLIENT + "')].roles");

        assertThat(mapped).hasSize(1);
        assertThat(new TreeSet<>(mapped.getFirst())).isEqualTo(declaredPermissions);
    }

    private static Set<String> readPermissionsWithoutReports() {
        return new TreeSet<>(declaredPermissions.stream()
                .filter(p -> p.endsWith(":read") && !p.startsWith(REPORT_PREFIX))
                .toList());
    }

    private static List<String> compositePermissions(String role) {
        List<List<String>> composites = read("$.roles.realm[?(@.name == '" + role + "')].composites.client['" + API_CLIENT + "']");
        assertThat(composites).as("職務 %s", role).hasSize(1);
        return composites.getFirst();
    }

    private static <T> T read(String path) {
        return JsonPath.read(realm, path);
    }
}
