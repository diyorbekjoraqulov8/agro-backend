<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { toast } from 'vue-sonner'
import { Inbox, MessagesSquare, Reply, Search, UserRoundCog } from '@lucide/vue'
import ChatPanel from '@/components/feedback/ChatPanel.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { feedbackApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { Feedback, OperatorStats } from '@/api/types'
import { ago, avatarClass, initials, who } from '@/lib/feedback'
import { FEEDBACK_KIND } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * Messenger ko'rinishi: chapda ochiq suhbatlar, o'ngda tanlangani.
 * scope=mine — menga biriktirilganlar; scope=all — barcha operatorlarniki (feedback:manage).
 */
const props = defineProps<{ scope: 'mine' | 'all' }>()
const selected = defineModel<number | null>('selected', { default: null })
const emit = defineEmits<{ toQueue: [] }>()

const auth = useAuth()
const ALL = 'ALL'
const items = ref<Feedback[] | null>(null)
const q = ref('')
const operator = ref<string>(ALL)
const operators = ref<OperatorStats[]>([])
const now = ref(Date.now())

async function load() {
  try {
    const page = await feedbackApi.search({
      status: 'OPEN', mine: props.scope === 'mine' || undefined,
      assigneeId: props.scope === 'all' && operator.value !== ALL ? operator.value : undefined,
      q: q.value.trim() || undefined, size: 100, sort: 'lastMessageAt,desc',
    })
    items.value = page.content
    now.value = Date.now()
  } catch (e) {
    toast.error(errorMessage(e))
  }
}

let timer: ReturnType<typeof setTimeout> | undefined
watch(q, () => { clearTimeout(timer); timer = setTimeout(load, 350) })
watch(operator, load)
watch(() => props.scope, () => { items.value = null; load() })
onMounted(async () => {
  load()
  if (props.scope === 'all') {
    try { operators.value = await feedbackApi.operators() } catch { /* filtr ixtiyoriy */ }
  }
})

const visibility = useDocumentVisibility()
let tick = 0
const { pause } = useIntervalFn(() => { if (visibility.value === 'visible' || ++tick % 5 === 0) load() }, 8000)
onBeforeUnmount(pause)
window.addEventListener('feedback-changed', load)
onBeforeUnmount(() => window.removeEventListener('feedback-changed', load))

// Panel yangilaganda ro'yxatdagi nusxa ham (o'qilmaganlar, oxirgi xabar vaqti)
function onChanged(f: Feedback) {
  if (!items.value) return
  const i = items.value.findIndex((x) => x.id === f.id)
  if (i >= 0) items.value[i] = f
}

const sorted = computed(() => [...(items.value ?? [])].sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt)))
</script>

<template>
  <div class="bg-background grid h-[calc(100svh-17rem)] min-h-[480px] overflow-hidden rounded-xl border lg:grid-cols-[340px_1fr]">
    <!-- Ro'yxat -->
    <aside :class="['flex min-h-0 flex-col border-r', selected ? 'hidden lg:flex' : 'flex']">
      <div class="space-y-2 border-b p-3">
        <div class="relative">
          <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
          <Input v-model="q" placeholder="Fermer yoki xabar…" class="pl-8" />
        </div>
        <Select v-if="scope === 'all'" v-model="operator">
          <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem :value="ALL">Barcha operatorlar</SelectItem>
            <SelectItem v-for="o in operators" :key="o.staffId" :value="String(o.staffId)">{{ o.email }}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto">
        <div v-if="!items" class="space-y-1 p-2">
          <div v-for="i in 5" :key="i" class="flex gap-3 p-2"><Skeleton class="size-10 rounded-full" /><div class="flex-1 space-y-2"><Skeleton class="h-3.5 w-2/3" /><Skeleton class="h-3 w-full" /></div></div>
        </div>

        <div v-else-if="!sorted.length" class="text-muted-foreground flex flex-col items-center gap-3 px-6 py-12 text-center text-sm">
          <Inbox class="size-10 opacity-40" />
          <template v-if="scope === 'mine'">
            <p>Ochiq suhbatlaringiz yoʻq.<br />Navbatdagi murojaatni oling.</p>
            <Button size="sm" variant="outline" @click="emit('toQueue')">Navbatga oʻtish</Button>
          </template>
          <p v-else>Hozir ochiq suhbat yoʻq</p>
        </div>

        <button
          v-for="x in sorted" :key="x.id" type="button"
          :class="['hover:bg-muted/60 flex w-full gap-3 border-b px-3 py-3 text-left transition-colors last:border-b-0',
                   selected === x.id ? 'bg-muted' : '']"
          @click="selected = x.id"
        >
          <div :class="['relative flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold', avatarClass(x.farmerId)]">
            {{ initials(who(x)) }}
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-baseline justify-between gap-2">
              <span :class="['truncate text-sm', x.unreadStaff ? 'font-semibold' : 'font-medium']">{{ who(x) }}</span>
              <span :class="['shrink-0 text-[11px]', x.unreadStaff ? 'text-primary font-semibold' : 'text-muted-foreground']">{{ ago(x.lastMessageAt, now) }}</span>
            </div>
            <div class="mt-0.5 flex items-center gap-2">
              <p class="text-muted-foreground min-w-0 flex-1 truncate text-xs">
                <Reply v-if="x.lastSender === 'STAFF'" class="mr-0.5 inline size-3" />
                <span class="text-foreground/70">{{ FEEDBACK_KIND[x.kind] }}:</span> {{ x.subject }}
              </p>
              <span v-if="x.unreadStaff" class="bg-primary text-primary-foreground flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full px-1.5 text-[11px] font-semibold">
                {{ x.unreadStaff }}
              </span>
            </div>
            <p v-if="scope === 'all' && x.assigneeEmail" class="text-muted-foreground mt-1 flex items-center gap-1 truncate text-[11px]">
              <UserRoundCog class="size-3 shrink-0" />{{ x.assigneeId === auth.me?.id ? 'Siz' : x.assigneeEmail }}
            </p>
          </div>
        </button>
      </div>
    </aside>

    <!-- Suhbat -->
    <section :class="['min-h-0', selected ? 'flex flex-col' : 'hidden lg:flex lg:flex-col']">
      <ChatPanel v-if="selected" :id="selected" show-back @changed="onChanged" @back="selected = null" @gone="selected = null; load()" />
      <div v-else class="text-muted-foreground flex flex-1 flex-col items-center justify-center gap-3 p-6 text-center">
        <div class="bg-muted flex size-16 items-center justify-center rounded-full"><MessagesSquare class="size-7 opacity-60" /></div>
        <p class="text-sm">Suhbatni tanlang</p>
      </div>
    </section>
  </div>
</template>
