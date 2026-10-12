// Murojaatlar (suhbatlar) — ko'rinish yordamchilari
import type { Feedback, FeedbackKind, FeedbackStatus } from '@/api/types'

export const kindTone: Record<FeedbackKind, 'red' | 'violet' | 'blue'> = { PROBLEM: 'red', SUGGESTION: 'violet', QUESTION: 'blue' }
export const statusTone: Record<FeedbackStatus, 'amber' | 'green' | 'gray'> = { NEW: 'amber', OPEN: 'green', CLOSED: 'gray' }

export function who(x: Pick<Feedback, 'farmerName' | 'farmerUsername' | 'farmerId'>): string {
  return x.farmerName || (x.farmerUsername ? '@' + x.farmerUsername : `Fermer #${x.farmerId}`)
}

export function initials(name: string): string {
  const parts = name.replace('@', '').trim().split(/\s+/).filter(Boolean)
  const s = parts.length > 1 ? parts[0]![0]! + parts[1]![0]! : (parts[0] ?? '?').slice(0, 2)
  return s.toUpperCase()
}

// Avatar rangi fermer id'siga bog'liq — ro'yxatda ko'z bilan ajratish oson
const AVATAR = [
  'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
  'bg-sky-100 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300',
  'bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300',
  'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  'bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300',
  'bg-teal-100 text-teal-700 dark:bg-teal-500/15 dark:text-teal-300',
]
export function avatarClass(id: number): string {
  return AVATAR[id % AVATAR.length]!
}

/** "hozir", "5 daq", "3 soat", "kecha", "12.10" */
export function ago(v?: string | null, now = Date.now()): string {
  if (!v) return ''
  const t = new Date(v).getTime()
  const s = Math.max(0, Math.round((now - t) / 1000))
  if (s < 60) return 'hozir'
  if (s < 3600) return `${Math.floor(s / 60)} daq`
  const d = new Date(t)
  const today = new Date(now)
  if (s < 86400 && d.getDate() === today.getDate()) return `${Math.floor(s / 3600)} soat`
  const y = new Date(now - 86400000)
  if (d.toDateString() === y.toDateString()) return 'kecha'
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' })
}

/** Davomiylik: "5 daq", "1 soat 15 daq", "2 kun" — kutish vaqti uchun */
export function waited(v: string, now = Date.now()): string {
  const m = Math.max(0, Math.floor((now - new Date(v).getTime()) / 60000))
  if (m < 1) return '1 daq'
  if (m < 60) return `${m} daq`
  const h = Math.floor(m / 60)
  if (h < 24) return m % 60 ? `${h} soat ${m % 60} daq` : `${h} soat`
  return `${Math.floor(h / 24)} kun`
}

/** Navbatda qancha kutyapti — rang bilan (1 soatdan ko'p — sariq, 1 kundan — qizil) */
export function waitTone(v: string, now = Date.now()): string {
  const h = (now - new Date(v).getTime()) / 3600000
  return h >= 24 ? 'text-red-600 dark:text-red-400' : h >= 1 ? 'text-amber-600 dark:text-amber-400' : 'text-muted-foreground'
}

export function dayLabel(v: string): string {
  const d = new Date(v)
  const today = new Date()
  if (d.toDateString() === today.toDateString()) return 'Bugun'
  if (d.toDateString() === new Date(Date.now() - 86400000).toDateString()) return 'Kecha'
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export function timeOf(v: string): string {
  return new Date(v).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}

export const CLOSED_BY: Record<string, string> = { FARMER: 'Fermer', STAFF: 'Xodim', SYSTEM: 'Tizim' }
