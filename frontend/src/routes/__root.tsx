import { createRootRoute, Link, Outlet } from '@tanstack/react-router'

export const Route = createRootRoute({
  component: CinemaShell,
  notFoundComponent: PageNotFound,
})

function CinemaShell() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex min-h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
          <Link
            to="/"
            className="inline-flex items-center gap-2.5 rounded-lg font-serif text-xl font-bold tracking-tight focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
          >
            <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary text-primary-foreground">
              <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" className="size-5" stroke="currentColor" strokeWidth="1.6">
                <rect x="3.5" y="5" width="17" height="14" rx="2" />
                <path d="M8 5v14M16 5v14M3.5 10H8m8 0h4.5M3.5 14H8m8 0h4.5" />
              </svg>
            </span>
            Holi Lichtspielhaus
          </Link>

          <nav aria-label="Hauptnavigation">
            <Link
              to="/"
              activeOptions={{ exact: true }}
              className="inline-flex min-h-11 items-center rounded-full px-3 text-sm font-semibold text-primary hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring sm:px-4"
              activeProps={{ className: 'bg-secondary' }}
            >
              Programm
            </Link>
          </nav>
        </div>
      </header>
      <Outlet />
    </div>
  )
}

function PageNotFound() {
  return (
    <main className="mx-auto max-w-6xl px-4 py-12 sm:px-6 lg:px-8">
      <h1 className="font-serif text-3xl font-bold">Seite nicht gefunden</h1>
      <Link
        to="/"
        className="mt-5 inline-flex min-h-11 items-center rounded-xl bg-primary px-4 font-semibold text-primary-foreground hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
      >
        Zum Programm
      </Link>
    </main>
  )
}
