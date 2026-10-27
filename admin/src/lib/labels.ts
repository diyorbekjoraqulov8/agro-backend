import type { Crop, DiscountAudience, DiscountKind, FeedbackKind, FeedbackStatus, IrrigationType, JobKind, JobStatus, LeadStatus, MatchResult, ObsCause, ObsCondition, PaymentMethod, ReviewVerdict, Sowing, WaterSource } from '@/api/types'

export const CROP: Record<Crop, string> = {
  COTTON: 'Paxta', WHEAT: 'Bugʻdoy', CORN: 'Makkajoʻxori', RICE: 'Sholi',
  ALFALFA: 'Beda', VEGETABLES: 'Sabzavot', ORCHARD: 'Bogʻ', OTHER: 'Boshqa',
}
export const IRRIGATION: Record<IrrigationType, string> = {
  FURROW: 'Egat', DRIP: 'Tomchilatib', SPRINKLER: 'Yomgʻirlatib', RAINFED: 'Lalmi', OTHER: 'Boshqa',
}
export const SOWING: Record<Sowing, string> = {
  UNANSWERED: 'Soʻralmagan', SOWN: 'Ekilgan', NOT_SOWN: 'Hali ekilmagan', UNKNOWN: 'Fermer bilmaydi',
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
  CASH: 'Naqd', CARD: 'Karta', TRANSFER: 'Oʻtkazma', OTHER: 'Boshqa', REFERRAL: 'Taklif mukofoti', TRIAL: 'Pro sinovi',
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
  FARMER_READ: 'farmer:read', FARMER_UPDATE: 'farmer:update', FIELD_READ: 'field:read', FIELD_WRITE: 'field:write',
  ANALYSIS_READ: 'analysis:read', ANALYSIS_RUN: 'analysis:run',
  BILLING_READ: 'billing:read', BILLING_MANAGE: 'billing:manage',
  USER_READ: 'user:read', ROLE_READ: 'role:read', ROLE_CREATE: 'role:create', ROLE_UPDATE: 'role:update',
  AUDIT_READ: 'audit:read',
  FEEDBACK_READ: 'feedback:read', FEEDBACK_REPLY: 'feedback:reply', FEEDBACK_MANAGE: 'feedback:manage', FILE_READ: 'file:read', FILE_UPLOAD: 'file:upload', FILE_MANAGE: 'file:manage',
  SETTINGS_READ: 'settings:read', SETTINGS_MANAGE: 'settings:manage', REFERRAL_READ: 'referral:read',
  ONBOARDING_READ: 'onboarding:read', ONBOARDING_MANAGE: 'onboarding:manage',
  NOTIFICATION_READ: 'notification:read', NOTIFICATION_SEND: 'notification:send',
  ADVISOR_READ: 'advisor:read', PROBLEMS_READ: 'problems:read', PROBLEMS_MANAGE: 'problems:manage',
} as const

export const WEATHER_KIND: Record<string, string> = { FROST: 'Sovuq', HEAT: 'Jazirama', WIND: 'Kuchli shamol', RAIN: 'Kuchli yomgʻir' }
export const WEATHER_LEVEL: Record<string, string> = { WARNING: 'Ogohlantirish', DANGER: 'Xavfli' }
export const CONDITION: Record<ObsCondition, string> = {
  GOOD: 'Yaxshi', WEAK: 'Sust', DAMAGED: 'Zararlangan', BARE: 'Ekin yoʻq / siyrak', HARVESTED: 'Yigʻib olingan',
}
export const CAUSE: Record<ObsCause, string> = {
  WATER: 'Suv yetishmasligi', NUTRIENT: 'Ozuqa', DISEASE: 'Kasallik', PEST: 'Zararkunanda', WEEDS: 'Begona oʻt',
  SALINITY: 'Shoʻr', LODGING: 'Yotib qolgan', HAIL: 'Doʻl', OTHER: 'Boshqa', UNKNOWN: 'Nomaʼlum',
}
export const REVIEW: Record<ReviewVerdict, string> = { CORRECT: 'Toʻgʻri', PARTIAL: 'Qisman', WRONG: 'Notoʻgʻri' }
export const MATCH: Record<MatchResult, string> = {
  AGREE: 'Mos', DISAGREE: 'Mos emas', NOT_APPLICABLE: 'Qoʻllanmaydi', NO_DATA: 'Maʼlumot yoʻq',
}
/** Avtomatik solishtirish sababi (accuracy/Match.java) */
export const MATCH_REASON: Record<string, string> = {
  HARVESTED: 'Yigʻilgan dala — tahlil zonasi bilan solishtirilmaydi',
  NO_ANALYSIS: 'Mavsum choʻqqisi kuzatuvdan ±45 kunda boʻlgan tahlil yoʻq',
  RELATIVE_ZONES: 'Butun dala muammosi: zonalar dala oʻrtachasiga nisbatan — buni koʻrmaydi',
  LOW_WEAK: 'Sust ulush kam — dala yaxshi deb topilgan',
  WEAK_ZONE_FOUND: 'Tizim sust zona topgan, aslida dala yaxshi',
  NO_DEBUG: 'Eski tahlil — sust markaz saqlanmagan, qayta tahlil qiling',
  NO_WEAK_ZONE: 'Tizim sust zona topmagan',
  NEAR_WEAK: 'Nuqta tizim topgan sust zonaga tushdi',
  FAR_FROM_WEAK: 'Nuqta tizim topgan sust zonadan uzoq',
  IN_WEAK_ZONE: 'Yaxshi joy tizim sust zonasiga tushdi',
  OUTSIDE_WEAK: 'Yaxshi joy sust zonadan tashqarida',
  NO_EVENTS: 'Kuzatuvdan −21…+7 kunda monitoring surati yoʻq',
  HARVEST_DETECTED: 'Monitoring oʻrim/ekin yoʻqligini koʻrgan',
  NOT_DETECTED: 'Monitoring oʻrimni koʻrmagan',
  FALSE_DECLINE: 'Monitoring "sustlashdi" degan, aslida yaxshi',
  NO_DECLINE: 'Monitoring sustlashuvni koʻrmagan',
  DECLINE_DETECTED: 'Monitoring sustlashuvni koʻrgan',
}
export const SOIL: Record<string, string> = { SAND: 'Qumloq', LOAM: 'Oʻrtacha (qumoq)', CLAY: 'Ogʻir (soz)' }
export const REMINDER: Record<string, string> = { DUE: 'Sugʻorish vaqti', SOON: '1–2 kun qoldi', STATUS: 'Haftalik holat' }
export const IRRIGATION_SOURCE: Record<string, string> = { FARMER: 'Fermer (✅ Sugʻordim)', SETUP: 'Sozlashda', STAFF: 'Xodim' }

export const FEEDBACK_KIND: Record<FeedbackKind, string> = { PROBLEM: 'Muammo', SUGGESTION: 'Taklif', QUESTION: 'Savol', SUBSCRIPTION: 'Obuna' }
export const FEEDBACK_STATUS: Record<FeedbackStatus, string> = { NEW: 'Navbatda', OPEN: 'Suhbatda', CLOSED: 'Yakunlangan' }

export const DISCOUNT_KIND: Record<DiscountKind, string> = { SEASONAL: 'Mavsumiy', PROMO: 'Aksiya', FIRST_PAYMENT: 'Birinchi toʻlov', LOYALTY: 'Uzaytirish (sodiq mijoz)' }
export const DISCOUNT_AUDIENCE: Record<DiscountAudience, string> = { FREE: 'Bepul', TRIAL: 'Pro sinovida', PAID: 'Faol obunachi', EXPIRED: 'Obunasi tugagan' }
