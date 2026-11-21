import { Suspense, useState } from 'react'
import {
  Bell,
  CalendarCheck2,
  Check,
  ChevronRight,
  FileText,
  Heart,
  ReceiptText,
  UserRound,
} from 'lucide-react'
import { Link, NavLink, Navigate, Outlet } from 'react-router-dom'
import { patch, post, remove } from '../api'
import { useResource, useSession, useToast } from '../hooks'
import {
  Badge,
  dateTime,
  Empty,
  ErrorBox,
  errorText,
  ListingCard,
  Loading,
  Modal,
  money,
  PageHeading,
  Pagination,
  shortDate,
  Table,
} from '../components'
import type { Appointment, Bill, Lease, Listing, Notice, Page } from '../types'

export function PersonalLayout() {
  const { session, loading } = useSession()
  if (loading) return <Loading />
  if (!session.authenticated) return <Navigate to="/login" replace />
  if (session.role === 'ADMIN') return <Navigate to="/admin" replace />
  return (
    <div className="workspace personal-workspace">
      <aside className="personal-sidebar">
        <div className="profile-card">
          <span>
            <UserRound size={26} />
          </span>
          <strong>{session.account?.displayName || session.account?.username}</strong>
          <small>愿每一天，都有家的温度。</small>
        </div>
        <nav>
          {[
            { to: 'appointments', label: '我的预约', icon: CalendarCheck2 },
            { to: 'favorites', label: '收藏房源', icon: Heart },
            { to: 'leases', label: '我的租约', icon: FileText },
            { to: 'bills', label: '我的账单', icon: ReceiptText },
            { to: 'notifications', label: '消息通知', icon: Bell },
          ].map((item) => (
            <NavLink key={item.to} to={`/me/${item.to}`}>
              <item.icon size={18} />
              {item.label}
              <ChevronRight size={14} />
            </NavLink>
          ))}
        </nav>
        <Link className="sidebar-hint" to="/">
          还有更多合适的家
          <br />
          <strong>继续发现房源 →</strong>
        </Link>
      </aside>
      <div className="workspace-content">
        <Suspense fallback={<Loading />}>
          <Outlet />
        </Suspense>
      </div>
    </div>
  )
}

export function MyAppointments() {
  const [page, setPage] = useState(1)
  const result = useResource<Page<Appointment>>(`/api/me/appointments?page=${page}&pageSize=10`)
  const [cancel, setCancel] = useState<Appointment | null>(null)
  const [busy, setBusy] = useState(false)
  const notify = useToast()
  const perform = async () => {
    if (!cancel) return
    setBusy(true)
    try {
      await post(`/api/me/appointments/${cancel.id}/cancel`)
      setCancel(null)
      notify('预约已取消')
      result.reload()
    } catch (error) {
      notify(errorText(error), 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="MY APPOINTMENTS"
        title="每一次看房，都离家更近"
        description="在这里查看预约进度、看房时间和联系信息。"
        action={
          <Link className="button button-primary" to="/">
            继续找房
          </Link>
        }
      />
      <div className="panel">
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['预约房源', '看房时间', '联系人', '状态', '操作']}>
              {result.data.records.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.apartmentName}</strong>
                    <small>
                      {item.roomNumber} 室 · 预约 #{item.id}
                    </small>
                  </td>
                  <td>{dateTime(item.appointmentTime)}</td>
                  <td>
                    {item.contactName}
                    <small>{item.contactPhone}</small>
                  </td>
                  <td>
                    <Badge status={item.status} />
                  </td>
                  <td>
                    {['PENDING', 'CONFIRMED'].includes(item.status) ? (
                      <button className="text-button danger" onClick={() => setCancel(item)}>
                        取消预约
                      </button>
                    ) : (
                      <span className="muted">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty
            title="你的第一场看房，从这里开始"
            description="先挑一个喜欢的住处，再选择方便的看房时间。"
            action={
              <Link className="button button-primary" to="/">
                去发现房源
              </Link>
            }
          />
        )}
      </div>
      {cancel ? (
        <Modal title="取消这次预约？" onClose={() => setCancel(null)}>
          <p className="modal-description">
            将取消 {cancel.apartmentName} {cancel.roomNumber} 室的看房预约，已占用的名额会释放。
          </p>
          <div className="form-actions">
            <button className="button button-plain" disabled={busy} onClick={() => setCancel(null)}>
              保留预约
            </button>
            <button className="button button-danger" disabled={busy} onClick={perform}>
              {busy ? '取消中…' : '确认取消'}
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  )
}

export function Favorites() {
  const result = useResource<Listing[]>('/api/me/favorites')
  const notify = useToast()
  const unfavorite = async (listing: Listing) => {
    try {
      await remove(`/api/me/favorites/${listing.id}`)
      notify('已取消收藏')
      result.reload()
    } catch (error) {
      notify(errorText(error), 'error')
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="SAVED HOMES"
        title="把心动的住处，留在这里"
        description="慢慢比较，认真选择。收藏不会占用房源或看房名额。"
      />
      {result.loading ? (
        <Loading />
      ) : result.error ? (
        <ErrorBox error={result.error} onRetry={result.reload} />
      ) : result.data?.length ? (
        <div className="listing-grid personal-listings">
          {result.data.map((listing) => (
            <ListingCard key={listing.id} listing={listing} favorite onFavorite={unfavorite} />
          ))}
        </div>
      ) : (
        <div className="panel">
          <Empty
            title="还没有收藏的房源"
            description="看到喜欢的住处时，点击爱心就能保存。"
            action={
              <Link className="button button-primary" to="/">
                发现心动房源
              </Link>
            }
          />
        </div>
      )}
    </>
  )
}

export function MyLeases() {
  const [page, setPage] = useState(1)
  const result = useResource<Page<Lease>>(`/api/me/leases?page=${page}&pageSize=10`)
  return (
    <>
      <PageHeading
        eyebrow="MY LEASES"
        title="安心租住，有约可循"
        description="租期、租金和履约状态，一目了然。"
      />
      <div className="panel">
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['租住房源', '租期', '每月租金', '押金', '状态']}>
              {result.data.records.map((lease) => (
                <tr key={lease.id}>
                  <td>
                    <strong>{lease.apartmentName}</strong>
                    <small>
                      {lease.roomNumber} 室 · 租约 #{lease.id}
                    </small>
                  </td>
                  <td>
                    {shortDate(lease.startDate)}
                    <small>至 {shortDate(lease.endDate)}</small>
                  </td>
                  <td>¥ {money(lease.monthlyRent)}</td>
                  <td>¥ {money(lease.deposit)}</td>
                  <td>
                    <Badge status={lease.status} />
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty title="还没有租约" description="看房确认后，管理员会与你沟通租约事宜。" />
        )}
      </div>
    </>
  )
}

export function MyBills() {
  const [page, setPage] = useState(1)
  const result = useResource<Page<Bill>>(`/api/me/bills?page=${page}&pageSize=10`)
  const [pay, setPay] = useState<Bill | null>(null)
  const [busy, setBusy] = useState(false)
  const notify = useToast()
  const perform = async () => {
    if (!pay) return
    setBusy(true)
    try {
      await post(`/api/me/bills/${pay.id}/pay`)
      notify('演示支付完成，账单状态已更新')
      setPay(null)
      result.reload()
    } catch (error) {
      notify(errorText(error), 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="MY BILLS"
        title="每笔账单，明明白白"
        description="查看应付账单、到期时间和支付记录。"
      />
      <div className="notice-bar">
        <ReceiptText size={18} />
        <span>当前为学习演示环境。演示支付仅更新账单状态，不会产生真实扣款。</span>
      </div>
      <div className="panel">
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['账单', '金额', '到期日', '状态', '操作']}>
              {result.data.records.map((bill) => (
                <tr key={bill.id}>
                  <td>
                    <strong>
                      {bill.apartmentName} · {bill.roomNumber}
                    </strong>
                    <small>
                      {bill.period} · 账单 #{bill.id}
                    </small>
                  </td>
                  <td className="amount">¥ {money(bill.amount)}</td>
                  <td>{shortDate(bill.dueDate)}</td>
                  <td>
                    <Badge status={bill.status} />
                  </td>
                  <td>
                    {['UNPAID', 'OVERDUE'].includes(bill.status) ? (
                      <button
                        className="button button-small button-primary"
                        onClick={() => setPay(bill)}
                      >
                        演示支付
                      </button>
                    ) : (
                      <span className="muted">{bill.paidAt ? dateTime(bill.paidAt) : '—'}</span>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty title="暂无账单" description="租约生效后，相关账单会显示在这里。" />
        )}
      </div>
      {pay ? (
        <Modal title="确认演示支付" onClose={() => setPay(null)}>
          <div className="payment-summary">
            <span>应付金额</span>
            <strong>¥ {money(pay.amount)}</strong>
            <p>
              {pay.apartmentName} · {pay.roomNumber} · {pay.period}
            </p>
          </div>
          <p className="modal-description">
            这是学习环境的模拟支付，不连接支付平台，不会扣款。确认后账单会标记为已支付。
          </p>
          <div className="form-actions">
            <button className="button button-plain" disabled={busy} onClick={() => setPay(null)}>
              取消
            </button>
            <button className="button button-primary" disabled={busy} onClick={perform}>
              {busy ? '处理中…' : '确认演示支付'}
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  )
}

export function Notifications() {
  const result = useResource<Notice[]>('/api/me/notifications')
  const notify = useToast()
  const mark = async (item: Notice) => {
    if (item.read) return
    try {
      await patch(`/api/me/notifications/${item.id}/read`, {})
      result.reload()
    } catch (error) {
      notify(errorText(error), 'error')
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="YOUR UPDATES"
        title="重要的消息，不错过"
        description="租约、账单与租住事项的最新动态。"
      />
      <div className="panel">
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.length ? (
          <div className="notification-list">
            {result.data.map((item) => (
              <article className={item.read ? 'notification' : 'notification unread'} key={item.id}>
                <span className="notification-icon">
                  <Bell size={19} />
                </span>
                <div>
                  <h3>
                    {item.title}
                    {!item.read ? <span className="unread-dot" /> : null}
                  </h3>
                  <p>{item.content}</p>
                  <small>{dateTime(item.createdAt)}</small>
                </div>
                {!item.read ? (
                  <button className="text-button" onClick={() => mark(item)}>
                    <Check size={15} />
                    标为已读
                  </button>
                ) : null}
              </article>
            ))}
          </div>
        ) : (
          <Empty title="暂时没有新消息" description="有新的租住事项时，我们会在这里提醒你。" />
        )}
      </div>
    </>
  )
}
