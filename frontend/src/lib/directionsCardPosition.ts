interface DirectionsCardPositionInput {
  selectedTop: number
  workspaceTop: number
  workspaceHeight: number
  cardHeight: number
  gap: number
}

export function calculateDirectionsCardTop({
  selectedTop,
  workspaceTop,
  workspaceHeight,
  cardHeight,
  gap,
}: DirectionsCardPositionInput): number {
  const rawTop = selectedTop - workspaceTop
  const maximumTop = Math.max(gap, workspaceHeight - cardHeight - gap)

  return Math.max(gap, Math.min(rawTop, maximumTop))
}
