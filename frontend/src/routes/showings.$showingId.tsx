import { createFileRoute, notFound } from '@tanstack/react-router'
import { BookingPage } from '@/pages/booking-page'

export const Route = createFileRoute('/showings/$showingId')({
  beforeLoad: ({ params }) => {
    const showingId = Number(params.showingId)
    if (!/^[1-9]\d*$/.test(params.showingId) || !Number.isSafeInteger(showingId)) throw notFound()
    return { showingId }
  },
  component: ShowingBookingRoute,
})

function ShowingBookingRoute() {
  const { showingId } = Route.useRouteContext()
  return <BookingPage key={showingId} showingId={showingId} />
}
