import type { Crop, FeedbackKind, FeedbackStatus, IrrigationType, JobKind, JobStatus, LeadStatus, PaymentMethod, WaterSource } from '@/api/types'

export const CROP: Record<Crop, string> = {
  COTTON: 'Paxta', WHEAT: 'Bugʻdoy', CORN: 'Makkajoʻxori', RICE: 'Sholi',
  ALFALFA: 'Beda', VEGETABLES: 'Sabzavot', ORCHARD: 'Bogʻ', OTHER: 'Boshqa',
}
export const IRRIGATION: Record<IrrigationType, string> = {
  FURROW: 'Egat', DRIP: 'Tomchilatib', SPRINKLER: 'Yomgʻirlatib', OTHER: 'Boshqa',
}
export const WATER: Record<WaterSource, string> = {
  CANAL: 'Kanal / ariq', OWN_PUMP: 'Oʻz nasosi', WELL: 'Quduq', OTHER: 'Boshqa',
}
export const LEAD: Record<LeadStatus, string> = {
  NONE: 'Yoʻq', REQUESTED: 'Soʻragan', CONTACTED: 'Bogʻlanildi', PILOT: 'Pilot', REJECTED: 'Rad etdi',
}
export const JOB_STATUS: Record<JobStatus, string> = {
  PENDING: 'Navbatda', RUNNING: 'Bajarilmoqda', DONE: 'Tayyor', FAILED: 'Xato',
}
export const JOB_KIND: Record<JobKind, string> = { FREE: 'Bepul', PAID: 'Pullik', STAFF: 'Xodim' }
export const PAYMENT: Record<PaymentMethod, string> = {
  CASH: 'Naqd', CARD: 'Karta', TRANSFER: 'Oʻtkazma', OTHER: 'Boshqa', REFERRAL: 'Taklif mukofoti',
}
export const SIDE: Record<string, string> = {
  N: 'shimoliy', NE: 'shimoli-sharqiy', E: 'sharqiy', SE: 'janubi-sharqiy', S: 'janubiy',
  SW: 'janubi-gʻarbiy', W: 'gʻarbiy', NW: 'shimoli-gʻarbiy', CENTER: 'markaziy',
}
export const LANG: Record<string, string> = { uz: 'Oʻzbekcha', ru: 'Ruscha', en: 'Inglizcha' }

export function recommendation(code: string, p: Record<string, unknown>): string {
  switch (code) {
    case 'WATER_STRESS': return `Suv stressi: ${p.from} – ${p.to}`
    case 'WEAK_ZONE': return `Zaif zona ${p.weak_pct}% (${SIDE[String(p.side)] ?? '—'} qism)`
    case 'BELOW_LAST_YEAR': return `Oʻtgan yilga nisbatan ${p.pct}% past`
    case 'FIELD_UNIFORM': return 'Dala bir tekis rivojlangan'
    case 'LOW_CONFIDENCE': return `Bulutsiz suratlar kam (${p.scenes})`
    case 'NO_CROP_DETECTED': return 'Ekin aniqlanmadi'
    default: return code
  }
}

// Ruxsat kodlari — backend Permissions.java bilan bir xil
export const P = {
  FARMER_READ: 'farmer:read', FARMER_UPDATE: 'farmer:update', FIELD_READ: 'field:read',
  ANALYSIS_READ: 'analysis:read', ANALYSIS_RUN: 'analysis:run',
  BILLING_READ: 'billing:read', BILLING_MANAGE: 'billing:manage',
  USER_READ: 'user:read', ROLE_READ: 'role:read', ROLE_CREATE: 'role:create', ROLE_UPDATE: 'role:update',
  AUDIT_READ: 'audit:read',
  FEEDBACK_READ: 'feedback:read', FEEDBACK_REPLY: 'feedback:reply', FEEDBACK_MANAGE: 'feedback:manage', FILE_READ: 'file:read', FILE_UPLOAD: 'file:upload', FILE_MANAGE: 'file:manage',
  SETTINGS_READ: 'settings:read', SETTINGS_MANAGE: 'settings:manage', REFERRAL_READ: 'referral:read',
  ONBOARDING_READ: 'onboarding:read', ONBOARDING_MANAGE: 'onboarding:manage',
  NOTIFICATION_READ: 'notification:read', NOTIFICATION_SEND: 'notification:send',
  ADVISOR_READ: 'advisor:read',
} as const

export const SOIL: Record<string, string> = { SAND: 'Qumloq', LOAM: 'Oʻrtacha (qumoq)', CLAY: 'Ogʻir (soz)' }
export const REMINDER: Record<string, string> = { DUE: 'Sugʻorish vaqti', SOON: '1–2 kun qoldi', STATUS: 'Haftalik holat' }
export const IRRIGATION_SOURCE: Record<string, string> = { FARMER: 'Fermer (✅ Sugʻordim)', SETUP: 'Sozlashda', STAFF: 'Xodim' }

export const FEEDBACK_KIND: Record<FeedbackKind, string> = { PROBLEM: 'Muammo', SUGGESTION: 'Taklif', QUESTION: 'Savol' }
export const FEEDBACK_STATUS: Record<FeedbackStatus, string> = { NEW: 'Navbatda', OPEN: 'Suhbatda', CLOSED: 'Yakunlangan' }
