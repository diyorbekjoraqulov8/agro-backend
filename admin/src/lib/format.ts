// Ko'rinish uchun formatlash: sana dd.MM.yyyy, raqam — bo'shliq bilan (uz)

export function fmtDate(v?: string | null): string {
  if (!v) return '—'
  const d = new Date(v.length === 10 ? `${v}T00:00:00` : v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export function fmtDateTime(v?: string | null): string {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return `${fmtDate(v)} ${d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}`
}

export function fmtNum(v?: number | null, digits = 0): string {
  if (v === null || v === undefined) return '—'
  return v.toLocaleString('ru-RU', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

export function fmtMoney(v: number, currency: string): string {
  return `${fmtNum(v, v % 1 ? 2 : 0)} ${currency}`
}

export function isoDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function daysFromNow(n: number): string {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return isoDate(d)
}

export function duration(from?: string | null, to?: string | null): string {
  if (!from || !to) return '—'
  const s = Math.round((new Date(to).getTime() - new Date(from).getTime()) / 1000)
  return s < 60 ? `${s} s` : `${Math.floor(s / 60)} daq ${s % 60} s`
}

export function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${Math.round(n / 1024)} KB`
  return `${(n / 1024 / 1024).toFixed(1).replace('.', ',')} MB`
}
