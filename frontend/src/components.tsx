import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import {
  ArrowLeft,
  ArrowRight,
  Building2,
  Heart,
  MapPin,
  RefreshCw,
  SearchX,
  Upload,
  X,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, ApiError } from './api'
import type { Listing } from './types'

export const money = (value: number | null | undefined) =>
  new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(Number(value ?? 0))
export const dateTime = (value?: string) =>
  value
    ? new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(new Date(value))
    : '—'
export const shortDate = (value?: string) => (value ? value.slice(0, 10) : '—')
export function errorText(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}
const statuses: Record<string, string> = {
  PENDING: '待审核',
  CONFIRMED: '已确认',
  REJECTED: '已拒绝',
  CANCELLED: '已取消',
  DRAFT: '待生效',
  ACTIVE: '履约中',
  TERMINATED: '已终止',
  EXPIRED: '已到期',
  UNPAID: '待支付',
  PAID: '已支付',
  OVERDUE: '已逾期',
}
export function Badge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{statuses[status] ?? status}</span>
}
export function Loading({ text = '正在加载' }: { text?: string }) {
  return (
    <div className="loading-state" role="status">
      <span className="spinner" />
      {text}…
    </div>
  )
}
export function Empty({
  title = '暂时没有记录',
  description = '新的记录会显示在这里。',
  action,
}: {
  title?: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <SearchX size={34} strokeWidth={1.3} />
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  )
}
export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="error-box" role="alert">
      <div>
        <strong>{errorText(error)}</strong>
        {error instanceof ApiError && error.fields ? (
          <ul>
            {Object.entries(error.fields).map(([field, message]) => (
              <li key={field}>{message}</li>
            ))}
          </ul>
        ) : null}
        {error instanceof ApiError && error.requestId ? (
          <small>请求编号：{error.requestId}</small>
        ) : null}
      </div>
      {onRetry ? (
        <button className="button button-soft" onClick={onRetry}>
          <RefreshCw size={15} />
          重新加载
        </button>
      ) : null}
    </div>
  )
}
export function Pagination({
  page,
  pageSize,
  total,
  onChange,
}: {
  page: number
  pageSize: number
  total: number
  onChange: (page: number) => void
}) {
  const pages = Math.max(1, Math.ceil(total / pageSize))
  return (
    <div className="pagination">
      <span>
        共 <strong>{total}</strong> 条 · 第 {page} / {pages} 页
      </span>
      <div>
        <button
          className="icon-button"
          aria-label="上一页"
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
        >
          <ArrowLeft size={17} />
        </button>
        <button
          className="icon-button"
          aria-label="下一页"
          disabled={page >= pages}
          onClick={() => onChange(page + 1)}
        >
          <ArrowRight size={17} />
        </button>
      </div>
    </div>
  )
}
export function PageHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow ? <span className="eyebrow">{eyebrow}</span> : null}
        <h1>{title}</h1>
        {description ? <p>{description}</p> : null}
      </div>
      {action}
    </div>
  )
}
export function Modal({
  title,
  children,
  onClose,
  wide = false,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  wide?: boolean
}) {
  const dialog = useRef<HTMLDivElement>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current?.focus()
    const key = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeRef.current()
      if (event.key === 'Tab') {
        const nodes = dialog.current?.querySelectorAll<HTMLElement>(
          'button:not([disabled]),input:not([hidden]),select,textarea,a[href]',
        )
        if (!nodes?.length) return
        const first = nodes[0],
          last = nodes[nodes.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', key)
    return () => {
      document.body.style.overflow = overflow
      document.removeEventListener('keydown', key)
      previous?.focus()
    }
  }, [])
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        className={`modal ${wide ? 'modal-wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        ref={dialog}
      >
        <div className="modal-heading">
          <h2>{title}</h2>
          <button className="icon-button" aria-label="关闭对话框" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
export function Field({
  label,
  children,
  hint,
}: {
  label: string
  children: ReactNode
  hint?: string
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint ? <small>{hint}</small> : null}
    </label>
  )
}
export function FormActions({
  busy,
  onCancel,
  label = '保存',
}: {
  busy: boolean
  onCancel: () => void
  label?: string
}) {
  return (
    <div className="form-actions">
      <button type="button" className="button button-plain" disabled={busy} onClick={onCancel}>
        取消
      </button>
      <button type="submit" className="button button-primary" disabled={busy}>
        {busy ? '正在处理…' : label}
      </button>
    </div>
  )
}
export function ListingImage({
  listing,
  className = '',
}: {
  listing: Pick<Listing, 'id' | 'title' | 'imageUrl'>
  className?: string
}) {
  return (
    <img
      className={className}
      src={listing.imageUrl || `/images/room-${(listing.id % 3) + 1}.svg`}
      alt={listing.title || '公寓房间'}
      loading="lazy"
      onError={(event) => {
        event.currentTarget.onerror = null
        event.currentTarget.src = `/images/room-${(listing.id % 3) + 1}.svg`
      }}
    />
  )
}
export function ListingCard({
  listing,
  onFavorite,
  favorite = false,
}: {
  listing: Listing
  onFavorite?: (listing: Listing) => void
  favorite?: boolean
}) {
  return (
    <article className="listing-card">
      <Link to={`/rooms/${listing.id}`} className="listing-picture">
        <ListingImage listing={listing} />
        <span className="picture-label">{listing.available ? '可预约看房' : '了解房源'}</span>
      </Link>
      {onFavorite ? (
        <button
          className={`favorite-button ${favorite ? 'is-favorite' : ''}`}
          aria-label={favorite ? '取消收藏' : '收藏房源'}
          onClick={() => onFavorite(listing)}
        >
          <Heart size={19} fill={favorite ? 'currentColor' : 'none'} />
        </button>
      ) : null}
      <div className="listing-body">
        <div className="listing-location">
          <MapPin size={13} />
          {listing.city} · {listing.apartmentName}
        </div>
        <h3>
          <Link to={`/rooms/${listing.id}`}>
            {listing.title || `${listing.apartmentName} ${listing.roomNumber}`}
          </Link>
        </h3>
        <p className="listing-spec">
          {listing.area || '—'}㎡<span /> {listing.bedrooms || 1} 居室
          <span />朝{listing.orientation || '南'}
        </p>
        <div className="tags">
          {(listing.tags ?? []).slice(0, 3).map((tag) => (
            <span key={tag}>{tag}</span>
          ))}
        </div>
        <div className="listing-bottom">
          <div className="rent">
            ¥<strong>{money(listing.monthlyRent)}</strong>
            <small>/月</small>
          </div>
          <Link className="text-link" to={`/rooms/${listing.id}`}>
            看房源 <ArrowRight size={15} />
          </Link>
        </div>
      </div>
    </article>
  )
}
export function Table({ headers, children }: { headers: string[]; children: ReactNode }) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            {headers.map((header) => (
              <th key={header}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}
export function Brand({ light = false }: { light?: boolean }) {
  return (
    <Link className={`brand ${light ? 'brand-light' : ''}`} to="/">
      <span className="brand-mark">
        <Building2 size={23} />
      </span>
      <span>
        <strong>尚庭公寓</strong>
        <small>SHANGTING LIVING</small>
      </span>
    </Link>
  )
}
export const readForm = (event: FormEvent<HTMLFormElement>) => {
  event.preventDefault()
  return new FormData(event.currentTarget)
}
export const value = (data: FormData, key: string) => String(data.get(key) ?? '').trim()
export const numeric = (data: FormData, key: string) => Number(data.get(key))

export function ImageUpload() {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState<unknown>(null)
  const upload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget,
      file = input.files?.[0],
      form = input.form
    if (!file) return
    if (file.size > 5 * 1024 * 1024) {
      setError(new Error('图片不能超过 5 MB'))
      input.value = ''
      return
    }
    setBusy(true)
    setError(null)
    setMessage('')
    try {
      const data = new FormData()
      data.set('file', file)
      const result = await api<{ url: string }>('/api/admin/catalog/uploads', {
        method: 'POST',
        body: data,
      })
      const target = form?.elements.namedItem('imageUrl')
      if (target instanceof HTMLInputElement) target.value = result.url
      setMessage('图片已上传，地址已自动填写')
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
      input.value = ''
    }
  }
  return (
    <div className="upload-control">
      <label className="button button-soft button-small">
        <Upload size={14} />
        {busy ? '正在上传…' : '上传房源图片'}
        <input hidden type="file" accept="image/png,image/jpeg" disabled={busy} onChange={upload} />
      </label>
      <small>{message || '支持 JPG、PNG，最大 5 MB'}</small>
      {error ? <ErrorBox error={error} /> : null}
    </div>
  )
}
