import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'

export interface Showing {
  id: number
  movieTitle: string
  startsAt: string
}

const showingTime = new Intl.DateTimeFormat('de-DE', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Europe/Berlin',
})

export function formatShowingTime(startsAt: string): string {
  return showingTime.format(new Date(startsAt))
}

export function useShowings() {
  return useQuery({
    queryKey: ['programme', 'showings'],
    queryFn: async ({ signal }): Promise<Showing[]> => {
      const { data, response } = await apiClient.GET('/showings', { signal })

      if (!response.ok || data === undefined) {
        throw new Error('Das Kinoprogramm konnte nicht geladen werden.')
      }

      return data.map(({ showingId, movieTitle, startsAt }) => ({
        id: showingId,
        movieTitle,
        startsAt,
      }))
    },
    retry: 1,
  })
}
