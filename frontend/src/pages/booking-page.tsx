import { Link } from '@tanstack/react-router'
import { useEffect, useRef } from 'react'
import { BookingApiError } from '@/domains/booking/booking-api'
import { CheckoutForm } from '@/domains/booking/checkout/checkout-form'
import { seatPosition } from '@/domains/booking/seat-selection/seat-position'
import { SeatSelection } from '@/domains/booking/seat-selection/seat-selection'
import { useBookingManager } from '@/domains/booking/use-booking-manager'
import { formatShowingTime, useShowings } from '@/domains/programme/list-showings/showings'

const stageTitles = {
  selection: 'Wähle deine Sitzplätze',
  checking: 'Wir prüfen deine Reservierung',
  checkout: 'Schließe deine Buchung ab',
  confirmed: 'Deine Buchung ist bestätigt',
}

function formatCountdown(seconds: number) {
  return String(Math.floor(seconds / 60)).padStart(2, '0') + ':' + String(seconds % 60).padStart(2, '0')
}

function SeatSummary({ seatIds }: { seatIds: number[] }) {
  const seatsByRow = new Map<number, number[]>()
  for (const seatId of [...seatIds].sort((a, b) => a - b)) {
    const { row, number } = seatPosition(seatId)
    const seats = seatsByRow.get(row) ?? []
    seats.push(number)
    seatsByRow.set(row, seats)
  }

  return (
    <ul className="space-y-1 text-lg font-semibold">
      {[...seatsByRow].map(([row, seats]) => (
        <li key={row}>Reihe {row} · {seats.length === 1 ? 'Platz' : 'Plätze'} {seats.join(', ')}</li>
      ))}
    </ul>
  )
}

export function BookingPage({ showingId }: { showingId: number }) {
  const showings = useShowings()
  const showing = showings.data?.find((entry) => entry.id === showingId)
  const manager = useBookingManager(showingId)
  const { progress } = manager
  const heading = useRef<HTMLHeadingElement>(null)
  const missing = manager.seats.error instanceof BookingApiError && manager.seats.error.status === 404
  const checkoutDisabled = manager.isReleasing || manager.isVerifyingCheckout

  useEffect(() => {
    heading.current?.focus()
  }, [progress.phase])

  const holdVerification = (
    <div className="max-w-2xl rounded-2xl border bg-card p-5 sm:p-8">
      {manager.holdCheckFailed && !manager.isCheckingHold ? (
        <div className="space-y-3">
          <p role="alert">Deine Reservierung konnte gerade nicht geprüft werden.</p>
          <button
            type="button"
            onClick={() => { void manager.retryHoldCheck() }}
            className="min-h-11 rounded-lg border border-primary px-3 font-semibold text-primary hover:bg-secondary focus-visible:outline-2 focus-visible:outline-ring"
          >
            Erneut prüfen
          </button>
        </div>
      ) : <p role="status">Reservierungsstatus wird geladen …</p>}
    </div>
  )

  return (
    <main id="main-content" className="mx-auto max-w-6xl space-y-6 px-4 py-8 sm:px-6 sm:py-12 lg:px-8">
      <Link
        to="/"
        className="inline-flex items-center gap-2 rounded-sm font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
      >
        <span aria-hidden="true">←</span> Zum Programm
      </Link>
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.2em] text-primary uppercase">
          {progress.phase === 'selection' ? 'Schritt 2 von 3 · Sitzplätze' : 'Schritt 3 von 3 · Buchung'}
        </p>
        <h1 ref={heading} tabIndex={-1} className="font-serif text-3xl font-semibold tracking-tight focus:outline-none sm:text-5xl">
          {stageTitles[progress.phase]}
        </h1>
        <p className="text-sm font-medium text-muted-foreground sm:text-base">
          {showing ? showing.movieTitle + ' · ' + formatShowingTime(showing.startsAt) : 'Vorstellung'}
        </p>
      </header>
      {progress.phase === 'selection' && (
        <SeatSelection
          seats={manager.seats.data ?? []}
          selectedSeatIds={manager.selectedSeatIds}
          unavailableSeatIds={manager.unavailableSeatIds}
          loading={manager.seats.isPending}
          failed={manager.seats.isError}
          missing={missing}
          busy={manager.isReserving}
          notice={manager.notice}
          onRetry={() => { void manager.seats.refetch() }}
          onSelect={manager.toggleSeat}
          onRemoveUnavailable={manager.removeUnavailableSeats}
          onReserve={() => { void manager.reserveSelection() }}
        />
      )}
      {progress.phase === 'checking' && holdVerification}
      {progress.phase === 'checkout' && (
        <div className="mx-auto max-w-2xl space-y-4">
          {manager.notice && <p role="status" className="rounded-xl border bg-card px-4 py-3">{manager.notice}</p>}
          <aside aria-label="Deine Reservierung" className="space-y-4 rounded-2xl border bg-accent px-5 py-4 text-accent-foreground">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="space-y-1">
                <p className="text-sm font-medium">{progress.retryCustomer ? 'Bestätigung ausstehend' : 'Deine reservierten Sitzplätze'}</p>
                <SeatSummary seatIds={progress.saved.hold.seatIds} />
              </div>
              {!progress.retryCustomer && manager.remainingSeconds !== undefined && (
                <div className="text-right">
                  <p className="text-xs font-medium">Verbleibende Zeit</p>
                  <p role="timer" aria-label="Verbleibende Reservierungszeit" className="text-2xl font-semibold tabular-nums">
                    {formatCountdown(manager.remainingSeconds)}
                  </p>
                </div>
              )}
            </div>
            <button
              type="button"
              disabled={manager.isConfirming || checkoutDisabled}
              onClick={() => { void manager.releaseReservation() }}
              className="min-h-11 rounded-lg border border-primary px-3 text-sm font-semibold text-primary enabled:cursor-pointer enabled:hover:bg-card focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:opacity-50"
            >
              {manager.isReleasing ? 'Reservierung wird freigegeben …' : 'Reservierung freigeben'}
            </button>
          </aside>
          {manager.isVerifyingCheckout && holdVerification}
          <fieldset disabled={checkoutDisabled} className="min-w-0">
            <CheckoutForm
              key={progress.saved.hold.id}
              onConfirm={manager.submitBooking}
              error={manager.confirmationError}
              isConfirming={manager.isConfirming}
              retrying={Boolean(progress.retryCustomer)}
            />
          </fieldset>
        </div>
      )}
      {progress.phase === 'confirmed' && (
        <section aria-label="Buchungsbestätigung" className="max-w-2xl space-y-5 rounded-2xl border bg-card p-5 sm:p-8">
          <SeatSummary seatIds={progress.seatIds} />
          <dl className="rounded-xl bg-secondary p-4">
            <dt className="text-sm font-semibold text-secondary-foreground">Buchungsnummer</dt>
            <dd className="mt-1 font-mono text-sm break-all text-secondary-foreground">{progress.bookingId}</dd>
          </dl>
          <Link
            to="/"
            className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 font-semibold text-primary-foreground hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
          >
            Weitere Vorstellung wählen
          </Link>
        </section>
      )}
    </main>
  )
}
