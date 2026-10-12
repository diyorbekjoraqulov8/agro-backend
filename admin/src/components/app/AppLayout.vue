<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useIntervalFn } from '@vueuse/core'
import {
  BarChart3, CreditCard, FileClock, FolderOpen, Gift, Layers, Megaphone, Route, SlidersHorizontal, LogOut, Map, MessageSquare, ReceiptText, Satellite, Shield, Sprout, UserRound, Users,
} from '@lucide/vue'
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarGroupLabel, SidebarHeader,
  SidebarInset, SidebarMenu, SidebarMenuBadge, SidebarMenuButton, SidebarMenuItem, SidebarProvider, SidebarRail, SidebarTrigger,
} from '@/components/ui/sidebar'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Separator } from '@/components/ui/separator'
import { useAuth } from '@/stores/auth'
import { P } from '@/lib/labels'
import { feedbackApi } from '@/api'

const auth = useAuth()
const route = useRoute()
const router = useRouter()

interface NavItem { to: string; title: string; icon: Component; perm: string; exact?: boolean }

const groups: { label: string; items: NavItem[] }[] = [
  {
    label: 'Asosiy',
    items: [
      { to: '/', title: 'Dashboard', icon: BarChart3, perm: P.FARMER_READ, exact: true },
      { to: '/farmers', title: 'Fermerlar', icon: Users, perm: P.FARMER_READ },
      { to: '/fields', title: 'Dalalar', icon: Map, perm: P.FIELD_READ },
      { to: '/analyses', title: 'Tahlillar', icon: Satellite, perm: P.ANALYSIS_READ },
      { to: '/feedback', title: 'Murojaatlar', icon: MessageSquare, perm: P.FEEDBACK_READ },
      { to: '/notifications', title: 'Bildirishnomalar', icon: Megaphone, perm: P.NOTIFICATION_READ },
    ],
  },
  {
    label: 'Moliya',
    items: [
      { to: '/billing/subscriptions', title: 'Toʻlovlar', icon: ReceiptText, perm: P.BILLING_READ },
      { to: '/billing/plans', title: 'Tariflar', icon: CreditCard, perm: P.BILLING_READ },
      { to: '/referrals', title: 'Takliflar', icon: Gift, perm: P.REFERRAL_READ },
    ],
  },
  {
    label: 'Tizim',
    items: [
      { to: '/onboarding', title: 'Onboarding', icon: Route, perm: P.ONBOARDING_READ },
      { to: '/settings', title: 'Sozlamalar', icon: SlidersHorizontal, perm: P.SETTINGS_READ },
      { to: '/files', title: 'Fayllar', icon: FolderOpen, perm: P.FILE_MANAGE },
      { to: '/users', title: 'Xodimlar', icon: UserRound, perm: P.USER_READ },
      { to: '/roles', title: 'Rollar', icon: Shield, perm: P.ROLE_READ },
      { to: '/audit', title: 'Audit', icon: FileClock, perm: P.AUDIT_READ },
    ],
  },
]

const visibleGroups = computed(() =>
  groups.map((g) => ({ ...g, items: g.items.filter((i) => auth.can(i.perm)) })).filter((g) => g.items.length),
)

// Nishon: navbatdagi murojaatlar + mening suhbatlarimdagi o'qilmaganlar. Har 20 s (sahifa ko'rinib turganda),
// sahifa almashganda va suhbat o'zgarganda yangilanadi — operator boshqa bo'limda bo'lsa ham yangi xabarni ko'radi
const newFeedback = ref(0)
async function loadCounts() {
  if (!auth.can(P.FEEDBACK_READ)) return
  try {
    const c = await feedbackApi.counts()
    newFeedback.value = (c.NEW ?? 0) + (c.MINE_UNREAD ?? 0)
  } catch { /* nishon ixtiyoriy */ }
}
// Fonda ham ishlaydi: operator boshqa tabda bo'lsa, sarlavhadagi "(3)" yangi xabarni bildiradi
const { pause } = useIntervalFn(loadCounts, 20000)
function applyTitle() {
  const base = document.title.replace(/^\(\d+\) /, '')
  document.title = newFeedback.value ? `(${newFeedback.value}) ${base}` : base
}
watch(newFeedback, applyTitle)
const offAfterEach = router.afterEach(() => nextTick(applyTitle))
watch(() => route.path, loadCounts)
onMounted(() => { loadCounts(); window.addEventListener('feedback-changed', loadCounts) })
onBeforeUnmount(() => { pause(); offAfterEach(); window.removeEventListener('feedback-changed', loadCounts) })

function active(to: string, exact?: boolean) {
  return exact ? route.path === to : route.path === to || route.path.startsWith(`${to}/`)
}

const initials = computed(() => (auth.me?.email ?? '?').slice(0, 2).toUpperCase())

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <SidebarProvider>
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" as-child>
              <RouterLink to="/">
                <div class="bg-primary text-primary-foreground flex size-8 items-center justify-center rounded-lg">
                  <Sprout class="size-4" />
                </div>
                <div class="grid flex-1 text-left leading-tight">
                  <span class="truncate font-semibold">Agro</span>
                  <span class="text-muted-foreground truncate text-xs">Boshqaruv paneli</span>
                </div>
              </RouterLink>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup v-for="g in visibleGroups" :key="g.label">
          <SidebarGroupLabel>{{ g.label }}</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem v-for="i in g.items" :key="i.to">
                <SidebarMenuButton as-child :is-active="active(i.to, i.exact)" :tooltip="i.title">
                  <RouterLink :to="i.to">
                    <component :is="i.icon" />
                    <span>{{ i.title }}</span>
                  </RouterLink>
                </SidebarMenuButton>
                <SidebarMenuBadge v-if="i.to === '/feedback' && newFeedback">{{ newFeedback }}</SidebarMenuBadge>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <SidebarMenuButton size="lg">
                  <Avatar class="size-8 rounded-lg"><AvatarFallback class="rounded-lg text-xs">{{ initials }}</AvatarFallback></Avatar>
                  <div class="grid flex-1 text-left text-sm leading-tight">
                    <span class="truncate font-medium">{{ auth.me?.email }}</span>
                    <span class="text-muted-foreground truncate text-xs">{{ auth.me?.roles.join(', ') }}</span>
                  </div>
                </SidebarMenuButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent side="top" align="start" class="w-56">
                <DropdownMenuLabel class="truncate">{{ auth.me?.email }}</DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem @click="router.push('/profile')"><Layers /> Profil va sessiyalar</DropdownMenuItem>
                <DropdownMenuItem variant="destructive" @click="logout"><LogOut /> Chiqish</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>

    <!-- min-w-0: flex element default'da kontentdan torayolmaydi — keng jadval butun sahifani x bo'yicha surardi -->
    <SidebarInset class="min-w-0">
      <header class="bg-background/95 sticky top-0 z-10 flex h-14 shrink-0 items-center gap-2 border-b px-4 backdrop-blur">
        <SidebarTrigger class="-ml-1" />
        <Separator orientation="vertical" class="mr-2 h-4" />
        <span class="text-sm font-medium">{{ route.meta.title }}</span>
      </header>
      <main class="min-w-0 flex-1 p-4 md:p-6">
        <!-- key — /farmers/1 → /farmers/2 da sahifa qayta yaratilsin (aks holda eski id bilan qoladi) -->
        <RouterView :key="route.path" />
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>
