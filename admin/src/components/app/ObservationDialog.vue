<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Loader2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import FieldPointPicker from '@/components/app/FieldPointPicker.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { accuracyApi } from '@/api'
import { errorMessage } from '@/api/http'
import type { ObsCause, ObsCondition } from '@/api/types'
import { isoDate } from '@/lib/format'
import { CAUSE, CONDITION } from '@/lib/labels'

/** L2: "aslida nima bor edi" — admin dalada ko'rgani. Nuqta yoki butun dala */
const open = defineModel<boolean>('open', { required: true })
const props = defineProps<{ fieldId: number; polygon: { coordinates: number[][][] } | null }>()
const emit = defineEmits<{ saved: [] }>()

const today = isoDate(new Date())
const blank = () => ({ observedOn: today, condition: 'WEAK' as ObsCondition, cause: null as ObsCause | null, whole: false, note: '' })
const form = ref(blank())
const point = ref<[number, number] | null>(null)
const saving = ref(false)
watch(open, (o) => { if (o) { form.value = blank(); point.value = null } })

const missing = computed(() => !form.value.observedOn ? 'Sanani kiriting'
  : !form.value.whole && !point.value ? 'Xaritada joyni bosing yoki "Butun dala" ni tanlang' : '')

const chip = (on: boolean) => ['h-7 rounded-md border px-2.5 text-xs font-medium transition-colors',
  on ? 'border-primary bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-accent hover:text-foreground']

async function save() {
  if (missing.value) return toast.error(missing.value)
  saving.value = true
  try {
    const p = form.value.whole ? null : point.value
    await accuracyApi.createObservation(props.fieldId, {
      observedOn: form.value.observedOn, condition: form.value.condition,
      cause: form.value.condition === 'GOOD' ? null : form.value.cause,
      lat: p?.[0] ?? null, lon: p?.[1] ?? null, note: form.value.note.trim() || undefined,
    })
    toast.success('Kuzatuv saqlandi')
    open.value = false
    emit('saved')
  } catch (e) {
    toast.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-xl">
      <DialogHeader>
        <DialogTitle>Kuzatuv qoʻshish</DialogTitle>
        <DialogDescription>Dalada aslida nima borligi — tizim xulosalari shu bilan solishtiriladi</DialogDescription>
      </DialogHeader>
      <div class="space-y-4">
        <div class="space-y-1.5">
          <Label for="obs-date">Qachon koʻrildi</Label>
          <Input id="obs-date" v-model="form.observedOn" type="date" :max="today" class="w-44" />
        </div>
        <div class="space-y-1.5">
          <Label>Holat</Label>
          <div class="flex flex-wrap gap-1.5" role="group" aria-label="Holat">
            <button v-for="(l, k) in CONDITION" :key="k" type="button" :aria-pressed="form.condition === k"
                    :class="chip(form.condition === k)" @click="form.condition = k">{{ l }}</button>
          </div>
        </div>
        <div v-if="form.condition !== 'GOOD'" class="space-y-1.5">
          <Label>Sabab <span class="text-muted-foreground font-normal">· maʼlum boʻlsa</span></Label>
          <div class="flex flex-wrap gap-1.5" role="group" aria-label="Sabab">
            <button v-for="(l, k) in CAUSE" :key="k" type="button" :aria-pressed="form.cause === k"
                    :class="chip(form.cause === k)" @click="form.cause = form.cause === k ? null : k">{{ l }}</button>
          </div>
        </div>
        <div class="space-y-1.5">
          <div class="flex items-center justify-between">
            <Label>Qayerda</Label>
            <div class="flex gap-1.5" role="group" aria-label="Qayerda">
              <button type="button" :aria-pressed="!form.whole" :class="chip(!form.whole)" @click="form.whole = false">Xaritada nuqta</button>
              <button type="button" :aria-pressed="form.whole" :class="chip(form.whole)" @click="form.whole = true">Butun dala</button>
            </div>
          </div>
          <FieldPointPicker v-if="!form.whole" v-model="point" :polygon="polygon" />
          <p v-if="!form.whole" class="text-muted-foreground text-xs tabular-nums">
            {{ point ? `${point[0]}, ${point[1]}` : 'Muammoli (yoki yaxshi) joyni bosing — dala ichida' }}</p>
        </div>
        <div class="space-y-1.5">
          <Label for="obs-note">Izoh <span class="text-muted-foreground font-normal">· ixtiyoriy</span></Label>
          <Textarea id="obs-note" v-model="form.note" rows="2" maxlength="1000" placeholder="Masalan: shimoliy chekkada suv yetmagan, barglari sargʻaygan" />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="open = false">Bekor</Button>
        <Button :disabled="saving || !!missing" @click="save"><Loader2 v-if="saving" class="animate-spin" /> Saqlash</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
