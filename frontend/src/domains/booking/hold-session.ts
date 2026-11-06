import { z } from 'zod'
import type { SeatHold } from './booking-api'

const savedHold = z.strictObject({
  hold: z.strictObject({
    id: z.string().min(1),
    expiresAt: z.number().int().positive(),
    seatIds: z.array(z.number().int().min(1).max(200)).min(1).max(200),
  }),
  confirmationPending: z.boolean(),
})

export type SavedHold = { hold: SeatHold; confirmationPending: boolean }

const memoryFallback = new Map<number, SavedHold | null>()

function storageKey(showingId: number) {
  return `holi:showing:${showingId}:hold`
}

export function readSavedHold(showingId: number): SavedHold | undefined {
  if (memoryFallback.has(showingId)) return memoryFallback.get(showingId) ?? undefined

  try {
    const entry = window.sessionStorage.getItem(storageKey(showingId))
    if (entry === null) return undefined

    const parsed = savedHold.safeParse(JSON.parse(entry))
    if (parsed.success) return parsed.data
  } catch {
    saveHold(showingId)
    return undefined
  }

  saveHold(showingId)
  return undefined
}

export function saveHold(showingId: number, entry?: SavedHold) {
  try {
    if (entry) {
      const { id, expiresAt, seatIds } = entry.hold
      window.sessionStorage.setItem(storageKey(showingId), JSON.stringify({
        hold: { id, expiresAt, seatIds },
        confirmationPending: entry.confirmationPending,
      }))
    } else {
      window.sessionStorage.removeItem(storageKey(showingId))
    }
    memoryFallback.delete(showingId)
  } catch {
    memoryFallback.set(showingId, entry ?? null)
  }
}
