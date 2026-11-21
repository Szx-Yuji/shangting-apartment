export interface Page<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
export interface Account {
  id: number
  username: string
  displayName?: string
  enabled?: boolean
  createdAt?: string
}
export interface Session {
  authenticated: boolean
  role: 'ADMIN' | 'USER' | null
  account: Account | null
}
export interface Listing {
  id: number
  apartmentId: number
  apartmentName: string
  city: string
  address: string
  roomNumber: string
  title: string
  monthlyRent: number
  area: number
  bedrooms: number
  orientation: string
  imageUrl: string
  tags: string[]
  published: boolean
  available: boolean
  description?: string
}
export interface Apartment {
  id: number
  name: string
  city: string
  monthlyRent: number
  address: string
  description: string
  imageUrl: string
  roomCount: number
}
export interface Slot {
  id: number
  roomId: number
  startTime: string
  endTime: string
  remainingCapacity: number
  capacity?: number
  reservedCount?: number
}
export interface Appointment {
  id: number
  roomId: number
  roomNumber: string
  apartmentName: string
  contactName: string
  contactPhone: string
  appointmentTime: string
  status: string
  createdAt: string
}
export interface ReviewLog {
  id: number
  appointmentId: number
  adminId: number
  fromStatus: string
  toStatus: string
  createdAt: string
}
export interface Lease {
  id: number
  roomId: number
  roomNumber: string
  apartmentName: string
  userId: number
  username: string
  startDate: string
  endDate: string
  monthlyRent: number
  deposit: number
  status: string
  createdAt: string
}
export interface Bill {
  id: number
  leaseId: number
  userId: number
  roomNumber: string
  apartmentName: string
  period: string
  dueDate: string
  amount: number
  status: string
  paidAt?: string
  createdAt: string
}
export interface Notice {
  id: number
  title: string
  content: string
  read: boolean
  createdAt: string
}
export interface Dashboard {
  apartmentCount: number
  roomCount: number
  publishedRoomCount: number
  pendingAppointmentCount: number
  activeLeaseCount: number
  unpaidBillCount: number
  monthlyRevenue: number
  cityStats: { city: string; roomCount: number }[]
  recentAppointments: Appointment[]
}
