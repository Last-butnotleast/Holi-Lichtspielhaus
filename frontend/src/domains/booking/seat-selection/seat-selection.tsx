import type { CinemaSeat, SeatStatus } from '../booking-api'

const rows = Array.from({ length: 10 }, (_, index) => index + 1)
const statuses: SeatDisplayStatus[] = ['AVAILABLE', 'SELECTED', 'HELD', 'BOOKED']

type SeatDisplayStatus = SeatStatus | 'SELECTED'

const statusLabels: Record<SeatDisplayStatus, string> = {
  AVAILABLE: 'frei',
  SELECTED: 'ausgewählt',
  HELD: 'reserviert',
  BOOKED: 'gebucht',
}

const seatStyles: Record<SeatDisplayStatus, string> = {
  AVAILABLE: 'border-primary/40 bg-card text-primary',
  SELECTED: 'border-primary bg-primary text-primary-foreground',
  HELD: 'border-border bg-accent text-accent-foreground',
  BOOKED: 'border-border bg-secondary text-muted-foreground line-through',
}

type SeatSelectionProps = {
  seats: CinemaSeat[]
  selectedSeatIds: number[]
  unavailableSeatIds: number[]
  loading: boolean
  failed: boolean
  missing: boolean
  busy: boolean
  notice?: string
  onRetry: () => void
  onSelect: (seat: CinemaSeat) => void
  onRemoveUnavailable: () => void
  onReserve: () => void
}

export function SeatSelection({ seats, selectedSeatIds, unavailableSeatIds, loading, failed, missing, busy, notice, onRetry, onSelect, onRemoveUnavailable, onReserve }: SeatSelectionProps) {
  const availableCount = seats.filter(({ status }) => status === 'AVAILABLE').length
  const selectedCount = selectedSeatIds.length

  return (
    <section aria-label="Sitzplätze auswählen" className="space-y-4">
      {notice && <p role="alert" className="rounded-xl border border-border bg-accent px-4 py-3 text-accent-foreground">{notice}</p>}
      {!missing && unavailableSeatIds.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-accent px-4 py-3 text-accent-foreground">
          <p role="alert">Einige gewählte Plätze sind nicht mehr frei. Wähle sie ab, um fortzufahren.</p>
          <button
            type="button"
            onClick={onRemoveUnavailable}
            disabled={busy || failed}
            className="min-h-11 rounded-lg border border-primary px-3 font-semibold text-primary enabled:hover:bg-card focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-50"
          >
            Nicht verfügbare Plätze abwählen
          </button>
        </div>
      )}
      {loading && <p role="status" className="rounded-2xl border bg-card p-6">Sitzplan wird geladen …</p>}
      {missing && <p role="alert" className="rounded-2xl border bg-card p-6">Diese Vorstellung ist nicht mehr verfügbar.</p>}
      {failed && !missing && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-card px-4 py-3">
          <p>Die Verfügbarkeit konnte gerade nicht aktualisiert werden.</p>
          <button
            type="button"
            onClick={onRetry}
            className="rounded-lg border border-primary px-3 py-2 font-semibold text-primary hover:bg-secondary focus-visible:outline-2 focus-visible:outline-ring"
          >
            Erneut laden
          </button>
        </div>
      )}
      {!loading && !failed && seats.length === 0 && (
        <p role="status" className="rounded-2xl border bg-card p-6">Für diese Vorstellung sind keine Sitzplätze verfügbar.</p>
      )}
      {!missing && seats.length > 0 && (
        <div className="rounded-2xl border bg-card p-4 sm:p-6">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <p className="font-semibold">{availableCount} {availableCount === 1 ? 'freier Platz' : 'freie Plätze'}</p>
            <ul aria-label="Sitzplatzstatus" className="flex flex-wrap gap-3 text-xs text-muted-foreground sm:gap-4 sm:text-sm">
              {statuses.map((status) => (
                <li key={status} className="flex items-center gap-1.5">
                  <span aria-hidden="true" className={'size-4 rounded border ' + seatStyles[status]} />
                  {statusLabels[status]}
                </li>
              ))}
            </ul>
          </div>
          <p className="mb-3 text-sm text-muted-foreground lg:hidden">Wische oder scrolle seitlich, um alle Plätze zu sehen.</p>
          <div
            role="region"
            aria-label="Saalplan mit zehn Reihen"
            tabIndex={0}
            className="overflow-x-auto rounded-lg p-1 focus-visible:outline-2 focus-visible:outline-ring"
          >
            <div className="mx-auto min-w-[49rem] max-w-5xl space-y-2 pb-2">
              <div className="mb-6 text-center">
                <div className="mx-auto mb-2 h-1.5 w-3/5 rounded-full bg-primary/40" />
                <span className="text-xs font-semibold tracking-[0.25em] text-muted-foreground uppercase">Leinwand</span>
              </div>
              {rows.map((row) => (
                <div key={row} className="flex items-center gap-2">
                  <span aria-hidden="true" className="w-8 shrink-0 text-right text-xs font-semibold text-muted-foreground">{row}</span>
                  <div className="grid flex-1 grid-cols-[repeat(20,minmax(0,1fr))] gap-1.5">
                    {seats.filter((seat) => seat.row === row).map((seat) => {
                      const selected = selectedSeatIds.includes(seat.id)
                      const unavailable = seat.status !== 'AVAILABLE'
                      const style = selected && unavailable
                        ? 'border-primary border-dashed bg-accent text-primary line-through'
                        : seatStyles[selected ? 'SELECTED' : seat.status]
                      return (
                        <button
                          key={seat.id}
                          type="button"
                          aria-label={'Reihe ' + seat.row + ', Platz ' + seat.number + ', ' + statusLabels[seat.status]}
                          aria-pressed={selected || !unavailable ? selected : undefined}
                          disabled={busy || (unavailable && !selected)}
                          onClick={() => onSelect(seat)}
                          className={'aspect-square rounded-md border text-xs font-semibold tabular-nums transition enabled:cursor-pointer enabled:hover:bg-primary enabled:hover:text-primary-foreground focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring ' + style}
                        >
                          {seat.number}
                        </button>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
            <p role="status" className="text-sm font-medium text-muted-foreground">
              {busy ? 'Plätze werden reserviert …' : `${selectedCount} ${selectedCount === 1 ? 'Platz' : 'Plätze'} ausgewählt`}
            </p>
            <button
              type="button"
              onClick={onReserve}
              disabled={busy || failed || selectedCount === 0 || unavailableSeatIds.length > 0}
              className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 font-semibold text-primary-foreground enabled:cursor-pointer enabled:hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring disabled:opacity-50"
            >
              Reservieren
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
