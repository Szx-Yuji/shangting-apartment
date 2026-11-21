import { Suspense, useState } from 'react'
import {
  Activity,
  ArrowRight,
  Bell,
  Building2,
  CalendarCheck2,
  CalendarDays,
  ChevronRight,
  CircleDollarSign,
  FileText,
  Home,
  LayoutDashboard,
  LogOut,
  Plus,
  ReceiptText,
  ShieldCheck,
  Users,
} from 'lucide-react'
import { Link, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useResource, useSession, useToast } from '../hooks'
import {
  Brand,
  Badge,
  dateTime,
  Empty,
  ErrorBox,
  errorText,
  Loading,
  money,
  PageHeading,
  Table,
} from '../components'
import type { Dashboard } from '../types'

export function AdminLayout() {
  const { session, loading, logout } = useSession()
  const notify = useToast()
  const location = useLocation()
  const [busy, setBusy] = useState(false)
  if (loading) return <Loading />
  if (!session.authenticated || session.role !== 'ADMIN')
    return <Navigate to="/admin/login" state={{ from: location.pathname }} replace />
  const exit = async () => {
    setBusy(true)
    try {
      await logout()
      notify('已安全退出')
    } catch (error) {
      notify(errorText(error), 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <Brand light />
        <div className="admin-nav-label">运营工作台</div>
        <nav>
          {[
            { to: '/admin', label: '运营概览', icon: LayoutDashboard, end: true },
            { to: '/admin/apartments', label: '公寓管理', icon: Building2 },
            { to: '/admin/rooms', label: '房间管理', icon: Home },
            { to: '/admin/slots', label: '看房时段', icon: CalendarDays },
            { to: '/admin/appointments', label: '预约审核', icon: CalendarCheck2 },
            { to: '/admin/leases', label: '租约管理', icon: FileText },
            { to: '/admin/bills', label: '账单管理', icon: ReceiptText },
            { to: '/admin/users', label: '租客账号', icon: Users },
            { to: '/admin/accounts', label: '管理员账号', icon: ShieldCheck },
          ].map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end}>
              <item.icon size={18} />
              <span>{item.label}</span>
              <ChevronRight size={14} />
            </NavLink>
          ))}
        </nav>
        <div className="admin-sidebar-bottom">
          <Link to="/">
            <ArrowRight size={17} />
            查看租客端
          </Link>
          <button onClick={exit} disabled={busy}>
            <LogOut size={17} />
            {busy ? '正在退出…' : '退出登录'}
          </button>
        </div>
      </aside>
      <div className="admin-body">
        <header className="admin-topbar">
          <div>
            <span className="online-dot" />
            尚庭公寓运营中心
          </div>
          <div className="admin-account">
            <span>
              {new Intl.DateTimeFormat('zh-CN', {
                month: 'long',
                day: 'numeric',
                weekday: 'long',
              }).format(new Date())}
            </span>
            <span className="avatar">{session.account?.username.slice(0, 1).toUpperCase()}</span>
            <strong>{session.account?.username}</strong>
          </div>
        </header>
        <main className="admin-content">
          <Suspense fallback={<Loading />}>
            <Outlet />
          </Suspense>
        </main>
        <footer className="admin-footer">
          尚庭公寓 · 把每一份租住期待，妥善安放。<span>运营工作台</span>
        </footer>
      </div>
    </div>
  )
}

export function AdminDashboard() {
  const result = useResource<Dashboard>('/api/admin/dashboard')
  if (result.loading) return <Loading />
  if (result.error) return <ErrorBox error={result.error} onRetry={result.reload} />
  const data = result.data
  if (!data) return null
  const stats = [
    { label: '在管公寓', value: data.apartmentCount, suffix: '栋', icon: Building2, tone: 'blue' },
    { label: '房间总数', value: data.roomCount, suffix: '间', icon: Home, tone: 'teal' },
    {
      label: '待处理预约',
      value: data.pendingAppointmentCount,
      suffix: '条',
      icon: CalendarCheck2,
      tone: 'amber',
    },
    {
      label: '履约中租约',
      value: data.activeLeaseCount,
      suffix: '份',
      icon: FileText,
      tone: 'purple',
    },
    {
      label: '待支付账单',
      value: data.unpaidBillCount,
      suffix: '笔',
      icon: ReceiptText,
      tone: 'rose',
    },
    {
      label: '本月已收',
      value: money(data.monthlyRevenue),
      suffix: '元',
      icon: CircleDollarSign,
      tone: 'teal',
    },
  ]
  const max = Math.max(1, ...data.cityStats.map((item) => item.roomCount))
  return (
    <>
      <PageHeading
        eyebrow="OPERATIONS OVERVIEW"
        title="你好，今天也把生活照顾好。"
        description="房源、预约与租约动态，都在这里。"
        action={
          <Link className="button button-primary" to="/admin/appointments">
            处理预约 <ArrowRight size={16} />
          </Link>
        }
      />
      <div className="stats-grid">
        {stats.map((item) => (
          <div className={`stat-card ${item.tone}`} key={item.label}>
            <div>
              <span>{item.label}</span>
              <item.icon size={19} />
            </div>
            <strong>
              {item.value}
              <small>{item.suffix}</small>
            </strong>
          </div>
        ))}
      </div>
      <div className="dashboard-columns">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>房源分布</h2>
              <p>按城市查看在管房间</p>
            </div>
            <Building2 size={21} />
          </div>
          <div className="city-bars">
            {data.cityStats.length ? (
              data.cityStats.map((item) => (
                <div key={item.city}>
                  <div>
                    <span>{item.city}</span>
                    <strong>{item.roomCount} 间</strong>
                  </div>
                  <span className="bar-track">
                    <span style={{ width: `${(item.roomCount / max) * 100}%` }} />
                  </span>
                </div>
              ))
            ) : (
              <Empty title="暂无房源分布" description="创建公寓和房间后即可查看。" />
            )}
          </div>
        </section>
        <section className="panel overview-action">
          <span className="overview-emblem">
            <Activity size={28} />
          </span>
          <span className="eyebrow">今日运营</span>
          <h2>
            让每间房，
            <br />
            都被妥善照顾。
          </h2>
          <p>
            当前已上架 <strong>{data.publishedRoomCount}</strong>{' '}
            间房源。及时更新可看房时段，让租客的期待得到回应。
          </p>
          <Link to="/admin/slots">
            安排看房时段 <ArrowRight size={17} />
          </Link>
        </section>
      </div>
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>最近预约</h2>
            <p>及时确认每一次看房期待</p>
          </div>
          <Link className="text-link" to="/admin/appointments">
            查看全部 <ArrowRight size={15} />
          </Link>
        </div>
        {data.recentAppointments.length ? (
          <Table headers={['预约房源', '联系人', '看房时间', '状态']}>
            {data.recentAppointments.map((item) => (
              <tr key={item.id}>
                <td>
                  <strong>{item.apartmentName}</strong>
                  <small>
                    {item.roomNumber} 室 · #{item.id}
                  </small>
                </td>
                <td>{item.contactName}</td>
                <td>{dateTime(item.appointmentTime)}</td>
                <td>
                  <Badge status={item.status} />
                </td>
              </tr>
            ))}
          </Table>
        ) : (
          <Empty title="还没有预约" description="租客提交的预约会显示在这里。" />
        )}
      </section>
      <div className="quick-links">
        <Link to="/admin/apartments">
          <Plus size={18} />
          添加公寓与房源
          <ArrowRight size={16} />
        </Link>
        <Link to="/admin/leases">
          <FileText size={18} />
          管理租住合约
          <ArrowRight size={16} />
        </Link>
        <Link to="/admin/bills">
          <Bell size={18} />
          跟进待支付账单
          <ArrowRight size={16} />
        </Link>
      </div>
    </>
  )
}
