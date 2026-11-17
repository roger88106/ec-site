import type { CartMergeNotice } from '@/lib/cartMergeNotice'

/**
 * カートの画面での、カートを移す処理の再試行の結果
 *
 * サーバーアクション retryCartMerge が返し、カートの画面のクライアントコンポーネントが表示する。
 * 'use server' のファイルは非同期の関数しかエクスポートできないので、型はここに置く。
 */
export type CartMergeRetryResult =
  /** 移せた。notice は、切り詰めた品目と移せなかった品目がなければ null */
  | { kind: 'merged'; notice: CartMergeNotice | null }
  /** 一時的な失敗（Cookie のカートの ID は残してある） */
  | { kind: 'failed' }
  /** 再試行する対象がない、または移すカートがない（何も表示しない） */
  | { kind: 'none' }
