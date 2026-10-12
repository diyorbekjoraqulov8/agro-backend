import type { JobStatus, LeadStatus } from '@/api/types'

export const jobTone: Record<JobStatus, 'gray' | 'blue' | 'green' | 'red'> = {
  PENDING: 'gray', RUNNING: 'blue', DONE: 'green', FAILED: 'red',
}
export const leadTone: Record<LeadStatus, 'gray' | 'amber' | 'blue' | 'green' | 'red'> = {
  NONE: 'gray', REQUESTED: 'amber', CONTACTED: 'blue', PILOT: 'green', REJECTED: 'red',
}
