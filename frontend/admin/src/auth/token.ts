/** API のクライアントが使う、ログインの情報の出どころ（管理画面の構成 第 11 節） */
type TokenSource = {
  /** いまの access token。なければ undefined */
  accessToken: () => string | undefined
  /** API が 401 を返したとき（ログインし直す） */
  onUnauthorized: () => void
}

/** ログインを求める枠（AuthGate）が渡す。結合テストでは渡さない（token を付けず、401 でも何もしない） */
let source: TokenSource | null = null

/** ログインの情報の出どころを渡す。AuthGate から呼ぶ */
export function setTokenSource(next: TokenSource | null): void {
  source = next
}

/** API に付ける access token */
export function currentAccessToken(): string | undefined {
  return source?.accessToken()
}

/** API が 401 を返した（token が無効になった）。ログインし直す */
export function handleUnauthorized(): void {
  source?.onUnauthorized()
}
