// Backend DTO'lari (record) — o'zgartirsangiz Java tomonini ham tekshiring

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface PageQuery {
  page?: number
  size?: number
  sort?: string
}

// ── Auth / RBAC ──────────────────────────────────────────────────────────
export interface Me { id: number; email: string; roles: string[]; permissions: string[] }
export interface SessionInfo { id: string; device: string; ip: string; createdDate: string; lastSeenAt: string; current: boolean }
export interface UserResponse { id: number; email: string; roles: string[]; active: boolean; createdDate: string }
export interface PermissionResponse { id: number; name: string; description: string }
export interface RoleResponse { id: number; name: string; description: string | null; systemRole: boolean; createdDate: string }
export interface RoleWithPermissions extends RoleResponse { permissions: PermissionResponse[] }
export interface AuditLog {
  id: number; eventType: string; outcome: 'SUCCESS' | 'FAILURE'
  actorId: number | null; actorEmail: string | null; targetType: string | null; targetId: string | null
  ip: string | null; userAgent: string | null; details: Record<string, unknown> | null; createdAt: string
}

// ── Fermer / dala ────────────────────────────────────────────────────────
export type Crop = 'COTTON' | 'WHEAT' | 'CORN' | 'RICE' | 'ALFALFA' | 'VEGETABLES' | 'ORCHARD' | 'OTHER'
export type IrrigationType = 'FURROW' | 'DRIP' | 'SPRINKLER' | 'OTHER'
export type WaterSource = 'CANAL' | 'OWN_PUMP' | 'WELL' | 'OTHER'
export type LeadStatus = 'NONE' | 'REQUESTED' | 'CONTACTED' | 'PILOT' | 'REJECTED'

export interface FarmerListItem {
  id: number; fullName: string | null; telegramUsername: string | null; phone: string | null
  countryCode: string | null; language: string; mainCrop: Crop | null; irrigationType: IrrigationType | null
  waterSource: WaterSource | null; source: string | null; leadStatus: LeadStatus; blockedBot: boolean; createdDate: string
}
export interface FieldSummary {
  id: number; farmerId: number; name: string; crop: Crop; areaHa: number | null
  centroidLat: number | null; centroidLon: number | null; createdDate: string
}
export interface EffectivePlan {
  planCode: string; planName: string; paid: boolean; subscriptionId: number | null; validUntil: string | null
  maxFieldHa: number; maxFields: number; maxAnalyses: number | null; usedAnalyses: number
}
export interface FarmerDetail extends FarmerListItem {
  telegramUserId: number; totalAreaHa: number | null; leadNote: string | null; updatedDate: string
  fields: FieldSummary[]; plan: EffectivePlan
}
export interface GeoPolygon { type: 'Polygon'; coordinates: number[][][] }
export interface FieldResponse extends FieldSummary { farmerName: string | null; boundaryGeojson: GeoPolygon }

// ── Tahlil ───────────────────────────────────────────────────────────────
export type JobStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
export type JobKind = 'FREE' | 'PAID' | 'STAFF'
export interface AnalysisListItem {
  id: number; fieldId: number; kind: JobKind; status: JobStatus; seasonStart: string; seasonEnd: string
  attempts: number; error: string | null; startedAt: string | null; finishedAt: string | null; createdDate: string
}
export interface Period { from: string; to: string }
export interface AnalysisResult {
  area_ha: number; country_code: string | null; scenes_used: number; scenes_rejected_cloud: number
  zones: { good_pct: number; mid_pct: number; weak_pct: number; weak_side: string | null }
  peak_ndvi: number; peak_date: string; prev_year_peak_ndvi: number | null
  growing_season: Period | null; stress_periods: Period[]
  water: { et0_sum_mm: number; etc_sum_mm: number; effective_rain_mm: number; irrigation_need_m3_ha: number
           irrigation_need_low_m3_ha: number; irrigation_need_high_m3_ha: number } | null
  timeseries: { date: string; ndvi: number; ndmi: number }[]
  recommendations: { code: string; params: Record<string, unknown> }[]
}
export interface AnalysisResponse extends AnalysisListItem { nextTryAt: string; result: AnalysisResult | null }

// ── Statistika ───────────────────────────────────────────────────────────
export interface Overview {
  farmers: number; fields: number; totalAreaHa: number; leads: number
  analysesByStatus: Record<string, number>; farmersByLeadStatus: Record<string, number>; farmersByCountry: Record<string, number>
}
export interface SourceFunnel { source: string; farmers: number; withField: number; analyzed: number; leads: number; leadRatePct: number }
export interface SourcesReport { from: string; to: string; sources: SourceFunnel[] }

// ── To'lovlar ────────────────────────────────────────────────────────────
export type PaymentMethod = 'CASH' | 'CARD' | 'TRANSFER' | 'OTHER' | 'REFERRAL'
export interface Plan {
  id: number; code: string; name: string; description: string | null; price: number; currency: string
  durationDays: number | null; maxFieldHa: number; maxFields: number; maxAnalyses: number | null
  systemPlan: boolean; active: boolean; updatedDate: string | null
}
export interface PlanRequest {
  code?: string; name: string; description?: string | null; price: number; currency: string
  durationDays?: number | null; maxFieldHa: number; maxFields: number; maxAnalyses?: number | null; active: boolean
}
export interface Subscription {
  id: number; farmerId: number; planId: number; planCode: string; planName: string
  status: 'ACTIVE' | 'CANCELLED'; activeToday: boolean; validFrom: string; validUntil: string
  amount: number; currency: string; paidAt: string; paymentMethod: PaymentMethod; note: string | null
  recordedByEmail: string | null; cancelledAt: string | null; cancelReason: string | null; createdDate: string
}
export interface PaymentRequest {
  planId: number; paymentMethod: PaymentMethod; amount?: number | null; currency?: string | null
  paidAt?: string | null; validFrom?: string | null; validUntil?: string | null; note?: string | null
}

// ── Sug'orish ────────────────────────────────────────────────────────────
export type SoilType = 'SAND' | 'LOAM' | 'CLAY'
export interface WaterView {
  configured: boolean; enabled: boolean; soil: SoilType | null; soilAuto: SoilType | null; soilUsed: SoilType | null
  soilMismatch: boolean; sandPct: number | null; clayPct: number | null; lastIrrigatedOn: string | null
  nextIrrigationOn: string | null; depletionPct: number | null; ndvi: number | null; ndviOn: string | null
  timezone: string | null; lastReminderOn: string | null; lastReminderKind: 'DUE' | 'SOON' | 'STATUS' | null
  events: { irrigatedOn: string; source: 'FARMER' | 'SETUP' | 'STAFF' }[]
}

export type FeedbackKind = 'PROBLEM' | 'SUGGESTION' | 'QUESTION'
export type FeedbackStatus = 'NEW' | 'OPEN' | 'CLOSED'
export type FeedbackSender = 'FARMER' | 'STAFF' | 'SYSTEM'
export interface Feedback {
  id: number; farmerId: number; farmerName: string | null; farmerUsername: string | null; farmerLanguage: string
  kind: FeedbackKind; subject: string; status: FeedbackStatus
  assigneeId: number | null; assigneeEmail: string | null; assignedAt: string | null
  lastMessageAt: string; lastSender: FeedbackSender | null; unreadStaff: number
  closedAt: string | null; closedBy: FeedbackSender | null; closedByEmail: string | null
  rating: number | null; ratedAt: string | null; createdDate: string
}
export interface FileInfo {
  hashId: string; name: string; contentType: string; size: number; width: number | null; height: number | null
  image: boolean; url: string; createdDate: string
}
export type FileSource = 'STAFF' | 'TELEGRAM' | 'SYSTEM'
export interface FileUsage { kind: string; refId: number; label: string }
export interface FileAdmin extends FileInfo {
  thumbUrl: string | null; sha256: string; source: FileSource; uploadedByEmail: string | null; usages: FileUsage[]
}
export interface FileStats { total: number; totalBytes: number; images: number; pdfs: number; fromTelegram: number; fromStaff: number }
export interface FeedbackMessage {
  id: number; sender: FeedbackSender; staffId: number | null; staffEmail: string | null
  text: string | null; file: FileInfo | null; photoPending: boolean; delivered: boolean | null; createdDate: string
}
export interface FeedbackCounts { NEW: number; MINE: number; MINE_UNREAD: number; OPEN?: number }
export interface OperatorStats { staffId: number; email: string; open: number; closed: number; rated: number; avgRating: number | null }

// ── Sozlamalar ───────────────────────────────────────────────────────────
export interface AppSetting {
  key: string; group: string; description: string; type: 'BOOL' | 'INT' | 'STRING' | 'PLAN'
  value: string; defaultValue: string; min: number | null; max: number | null
  custom: boolean; updatedByEmail: string | null; updatedDate: string | null; warning: string | null
}

// ── Takliflar ────────────────────────────────────────────────────────────
export interface ReferralStats { referrers: number; invited: number; activated: number; rewards: number }
export interface ReferrerRow {
  farmerId: number; farmerName: string | null; farmerUsername: string | null; code: string | null
  invited: number; activated: number; rewards: number; lastInvitedAt: string | null
}
export interface FarmerReferral {
  code: string | null
  referredBy: { id: number; name: string | null; username: string | null } | null
  referredAt: string | null; activatedAt: string | null
  invited: { id: number; name: string | null; username: string | null; referredAt: string | null; activatedAt: string | null }[]
  rewards: { subscriptionId: number; planName: string; validFrom: string; validUntil: string; referralsUsed: number; createdDate: string }[]
}

// ── Onboarding ───────────────────────────────────────────────────────────
export type StepKind = 'MESSAGE' | 'QUESTION'
export type StepTarget = 'MAIN_CROP' | 'IRRIGATION_TYPE' | 'WATER_SOURCE'
export type Texts = Partial<Record<'uz' | 'ru' | 'en', string>>
export interface StepOption { value: string; labels: Texts }
export interface OnboardingStep {
  id: number; code: string; position: number; kind: StepKind; target: StepTarget | null; texts: Texts
  options: StepOption[]; system: boolean; active: boolean; updatedByEmail: string | null; updatedDate: string; answers: number
}
export interface StepRequest { code?: string; kind?: StepKind; texts: Texts; options?: StepOption[]; active?: boolean }

// ── Bildirishnomalar ─────────────────────────────────────────────────────
export type NotificationStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED' | 'CANCELLED'
export type NotificationCategory = 'ANALYSIS' | 'IRRIGATION' | 'FEEDBACK' | 'REFERRAL' | 'BROADCAST' | 'SYSTEM'
export type CampaignStatus = 'SCHEDULED' | 'SENDING' | 'DONE' | 'CANCELLED'
export interface NotificationItem {
  id: number; farmerId: number; farmerName: string | null; channel: string; category: NotificationCategory; template: string
  preview: string | null; status: NotificationStatus; attempts: number; lastError: string | null; campaignId: number | null
  createdDate: string; nextAttemptAt: string; sentAt: string | null
}
export interface Audience {
  type: 'ALL' | 'FILTER' | 'SINGLE'; farmerId?: number | null; languages?: string[]; countries?: string[]; paid?: boolean | null
  crops?: Crop[]; hasFields?: boolean | null; activated?: boolean | null; registeredFrom?: string | null; registeredTo?: string | null
  leadStatuses?: LeadStatus[]
}
export interface AudiencePreview { count: number; byLanguage: Record<string, number>; sample: string[] }
export interface Campaign {
  id: number; title: string; texts: Texts; buttonTexts: Texts | null; buttonUrl: string | null; audience: Audience
  status: CampaignStatus; scheduledAt: string; total: number | null; counts: Partial<Record<NotificationStatus, number>>
  createdByEmail: string | null; createdDate: string; startedAt: string | null; finishedAt: string | null
}
export interface CampaignRequest {
  title: string; texts: Texts; buttonTexts?: Texts | null; buttonUrl?: string | null; audience: Audience; scheduledAt?: string | null
}
