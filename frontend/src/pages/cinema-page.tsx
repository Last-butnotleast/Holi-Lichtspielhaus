import { ProgrammeManager } from '@/domains/programme/list-showings/programme-manager'

export function CinemaPage() {
  return (
    <main className="mx-auto max-w-6xl px-4 pb-16 pt-6 sm:px-6 sm:pt-9 lg:px-8">
      <section
        aria-labelledby="cinema-title"
        className="relative isolate overflow-hidden rounded-3xl bg-primary px-6 py-9 text-primary-foreground sm:px-10 sm:py-12"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 320 210"
          fill="none"
          className="pointer-events-none absolute -bottom-14 -right-12 -z-10 w-72 rotate-[-13deg] text-amber-100 opacity-20 sm:-bottom-12 sm:right-2 sm:w-96 sm:opacity-30"
        >
          <rect x="25" y="24" width="270" height="162" rx="19" stroke="currentColor" strokeWidth="2" />
          <rect x="54" y="53" width="212" height="104" rx="8" stroke="currentColor" strokeWidth="2" />
          <path d="M134 53v104m52-104v104" stroke="currentColor" strokeWidth="2" />
          <path d="M49 39h222M49 171h222" stroke="currentColor" strokeWidth="9" strokeDasharray="12 11" />
          <circle cx="94" cy="104" r="22" fill="currentColor" />
          <path d="M220 75l26 29-26 29-26-29 26-29Z" fill="currentColor" />
        </svg>

        <p className="text-xs font-bold uppercase tracking-[0.2em] text-amber-100">Film &amp; Spielzeit</p>
        <h1 id="cinema-title" className="mt-3 font-serif text-4xl font-bold tracking-tight sm:text-6xl">
          Kinoprogramm
        </h1>
        <p className="mt-3 max-w-md text-base leading-relaxed sm:text-lg">
          Wähle einen Film und die passende Spielzeit. Danach suchst du dir deinen Sitzplatz aus.
        </p>
      </section>

      <ProgrammeManager />
    </main>
  )
}
