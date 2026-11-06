import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useBlocker } from '@tanstack/react-router'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import {
  BookingApiError,
  confirmBooking,
  loadHoldStatus,
  loadSeats,
  releaseHold,
  reserveSeats,
  type CinemaSeat,
} from './booking-api'
import type { BookingCustomer } from './checkout/checkout-form'
import { readSavedHold, saveHold, type SavedHold } from './hold-session'

type BookingProgress =
  | { phase: 'selection' }
  | { phase: 'checking'; saved: SavedHold; attempt: number; retryCustomer?: BookingCustomer }
  | { phase: 'checkout'; saved: SavedHold; deadline?: number; attempt?: number; retryCustomer?: BookingCustomer }
  | { phase: 'confirmed'; bookingId: string; seatIds: number[] }

type BookingAction = 'reserve' | 'confirm' | 'release'

const staleNotice = 'Die Reservierung ist nicht mehr verfügbar. Bitte wähle deine Plätze erneut.'
const uncertainNotice = 'Der Buchungsstatus ist unklar. Prüfe die Bestätigung erneut, bevor du andere Plätze wählst.'

function responseStatus(error: unknown) {
  return error instanceof BookingApiError ? error.status : undefined
}

function seatMapKey(showingId: number) {
  return ['showings', showingId, 'seats'] as const
}

export function useBookingManager(showingId: number) {
  const queryClient = useQueryClient()
  const [progress, setProgress] = useState<BookingProgress>(() => {
    const saved = readSavedHold(showingId)
    return saved ? { phase: 'checking', saved, attempt: 0 } : { phase: 'selection' }
  })
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([])
  const [notice, setNotice] = useState<string>()
  const [confirmationError, setConfirmationError] = useState<string>()
  const [now, setNow] = useState(() => performance.now())
  const actionInFlight = useRef<BookingAction | null>(null)
  const confirmationPending = useRef(progress.phase === 'checking' && progress.saved.confirmationPending)
  const statusScope = useId()
  const statusAttempt = useRef(0)
  const checkout = progress.phase === 'checkout' ? progress : undefined
  const checked = progress.phase === 'checking' ? progress : checkout?.attempt !== undefined ? checkout : undefined
  const deadline = checkout?.deadline

  const warnBeforeUnload = useCallback(() => actionInFlight.current !== null || confirmationPending.current, [])
  const blockNavigation = useCallback(() => {
    if (actionInFlight.current === 'confirm') {
      setConfirmationError('Die Bestätigung läuft. Bitte warte auf das Ergebnis.')
      return true
    }
    if (actionInFlight.current !== null) return true
    if (confirmationPending.current) {
      return !window.confirm(
        'Der Buchungsstatus ist unklar. Bleibe hier, um die Buchung zu prüfen. Möchtest du die Seite trotzdem verlassen?',
      )
    }
    return false
  }, [])

  useBlocker({ shouldBlockFn: blockNavigation, enableBeforeUnload: warnBeforeUnload })

  const seats = useQuery({
    queryKey: seatMapKey(showingId),
    queryFn: ({ signal }) => loadSeats(showingId, signal),
    enabled: progress.phase === 'selection',
    refetchInterval: 4000,
    retry: (failureCount, error) => responseStatus(error) !== 404 && failureCount < 2,
  })

  const holdStatus = useQuery({
    queryKey: ['showings', showingId, 'hold-status', statusScope, checked?.attempt],
    queryFn: checked ? () => loadHoldStatus(showingId, checked.saved.hold.id) : skipToken,
    networkMode: 'always',
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    gcTime: 0,
  })

  const reserve = useMutation({
    mutationFn: (seatIds: number[]) => reserveSeats(showingId, seatIds),
    networkMode: 'always',
    retry: false,
    gcTime: 0,
    onError: (error) => {
      if (responseStatus(error) === 409) return queryClient.invalidateQueries({ queryKey: seatMapKey(showingId) })
    },
  })

  const release = useMutation({
    mutationFn: (holdId: string) => releaseHold(showingId, holdId),
    networkMode: 'always',
    retry: false,
    gcTime: 0,
  })

  const confirm = useMutation({
    mutationFn: ({ holdId, customer }: { holdId: string; customer: BookingCustomer }) =>
      confirmBooking(showingId, holdId, customer),
    networkMode: 'always',
    retry: false,
    gcTime: 0,
  })

  const refreshSeats = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: seatMapKey(showingId) })
  }, [queryClient, showingId])

  const returnToSelection = useCallback((message: string) => {
    saveHold(showingId)
    confirmationPending.current = false
    setNotice(message)
    setConfirmationError(undefined)
    setProgress({ phase: 'selection' })
    refreshSeats()
  }, [refreshSeats, showingId])

  const completeBooking = useCallback((bookingId: string, seatIds: number[]) => {
    saveHold(showingId)
    confirmationPending.current = false
    setProgress({ phase: 'confirmed', bookingId, seatIds })
    refreshSeats()
  }, [refreshSeats, showingId])

  const openCheckout = useCallback((saved: SavedHold, deadline?: number, retryCustomer?: BookingCustomer) => {
    saveHold(showingId, saved)
    confirmationPending.current = saved.confirmationPending
    setNow(performance.now())
    setProgress({ phase: 'checkout', saved, deadline, retryCustomer })
  }, [showingId])

  const checkHold = useCallback((saved: SavedHold, retryCustomer?: BookingCustomer) => {
    setNotice(undefined)
    setConfirmationError(undefined)
    setProgress({ phase: 'checking', saved, attempt: ++statusAttempt.current, retryCustomer })
  }, [])

  useEffect(() => {
    if (!checked) return

    if (holdStatus.data) {
      const { hold: verifiedHold, deadline, bookingId } = holdStatus.data
      if (bookingId) {
        completeBooking(bookingId, verifiedHold.seatIds)
      } else {
        const pending = checked.saved.confirmationPending
        if (checked.retryCustomer) setConfirmationError(uncertainNotice)
        else if (pending && checked.phase === 'checking') setNotice('Deine Reservierung ist noch aktiv. Bitte gib deine Buchungsdaten erneut ein.')
        openCheckout({ hold: verifiedHold, confirmationPending: pending }, deadline, checked.retryCustomer)
      }
      return
    }

    const status = responseStatus(holdStatus.error)
    if (status === 404 || status === 409) returnToSelection(staleNotice)
  }, [checked, completeBooking, holdStatus.data, holdStatus.error, openCheckout, returnToSelection])

  useEffect(() => {
    if (deadline === undefined) return
    const interval = window.setInterval(() => setNow(performance.now()), 250)
    return () => window.clearInterval(interval)
  }, [deadline])

  useEffect(() => {
    if (
      !checkout ||
      checkout.attempt !== undefined ||
      checkout.retryCustomer ||
      actionInFlight.current ||
      deadline === undefined ||
      now < deadline
    ) return

    setProgress({ ...checkout, deadline: undefined, attempt: ++statusAttempt.current })
  }, [checkout, deadline, now])

  const availableSeatIds = new Set(seats.data?.filter(({ status }) => status === 'AVAILABLE').map(({ id }) => id))
  const unavailableSeatIds = selectedSeatIds.filter((id) => !availableSeatIds.has(id))

  function toggleSeat(seat: CinemaSeat) {
    if (progress.phase !== 'selection' || actionInFlight.current) return

    setSelectedSeatIds((ids) => {
      if (ids.includes(seat.id)) return ids.filter((id) => id !== seat.id)
      return seat.status === 'AVAILABLE' ? [...ids, seat.id] : ids
    })
  }

  function removeUnavailableSeats() {
    if (progress.phase !== 'selection' || actionInFlight.current) return

    setSelectedSeatIds((ids) => ids.filter((id) => availableSeatIds.has(id)))
    setNotice(undefined)
  }

  async function reserveSelection() {
    if (progress.phase !== 'selection' || !selectedSeatIds.length || unavailableSeatIds.length || actionInFlight.current || seats.isError) return

    actionInFlight.current = 'reserve'
    setNotice(undefined)
    try {
      const { hold, deadline } = await reserve.mutateAsync([...selectedSeatIds].sort((a, b) => a - b))
      setSelectedSeatIds([])
      openCheckout({ hold, confirmationPending: false }, deadline)
      refreshSeats()
    } catch (error) {
      const conflict = responseStatus(error) === 409
      setNotice(conflict
        ? 'Mindestens ein Platz ist inzwischen vergeben. Prüfe deine Auswahl und reserviere erneut.'
        : 'Die Plätze konnten nicht reserviert werden. Prüfe die Verfügbarkeit und versuche es erneut.')
      if (!conflict) refreshSeats()
    } finally {
      actionInFlight.current = null
      reserve.reset()
    }
  }

  async function releaseReservation() {
    if (!checkout || checkout.attempt !== undefined || actionInFlight.current) return

    actionInFlight.current = 'release'
    setNotice(undefined)
    try {
      await release.mutateAsync(checkout.saved.hold.id)
      returnToSelection('Deine Reservierung wurde freigegeben.')
    } catch (error) {
      const status = responseStatus(error)
      if (status === 404 || status === 409) checkHold(checkout.saved, checkout.retryCustomer)
      else setNotice('Die Reservierung konnte nicht freigegeben werden. Bitte versuche es erneut.')
    } finally {
      actionInFlight.current = null
      release.reset()
    }
  }

  async function submitBooking(customer: BookingCustomer) {
    if (!checkout || checkout.attempt !== undefined || actionInFlight.current) return

    const { saved, retryCustomer } = checkout
    const submittedCustomer = retryCustomer ?? { ...customer }
    actionInFlight.current = 'confirm'
    confirmationPending.current = true
    saveHold(showingId, { ...saved, confirmationPending: true })
    setNotice(undefined)
    setConfirmationError(undefined)
    try {
      const bookingId = await confirm.mutateAsync({ holdId: saved.hold.id, customer: submittedCustomer })
      completeBooking(bookingId, saved.hold.seatIds)
    } catch (error) {
      const status = responseStatus(error)
      if (status === 409 || status === 404) {
        returnToSelection(staleNotice)
      } else if (saved.confirmationPending || status === undefined || status === 408 || status >= 500) {
        openCheckout({ ...saved, confirmationPending: true }, deadline, submittedCustomer)
        setConfirmationError(uncertainNotice)
      } else {
        openCheckout({ ...saved, confirmationPending: false }, deadline)
        setConfirmationError('Die Buchung wurde nicht bestätigt. Bitte prüfe deine Angaben und versuche es erneut.')
      }
    } finally {
      actionInFlight.current = null
      confirm.reset()
    }
  }

  return {
    progress,
    seats,
    notice,
    confirmationError,
    selectedSeatIds,
    unavailableSeatIds,
    toggleSeat,
    removeUnavailableSeats,
    reserveSelection,
    releaseReservation,
    submitBooking,
    isReserving: reserve.isPending,
    isReleasing: release.isPending,
    isConfirming: confirm.isPending,
    isVerifyingCheckout: checkout?.attempt !== undefined,
    isCheckingHold: holdStatus.isFetching,
    holdCheckFailed: holdStatus.isError,
    retryHoldCheck: holdStatus.refetch,
    remainingSeconds: deadline === undefined ? undefined : Math.max(0, Math.ceil((deadline - now) / 1000)),
  }
}
