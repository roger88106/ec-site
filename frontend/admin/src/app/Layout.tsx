import { Button } from '@ec-site/shared/ui/button'
import { LogOutIcon } from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useTranslations } from 'use-intl'
import { menu } from '@/app/menu'
import { useOperator } from '@/auth/operator'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from '@/components/ui/sidebar'

/** 画面の共通の枠（左のメニューと本文。上に運営者の氏名とログアウト。AC-135） */
export function Layout() {
  const { pathname } = useLocation()
  const operator = useOperator()
  const t = useTranslations('app')
  const tCommon = useTranslations('common')
  return (
    <SidebarProvider>
      <Sidebar>
        <SidebarHeader className="p-4">
          <span className="font-heading font-semibold text-lg">{t('layout.title') /* 管理画面の名前 */}</span>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu>
                {menu.map((item) => (
                  <SidebarMenuItem key={item.to}>
                    <SidebarMenuButton isActive={pathname.startsWith(item.to)} render={<NavLink to={item.to} />}>
                      <item.icon />
                      <span>{t(`menu.${item.labelKey}`) /* 機能の名前 */}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
      </Sidebar>
      <SidebarInset>
        <header className="flex h-12 items-center gap-4 border-b px-4">
          <SidebarTrigger />
          <span className="ml-auto text-sm">{operator.name}</span>
          <Button onClick={operator.logout} size="sm" variant="ghost">
            <LogOutIcon />
            {tCommon('logout') /* ログアウト */}
          </Button>
        </header>
        <div className="flex-1 p-6">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
