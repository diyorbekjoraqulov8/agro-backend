import { http } from './http'
import type * as T from './types'

type Q = T.PageQuery & Record<string, string | number | boolean | null | undefined>

export const authApi = {
  login: (email: string, password: string) => http.post<void>('/auth/login', { email, password }),
  logout: () => http.post<void>('/auth/logout'),
  me: () => http.get<T.Me>('/auth/me'),
  sessions: () => http.get<T.SessionInfo[]>('/auth/sessions'),
  revokeSession: (id: string) => http.del<void>(`/auth/sessions/${id}`),
}

export const usersApi = {
  list: () => http.get<T.UserResponse[]>('/users'),
  get: (id: number) => http.get<T.UserResponse>(`/users/${id}`),
}

export const rolesApi = {
  list: () => http.get<T.RoleResponse[]>('/roles'),
  detailed: () => http.get<T.RoleWithPermissions[]>('/roles/detailed'),
  create: (body: { name: string; description?: string; permissionIds: number[] }) => http.post<T.RoleResponse>('/roles', body),
  update: (id: number, body: { description?: string; permissionIds: number[] }) => http.put<T.RoleResponse>(`/roles/${id}`, body),
}

export const auditApi = {
  search: (q: Q) => http.get<T.Page<T.AuditLog>>('/audit', q),
}

export const farmersApi = {
  search: (q: Q) => http.get<T.Page<T.FarmerListItem>>('/farmers', q),
  get: (id: number) => http.get<T.FarmerDetail>(`/farmers/${id}`),
  updateLead: (id: number, body: { leadStatus: T.LeadStatus; leadNote?: string | null }) =>
    http.patch<T.FarmerDetail>(`/farmers/${id}/lead`, body),
}

export const fieldsApi = {
  search: (q: Q) => http.get<T.Page<T.FieldSummary>>('/fields', q),
  get: (id: number) => http.get<T.FieldResponse>(`/fields/${id}`),
  water: (id: number) => http.get<T.WaterView>(`/fields/${id}/water`),
  monitoring: (id: number) => http.get<T.FieldMonitoring>(`/fields/${id}/monitoring`),
}

export const analysesApi = {
  search: (q: Q) => http.get<T.Page<T.AnalysisListItem>>('/analyses', q),
  get: (id: number) => http.get<T.AnalysisResponse>(`/analyses/${id}`),
  run: (fieldId: number, body?: { seasonStart?: string; seasonEnd?: string }) =>
    http.post<T.AnalysisResponse>(`/fields/${fieldId}/analyses`, body ?? {}),
  retry: (id: number) => http.post<T.AnalysisResponse>(`/analyses/${id}/retry`),
  // <img src> — cookie bir origin'da o'zi ketadi
  mapUrl: (id: number) => `/api/v1/analyses/${id}/map.png`,
  chartUrl: (id: number) => `/api/v1/analyses/${id}/chart.png`,
}

export const statsApi = {
  overview: () => http.get<T.Overview>('/stats/overview'),
  sources: (from?: string, to?: string) => http.get<T.SourcesReport>('/stats/sources', { from, to }),
}

export const billingApi = {
  plans: () => http.get<T.Plan[]>('/plans'),
  createPlan: (body: T.PlanRequest) => http.post<T.Plan>('/plans', body),
  updatePlan: (id: number, body: T.PlanRequest) => http.put<T.Plan>(`/plans/${id}`, body),
  farmerPlan: (farmerId: number) => http.get<T.EffectivePlan>(`/farmers/${farmerId}/plan`),
  history: (farmerId: number) => http.get<T.Subscription[]>(`/farmers/${farmerId}/subscriptions`),
  recordPayment: (farmerId: number, body: T.PaymentRequest) => http.post<T.Subscription>(`/farmers/${farmerId}/subscriptions`, body),
  cancel: (id: number, reason: string) => http.post<T.Subscription>(`/subscriptions/${id}/cancel`, { reason }),
  subscriptions: (q: Q) => http.get<T.Page<T.Subscription>>('/subscriptions', q),
}

export const feedbackApi = {
  search: (q: Q) => http.get<T.Page<T.Feedback>>('/feedback', q),
  counts: () => http.get<T.FeedbackCounts>('/feedback/counts'),
  operators: () => http.get<T.OperatorStats[]>('/feedback/operators'),
  get: (id: number) => http.get<T.Feedback>(`/feedback/${id}`),
  messages: (id: number, after?: number) => http.get<T.FeedbackMessage[]>(`/feedback/${id}/messages`, { after }),
  claim: (id: number) => http.post<T.Feedback>(`/feedback/${id}/claim`),
  read: (id: number) => http.post<void>(`/feedback/${id}/read`),
  send: (id: number, text: string | null, fileHashId?: string) =>
    http.post<T.FeedbackMessage>(`/feedback/${id}/messages`, { text: text || null, fileHashId }),
  close: (id: number) => http.post<T.Feedback>(`/feedback/${id}/close`),
}

export const filesApi = {
  search: (q: Q) => http.get<T.Page<T.FileAdmin>>('/files', q),
  stats: () => http.get<T.FileStats>('/files/stats'),
  upload: (file: File) => {
    const f = new FormData()
    f.append('file', file)
    return http.upload<T.FileInfo>('/files', f)
  },
}

export const settingsApi = {
  list: () => http.get<T.AppSetting[]>('/settings'),
  update: (key: string, value: string) => http.put<T.AppSetting>(`/settings/${encodeURIComponent(key)}`, { value }),
  reset: (key: string) => http.del<T.AppSetting>(`/settings/${encodeURIComponent(key)}`),
}

export const referralsApi = {
  stats: () => http.get<T.ReferralStats>('/referrals/stats'),
  referrers: (q: Q) => http.get<T.Page<T.ReferrerRow>>('/referrals', q),
  forFarmer: (farmerId: number) => http.get<T.FarmerReferral>(`/referrals/farmers/${farmerId}`),
}

export const onboardingApi = {
  steps: () => http.get<T.OnboardingStep[]>('/onboarding/steps'),
  create: (body: T.StepRequest) => http.post<T.OnboardingStep>('/onboarding/steps', body),
  update: (id: number, body: T.StepRequest) => http.put<T.OnboardingStep>(`/onboarding/steps/${id}`, body),
  move: (id: number, direction: 'up' | 'down') => http.post<T.OnboardingStep[]>(`/onboarding/steps/${id}/move?direction=${direction}`),
  remove: (id: number) => http.del<void>(`/onboarding/steps/${id}`),
}

export const advisorApi = {
  list: (q: Q) => http.get<T.Page<T.AdvisorItem>>('/advisor/requests', q),
  get: (id: number) => http.get<T.AdvisorItem>(`/advisor/requests/${id}`),
  stats: () => http.get<T.AdvisorStats>('/advisor/stats'),
}

export const notificationsApi = {
  search: (q: Q) => http.get<T.Page<T.NotificationItem>>('/notifications', q),
  stats: () => http.get<Partial<Record<T.NotificationStatus, number>>>('/notifications/stats'),
  retry: (id: number) => http.post<T.NotificationItem>(`/notifications/${id}/retry`),
  campaigns: (q: Q) => http.get<T.Page<T.Campaign>>('/notifications/campaigns', q),
  preview: (a: T.Audience) => http.post<T.AudiencePreview>('/notifications/campaigns/preview', a),
  create: (body: T.CampaignRequest) => http.post<T.Campaign>('/notifications/campaigns', body),
  cancel: (id: number) => http.post<T.Campaign>(`/notifications/campaigns/${id}/cancel`),
}
