import { useState } from 'react'
import { ArrowRight, Building2, KeyRound, ShieldCheck } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { post } from '../api'
import { useSession, useToast } from '../hooks'
import { ErrorBox, Field } from '../components'

export default function Auth({
  register = false,
  admin = false,
}: {
  register?: boolean
  admin?: boolean
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const { refresh } = useSession()
  const notify = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const body: Record<string, string> = {
      username: String(data.get('username')).trim(),
      password: String(data.get('password')),
    }
    if (register) body.displayName = String(data.get('displayName')).trim()
    setBusy(true)
    setError(null)
    try {
      await post(
        admin ? '/api/admin/auth/login' : register ? '/api/auth/register' : '/api/auth/login',
        body,
      )
      const session = await refresh()
      notify(register ? '注册成功，欢迎来到尚庭' : '登录成功')
      navigate(
        admin || session.role === 'ADMIN'
          ? '/admin'
          : ((location.state as { from?: string } | null)?.from ?? '/me/appointments'),
        { replace: true },
      )
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="auth-page">
      <div className="auth-story">
        <div className="auth-story-mark">
          <Building2 size={33} />
        </div>
        <span className="eyebrow">SHANGTING LIVING</span>
        <h1>{admin ? '让每一份租住期待，\n都有妥善的回应。' : '从今天起，\n让生活有个好住处。'}</h1>
        <p>
          {admin
            ? '管理房源、安排看房、照顾每一位住户。'
            : '在尚庭，找房、预约和租住管理，\n都可以从这里安心开始。'}
        </p>
        <img src="/images/city-home.svg" alt="城市公寓建筑插画" />
      </div>
      <div className="auth-form-wrap">
        <div className="auth-form-heading">
          <span className="auth-icon">
            {admin ? <ShieldCheck size={23} /> : <KeyRound size={23} />}
          </span>
          <h2>{admin ? '管理员登录' : register ? '创建租客账号' : '欢迎回来'}</h2>
          <p>
            {admin
              ? '请使用管理员账号进入运营工作台'
              : register
                ? '注册后即可收藏房源、预约看房和管理租约'
                : '登录后，继续你的租住生活'}
          </p>
        </div>
        <form onSubmit={submit}>
          {register ? (
            <Field label="怎么称呼你">
              <input
                name="displayName"
                required
                minLength={1}
                maxLength={30}
                autoComplete="name"
                placeholder="请输入姓名或昵称"
              />
            </Field>
          ) : null}
          <Field label="账号" hint="3–50 位字母、数字或下划线">
            <input
              name="username"
              required
              pattern="[A-Za-z0-9_]{3,50}"
              maxLength={50}
              autoComplete="username"
              placeholder="请输入账号"
            />
          </Field>
          <Field
            label="密码"
            hint={register ? '至少 12 位字符，建议组合使用字母和数字' : undefined}
          >
            <input
              name="password"
              type="password"
              required
              minLength={register ? 12 : 1}
              maxLength={72}
              autoComplete={register ? 'new-password' : 'current-password'}
              placeholder={register ? '设置你的登录密码' : '请输入密码'}
            />
          </Field>
          {error ? <ErrorBox error={error} /> : null}
          <button className="button button-primary full" disabled={busy}>
            {busy ? '正在处理…' : admin ? '进入工作台' : register ? '注册并登录' : '登录'}
            <ArrowRight size={17} />
          </button>
        </form>
        <div className="auth-links">
          {admin ? (
            <Link to="/login">返回租客登录</Link>
          ) : register ? (
            <span>
              已经有账号？<Link to="/login">立即登录</Link>
            </span>
          ) : (
            <span>
              还没有账号？<Link to="/register">免费注册</Link>
            </span>
          )}
          {!admin ? (
            <Link className="muted" to="/admin/login">
              管理员入口
            </Link>
          ) : null}
        </div>
        <p className="auth-footnote">账号通过安全会话保持登录，退出后登录状态立即失效。</p>
      </div>
    </section>
  )
}
