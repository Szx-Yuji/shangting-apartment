import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, CalendarDays, Check, Heart, MapPin, ShieldCheck } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, post } from '../api'
import { useResource, useSession, useToast } from '../hooks'
import {
  dateTime,
  Empty,
  ErrorBox,
  errorText,
  Field,
  ListingImage,
  Loading,
  money,
} from '../components'
import type { Listing, Page, Slot } from '../types'

export default function RoomDetail() {
  const { id } = useParams()
  const room = useResource<Listing>(`/api/public/listings/${id}`)
  const slots = useResource<Page<Slot>>(`/api/public/rooms/${id}/appointment-slots?pageSize=100`)
  const { session } = useSession()
  const notify = useToast()
  const navigate = useNavigate()
  const [selected, setSelected] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [booked, setBooked] = useState<number | null>(null)
  const intent = useRef<{ body: string; key: string } | null>(null)
  useEffect(() => {
    setSelected(null)
    setBooked(null)
    intent.current = null
  }, [id])
  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!session.authenticated || session.role !== 'USER') {
      notify('请先登录租客账号，再预约看房')
      navigate('/login', { state: { from: `/rooms/${id}` } })
      return
    }
    if (!selected) {
      notify('请选择看房时段', 'error')
      return
    }
    const data = new FormData(event.currentTarget)
    const body = {
      slotId: selected,
      contactName: String(data.get('contactName')).trim(),
      contactPhone: String(data.get('contactPhone')).trim(),
    }
    const serialized = JSON.stringify(body)
    if (!intent.current || intent.current.body !== serialized)
      intent.current = { body: serialized, key: crypto.randomUUID() }
    setBusy(true)
    setError(null)
    try {
      const result = await post<{ id: number }>('/api/me/appointments', body, {
        'Idempotency-Key': intent.current.key,
      })
      setBooked(result.id)
      notify('预约已提交，请等待管理员确认')
      slots.reload()
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }
  const favorite = async () => {
    if (session.role !== 'USER') {
      navigate('/login')
      return
    }
    try {
      await api(`/api/me/favorites/${id}`, { method: 'PUT' })
      notify('已加入我的收藏')
    } catch (failure) {
      notify(errorText(failure), 'error')
    }
  }
  if (room.loading) return <Loading />
  if (room.error)
    return (
      <div className="section">
        <ErrorBox error={room.error} onRetry={room.reload} />
      </div>
    )
  if (!room.data) return null
  const item = room.data
  return (
    <div className="section detail-page">
      <Link to="/" className="back-link">
        <ArrowLeft size={16} />
        返回房源列表
      </Link>
      <div className="detail-title">
        <div>
          <div className="listing-location">
            <MapPin size={15} />
            {item.city} · {item.apartmentName}
          </div>
          <h1>{item.title || `${item.apartmentName} ${item.roomNumber}`}</h1>
          <p>{item.address}</p>
        </div>
        <button className="button button-soft" onClick={favorite}>
          <Heart size={17} />
          收藏房源
        </button>
      </div>
      <div className="detail-grid">
        <div>
          <div className="detail-photo">
            <ListingImage listing={item} />
            <span className="photo-caption">房源示意图 · 实际情况以现场看房为准</span>
          </div>
          <div className="detail-specs">
            <div>
              <strong>
                {item.area} <small>㎡</small>
              </strong>
              <span>建筑面积</span>
            </div>
            <div>
              <strong>
                {item.bedrooms} <small>居室</small>
              </strong>
              <span>房间格局</span>
            </div>
            <div>
              <strong>{item.orientation || '南'}</strong>
              <span>房屋朝向</span>
            </div>
            <div>
              <strong>{item.roomNumber}</strong>
              <span>房间编号</span>
            </div>
          </div>
          <section className="detail-description">
            <h2>关于这个住处</h2>
            <p>
              {item.description ||
                '采光通透，配套齐全。在舒适的居住空间里，留出属于自己的生活节奏。欢迎预约实地看房，了解更多房间细节。'}
            </p>
            <div className="amenity-grid">
              {(item.tags?.length ? item.tags : ['独立卫浴', '家具齐全', '便捷通勤']).map((tag) => (
                <span key={tag}>
                  <Check size={17} />
                  {tag}
                </span>
              ))}
            </div>
          </section>
          <section className="viewing-steps">
            <h2>预约看房，简单三步</h2>
            <div>
              <span>
                <b>1</b>选择合适时段
              </span>
              <span>
                <b>2</b>等待预约确认
              </span>
              <span>
                <b>3</b>按时到店看房
              </span>
            </div>
          </section>
        </div>
        <aside className="booking-card">
          <div className="rent detail-rent">
            ¥<strong>{money(item.monthlyRent)}</strong>
            <small>/月</small>
          </div>
          <p className="muted">明晰租住价格，安心计划生活。</p>
          <div className="line" />
          {booked ? (
            <div className="booking-success">
              <span>
                <Check size={27} />
              </span>
              <h3>预约已提交</h3>
              <p>预约编号 #{booked}，可在个人中心查看审核进度。</p>
              <Link className="button button-primary" to="/me/appointments">
                查看我的预约
              </Link>
            </div>
          ) : (
            <form onSubmit={submit}>
              <h3>
                <CalendarDays size={19} />
                预约实地看房
              </h3>
              {slots.loading ? (
                <Loading text="加载时段" />
              ) : slots.error ? (
                <ErrorBox error={slots.error} onRetry={slots.reload} />
              ) : slots.data?.records.length ? (
                <div className="slot-grid">
                  {slots.data.records.map((slot) => (
                    <button
                      type="button"
                      key={slot.id}
                      className={selected === slot.id ? 'slot selected' : 'slot'}
                      onClick={() => {
                        setSelected(slot.id)
                      }}
                    >
                      <strong>{dateTime(slot.startTime)}</strong>
                      <span>
                        至{' '}
                        {new Date(slot.endTime).toLocaleTimeString('zh-CN', {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}{' '}
                        · 余 {slot.remainingCapacity} 名
                      </span>
                    </button>
                  ))}
                </div>
              ) : (
                <Empty title="暂无可预约时段" description="管理员更新时段后即可预约。" />
              )}
              <Field label="联系人">
                <input
                  name="contactName"
                  required
                  maxLength={50}
                  placeholder="请输入联系人姓名"
                  defaultValue={session.account?.displayName ?? ''}
                />
              </Field>
              <Field label="联系电话">
                <input
                  name="contactPhone"
                  required
                  inputMode="tel"
                  pattern="1[3-9][0-9]{9}"
                  maxLength={11}
                  placeholder="请输入 11 位手机号"
                />
              </Field>
              {error ? <ErrorBox error={error} /> : null}
              <button className="button button-primary full" disabled={busy || !selected}>
                {busy ? '正在提交…' : session.role === 'USER' ? '确认预约' : '登录后预约'}
              </button>
              <p className="booking-tip">
                <ShieldCheck size={14} />
                联系方式仅用于本次预约沟通
              </p>
            </form>
          )}
        </aside>
      </div>
    </div>
  )
}
