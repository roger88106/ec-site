import { flattenTree } from '@ec-site/shared/lib/categoryTree'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { OptionSelect } from '@/components/OptionSelect'

/** カテゴリを 1 つ選ぶ（絞り込み用）。null は「すべて」。階層は字下げで示す */
export function CategorySelect({
  id,
  value,
  onChange,
}: {
  id?: string
  value: string | null
  onChange: (value: string | null) => void
}) {
  const query = $api.useQuery('get', '/categories')
  const t = useTranslations('components.categorySelect')
  const options = [
    { value: null, label: t('all') /* すべて */ },
    ...flattenTree(query.data?.items ?? []).map((category) => ({
      value: category.id,
      label: `${t('indent').repeat(category.depth - 1)}${category.name}`, // 階層の深さだけ字下げ
    })),
  ]
  return <OptionSelect className="w-48" id={id} onChange={onChange} options={options} value={value} />
}
