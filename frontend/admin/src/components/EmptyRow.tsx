import type { ReactNode } from 'react'
import { TableCell, TableRow } from '@/components/ui/table'

/**
 * 空の表の行
 *
 * 表に出す行がないときに、全ての列をまたいで案内の文を出す。
 */
export function EmptyRow({ colSpan, children }: { colSpan: number; children: ReactNode }) {
  return (
    <TableRow>
      <TableCell className="text-muted-foreground text-center" colSpan={colSpan}>
        {children}
      </TableCell>
    </TableRow>
  )
}
