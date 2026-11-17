import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { TableRow } from '@/components/ui/table'

/**
 * 詳細へ移る表の行
 *
 * クリックすると詳細の画面へ移る。行の中には {@link RowLink} を置く。
 */
export function LinkRow({ to, children }: { to: string; children: ReactNode }) {
  const navigate = useNavigate()
  return (
    <TableRow className="cursor-pointer" onClick={() => navigate(to)}>
      {children}
    </TableRow>
  )
}

/**
 * 行の中のリンク
 *
 * {@link LinkRow} の中のリンク。行のクリックでも同じ画面へ移るので、リンクのクリックを行に伝えない（二重に移らない）。
 */
export function RowLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link onClick={(event) => event.stopPropagation()} to={to}>
      {children}
    </Link>
  )
}
