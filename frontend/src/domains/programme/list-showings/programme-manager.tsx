import { ProgrammeDisplay } from './programme-display'
import { useShowings } from './showings'

export function ProgrammeManager() {
  const programme = useShowings()

  return (
    <section aria-labelledby="programme-title" className="mt-8 sm:mt-10">
      <h2 id="programme-title" className="font-serif text-2xl font-bold tracking-tight sm:text-3xl">
        Filme und Spielzeiten
      </h2>

      <div className="mt-4 sm:mt-5">
        {programme.isPending ? (
          <p role="status" className="rounded-2xl border border-border bg-card p-6 text-muted-foreground">
            Das Kinoprogramm wird geladen…
          </p>
        ) : programme.isError && programme.data === undefined ? (
          <div className="rounded-2xl border border-border bg-card p-6">
            <p role="alert" className="font-semibold">Das Kinoprogramm konnte nicht geladen werden.</p>
            <button
              type="button"
              onClick={() => void programme.refetch()}
              disabled={programme.isFetching}
              className="mt-4 inline-flex min-h-11 items-center rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:opacity-60"
            >
              {programme.isFetching ? 'Wird geladen…' : 'Erneut versuchen'}
            </button>
          </div>
        ) : (
          <ProgrammeDisplay showings={programme.data ?? []} />
        )}
      </div>
    </section>
  )
}
