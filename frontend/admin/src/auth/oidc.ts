import { InMemoryWebStorage, type UserManagerSettings, WebStorageStateStore } from 'oidc-client-ts'

/**
 * IdP（運営者の realm）の設定（管理画面の構成 第 10 節）。<br />
 * 接続先は環境変数 VITE_OIDC_AUTHORITY・VITE_OIDC_CLIENT_ID。既定はローカルの Keycloak。
 * token はメモリーにだけ持つ（userStore）。IdP へ移る間の state と PKCE の値は、sessionStorage に置く（既定は localStorage）。
 */
export const oidcSettings: UserManagerSettings = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/ec-admin',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'ec-admin-web',
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  // access token の期限が近ければ、refresh token で取り直す
  automaticSilentRenew: true,
  // IdP のログインの状態を、隠した iframe で確認しない（第三者の Cookie になり、ブラウザーに止められることがある）
  monitorSession: false,
}
