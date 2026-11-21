import { useState } from 'react'
import { Check, Plus, X } from 'lucide-react'
import { post, query } from '../api'
import { useResource, useToast } from '../hooks'
import {
  Badge,
  Empty,
  ErrorBox,
  errorText,
  Field,
  FormActions,
  Loading,
  Modal,
  money,
  numeric,
  PageHeading,
  Pagination,
  readForm,
  shortDate,
  Table,
  value,
} from '../components'
import type { Account, Bill, Lease, Listing, Page } from '../types'

export function AdminLeases() {
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const result = useResource<Page<Lease>>(
    `/api/admin/leases?${query({ page, pageSize: 10, status })}`,
  )
  const rooms = useResource<Page<Listing>>('/api/admin/catalog/rooms?pageSize=100')
  const users = useResource<Page<Account>>('/api/admin/users?pageSize=100')
  const [creating, setCreating] = useState(false)
  const [action, setAction] = useState<{ lease: Lease; kind: 'activate' | 'terminate' } | null>(
    null,
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const notify = useToast()
  const close = () => {
    setCreating(false)
    setError(null)
  }
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    const data = readForm(event)
    if (value(data, 'startDate') >= value(data, 'endDate')) {
      setError(new Error('结束日期必须晚于开始日期'))
      return
    }
    setBusy(true)
    setError(null)
    try {
      await post('/api/admin/leases', {
        roomId: numeric(data, 'roomId'),
        userId: numeric(data, 'userId'),
        startDate: value(data, 'startDate'),
        endDate: value(data, 'endDate'),
        deposit: numeric(data, 'deposit'),
      })
      notify('租约已创建，确认后可激活')
      close()
      result.reload()
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }
  const perform = async () => {
    if (!action) return
    setBusy(true)
    try {
      await post(`/api/admin/leases/${action.lease.id}/${action.kind}`)
      notify(action.kind === 'activate' ? '租约已生效' : '租约已终止')
      setAction(null)
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
        eyebrow="LEASE MANAGEMENT"
        title="租约管理"
        description="管理租客、房间与租期，租金以创建租约时的房源价格为准。"
        action={
          <button className="button button-primary" onClick={() => setCreating(true)}>
            <Plus size={17} />
            新增租约
          </button>
        }
      />
      <div className="panel">
        <div className="table-toolbar">
          <select
            aria-label="筛选租约状态"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value)
              setPage(1)
            }}
          >
            <option value="">全部状态</option>
            <option value="DRAFT">待生效</option>
            <option value="ACTIVE">履约中</option>
            <option value="TERMINATED">已终止</option>
            <option value="EXPIRED">已到期</option>
          </select>
          <span className="muted">租期重叠会由系统阻止</span>
        </div>
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['房源 / 租客', '租期', '月租金 / 押金', '状态', '操作']}>
              {result.data.records.map((lease) => (
                <tr key={lease.id}>
                  <td>
                    <strong>
                      {lease.apartmentName} · {lease.roomNumber}
                    </strong>
                    <small>
                      {lease.username} · 租约 #{lease.id}
                    </small>
                  </td>
                  <td>
                    {shortDate(lease.startDate)}
                    <small>至 {shortDate(lease.endDate)}</small>
                  </td>
                  <td>
                    ¥ {money(lease.monthlyRent)} / 月<small>押金 ¥ {money(lease.deposit)}</small>
                  </td>
                  <td>
                    <Badge status={lease.status} />
                  </td>
                  <td>
                    <div className="row-actions">
                      {lease.status === 'DRAFT' ? (
                        <button
                          className="text-button"
                          onClick={() => setAction({ lease, kind: 'activate' })}
                        >
                          <Check size={14} />
                          激活
                        </button>
                      ) : null}
                      {['DRAFT', 'ACTIVE'].includes(lease.status) ? (
                        <button
                          className="text-button danger"
                          onClick={() => setAction({ lease, kind: 'terminate' })}
                        >
                          <X size={14} />
                          终止
                        </button>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty title="暂无租约" description="看房沟通完成后，为租客创建租约。" />
        )}
      </div>
      {creating ? (
        <Modal title="新增租约" onClose={close}>
          <form onSubmit={save}>
            <Field label="租住房间">
              <select name="roomId" required>
                <option value="">选择房间</option>
                {rooms.data?.records.map((room) => (
                  <option key={room.id} value={room.id}>
                    {room.apartmentName} · {room.roomNumber} · ¥{room.monthlyRent}/月
                  </option>
                ))}
              </select>
            </Field>
            <Field label="租客账号">
              <select name="userId" required>
                <option value="">选择租客</option>
                {users.data?.records
                  .filter((user) => user.enabled !== false)
                  .map((user) => (
                    <option value={user.id} key={user.id}>
                      {user.displayName || user.username}（{user.username}）
                    </option>
                  ))}
              </select>
            </Field>
            <div className="form-grid">
              <Field label="租期开始">
                <input name="startDate" type="date" required />
              </Field>
              <Field label="租期结束">
                <input name="endDate" type="date" required />
              </Field>
            </div>
            <Field label="押金（元）">
              <input
                name="deposit"
                type="number"
                required
                min={0}
                max={1000000}
                step="0.01"
                defaultValue={2500}
              />
            </Field>
            <p className="form-hint">租约创建后为待生效状态，激活后生成账单。</p>
            {error ? <ErrorBox error={error} /> : null}
            <FormActions busy={busy} onCancel={close} label="创建租约" />
          </form>
        </Modal>
      ) : null}
      {action ? (
        <Modal
          title={action.kind === 'activate' ? '激活租约？' : '终止租约？'}
          onClose={() => setAction(null)}
        >
          <p className="modal-description">
            租约 #{action.lease.id}，{action.lease.apartmentName} {action.lease.roomNumber} 室。
            {action.kind === 'activate'
              ? '激活后开始履约并生成对应账单。'
              : '终止后不再继续履约，已存在的账单记录会保留。'}
          </p>
          <div className="form-actions">
            <button className="button button-plain" onClick={() => setAction(null)}>
              取消
            </button>
            <button
              className={`button ${action.kind === 'terminate' ? 'button-danger' : 'button-primary'}`}
              disabled={busy}
              onClick={perform}
            >
              {busy ? '处理中…' : '确认操作'}
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  )
}

export function AdminBills() {
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const result = useResource<Page<Bill>>(
    `/api/admin/bills?${query({ page, pageSize: 10, status })}`,
  )
  const [pay, setPay] = useState<Bill | null>(null)
  const [busy, setBusy] = useState(false)
  const notify = useToast()
  const perform = async () => {
    if (!pay) return
    setBusy(true)
    try {
      await post(`/api/admin/bills/${pay.id}/mark-paid`)
      notify('账单已标记为已支付')
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
        eyebrow="BILLING & COLLECTION"
        title="账单管理"
        description="跟进租金账单和支付状态，线下收款后请及时确认入账。"
      />
      <div className="panel">
        <div className="table-toolbar">
          <select
            aria-label="筛选账单状态"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value)
              setPage(1)
            }}
          >
            <option value="">全部状态</option>
            <option value="UNPAID">待支付</option>
            <option value="PAID">已支付</option>
            <option value="OVERDUE">已逾期</option>
          </select>
          <span className="muted">共 {result.data?.total ?? 0} 笔账单</span>
        </div>
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['房源 / 账期', '应付金额', '到期日', '状态', '操作']}>
              {result.data.records.map((bill) => (
                <tr key={bill.id}>
                  <td>
                    <strong>
                      {bill.apartmentName} · {bill.roomNumber}
                    </strong>
                    <small>
                      {bill.period} · 租约 #{bill.leaseId} · 账单 #{bill.id}
                    </small>
                  </td>
                  <td className="amount">¥ {money(bill.amount)}</td>
                  <td>{shortDate(bill.dueDate)}</td>
                  <td>
                    <Badge status={bill.status} />
                  </td>
                  <td>
                    {['UNPAID', 'OVERDUE'].includes(bill.status) ? (
                      <button className="text-button" onClick={() => setPay(bill)}>
                        确认已收款
                      </button>
                    ) : (
                      <span className="muted">已完成</span>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty title="暂无账单" description="租约激活后会自动生成账单。" />
        )}
      </div>
      {pay ? (
        <Modal title="确认已收款？" onClose={() => setPay(null)}>
          <div className="payment-summary">
            <span>确认入账金额</span>
            <strong>¥ {money(pay.amount)}</strong>
            <p>
              {pay.apartmentName} · {pay.roomNumber} · {pay.period}
            </p>
          </div>
          <p className="modal-description">
            请在确认已实际收到款项后操作。此操作只登记收款状态，不调用真实支付平台。
          </p>
          <div className="form-actions">
            <button className="button button-plain" onClick={() => setPay(null)}>
              取消
            </button>
            <button className="button button-primary" disabled={busy} onClick={perform}>
              {busy ? '处理中…' : '确认已收款'}
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  )
}
