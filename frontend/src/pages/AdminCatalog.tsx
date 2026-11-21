import { useState } from 'react'
import { CalendarDays, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { patch, post, put, query, remove } from '../api'
import { useResource, useToast } from '../hooks'
import {
  Empty,
  ErrorBox,
  errorText,
  Field,
  FormActions,
  ImageUpload,
  Loading,
  Modal,
  money,
  numeric,
  PageHeading,
  Pagination,
  readForm,
  Table,
  value,
} from '../components'
import type { Apartment, Listing, Page } from '../types'

export function AdminApartments() {
  const [page, setPage] = useState(1)
  const [city, setCity] = useState('')
  const result = useResource<Page<Apartment>>(
    `/api/admin/catalog/apartments?${query({ page, pageSize: 10, city })}`,
  )
  const [editing, setEditing] = useState<Apartment | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Apartment | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const notify = useToast()
  const close = () => {
    setEditing(null)
    setError(null)
  }
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    const data = readForm(event)
    const body = {
      name: value(data, 'name'),
      city: value(data, 'city'),
      monthlyRent: numeric(data, 'monthlyRent'),
      address: value(data, 'address'),
      description: value(data, 'description'),
      imageUrl: value(data, 'imageUrl'),
    }
    setBusy(true)
    setError(null)
    try {
      if (editing === 'new') await post('/api/admin/catalog/apartments', body)
      else if (editing) await put(`/api/admin/catalog/apartments/${editing.id}`, body)
      notify(editing === 'new' ? '公寓已创建' : '公寓已更新')
      close()
      result.reload()
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }
  const destroy = async () => {
    if (!deleting) return
    setBusy(true)
    try {
      await remove(`/api/admin/catalog/apartments/${deleting.id}`)
      notify('公寓已删除')
      setDeleting(null)
      result.reload()
    } catch (failure) {
      notify(errorText(failure), 'error')
    } finally {
      setBusy(false)
    }
  }
  const current = editing && editing !== 'new' ? editing : null
  return (
    <>
      <PageHeading
        eyebrow="APARTMENT PORTFOLIO"
        title="公寓管理"
        description="管理公寓地址、介绍及基础租金，让每个住处有据可查。"
        action={
          <button className="button button-primary" onClick={() => setEditing('new')}>
            <Plus size={17} />
            新增公寓
          </button>
        }
      />
      <div className="panel">
        <div className="table-toolbar">
          <label className="inline-search">
            <Search size={16} />
            <input
              aria-label="按城市筛选公寓"
              placeholder="按城市筛选"
              value={city}
              onChange={(event) => {
                setCity(event.target.value)
                setPage(1)
              }}
            />
          </label>
          <span className="muted">共 {result.data?.total ?? 0} 栋公寓</span>
        </div>
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['公寓名称', '所在城市 / 地址', '基础租金', '房间数', '操作']}>
              {result.data.records.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.name}</strong>
                    <small>公寓 #{item.id}</small>
                  </td>
                  <td>
                    {item.city}
                    <small>{item.address || '尚未填写地址'}</small>
                  </td>
                  <td>¥ {money(item.monthlyRent)} / 月</td>
                  <td>{item.roomCount} 间</td>
                  <td>
                    <div className="row-actions">
                      <Link to={`/admin/rooms?apartmentId=${item.id}`} className="text-button">
                        查看房间
                      </Link>
                      <button
                        className="icon-button"
                        aria-label={`编辑${item.name}`}
                        onClick={() => setEditing(item)}
                      >
                        <Pencil size={15} />
                      </button>
                      <button
                        className="icon-button danger"
                        aria-label={`删除${item.name}`}
                        onClick={() => setDeleting(item)}
                      >
                        <Trash2 size={15} />
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
            title="暂无公寓"
            description="从创建第一栋公寓开始管理你的房源。"
            action={
              <button className="button button-primary" onClick={() => setEditing('new')}>
                新增公寓
              </button>
            }
          />
        )}
      </div>
      {editing ? (
        <Modal title={editing === 'new' ? '新增公寓' : '编辑公寓'} onClose={close} wide>
          <form onSubmit={save}>
            <div className="form-grid">
              <Field label="公寓名称">
                <input name="name" required maxLength={100} defaultValue={current?.name} />
              </Field>
              <Field label="所在城市">
                <input name="city" required maxLength={50} defaultValue={current?.city} />
              </Field>
              <Field label="基础月租金（元）">
                <input
                  name="monthlyRent"
                  type="number"
                  required
                  min={1}
                  max={1000000}
                  defaultValue={current?.monthlyRent ?? 2500}
                />
              </Field>
              <Field label="公寓地址">
                <input name="address" required maxLength={255} defaultValue={current?.address} />
              </Field>
              <Field label="图片地址" hint="可使用 /images/room-1.svg 等站内图片">
                <input
                  name="imageUrl"
                  maxLength={500}
                  defaultValue={current?.imageUrl ?? '/images/room-1.svg'}
                />
              </Field>
            </div>
            <ImageUpload />
            <Field label="公寓介绍">
              <textarea
                name="description"
                maxLength={2000}
                rows={4}
                defaultValue={current?.description}
              />
            </Field>
            {error ? <ErrorBox error={error} /> : null}
            <FormActions busy={busy} onCancel={close} />
          </form>
        </Modal>
      ) : null}
      {deleting ? (
        <Modal title="删除公寓？" onClose={() => setDeleting(null)}>
          <p className="modal-description">
            将删除“{deleting.name}”。仍有关联房间的公寓不能删除，请先处理房间。
          </p>
          <div className="form-actions">
            <button className="button button-plain" onClick={() => setDeleting(null)}>
              取消
            </button>
            <button className="button button-danger" disabled={busy} onClick={destroy}>
              {busy ? '删除中…' : '确认删除'}
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  )
}

export function AdminRooms() {
  const initial = new URLSearchParams(window.location.hash.split('?')[1] ?? '')
  const [apartmentId, setApartmentId] = useState(initial.get('apartmentId') ?? '')
  const [page, setPage] = useState(1)
  const result = useResource<Page<Listing>>(
    `/api/admin/catalog/rooms?${query({ page, pageSize: 10, apartmentId })}`,
  )
  const apartments = useResource<Page<Apartment>>('/api/admin/catalog/apartments?pageSize=100')
  const [editing, setEditing] = useState<Listing | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Listing | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const notify = useToast()
  const close = () => {
    setEditing(null)
    setError(null)
  }
  const current = editing && editing !== 'new' ? editing : null
  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    const data = readForm(event)
    const body = {
      apartmentId: numeric(data, 'apartmentId'),
      roomNumber: value(data, 'roomNumber'),
      title: value(data, 'title'),
      monthlyRent: numeric(data, 'monthlyRent'),
      area: numeric(data, 'area'),
      bedrooms: numeric(data, 'bedrooms'),
      orientation: value(data, 'orientation'),
      description: value(data, 'description'),
      imageUrl: value(data, 'imageUrl'),
      tags: value(data, 'tags')
        .split(/[,，]/)
        .map((tag) => tag.trim())
        .filter(Boolean),
      published: data.get('published') === 'on',
    }
    setBusy(true)
    setError(null)
    try {
      if (editing === 'new') await post('/api/admin/catalog/rooms', body)
      else if (editing) await put(`/api/admin/catalog/rooms/${editing.id}`, body)
      notify(editing === 'new' ? '房间已创建' : '房间已更新')
      close()
      result.reload()
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }
  const publication = async (item: Listing) => {
    try {
      await patch(`/api/rooms/${item.id}/publication`, { published: !item.published })
      notify(item.published ? '房间已下架' : '房间已上架')
      result.reload()
    } catch (failure) {
      notify(errorText(failure), 'error')
    }
  }
  const destroy = async () => {
    if (!deleting) return
    setBusy(true)
    try {
      await remove(`/api/admin/catalog/rooms/${deleting.id}`)
      notify('房间已删除')
      setDeleting(null)
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
        eyebrow="ROOM INVENTORY"
        title="房间管理"
        description="维护房间信息、租金与上架状态，安排租客的下一次心动。"
        action={
          <button className="button button-primary" onClick={() => setEditing('new')}>
            <Plus size={17} />
            新增房间
          </button>
        }
      />
      <div className="panel">
        <div className="table-toolbar">
          <select
            aria-label="按公寓筛选房间"
            value={apartmentId}
            onChange={(event) => {
              setApartmentId(event.target.value)
              setPage(1)
            }}
          >
            <option value="">全部公寓</option>
            {apartments.data?.records.map((item) => (
              <option value={item.id} key={item.id}>
                {item.name}
              </option>
            ))}
          </select>
          <span className="muted">共 {result.data?.total ?? 0} 间房</span>
        </div>
        {result.loading ? (
          <Loading />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <Table headers={['房间 / 公寓', '户型', '月租金', '上架状态', '操作']}>
              {result.data.records.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.title}</strong>
                    <small>
                      {item.apartmentName} · {item.roomNumber} 室 · #{item.id}
                    </small>
                  </td>
                  <td>
                    {item.area}㎡ · {item.bedrooms} 居室<small>朝{item.orientation}</small>
                  </td>
                  <td>¥ {money(item.monthlyRent)}</td>
                  <td>
                    <span className={item.published ? 'badge badge-active' : 'badge badge-draft'}>
                      {item.published ? '已上架' : '未上架'}
                    </span>
                  </td>
                  <td>
                    <div className="row-actions">
                      <Link
                        className="icon-button"
                        aria-label={`安排${item.roomNumber}室时段`}
                        to={`/admin/slots?roomId=${item.id}`}
                      >
                        <CalendarDays size={16} />
                      </Link>
                      <button className="text-button" onClick={() => publication(item)}>
                        {item.published ? '下架' : '上架'}
                      </button>
                      <button
                        className="icon-button"
                        aria-label={`编辑房间${item.id}`}
                        onClick={() => setEditing(item)}
                      >
                        <Pencil size={15} />
                      </button>
                      <button
                        className="icon-button danger"
                        aria-label={`删除房间${item.id}`}
                        onClick={() => setDeleting(item)}
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={page} pageSize={10} total={result.data.total} onChange={setPage} />
          </>
        ) : (
          <Empty title="暂无房间" description="先创建公寓，再添加房间及租金信息。" />
        )}
      </div>
      {editing ? (
        <Modal title={editing === 'new' ? '新增房间' : '编辑房间'} onClose={close} wide>
          <form onSubmit={save}>
            <div className="form-grid">
              <Field label="所属公寓">
                <select
                  name="apartmentId"
                  required
                  defaultValue={current?.apartmentId ?? apartmentId}
                >
                  <option value="">选择公寓</option>
                  {apartments.data?.records.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="房间号">
                <input
                  name="roomNumber"
                  required
                  maxLength={20}
                  defaultValue={current?.roomNumber}
                />
              </Field>
              <Field label="房源标题">
                <input
                  name="title"
                  required
                  maxLength={120}
                  defaultValue={current?.title}
                  placeholder="例如：阳光一居，留给自己的舒适空间"
                />
              </Field>
              <Field label="月租金（元）">
                <input
                  name="monthlyRent"
                  type="number"
                  required
                  min={1}
                  max={1000000}
                  defaultValue={current?.monthlyRent ?? 2500}
                />
              </Field>
              <Field label="建筑面积（㎡）">
                <input
                  name="area"
                  type="number"
                  required
                  min={1}
                  max={1000}
                  step="0.1"
                  defaultValue={current?.area ?? 35}
                />
              </Field>
              <Field label="居室数量">
                <input
                  name="bedrooms"
                  type="number"
                  required
                  min={1}
                  max={10}
                  defaultValue={current?.bedrooms ?? 1}
                />
              </Field>
              <Field label="朝向">
                <select name="orientation" defaultValue={current?.orientation ?? '南'}>
                  {['南', '东', '西', '北', '东南', '西南', '南北'].map((item) => (
                    <option key={item}>{item}</option>
                  ))}
                </select>
              </Field>
              <Field label="图片地址">
                <input
                  name="imageUrl"
                  maxLength={500}
                  defaultValue={current?.imageUrl ?? '/images/room-1.svg'}
                />
              </Field>
            </div>
            <ImageUpload />
            <Field label="房源标签" hint="多个标签用逗号分隔，例如：独立卫浴,近地铁,家具齐全">
              <input
                name="tags"
                maxLength={300}
                defaultValue={current?.tags?.join(',') ?? '独立卫浴,家具齐全'}
              />
            </Field>
            <Field label="房间介绍">
              <textarea
                name="description"
                maxLength={2000}
                rows={3}
                defaultValue={current?.description}
              />
            </Field>
            <label className="checkbox-field">
              <input
                name="published"
                type="checkbox"
                defaultChecked={current?.published ?? false}
              />
              保存后上架房间
            </label>
            {error ? <ErrorBox error={error} /> : null}
            <FormActions busy={busy} onCancel={close} />
          </form>
        </Modal>
      ) : null}
      {deleting ? (
        <Modal title="删除房间？" onClose={() => setDeleting(null)}>
          <p className="modal-description">
            将删除 {deleting.apartmentName} 的 {deleting.roomNumber}{' '}
            室。有预约或租约记录的房间可能无法直接删除。
          </p>
          <div className="form-actions">
            <button className="button button-plain" onClick={() => setDeleting(null)}>
              取消
            </button>
            <button className="button button-danger" disabled={busy} onClick={destroy}>
              {busy ? '删除中…' : '确认删除'}
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  )
}
