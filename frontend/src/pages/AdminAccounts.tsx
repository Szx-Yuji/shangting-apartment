import { useState } from 'react'
import { Plus, ShieldCheck, Users } from 'lucide-react'
import { patch, post } from '../api'
import { useResource, useSession, useToast } from '../hooks'
import {
  dateTime,
  Empty,
  ErrorBox,
  errorText,
  Field,
  FormActions,
  Loading,
  Modal,
  PageHeading,
  Pagination,
  readForm,
  Table,
  value,
} from '../components'
import type { Account, Page } from '../types'

export function AdminUsers() {
  const [page, setPage] = useState(1)
  const result = useResource<Page<Account>>(`/api/admin/users?page=${page}&pageSize=10`)
  return (
    <>
      <PageHeading
        eyebrow="RESIDENT DIRECTORY"
        title="租客账号"
        description="查看已注册租客，创建租约时可选择对应账号。"
      />
      <div className="panel">
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['租客', '账号', '状态', '注册时间']}>
              {result.data.records.map((item) => (
                <tr key={item.id}>
                  <td>
                    <div className="account-cell">
                      <span className="mini-avatar">
                        <Users size={17} />
                      </span>
                      <div>
                        <strong>{item.displayName || item.username}</strong>
                        <small>租客 #{item.id}</small>
                      </div>
                    </div>
                  </td>
                  <td>{item.username}</td>
                  <td>
                    <span className={item.enabled ? 'badge badge-active' : 'badge badge-rejected'}>
                      {item.enabled ? '正常' : '已停用'}
                    </span>
                  </td>
                  <td>{dateTime(item.createdAt)}</td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty title="暂无租客账号" description="租客在用户端注册后，会出现在这里。" />
        )}
      </div>
    </>
  )
}

export function AdminAccounts() {
  const [page, setPage] = useState(1)
  const result = useResource<Page<Account>>(`/api/admin/accounts?page=${page}&pageSize=10`)
  const [creating, setCreating] = useState(false)
  const [changing, setChanging] = useState<Account | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const { session } = useSession()
  const notify = useToast()
  const close = () => {
    setCreating(false)
    setError(null)
  }
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    const data = readForm(event)
    setBusy(true)
    setError(null)
    try {
      await post('/api/admin/accounts', {
        username: value(data, 'username'),
        password: String(data.get('password')),
      })
      notify('管理员账号已创建')
      close()
      result.reload()
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }
  const change = async () => {
    if (!changing) return
    setBusy(true)
    try {
      await patch(`/api/admin/accounts/${changing.id}/enabled`, { enabled: !changing.enabled })
      notify(changing.enabled ? '管理员已停用' : '管理员已启用')
      setChanging(null)
      result.reload()
    } catch (failure) {
      notify(errorText(failure), 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="ADMINISTRATOR ACCESS"
        title="管理员账号"
        description="为运营同事分配独立账号，停用账号后会立即失去访问权限。"
        action={
          <button className="button button-primary" onClick={() => setCreating(true)}>
            <Plus size={17} />
            新增管理员
          </button>
        }
      />
      <div className="panel">
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['管理员', '权限角色', '状态', '创建时间', '操作']}>
              {result.data.records.map((item) => (
                <tr key={item.id}>
                  <td>
                    <div className="account-cell">
                      <span className="mini-avatar">
                        <ShieldCheck size={17} />
                      </span>
                      <div>
                        <strong>
                          {item.username}
                          {item.id === session.account?.id ? (
                            <span className="self-tag">当前账号</span>
                          ) : null}
                        </strong>
                        <small>管理员 #{item.id}</small>
                      </div>
                    </div>
                  </td>
                  <td>运营管理员</td>
                  <td>
                    <span className={item.enabled ? 'badge badge-active' : 'badge badge-rejected'}>
                      {item.enabled ? '已启用' : '已停用'}
                    </span>
                  </td>
                  <td>{dateTime(item.createdAt)}</td>
                  <td>
                    <button
                      className={`text-button ${item.enabled ? 'danger' : ''}`}
                      disabled={item.id === session.account?.id}
                      onClick={() => setChanging(item)}
                    >
                      {item.enabled ? '停用' : '启用'}
                    </button>
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty title="暂无管理员" description="可为运营成员创建独立账号。" />
        )}
      </div>
      {creating ? (
        <Modal title="新增管理员" onClose={close}>
          <form onSubmit={save}>
            <Field label="管理员账号" hint="3–50 位字母、数字或下划线">
              <input
                name="username"
                required
                pattern="[A-Za-z0-9_]{3,50}"
                maxLength={50}
                autoComplete="off"
              />
            </Field>
            <Field label="初始密码" hint="至少 12 位字符，最多 72 字节">
              <input
                name="password"
                type="password"
                required
                minLength={12}
                maxLength={72}
                autoComplete="new-password"
              />
            </Field>
            {error ? <ErrorBox error={error} /> : null}
            <FormActions busy={busy} onCancel={close} label="创建账号" />
          </form>
        </Modal>
      ) : null}
      {changing ? (
        <Modal
          title={changing.enabled ? '停用管理员？' : '启用管理员？'}
          onClose={() => setChanging(null)}
        >
          <p className="modal-description">
            {changing.enabled
              ? `“${changing.username}”将无法继续访问运营工作台。`
              : `“${changing.username}”将恢复运营工作台访问权限。`}
          </p>
          <div className="form-actions">
            <button className="button button-plain" onClick={() => setChanging(null)}>
              取消
            </button>
            <button
              className={`button ${changing.enabled ? 'button-danger' : 'button-primary'}`}
              disabled={busy}
              onClick={change}
            >
              {busy ? '处理中…' : '确认操作'}
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  )
}
