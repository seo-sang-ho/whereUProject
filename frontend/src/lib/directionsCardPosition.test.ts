import { calculateDirectionsCardTop } from './directionsCardPosition'

describe('calculateDirectionsCardTop', () => {
  it('선택 항목의 workspace 기준 위치에 맞춘다', () => {
    expect(calculateDirectionsCardTop({
      selectedTop: 240,
      workspaceTop: 64,
      workspaceHeight: 700,
      cardHeight: 300,
      gap: 20,
    })).toBe(176)
  })

  it('상단 간격보다 위로 올라가지 않는다', () => {
    expect(calculateDirectionsCardTop({
      selectedTop: 70,
      workspaceTop: 64,
      workspaceHeight: 700,
      cardHeight: 300,
      gap: 20,
    })).toBe(20)
  })

  it('workspace 하단을 넘으면 아래쪽 간격에 맞춰 제한한다', () => {
    expect(calculateDirectionsCardTop({
      selectedTop: 600,
      workspaceTop: 64,
      workspaceHeight: 700,
      cardHeight: 300,
      gap: 20,
    })).toBe(380)
  })

  it('카드가 workspace보다 높아도 상단 간격을 반환한다', () => {
    expect(calculateDirectionsCardTop({
      selectedTop: 240,
      workspaceTop: 64,
      workspaceHeight: 300,
      cardHeight: 400,
      gap: 20,
    })).toBe(20)
  })
})
