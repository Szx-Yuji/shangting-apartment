import { lazy, Suspense, useState } from 'react'
import { ArrowRight, LogOut, Menu, X } from 'lucide-react'
import { HashRouter, Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { SessionProvider, ToastProvider, useSession, useToast } from './hooks'
import { Brand, Empty, errorText, Loading } from './components'
import Explore from './pages/Explore'
const RoomDetail = lazy(() => import('./pages/RoomDetail'))
const Auth = lazy(() => import('./pages/Auth'))
const PersonalLayout = lazy(() =>
  import('./pages/Personal').then((module) => ({ default: module.PersonalLayout })),
)
const MyAppointments = lazy(() =>
  import('./pages/Personal').then((module) => ({ default: module.MyAppointments })),
)
const Favorites = lazy(() =>
  import('./pages/Personal').then((module) => ({ default: module.Favorites })),
)
const MyLeases = lazy(() =>
  import('./pages/Personal').then((module) => ({ default: module.MyLeases })),
)
const MyBills = lazy(() =>
  import('./pages/Personal').then((module) => ({ default: module.MyBills })),
)
const Notifications = lazy(() =>
  import('./pages/Personal').then((module) => ({ default: module.Notifications })),
)
const AdminLayout = lazy(() =>
  import('./pages/Admin').then((module) => ({ default: module.AdminLayout })),
)
const AdminDashboard = lazy(() =>
  import('./pages/Admin').then((module) => ({ default: module.AdminDashboard })),
)
const AdminApartments = lazy(() =>
  import('./pages/AdminCatalog').then((module) => ({ default: module.AdminApartments })),
)
const AdminRooms = lazy(() =>
  import('./pages/AdminCatalog').then((module) => ({ default: module.AdminRooms })),
)
const AdminSlots = lazy(() =>
  import('./pages/AdminAppointments').then((module) => ({ default: module.AdminSlots })),
)
const AdminAppointments = lazy(() =>
  import('./pages/AdminAppointments').then((module) => ({ default: module.AdminAppointments })),
)
const AdminLeases = lazy(() =>
  import('./pages/AdminLeases').then((module) => ({ default: module.AdminLeases })),
)
const AdminBills = lazy(() =>
  import('./pages/AdminLeases').then((module) => ({ default: module.AdminBills })),
)
const AdminUsers = lazy(() =>
  import('./pages/AdminAccounts').then((module) => ({ default: module.AdminUsers })),
)
const AdminAccounts = lazy(() =>
  import('./pages/AdminAccounts').then((module) => ({ default: module.AdminAccounts })),
)

function PublicLayout() {
  const { session, logout } = useSession()
  const notify = useToast()
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const exit = async () => {
    try {
      await logout()
      notify('已安全退出')
    } catch (error) {
      notify(errorText(error), 'error')
    }
  }
  return (
    <>
      <header className="site-header">
        <div className="header-inner">
          <Brand />
          <nav className={open ? 'site-nav is-open' : 'site-nav'}>
            <Link
              className={location.pathname === '/' ? 'active' : ''}
              to="/"
              onClick={() => setOpen(false)}
            >
              发现好房
            </Link>
            {session.role === 'USER' ? (
              <>
                <Link to="/me/appointments" onClick={() => setOpen(false)}>
                  我的租住
                </Link>
                <Link to="/me/favorites" onClick={() => setOpen(false)}>
                  收藏房源
                </Link>
              </>
            ) : null}
            {session.role === 'ADMIN' ? <Link to="/admin">运营工作台</Link> : null}
          </nav>
          <div className="header-actions">
            {session.authenticated ? (
              <>
                <Link
                  className="user-link"
                  to={session.role === 'ADMIN' ? '/admin' : '/me/appointments'}
                >
                  <span className="small-avatar">
                    {(session.account?.displayName || session.account?.username || '我').slice(
                      0,
                      1,
                    )}
                  </span>
                  <span>{session.account?.displayName || session.account?.username}</span>
                </Link>
                <button className="icon-button" aria-label="退出登录" onClick={exit}>
                  <LogOut size={17} />
                </button>
              </>
            ) : (
              <>
                <Link className="login-link" to="/login">
                  登录
                </Link>
                <Link className="button button-primary button-small" to="/register">
                  开启租住生活 <ArrowRight size={15} />
                </Link>
              </>
            )}
            <button
              className="icon-button mobile-menu"
              aria-label="切换导航"
              onClick={() => setOpen(!open)}
            >
              {open ? <X size={21} /> : <Menu size={21} />}
            </button>
          </div>
        </div>
      </header>
      <main className="public-main">
        <Suspense fallback={<Loading />}>
          <Outlet />
        </Suspense>
      </main>
      <footer className="site-footer">
        <div>
          <Brand />
          <p>住进城市的好时光。</p>
        </div>
        <div>
          <Link to="/">发现房源</Link>
          <Link to="/me/appointments">我的租住</Link>
          <Link to="/admin/login">管理员入口</Link>
          <span>© {new Date().getFullYear()} 尚庭公寓</span>
        </div>
      </footer>
    </>
  )
}

export default function App() {
  return (
    <HashRouter>
      <ToastProvider>
        <SessionProvider>
          <Suspense fallback={<Loading />}>
            <Routes>
              <Route element={<PublicLayout />}>
                <Route index element={<Explore />} />
                <Route path="rooms/:id" element={<RoomDetail />} />
                <Route path="login" element={<Auth />} />
                <Route path="register" element={<Auth register />} />
                <Route path="admin/login" element={<Auth admin />} />
                <Route path="me" element={<PersonalLayout />}>
                  <Route index element={<Navigate to="appointments" replace />} />
                  <Route path="appointments" element={<MyAppointments />} />
                  <Route path="favorites" element={<Favorites />} />
                  <Route path="leases" element={<MyLeases />} />
                  <Route path="bills" element={<MyBills />} />
                  <Route path="notifications" element={<Notifications />} />
                </Route>
              </Route>
              <Route path="admin" element={<AdminLayout />}>
                <Route index element={<AdminDashboard />} />
                <Route path="apartments" element={<AdminApartments />} />
                <Route path="rooms" element={<AdminRooms />} />
                <Route path="slots" element={<AdminSlots />} />
                <Route path="appointments" element={<AdminAppointments />} />
                <Route path="leases" element={<AdminLeases />} />
                <Route path="bills" element={<AdminBills />} />
                <Route path="users" element={<AdminUsers />} />
                <Route path="accounts" element={<AdminAccounts />} />
              </Route>
              <Route
                path="*"
                element={
                  <Empty
                    title="这个页面暂时找不到"
                    description="可以返回首页，继续寻找合适的住处。"
                    action={
                      <Link className="button button-primary" to="/">
                        返回首页
                      </Link>
                    }
                  />
                }
              />
            </Routes>
          </Suspense>
        </SessionProvider>
      </ToastProvider>
    </HashRouter>
  )
}
