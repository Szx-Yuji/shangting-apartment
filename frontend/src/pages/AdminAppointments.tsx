import { useState } from 'react'
import { Check, Clock3, Download, FileSearch, Plus, Search, X } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { patch, post, query } from '../api'
import { useResource, useToast } from '../hooks'
import {
  Badge,
  dateTime,
  Empty,
  ErrorBox,
  errorText,
  Field,
  FormActions,
  Loading,
  Modal,
  numeric,
  PageHeading,
  Pagination,
  readForm,
  Table,
  value,
} from '../components'
import type { Appointment, Listing, Page, ReviewLog, Slot } from '../types'

export function AdminSlots() {
  const [params] = useSearchParams()
  const [roomId, setRoomId] = useState(params.get('roomId') ?? '')
  const [page, setPage] = useState(1)
  const rooms = useResource<Page<Listing>>('/api/admin/catalog/rooms?pageSize=100')
  const result = useResource<Page<Slot>>(
    roomId ? `/api/admin/appointment-slots?${query({ roomId, page, pageSize: 10 })}` : null,
  )
  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const notify = useToast()
  const close = () => {
    setCreating(false)
    setError(null)
  }
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    const data = readForm(event)
    const start = new Date(value(data, 'startTime'))
    const end = new Date(value(data, 'endTime'))
    if (start >= end) {
      setError(new Error('结束时间必须晚于开始时间'))
      return
    }
    setBusy(true)
    setError(null)
    try {
      const selectedRoom = numeric(data, 'roomId')
      await post('/api/admin/appointment-slots', {
        roomId: selectedRoom,
        startTime: start.toISOString(),
        endTime: end.toISOString(),
        capacity: numeric(data, 'capacity'),
      })
      setRoomId(String(selectedRoom))
      notify('看房时段已创建')
      close()
      result.reload()
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="VIEWING SCHEDULE"
        title="看房时段"
        description="为每个房间安排可预约时段，系统会控制名额，避免超额预约。"
        action={
          <button className="button button-primary" onClick={() => setCreating(true)}>
            <Plus size={17} />
            新增时段
          </button>
        }
      />
      <div className="panel">
        <div className="table-toolbar">
          <select
            aria-label="选择房间查看时段"
            value={roomId}
            onChange={(event) => {
              setRoomId(event.target.value)
              setPage(1)
            }}
          >
            <option value="">选择要查看的房间</option>
            {rooms.data?.records.map((room) => (
              <option key={room.id} value={room.id}>
                {room.apartmentName} · {room.roomNumber} 室
              </option>
            ))}
          </select>
          <span className="muted">所有时间按当前设备时区显示</span>
        </div>
        {!roomId ? (
          <Empty title="先选择一个房间" description="查看它的看房安排，或新增可预约时段。" />
        ) : result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['时段编号', '开始时间', '结束时间', '预约容量', '剩余名额']}>
              {result.data.records.map((slot) => (
                <tr key={slot.id}>
                  <td>#{slot.id}</td>
                  <td>{dateTime(slot.startTime)}</td>
                  <td>{dateTime(slot.endTime)}</td>
                  <td>{slot.capacity ?? slot.remainingCapacity + (slot.reservedCount ?? 0)} 人</td>
                  <td>
                    <span
                      className={
                        slot.remainingCapacity > 0 ? 'badge badge-active' : 'badge badge-rejected'
                      }
                    >
                      {slot.remainingCapacity} 个
                    </span>
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty title="这个房间还没有看房时段" description="创建未来的时段后，租客即可预约。" />
        )}
      </div>
      {creating ? (
        <Modal title="新增看房时段" onClose={close}>
          <form onSubmit={save}>
            <Field label="房间">
              <select name="roomId" required defaultValue={roomId}>
                <option value="">选择房间</option>
                {rooms.data?.records.map((room) => (
                  <option key={room.id} value={room.id}>
                    {room.apartmentName} · {room.roomNumber} 室
                  </option>
                ))}
              </select>
            </Field>
            <Field label="开始时间">
              <input name="startTime" type="datetime-local" required />
            </Field>
            <Field label="结束时间">
              <input name="endTime" type="datetime-local" required />
            </Field>
            <Field label="最多预约人数">
              <input name="capacity" type="number" min={1} max={1000} required defaultValue={3} />
            </Field>
            <p className="form-hint">时段须晚于当前时间，同一个房间的时段不能重叠。</p>
            {error ? <ErrorBox error={error} /> : null}
            <FormActions busy={busy} onCancel={close} label="创建时段" />
          </form>
        </Modal>
      ) : null}
    </>
  )
}

export function AdminAppointments() {
  const [page, setPage] = useState(1)
  const [filters, setFilters] = useState({ status: '', roomId: '', startTime: '', endTime: '' })
  const search = query({ ...filters, page, pageSize: 10 })
  const result = useResource<Page<Appointment>>(`/api/admin/appointments?${search}`)
  const [review, setReview] = useState<{
    item: Appointment
    status: 'CONFIRMED' | 'REJECTED'
  } | null>(null)
  const [logId, setLogId] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const notify = useToast()
  const log = useResource<ReviewLog>(logId ? `/api/admin/appointments/${logId}/review-log` : null)
  const apply = (event: React.FormEvent<HTMLFormElement>) => {
    const data = readForm(event)
    const start = value(data, 'startTime'),
      end = value(data, 'endTime')
    setFilters({
      status: value(data, 'status'),
      roomId: value(data, 'roomId'),
      startTime: start ? new Date(start).toISOString() : '',
      endTime: end ? new Date(end).toISOString() : '',
    })
    setPage(1)
  }
  const perform = async () => {
    if (!review) return
    setBusy(true)
    try {
      await patch(`/api/admin/appointments/${review.item.id}/status`, { status: review.status })
      notify(review.status === 'CONFIRMED' ? '预约已确认' : '预约已拒绝，名额已释放')
      setReview(null)
      result.reload()
    } catch (error) {
      notify(errorText(error), 'error')
    } finally {
      setBusy(false)
    }
  }
  const exportCsv = async () => {
    try {
      const response = await fetch(`/api/admin/appointments/export.csv?${query(filters)}`, {
        credentials: 'include',
      })
      if (!response.ok) {
        const body = await response.json().catch(() => ({}))
        throw new Error(body.detail ?? '导出失败，请稍后重试')
      }
      const url = URL.createObjectURL(await response.blob())
      const link = document.createElement('a')
      link.href = url
      link.download = '尚庭预约记录.csv'
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      notify('预约记录已导出')
    } catch (error) {
      notify(errorText(error), 'error')
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="VIEWING APPOINTMENTS"
        title="预约审核"
        description="核对看房安排，及时回应租客，并保留每一次审核记录。"
        action={
          <button className="button button-soft" onClick={exportCsv}>
            <Download size={16} />
            导出筛选结果
          </button>
        }
      />
      <div className="panel">
        <form className="table-filter-form" onSubmit={apply}>
          <Field label="预约状态">
            <select name="status" defaultValue="">
              <option value="">全部状态</option>
              <option value="PENDING">待审核</option>
              <option value="CONFIRMED">已确认</option>
              <option value="REJECTED">已拒绝</option>
              <option value="CANCELLED">已取消</option>
            </select>
          </Field>
          <Field label="房间编号">
            <input name="roomId" type="number" min={1} placeholder="全部房间" />
          </Field>
          <Field label="开始时间">
            <input name="startTime" type="datetime-local" />
          </Field>
          <Field label="结束时间">
            <input name="endTime" type="datetime-local" />
          </Field>
          <button className="button button-primary">
            <Search size={16} />
            筛选
          </button>
        </form>
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['预约 / 房源', '联系人', '看房时间', '状态', '操作']}>
              {result.data.records.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.apartmentName}</strong>
                    <small>
                      {item.roomNumber} 室 · 预约 #{item.id}
                    </small>
                  </td>
                  <td>
                    {item.contactName}
                    <small>{item.contactPhone}</small>
                  </td>
                  <td>{dateTime(item.appointmentTime)}</td>
                  <td>
                    <Badge status={item.status} />
                  </td>
                  <td>
                    <div className="row-actions">
                      {item.status === 'PENDING' ? (
                        <>
                          <button
                            className="text-button"
                            onClick={() => setReview({ item, status: 'CONFIRMED' })}
                          >
                            <Check size={14} />
                            确认
                          </button>
                          <button
                            className="text-button danger"
                            onClick={() => setReview({ item, status: 'REJECTED' })}
                          >
                            <X size={14} />
                            拒绝
                          </button>
                        </>
                      ) : null}
                      <button
                        className="icon-button"
                        aria-label={`查看预约${item.id}的审核记录`}
                        onClick={() => setLogId(item.id)}
                      >
                        <FileSearch size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty
            title="没有符合条件的预约"
            description="调整筛选条件，或等待租客提交新的看房预约。"
          />
        )}
      </div>
      {review ? (
        <Modal
          title={review.status === 'CONFIRMED' ? '确认看房预约？' : '拒绝看房预约？'}
          onClose={() => setReview(null)}
        >
          <p className="modal-description">
            {review.item.contactName} 预约了 {review.item.apartmentName} {review.item.roomNumber}{' '}
            室，时间为 {dateTime(review.item.appointmentTime)}。
            {review.status === 'REJECTED'
              ? '拒绝后会释放预约名额。'
              : '确认后租客可在个人中心看到最新状态。'}
          </p>
          <div className="form-actions">
            <button className="button button-plain" onClick={() => setReview(null)}>
              返回
            </button>
            <button
              className={`button ${review.status === 'REJECTED' ? 'button-danger' : 'button-primary'}`}
              disabled={busy}
              onClick={perform}
            >
              {busy ? '处理中…' : review.status === 'REJECTED' ? '确认拒绝' : '确认预约'}
            </button>
          </div>
        </Modal>
      ) : null}
      {logId ? (
        <Modal title={`预约 #${logId} 的审核记录`} onClose={() => setLogId(null)}>
          {log.loading ? (
            <Loading />
          ) : log.error ? (
            <ErrorBox error={log.error} />
          ) : log.data ? (
            <div className="review-timeline">
              <span className="timeline-icon">
                <Clock3 size={20} />
              </span>
              <div>
                <h3>
                  <Badge status={log.data.fromStatus} />
                  <Chevron />
                  <Badge status={log.data.toStatus} />
                </h3>
                <p>审核管理员：#{log.data.adminId}</p>
                <p>审核时间：{dateTime(log.data.createdAt)}</p>
                <small>记录编号 #{log.data.id}</small>
              </div>
            </div>
          ) : null}
        </Modal>
      ) : null}
    </>
  )
}
function Chevron() {
  return <span className="muted">→</span>
}
