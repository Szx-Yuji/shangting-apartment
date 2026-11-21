import { useState } from 'react'
import {
  ArrowDown,
  ArrowRight,
  CalendarCheck2,
  Check,
  MapPin,
  Search,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, query } from '../api'
import { useResource, useSession, useToast } from '../hooks'
import { Empty, ErrorBox, ListingCard, Loading, Pagination } from '../components'
import type { Listing, Page } from '../types'

export default function Explore() {
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')
  const [city, setCity] = useState(params.get('city') ?? '')
  const page = Number(params.get('page') ?? 1)
  const result = useResource<Page<Listing>>(
    `/api/public/listings?${query({ q: params.get('q'), city: params.get('city'), sort: params.get('sort') ?? 'newest', minRent: params.get('minRent'), maxRent: params.get('maxRent'), page, pageSize: 9 })}`,
  )
  const { session } = useSession()
  const notify = useToast()
  const navigate = useNavigate()
  const update = (values: Record<string, string>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(values)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    next.set('page', '1')
    setParams(next)
  }
  const favorite = async (listing: Listing) => {
    if (!session.authenticated || session.role !== 'USER') {
      notify('请先登录租客账号后收藏房源')
      navigate('/login')
      return
    }
    try {
      await api(`/api/me/favorites/${listing.id}`, { method: 'PUT' })
      notify('已加入我的收藏')
    } catch (error) {
      notify(error instanceof Error ? error.message : '收藏失败', 'error')
    }
  }
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">
            <span className="tiny-line" />
            为每一种城市生活，留一盏灯
          </span>
          <h1>
            把向往的生活，
            <br />
            安放在<span>合适的家。</span>
          </h1>
          <p>
            从看房到入住，让每一步都清楚、安心。
            <br />
            发现属于你的城市角落，开启新的日常。
          </p>
          <div className="hero-points">
            <span>
              <ShieldCheck size={17} />
              真实房源
            </span>
            <span>
              <CalendarCheck2 size={17} />
              在线约看
            </span>
            <span>
              <Check size={17} />
              租住一站管理
            </span>
          </div>
        </div>
        <div className="hero-art">
          <img src="/images/city-home.svg" alt="阳台、绿植与窗边的城市公寓生活插画" />
          <div className="hero-note">
            <Sparkles size={16} />
            <span>住得舒适，生活才有余地。</span>
          </div>
        </div>
      </section>
      <section className="search-panel" aria-label="搜索房源">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            update({ q: search, city })
          }}
        >
          <label>
            <MapPin size={18} />
            <select
              aria-label="选择城市"
              value={city}
              onChange={(event) => setCity(event.target.value)}
            >
              <option value="">全部城市</option>
              {['长春', '北京', '上海', '杭州', '深圳', '成都'].map((item) => (
                <option key={item}>{item}</option>
              ))}
            </select>
          </label>
          <span className="search-separator" />
          <label className="search-keyword">
            <Search size={18} />
            <input
              aria-label="搜索公寓或地址"
              placeholder="搜索公寓名称、地址或房间"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <button className="button button-primary" type="submit">
            寻找好房 <ArrowRight size={17} />
          </button>
        </form>
      </section>
      <section className="section listings-section" id="listings">
        <div className="section-heading">
          <div>
            <span className="eyebrow">A PLACE TO CALL HOME</span>
            <h2>下一站，理想生活</h2>
            <p>认真挑选每一个住处，也认真对待每一份期待。</p>
          </div>
          <a href="#listings" className="section-anchor">
            发现房源 <ArrowDown size={15} />
          </a>
        </div>
        <div className="filter-bar">
          <div className="filter-pills">
            {[
              { label: '全部租金', min: '', max: '' },
              { label: '¥ 2,000 以下', min: '', max: '2000' },
              { label: '¥ 2,000–3,500', min: '2000', max: '3500' },
              { label: '¥ 3,500 以上', min: '3500', max: '' },
            ].map((filter) => (
              <button
                key={filter.label}
                className={
                  (params.get('minRent') ?? '') === filter.min &&
                  (params.get('maxRent') ?? '') === filter.max
                    ? 'selected'
                    : ''
                }
                onClick={() => update({ minRent: filter.min, maxRent: filter.max })}
              >
                {filter.label}
              </button>
            ))}
          </div>
          <select
            aria-label="房源排序"
            value={params.get('sort') ?? 'newest'}
            onChange={(event) => update({ sort: event.target.value })}
          >
            <option value="newest">最新上架</option>
            <option value="rentAsc">租金从低到高</option>
            <option value="rentDesc">租金从高到低</option>
          </select>
        </div>
        {result.loading ? (
          <Loading text="正在寻找合适的房源" />
        ) : result.error ? (
          <ErrorBox error={result.error} onRetry={result.reload} />
        ) : result.data?.records.length ? (
          <>
            <div className="listing-grid">
              {result.data.records.map((listing) => (
                <ListingCard key={listing.id} listing={listing} onFavorite={favorite} />
              ))}
            </div>
            <Pagination
              page={page}
              pageSize={9}
              total={result.data.total}
              onChange={(next) => {
                const nextParams = new URLSearchParams(params)
                nextParams.set('page', String(next))
                setParams(nextParams)
                document.getElementById('listings')?.scrollIntoView({ behavior: 'smooth' })
              }}
            />
          </>
        ) : (
          <Empty
            title="暂时没有符合条件的房源"
            description="换一个城市或租金范围，再看看合适的家。"
            action={
              <button
                className="button button-soft"
                onClick={() => {
                  setCity('')
                  setSearch('')
                  setParams({})
                }}
              >
                清除筛选
              </button>
            }
          />
        )}
      </section>
      <section className="living-strip">
        <div>
          <span className="eyebrow">从一间房，到一种生活</span>
          <h2>找房之外，我们也照顾你的日常。</h2>
        </div>
        <Link
          className="button button-light"
          to={session.authenticated ? '/me/appointments' : '/register'}
        >
          开启我的租住生活 <ArrowRight size={17} />
        </Link>
      </section>
    </>
  )
}
