import { Link } from '@tanstack/react-router'
import { formatShowingTime, type Showing } from './showings'

export function ProgrammeDisplay({ showings }: { showings: Showing[] }) {
  if (showings.length === 0) {
    return (
      <p role="status" className="rounded-2xl border border-border bg-card p-6 text-muted-foreground">
        Zurzeit sind keine Spielzeiten im Programm.
      </p>
    )
  }

  const films = new Map<string, Showing[]>()

  for (const showing of showings) {
    const times = films.get(showing.movieTitle) ?? []
    times.push(showing)
    films.set(showing.movieTitle, times)
  }

  return (
    <ul className="grid gap-4 sm:grid-cols-2 sm:gap-5">
      {Array.from(films, ([movieTitle, times]) => (
        <li key={movieTitle}>
          <article className="h-full overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            <div className="border-b border-border bg-secondary/45 px-5 py-4 sm:px-6">
              <p className="text-xs font-bold uppercase tracking-[0.18em] text-primary">Im Programm</p>
              <h3 className="mt-1 break-words font-serif text-2xl font-bold tracking-tight">{movieTitle}</h3>
            </div>
            <ul className="space-y-2 p-4 sm:p-5">
              {times.map(({ id, startsAt }) => {
                const label = formatShowingTime(startsAt)
                return (
                  <li key={id}>
                    <Link
                      to="/showings/$showingId"
                      params={{ showingId: String(id) }}
                      aria-label={movieTitle + ', ' + label + ' Uhr, Sitzplatz wählen'}
                      className="group flex min-h-12 items-center justify-between gap-3 rounded-xl border border-border bg-background px-3 py-2.5 text-sm font-semibold transition-colors hover:border-primary/60 hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                    >
                      <time dateTime={startsAt}>{label} Uhr</time>
                      <span className="flex shrink-0 items-center gap-1.5 text-primary">
                        <span className="hidden lg:inline">Sitzplatz wählen</span>
                        <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" className="size-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M4 10h11m-4-4 4 4-4 4" />
                        </svg>
                      </span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          </article>
        </li>
      ))}
    </ul>
  )
}
