package com.ecsite.config.security;

import com.ecsite.api.admin.MeApi;
import com.ecsite.api.admin.model.Me;
import com.ecsite.common.security.CurrentToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自分の権限の一覧のコントローラー（B 側。admin.yaml の tag: me）。<br />
 * 管理画面がメニューと画面を権限で出し分けるのに使う。権限は token から {@link PermissionConverter} で読む
 * （認可と同じ読み方。Spring Security が追加する権限、たとえば認証の方式を表すものは含めない）。
 */
@RestController
@RequiredArgsConstructor
class MeController implements MeApi {

    /**
     * 認証の設定（権限の role を持つ client）
     */
    private final SecurityProperties properties;

// ============================== GET ==============================

    /**
     * 自分の権限の一覧
     *
     * @return Http 200 : {@link Me}
     */
    @Override
    public ResponseEntity<Me> getMe() {
        var permissions = new PermissionConverter(properties.admin().audience()).convert(CurrentToken.require()).stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        return ResponseEntity.ok(new Me(permissions));
    }
}
