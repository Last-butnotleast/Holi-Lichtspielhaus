import { apiClient } from '@/shared/api/client'
import type { BookingCustomer } from './checkout/checkout-form'
import { seatPosition, type SeatPosition } from './seat-selection/seat-position'

const BOOKING_REQUEST_TIMEOUT = 10_000

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED'

export type CinemaSeat = SeatPosition & {
  id: number
  status: SeatStatus
}

export type SeatHold = {
  id: string
  expiresAt: number
  seatIds: number[]
}

export class BookingApiError extends Error {
  constructor(readonly status: number) {
    super('Die Anfrage konnte nicht abgeschlossen werden.')
  }
}

function responseData<T>(response: Response, data: T | undefined): T {
  if (!response.ok) throw new BookingApiError(response.status)
  if (data === undefined) throw new Error('Die Antwort war unvollständig.')
  return data
}

function toSeatHold(data: { holdId: string; expiresAt: string; seatIds: number[] }): SeatHold {
  const expiresAt = Date.parse(data.expiresAt)
  if (!Number.isFinite(expiresAt)) throw new Error('Das Ende der Reservierung ist ungültig.')

  return { id: data.holdId, expiresAt, seatIds: data.seatIds }
}

function displayDeadline(response: Response, expiresAt: number) {
  const serverTime = Date.parse(response.headers.get('Date') ?? '')
  const remaining = expiresAt - serverTime
  return remaining > 0 ? performance.now() + remaining : undefined
}

export async function loadSeats(showingId: number, signal: AbortSignal): Promise<CinemaSeat[]> {
  const result = await apiClient.GET('/showings/{showingId}/seats', {
    params: { path: { showingId } },
    signal: AbortSignal.any([signal, AbortSignal.timeout(BOOKING_REQUEST_TIMEOUT)]),
  })

  return responseData(result.response, result.data).seats.map(({ seatId, status }) => ({
    ...seatPosition(seatId),
    id: seatId,
    status,
  }))
}

export async function reserveSeats(showingId: number, seatIds: number[]) {
  const result = await apiClient.POST('/showings/{showingId}/holds', {
    params: { path: { showingId } },
    body: { seatIds },
    signal: AbortSignal.timeout(BOOKING_REQUEST_TIMEOUT),
  })

  const hold = toSeatHold(responseData(result.response, result.data))
  return { hold, deadline: displayDeadline(result.response, hold.expiresAt) }
}

export async function loadHoldStatus(showingId: number, holdId: string) {
  const result = await apiClient.POST('/showings/{showingId}/holds/status', {
    params: { path: { showingId } },
    body: { holdId },
    signal: AbortSignal.timeout(BOOKING_REQUEST_TIMEOUT),
  })
  const data = responseData(result.response, result.data)
  const hold = toSeatHold(data)

  return { hold, deadline: displayDeadline(result.response, hold.expiresAt), bookingId: data.bookingId }
}

export async function releaseHold(showingId: number, holdId: string): Promise<void> {
  const { response } = await apiClient.POST('/showings/{showingId}/holds/release', {
    params: { path: { showingId } },
    body: { holdId },
    signal: AbortSignal.timeout(BOOKING_REQUEST_TIMEOUT),
  })

  if (!response.ok) throw new BookingApiError(response.status)
}

export async function confirmBooking(showingId: number, holdId: string, customer: BookingCustomer): Promise<string> {
  const result = await apiClient.POST('/showings/{showingId}/bookings', {
    params: { path: { showingId } },
    body: { holdId, ...customer },
    signal: AbortSignal.timeout(BOOKING_REQUEST_TIMEOUT),
  })

  return responseData(result.response, result.data).bookingId
}
