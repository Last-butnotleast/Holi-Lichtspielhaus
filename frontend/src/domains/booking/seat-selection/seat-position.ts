const SEATS_PER_ROW = 20

export type SeatPosition = {
  row: number
  number: number
}

export function seatPosition(seatId: number): SeatPosition {
  const index = seatId - 1

  return {
    row: Math.floor(index / SEATS_PER_ROW) + 1,
    number: index % SEATS_PER_ROW + 1,
  }
}
