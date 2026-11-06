import { revalidateLogic, useForm } from '@tanstack/react-form'
import { useId, useRef } from 'react'
import { z } from 'zod'

export type BookingCustomer = {
  name: string
  birthDate: string
  email: string
  phone?: string
  termsAccepted: true
  privacyAccepted: true
  newsletterOptIn: boolean
}

type CheckoutFormProps = {
  onConfirm: (customer: BookingCustomer) => Promise<void>
  error?: string
  isConfirming: boolean
  retrying: boolean
}

const berlinDateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Berlin',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

function todayInBerlin(): string {
  const parts = Object.fromEntries(
    berlinDateFormatter.formatToParts().map(({ type, value }) => [type, value]),
  )
  return `${parts.year}-${parts.month}-${parts.day}`
}

const checkoutSchema = z.object({
  name: z.string().trim()
    .min(1, 'Bitte gib deinen Namen ein.')
    .max(200, 'Dein Name darf höchstens 200 Zeichen lang sein.'),
  birthDate: z.iso.date({ error: 'Bitte gib ein gültiges Geburtsdatum ein.' })
    .refine((date) => date <= todayInBerlin(), 'Das Geburtsdatum darf nicht in der Zukunft liegen.'),
  email: z.string().trim()
    .max(254, 'Die E-Mail-Adresse darf höchstens 254 Zeichen lang sein.')
    .pipe(z.email({ error: 'Bitte gib eine gültige E-Mail-Adresse ein.' })),
  phone: z.string().trim()
    .max(50, 'Die Telefonnummer darf höchstens 50 Zeichen lang sein.')
    .transform((phone) => phone || undefined),
  termsAccepted: z.boolean().pipe(z.literal(true, { error: 'Bitte akzeptiere die AGB.' })),
  privacyAccepted: z.boolean().pipe(z.literal(true, { error: 'Bitte akzeptiere den Datenschutz.' })),
  newsletterOptIn: z.boolean(),
})

const defaultValues: z.input<typeof checkoutSchema> = {
  name: '',
  birthDate: '',
  email: '',
  phone: '',
  termsAccepted: false,
  privacyAccepted: false,
  newsletterOptIn: false,
}

const contactFields = [
  { name: 'name', label: 'Name', type: 'text', autoComplete: 'name', required: true, maxLength: 200 },
  { name: 'birthDate', label: 'Geburtsdatum', type: 'date', autoComplete: 'bday', required: true, maxLength: undefined },
  { name: 'email', label: 'E-Mail-Adresse', type: 'email', autoComplete: 'email', required: true, maxLength: 254 },
  { name: 'phone', label: 'Telefonnummer (optional)', type: 'tel', autoComplete: 'tel', required: false, maxLength: 50 },
] as const

const consentFields = [
  { name: 'termsAccepted', label: 'AGB akzeptieren', required: true },
  { name: 'privacyAccepted', label: 'Datenschutz akzeptieren', required: true },
  { name: 'newsletterOptIn', label: 'Newsletter abonnieren (optional)', required: false },
] as const

export function CheckoutForm({ onConfirm, error, isConfirming, retrying }: CheckoutFormProps) {
  const formId = useId()
  const formElement = useRef<HTMLFormElement>(null)
  const form = useForm({
    defaultValues,
    validationLogic: revalidateLogic({ mode: 'submit', modeAfterSubmission: 'change' }),
    validators: { onDynamic: checkoutSchema },
    onSubmit: async ({ value }) => {
      await onConfirm(checkoutSchema.parse(value))
    },
    onSubmitInvalid: () => {
      requestAnimationFrame(() => {
        formElement.current?.querySelector<HTMLInputElement>('input[aria-invalid="true"]')?.focus()
      })
    },
  })

  return (
    <form
      ref={formElement}
      aria-labelledby={`${formId}-heading`}
      className="rounded-2xl border bg-card p-5 text-card-foreground sm:p-8"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        if (!isConfirming && !form.state.isSubmitting) void form.handleSubmit()
      }}
    >
      <h2 id={`${formId}-heading`} className="font-serif text-2xl font-semibold tracking-tight">
        Deine Angaben
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Gib deine Kontaktdaten an, um die Buchung abzuschließen.
      </p>

      <form.Subscribe selector={(state) => state.isSubmitting}>
        {(isSubmitting) => {
          const pending = isSubmitting || isConfirming
          return (
            <>
              <fieldset className="mt-6 space-y-6" disabled={pending || retrying}>
                <legend className="sr-only">Kontaktdaten und Einwilligungen</legend>
                <div className="grid gap-4 sm:grid-cols-2">
                  {contactFields.map(({ name, label, type, autoComplete, required, maxLength }) => (
                    <form.Field key={name} name={name}>
                      {(field) => {
                        const id = `${formId}-${name}`
                        const fieldError = field.state.meta.errors[0]?.message
                        return (
                          <div className="min-w-0">
                            <label className="mb-1.5 block text-sm font-medium" htmlFor={id}>{label}</label>
                            <input
                              id={id}
                              name={name}
                              type={type}
                              autoComplete={autoComplete}
                              required={required}
                              maxLength={maxLength}
                              max={type === 'date' ? todayInBerlin() : undefined}
                              value={field.state.value}
                              onBlur={field.handleBlur}
                              onInput={(event) => field.handleChange(event.currentTarget.value)}
                              aria-invalid={fieldError ? true : undefined}
                              aria-describedby={fieldError ? `${id}-error` : undefined}
                              className="min-h-11 w-full min-w-0 rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring aria-invalid:border-destructive disabled:bg-muted"
                            />
                            {fieldError && <p id={`${id}-error`} className="mt-1.5 text-sm text-destructive">{fieldError}</p>}
                          </div>
                        )
                      }}
                    </form.Field>
                  ))}
                </div>

                <div className="space-y-3 border-t pt-5">
                  <p className="text-sm font-semibold">Einwilligungen</p>
                  {consentFields.map(({ name, label, required }) => (
                    <form.Field key={name} name={name}>
                      {(field) => {
                        const id = `${formId}-${name}`
                        const fieldError = field.state.meta.errors[0]?.message
                        return (
                          <div>
                            <label className="flex min-h-8 items-center gap-3 text-sm" htmlFor={id}>
                              <input
                                id={id}
                                name={name}
                                type="checkbox"
                                required={required}
                                checked={field.state.value}
                                onBlur={field.handleBlur}
                                onChange={(event) => field.handleChange(event.currentTarget.checked)}
                                aria-invalid={fieldError ? true : undefined}
                                aria-describedby={fieldError ? `${id}-error` : undefined}
                                className="size-5 shrink-0 rounded accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                              />
                              {label}
                            </label>
                            {fieldError && <p id={`${id}-error`} className="ml-8 text-sm text-destructive">{fieldError}</p>}
                          </div>
                        )
                      }}
                    </form.Field>
                  ))}
                </div>
              </fieldset>

              {error && (
                <p role="alert" className="mt-5 rounded-lg border border-destructive/25 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                  {error}
                </p>
              )}
              {retrying && (
                <p className="mt-4 text-sm text-muted-foreground">
                  Für die erneute Prüfung bleiben deine Angaben unverändert.
                </p>
              )}
              <button
                type="submit"
                disabled={pending}
                className="mt-6 min-h-11 w-full rounded-lg bg-primary px-5 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-wait disabled:opacity-70 sm:w-auto"
              >
                {pending ? 'Buchung wird geprüft …' : retrying ? 'Bestätigung erneut prüfen' : 'Verbindlich buchen'}
              </button>
            </>
          )
        }}
      </form.Subscribe>
    </form>
  )
}
