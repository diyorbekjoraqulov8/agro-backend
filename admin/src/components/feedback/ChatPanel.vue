<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { toast } from 'vue-sonner'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import {
  AlertTriangle, ArrowLeft, Check, CheckCheck, CircleCheckBig, FileText, Hand, ImageOff, Loader2, MessageCircleReply, Paperclip,
  SendHorizontal, Upload, UserRoundCog, X,
} from '@lucide/vue'
import ImageLightbox from '@/components/app/ImageLightbox.vue'
import StatusBadge from '@/components/app/StatusBadge.vue'
import Stars from '@/components/feedback/Stars.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { feedbackApi, filesApi } from '@/api'
import { ApiError, errorMessage } from '@/api/http'
import type { Feedback, FeedbackMessage, FileInfo } from '@/api/types'
import { fmtBytes, fmtDateTime } from '@/lib/format'
import { CLOSED_BY, avatarClass, waited, dayLabel, initials, kindTone, statusTone, timeOf, who } from '@/lib/feedback'
import { FEEDBACK_KIND, FEEDBACK_STATUS, LANG, P } from '@/lib/labels'
import { useAuth } from '@/stores/auth'

/**
 * Bitta suhbat: sarlavha (fermer, holat, amallar) · xabarlar · yozish maydoni.
 * Yangi xabarlar polling bilan (4 s, sahifa ko'rinib turganda) — WebSocket shart emas: operator
 * bir vaqtda bitta suhbatga qaraydi, 4 s kechikish chat uchun sezilmaydi.
 */
const props = defineProps<{ id: number; showBack?: boolean; closable?: boolean }>()
const emit = defineEmits<{ changed: [Feedback]; back: []; gone: [] }>()

const auth = useAuth()
const meId = computed(() => auth.me?.id)
const canReply = computed(() => auth.can(P.FEEDBACK_REPLY))
const canManage = computed(() => auth.can(P.FEEDBACK_MANAGE))

const fb = ref<Feedback | null>(null)
const msgs = ref<FeedbackMessage[]>([])
const loading = ref(true)
const text = ref('')
const sending = ref(false)
const busy = ref(false)
const confirmClose = ref(false)
const scroller = ref<HTMLElement | null>(null)
const input = ref<InstanceType<typeof Textarea> | null>(null)

// ── Fayl biriktirish: 📎 tugma, Ctrl+V (skrinshot), sudrab tashlash. Avval POST /files, keyin hashId bilan xabar ──
const ACCEPT = ['image/jpeg', 'image/png', 'application/pdf']
const MAX_BYTES = 10 * 1024 * 1024
const attach = ref<FileInfo | null>(null)
const uploading = ref(false)
const dragging = ref(false)
const picker = ref<HTMLInputElement | null>(null)

async function upload(file: File) {
  if (!canWrite.value || uploading.value) return
  if (!ACCEPT.includes(file.type)) { toast.error('Faqat JPG, PNG rasm yoki PDF hujjat'); return }
  if (file.size > MAX_BYTES) { toast.error(`Fayl juda katta (${fmtBytes(file.size)}) — ko‘pi bilan 10 MB`); return }
  uploading.value = true
  try {
    attach.value = await filesApi.upload(file)
    input.value?.$el?.focus?.()
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    uploading.value = false
  }
}
function onPick(e: Event) {
  const el = e.target as HTMLInputElement
  const f = el.files?.[0]
  if (f) upload(f)
  el.value = ''                                   // o'sha faylni qayta tanlash mumkin bo'lsin
}
function onPaste(e: ClipboardEvent) {
  const f = e.clipboardData?.files?.[0]
  if (f) { e.preventDefault(); upload(f) }
}
function onDragOver(e: DragEvent) {
  if (canWrite.value && e.dataTransfer?.types.includes('Files')) dragging.value = true
}
function onDrop(e: DragEvent) {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) upload(f)
}

const isMine = computed(() => !!fb.value && fb.value.assigneeId === meId.value)
const canWrite = computed(() => fb.value?.status === 'OPEN' && canReply.value && (isMine.value || canManage.value))
const canClaim = computed(() => !!fb.value && canReply.value
  && (fb.value.status === 'NEW' || (fb.value.status === 'OPEN' && !isMine.value && canManage.value)))
const canClose = computed(() => !!fb.value && canReply.value && fb.value.status !== 'CLOSED'
  && (fb.value.status === 'NEW' || isMine.value || canManage.value))
const name = computed(() => (fb.value ? who(fb.value) : ''))
const lastId = computed(() => msgs.value.at(-1)?.id ?? 0)

// Suhbatdagi barcha rasmlar — lightbox'da ←/→ bilan aylanadi
const gallery = computed(() => msgs.value.filter((m) => m.file?.image).map((m) => ({
  url: m.file!.url, name: m.file!.name,
  caption: `${m.sender === 'FARMER' ? who(fb.value!) : m.staffEmail ?? 'Xodim'} · ${fmtDateTime(m.createdDate)}`,
  hashId: m.file!.hashId,
})))
const viewing = ref<number | null>(null)
function openImage(hashId: string) {
  const i = gallery.value.findIndex((g) => g.hashId === hashId)
  if (i >= 0) viewing.value = i
}

// Xabarlar kun bo'yicha guruhlanadi; ketma-ket bir muallif — zichroq
const rows = computed(() => {
  const out: { day?: string; m: FeedbackMessage; first: boolean }[] = []
  let prevDay = ''
  let prev: FeedbackMessage | undefined
  for (const m of msgs.value) {
    const day = new Date(m.createdDate).toDateString()
    const first = !prev || prev.sender !== m.sender || prev.staffId !== m.staffId || day !== prevDay
    out.push({ day: day !== prevDay ? dayLabel(m.createdDate) : undefined, m, first })
    prevDay = day
    prev = m
  }
  return out
})

function nearBottom() {
  const el = scroller.value
  return !el || el.scrollHeight - el.scrollTop - el.clientHeight < 120
}
// Pastga "yopishish": rasmlar keyinroq yuklanib balandlik o'sadi — foydalanuvchi o'zi tepaga
// aylantirmaguncha oxirgi xabar ko'rinib tursin
const stick = ref(true)
function onScroll() {
  stick.value = nearBottom()
}
async function toBottom(force = false) {
  const go = force || stick.value || nearBottom()
  await nextTick()
  if (go && scroller.value) {
    scroller.value.scrollTop = scroller.value.scrollHeight
    stick.value = true
  }
}

function setFb(f: Feedback) {
  fb.value = f
  emit('changed', f)
}

async function markRead() {
  if (fb.value && isMine.value && fb.value.unreadStaff > 0) {
    try {
      await feedbackApi.read(fb.value.id)
      setFb({ ...fb.value, unreadStaff: 0 })
      window.dispatchEvent(new Event('feedback-changed'))
    } catch { /* keyingi poll'da qayta */ }
  }
}

function onError(e: unknown) {
  if (e instanceof ApiError && e.status === 404) {
    toast.error('Suhbat endi sizga ko‘rinmaydi (boshqa operatorga o‘tgan)')
    emit('gone')
  } else {
    toast.error(errorMessage(e))
  }
}

async function load() {
  loading.value = true
  msgs.value = []
  text.value = ''
  attach.value = null
  try {
    const [f, m] = await Promise.all([feedbackApi.get(props.id), feedbackApi.messages(props.id)])
    setFb(f)
    msgs.value = m
    loading.value = false                     // xabarlar DOM'ga tushsin, keyin pastga
    await toBottom(true)
    markRead()
    if (canWrite.value) input.value?.$el?.focus?.()
  } catch (e) {
    onError(e)
  } finally {
    loading.value = false
  }
}

async function poll() {
  if (!fb.value || loading.value) return
  try {
    const [f, m] = await Promise.all([feedbackApi.get(props.id), feedbackApi.messages(props.id, lastId.value)])
    const statusChanged = f.status !== fb.value.status
    setFb(f)
    if (m.length) {
      const known = new Set(msgs.value.map((x) => x.id))
      msgs.value.push(...m.filter((x) => !known.has(x.id)))
      await toBottom()
      if (document.hasFocus()) markRead()
    }
    if (statusChanged && f.status === 'CLOSED') toast.info(`Murojaat №${f.id} yakunlandi`)
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) onError(e)
  }
}

const visibility = useDocumentVisibility()
// Ko'rinmayotgan tabda ham to'xtamaydi, faqat siyrakroq (har 5-taktda ≈ 20 s)
let tick = 0
const { pause, resume } = useIntervalFn(() => { if (visibility.value === 'visible' || ++tick % 5 === 0) poll() }, 4000)
watch(() => props.id, load, { immediate: true })
watch(visibility, (v) => { if (v === 'visible') { poll(); resume() } else pause() })
onBeforeUnmount(pause)

async function send() {
  const t = text.value.trim()
  if ((!t && !attach.value) || sending.value || uploading.value || !fb.value) return
  sending.value = true
  try {
    const m = await feedbackApi.send(fb.value.id, t || null, attach.value?.hashId)
    msgs.value.push(m)
    text.value = ''
    attach.value = null
    await toBottom(true)
    if (m.delivered === false) toast.warning('Xabar saqlandi, lekin fermerga yetmadi (botni bloklagan boʻlishi mumkin)')
    setFb({ ...fb.value, lastMessageAt: m.createdDate, lastSender: 'STAFF', unreadStaff: isMine.value ? 0 : fb.value.unreadStaff })
  } catch (e) {
    onError(e)
  } finally {
    sending.value = false
  }
}

function onEnter(e: KeyboardEvent) {
  if (e.isComposing) return
  e.preventDefault()
  send()
}

async function claim() {
  if (!fb.value) return
  busy.value = true
  try {
    setFb(await feedbackApi.claim(fb.value.id))
    await poll()
    toast.success('Suhbat sizga biriktirildi — fermerga yozishingiz mumkin')
    window.dispatchEvent(new Event('feedback-changed'))
    await nextTick()
    input.value?.$el?.focus?.()
  } catch (e) {
    onError(e)
    poll()
  } finally {
    busy.value = false
  }
}

async function close() {
  if (!fb.value) return
  busy.value = true
  try {
    setFb(await feedbackApi.close(fb.value.id))
    await poll()
    confirmClose.value = false
    toast.success('Suhbat yakunlandi. Fermerga baholash taklif qilindi')
    window.dispatchEvent(new Event('feedback-changed'))
  } catch (e) {
    onError(e)
  } finally {
    busy.value = false
  }
}

const tg = computed(() => (fb.value?.farmerUsername ? `https://t.me/${fb.value.farmerUsername}` : null))
</script>

<template>
  <div class="relative flex h-full min-h-0 flex-col" @dragover.prevent="onDragOver" @drop.prevent>
    <div
      v-if="dragging" class="bg-background/85 border-primary absolute inset-2 z-20 flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed backdrop-blur-sm"
      @dragleave.self="dragging = false" @drop.prevent="onDrop"
    >
      <Upload class="text-primary pointer-events-none size-8" />
      <p class="pointer-events-none text-sm font-medium">Faylni shu yerga tashlang</p>
      <p class="text-muted-foreground pointer-events-none text-xs">JPG, PNG yoki PDF · 10 MB gacha</p>
    </div>
    <!-- Sarlavha -->
    <div class="flex items-center gap-3 border-b px-3 py-2.5 sm:px-4">
      <Button v-if="showBack" variant="ghost" size="icon" class="-ml-1 shrink-0 lg:hidden" @click="emit('back')"><ArrowLeft /></Button>
      <template v-if="fb">
        <div :class="['flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold', avatarClass(fb.farmerId)]">
          {{ initials(name) }}
        </div>
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
            <RouterLink :to="`/farmers/${fb.farmerId}`" class="truncate font-semibold hover:underline">{{ name }}</RouterLink>
            <StatusBadge :tone="kindTone[fb.kind]" :label="FEEDBACK_KIND[fb.kind]" />
            <StatusBadge :tone="statusTone[fb.status]" :label="FEEDBACK_STATUS[fb.status]" />
          </div>
          <div class="text-muted-foreground flex flex-wrap items-center gap-x-1.5 text-xs">
            <span>№{{ fb.id }}</span>
            <template v-if="tg"><span>·</span><a :href="tg" target="_blank" rel="noopener" class="hover:text-foreground">@{{ fb.farmerUsername }}</a></template>
            <span>·</span><span>{{ LANG[fb.farmerLanguage] ?? fb.farmerLanguage }}</span>
            <template v-if="fb.assigneeEmail && !isMine"><span>·</span><span class="inline-flex items-center gap-1"><UserRoundCog class="size-3" />{{ fb.assigneeEmail }}</span></template>
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-1.5">
          <Button v-if="canClaim && fb.status === 'OPEN'" size="sm" variant="outline" :disabled="busy" @click="claim">
            <Hand /> <span class="hidden sm:inline">Oʻzimga olish</span>
          </Button>
          <Button v-if="canClose" size="sm" variant="outline" :disabled="busy" @click="confirmClose = true">
            <CircleCheckBig /> <span class="hidden sm:inline">Yakunlash</span>
          </Button>
          <Button v-if="closable" variant="ghost" size="icon" aria-label="Yopish" @click="emit('back')"><X /></Button>
        </div>
      </template>
      <div v-else class="flex flex-1 items-center gap-3">
        <Skeleton class="size-10 rounded-full" /><div class="space-y-1.5"><Skeleton class="h-4 w-40" /><Skeleton class="h-3 w-24" /></div>
      </div>
    </div>

    <!-- Xabarlar -->
    <div ref="scroller" class="bg-muted/30 flex-1 overflow-y-auto px-3 py-4 sm:px-6" @scroll.passive="onScroll">
      <div v-if="loading" class="space-y-3">
        <Skeleton class="h-14 w-2/3 rounded-2xl" /><Skeleton class="ml-auto h-10 w-1/2 rounded-2xl" /><Skeleton class="h-20 w-3/5 rounded-2xl" />
      </div>
      <template v-else>
        <template v-for="r in rows" :key="r.m.id">
          <div v-if="r.day" class="my-4 flex justify-center">
            <span class="bg-background text-muted-foreground rounded-full border px-3 py-0.5 text-xs font-medium shadow-xs">{{ r.day }}</span>
          </div>

          <!-- Tizim -->
          <div v-if="r.m.sender === 'SYSTEM'" class="my-2 flex justify-center">
            <span class="text-muted-foreground bg-muted rounded-full px-3 py-1 text-center text-xs">
              {{ r.m.text }} · {{ timeOf(r.m.createdDate) }}
            </span>
          </div>

          <!-- Fermer / xodim -->
          <div v-else :class="['flex', r.m.sender === 'STAFF' ? 'justify-end' : 'justify-start', r.first ? 'mt-3' : 'mt-1']">
            <div class="max-w-[85%] sm:max-w-[70%]">
              <div v-if="r.first && r.m.sender === 'STAFF' && r.m.staffId !== meId" class="text-muted-foreground mb-0.5 px-1 text-right text-[11px]">
                {{ r.m.staffEmail }}
              </div>
              <div
                :class="[
                  'rounded-2xl px-3.5 py-2 text-sm shadow-xs',
                  r.m.sender === 'FARMER' ? 'bg-background rounded-bl-md border' :
                  r.m.staffId === meId ? 'bg-primary text-primary-foreground rounded-br-md' : 'rounded-br-md bg-sky-600 text-white',
                ]"
              >
                <template v-if="r.m.file">
                  <button v-if="r.m.file.image" type="button" class="-mx-1.5 -mt-0.5 mb-1.5 block cursor-zoom-in" :title="`${r.m.file.name} — kattalashtirish`" @click="openImage(r.m.file.hashId)">
                    <img :src="r.m.file.url" :alt="r.m.file.name" loading="lazy"
                         :width="r.m.file.width ?? undefined" :height="r.m.file.height ?? undefined"
                         class="h-auto max-h-72 w-auto max-w-full rounded-xl bg-black/5 object-contain transition hover:brightness-95" @load="toBottom()" />
                  </button>
                  <a v-else :href="r.m.file.url" :download="r.m.file.name"
                     :class="['mb-1.5 flex items-center gap-3 rounded-xl px-3 py-2 ring-1', r.m.sender === 'FARMER' ? 'bg-muted/60 ring-border hover:bg-muted' : 'bg-white/10 ring-white/25 hover:bg-white/15']">
                    <FileText class="size-8 shrink-0" />
                    <span class="min-w-0">
                      <span class="block truncate font-medium">{{ r.m.file.name }}</span>
                      <span class="block text-xs opacity-75">PDF · {{ fmtBytes(r.m.file.size) }}</span>
                    </span>
                  </a>
                </template>
                <p v-else-if="r.m.photoPending" class="mb-1 flex items-center gap-1.5 text-xs opacity-75">
                  <ImageOff class="size-4" /> Rasm hali serverga koʻchirilmagan (bot qayta ishga tushganda olinadi)
                </p>
                <p v-if="r.m.text && !(['📷', '📎'].includes(r.m.text) && (r.m.file || r.m.photoPending))" class="break-words whitespace-pre-wrap">{{ r.m.text }}</p>
                <div :class="['mt-0.5 flex items-center justify-end gap-1 text-[11px]', r.m.sender === 'FARMER' ? 'text-muted-foreground' : 'opacity-75']">
                  {{ timeOf(r.m.createdDate) }}
                  <template v-if="r.m.sender === 'STAFF'">
                    <CheckCheck v-if="r.m.delivered" class="size-3.5" />
                    <AlertTriangle v-else-if="r.m.delivered === false" class="size-3.5" />
                    <Check v-else class="size-3.5" />
                  </template>
                </div>
              </div>
              <p v-if="r.m.sender === 'STAFF' && r.m.delivered === false" class="mt-0.5 px-1 text-right text-[11px] text-red-600">
                Fermerga yetmadi
              </p>
            </div>
          </div>
        </template>
      </template>
    </div>

    <!-- Pastki qism -->
    <div v-if="fb && !loading" class="bg-background border-t">
      <form v-if="canWrite" class="p-3" @submit.prevent="send">
        <p v-if="!isMine" class="mb-2 text-xs text-sky-700 dark:text-sky-400">
          Bu suhbat {{ fb.assigneeEmail }} da — administrator sifatida yozyapsiz
        </p>
        <div v-if="attach || uploading" class="bg-muted/40 mb-2 flex items-center gap-3 rounded-lg border p-2">
          <div v-if="uploading" class="flex size-12 items-center justify-center"><Loader2 class="text-muted-foreground size-5 animate-spin" /></div>
          <img v-else-if="attach?.image" :src="attach.url" :alt="attach.name" class="size-12 rounded-md object-cover" />
          <div v-else class="flex size-12 items-center justify-center rounded-md bg-red-50 text-red-600 dark:bg-red-500/10"><FileText class="size-6" /></div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium">{{ uploading ? 'Yuklanmoqda…' : attach?.name }}</p>
            <p v-if="attach" class="text-muted-foreground text-xs">
              {{ fmtBytes(attach.size) }}<template v-if="attach.width"> · {{ attach.width }}×{{ attach.height }}</template>
            </p>
          </div>
          <Button v-if="attach" type="button" variant="ghost" size="icon" aria-label="Faylni olib tashlash" @click="attach = null"><X /></Button>
        </div>
        <div class="flex items-end gap-2">
          <input ref="picker" type="file" :accept="ACCEPT.join(',')" class="hidden" @change="onPick" />
          <Button type="button" variant="ghost" size="icon" class="text-muted-foreground size-10 shrink-0 rounded-full"
                  :disabled="uploading" aria-label="Fayl biriktirish" title="Rasm yoki PDF biriktirish" @click="picker?.click()">
            <Paperclip />
          </Button>
          <Textarea
            ref="input" v-model="text" rows="1" maxlength="3500"
            class="max-h-40 min-h-10 resize-none py-2"
            :placeholder="attach ? 'Izoh (ixtiyoriy)…' : `${LANG[fb.farmerLanguage] ?? fb.farmerLanguage} tilida javob yozing…`"
            @keydown.enter.exact="onEnter" @paste="onPaste"
          />
          <Button type="submit" size="icon" class="size-10 shrink-0 rounded-full" :disabled="sending || uploading || (!text.trim() && !attach)" aria-label="Yuborish">
            <Loader2 v-if="sending" class="animate-spin" /><SendHorizontal v-else />
          </Button>
        </div>
        <p class="text-muted-foreground mt-1.5 flex justify-between text-[11px]">
          <span><kbd class="font-sans">Enter</kbd> — yuborish · <kbd class="font-sans">Shift+Enter</kbd> — yangi qator · rasmni <kbd class="font-sans">Ctrl+V</kbd> yoki sudrab qoʻyish mumkin</span>
          <span v-if="text.length > 3000" :class="text.length >= 3500 ? 'text-red-600' : ''">{{ text.length }} / 3500</span>
        </p>
      </form>

      <div v-else-if="fb.status === 'NEW'" class="flex flex-col items-center gap-2 p-4 text-center sm:flex-row sm:justify-between sm:text-left">
        <p class="text-sm">
          <span class="font-medium">Fermer javob kutmoqda</span>
          <span class="text-muted-foreground"> · {{ waited(fb.createdDate) }} dan beri</span>
        </p>
        <Button v-if="canClaim" :disabled="busy" @click="claim">
          <Loader2 v-if="busy" class="animate-spin" /><MessageCircleReply v-else /> Javob berish
        </Button>
      </div>

      <div v-else-if="fb.status === 'CLOSED'" class="flex flex-wrap items-center justify-between gap-2 p-4 text-sm">
        <span class="text-muted-foreground">
          <CircleCheckBig class="mr-1 inline size-4 text-emerald-600" />
          Yakunlangan · {{ CLOSED_BY[fb.closedBy ?? ''] ?? '—' }}{{ fb.closedBy === 'STAFF' && fb.closedByEmail ? ` (${fb.closedByEmail})` : '' }} · {{ fmtDateTime(fb.closedAt) }}
        </span>
        <span class="flex items-center gap-2">
          <span class="text-muted-foreground text-xs">Fermer bahosi:</span>
          <Stars v-if="fb.rating" :value="fb.rating" size="size-4" />
          <span v-else class="text-muted-foreground text-xs">{{ fb.assigneeId ? 'hali baholamagan' : '—' }}</span>
        </span>
      </div>

      <p v-else class="text-muted-foreground p-4 text-center text-sm">
        Bu suhbat {{ fb.assigneeEmail }} da. Faqat koʻrish mumkin.
      </p>
    </div>

    <ImageLightbox v-model:index="viewing" :images="gallery" />

    <Dialog v-model:open="confirmClose">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Suhbatni yakunlaysizmi?</DialogTitle>
          <DialogDescription>
            <template v-if="fb?.status === 'NEW'">Murojaat javobsiz yopiladi (spam yoki takror). Fermerga yopilgani haqida xabar boradi.</template>
            <template v-else>Fermerga «suhbat yakunlandi» xabari va operatorni baholash taklifi boradi. Yana savoli boʻlsa, yangi murojaat ochadi.</template>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter class="gap-2">
          <Button variant="outline" :disabled="busy" @click="confirmClose = false">Bekor qilish</Button>
          <Button :disabled="busy" @click="close"><Loader2 v-if="busy" class="animate-spin" /><CircleCheckBig v-else /> Yakunlash</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
