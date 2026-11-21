import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiError, post } from './api'
import type { Session } from './types'

export function useResource<T>(url: string | null) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(Boolean(url))
  const [error, setError] = useState<Error | null>(null)
  const [version, setVersion] = useState(0)
  const reload = useCallback(() => setVersion((v) => v + 1), [])
  useEffect(() => {
    if (!url) {
      setLoading(false)
      setData(null)
      setError(null)
      return
    }
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    api<T>(url, { signal: controller.signal })
      .then((result) => {
        if (!controller.signal.aborted) setData(result)
      })
      .catch((failure: Error) => {
        if (!controller.signal.aborted) setError(failure)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [url, version])
  return { data, loading, error, reload }
}

const SessionContext = createContext<{
  session: Session
  loading: boolean
  refresh: () => Promise<Session>
  logout: () => Promise<void>
}>({
  session: { authenticated: false, role: null, account: null },
  loading: true,
  refresh: async () => ({ authenticated: false, role: null, account: null }),
  logout: async () => {},
})
export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>({
    authenticated: false,
    role: null,
    account: null,
  })
  const [loading, setLoading] = useState(true)
  const refresh = useCallback(async () => {
    try {
      const result = await api<Session>('/api/session')
      setSession(result)
      return result
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        const anonymous: Session = { authenticated: false, role: null, account: null }
        setSession(anonymous)
        return anonymous
      }
      throw error
    } finally {
      setLoading(false)
    }
  }, [])
  useEffect(() => {
    void refresh().catch(() => setLoading(false))
  }, [refresh])
  const logout = useCallback(async () => {
    await post('/api/auth/logout')
    await refresh()
  }, [refresh])
  return (
    <SessionContext.Provider value={{ session, loading, refresh, logout }}>
      {children}
    </SessionContext.Provider>
  )
}
export const useSession = () => useContext(SessionContext)

const ToastContext = createContext<(message: string, kind?: 'success' | 'error') => void>(() => {})
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; kind: string; id: number } | null>(null)
  const notify = useCallback(
    (message: string, kind = 'success') => setToast({ message, kind, id: Date.now() }),
    [],
  )
  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), 4500)
    return () => clearTimeout(timer)
  }, [toast])
  return (
    <ToastContext.Provider value={notify}>
      {children}
      {toast ? (
        <div role={toast.kind === 'error' ? 'alert' : 'status'} className={`toast ${toast.kind}`}>
          <span>{toast.kind === 'error' ? '!' : '✓'}</span>
          {toast.message}
          <button aria-label="关闭提示" onClick={() => setToast(null)}>
            ×
          </button>
        </div>
      ) : null}
    </ToastContext.Provider>
  )
}
export const useToast = () => useContext(ToastContext)
