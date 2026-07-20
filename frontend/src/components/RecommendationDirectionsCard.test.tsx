import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { GeolocationError } from '../lib/geolocation'
import type { DirectionsEstimate } from '../types/directions'
import type { RecommendedTourismContent } from '../types/tourismContent'
import { RecommendationDirectionsCard } from './RecommendationDirectionsCard'

const mocks = vi.hoisted(() => ({
  fetchDirectionsAvailability: vi.fn(),
  fetchDrivingEstimate: vi.fn(),
  requestCurrentPosition: vi.fn(),
}))

vi.mock('../api/directionsApi', () => ({
  fetchDirectionsAvailability: mocks.fetchDirectionsAvailability,
  fetchDrivingEstimate: mocks.fetchDrivingEstimate,
}))

vi.mock('../lib/geolocation', async (importOriginal) => ({
  ...await importOriginal<typeof import('../lib/geolocation')>(),
  requestCurrentPosition: mocks.requestCurrentPosition,
}))

const destination: RecommendedTourismContent = {
  contentId: '126508',
  contentTypeId: '12',
  title: '경복궁',
  address: '서울 종로구',
  firstImage: null,
  firstImage2: null,
  latitude: 37.579617,
  longitude: 126.977041,
  legalDongCode: '11110101',
  categoryCode: 'A02010100',
}

const successfulEstimate: DirectionsEstimate = {
  status: 'AVAILABLE',
  destinationContentId: destination.contentId,
  destinationName: destination.title,
  travelTimeMinutes: 75,
  distanceMeters: 84_200,
  tollFare: 3_200,
  calculatedAt: '2026-07-20T12:00:00+09:00',
  fallbackReason: null,
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    )
  }
}

function renderCard(
  props: Partial<React.ComponentProps<typeof RecommendationDirectionsCard>> = {},
) {
  return render(
    <RecommendationDirectionsCard
      destination={destination}
      mobileInline={false}
      onClose={vi.fn()}
      {...props}
    />,
    { wrapper: createWrapper() },
  )
}

describe('RecommendationDirectionsCard', () => {
  beforeEach(() => {
    mocks.fetchDirectionsAvailability.mockReset()
    mocks.fetchDrivingEstimate.mockReset()
    mocks.requestCurrentPosition.mockReset()
  })

  it('초기 카드에 목적지와 자동차 시간 확인 버튼을 보여준다', () => {
    renderCard()

    expect(screen.getByRole('heading', { name: '경복궁' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '자동차 시간 확인' })).toBeInTheDocument()
  })

  it('NAVER_MAP_REQUIRED이면 위치를 요청하지 않고 네이버 지도 버튼만 보여준다', async () => {
    mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'NAVER_MAP_REQUIRED' })
    const user = userEvent.setup()
    renderCard()

    await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))

    expect(await screen.findByRole('link', { name: '네이버 지도에서 길찾기' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '앱이 열리지 않으면 웹에서 확인' }))
      .not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '자동차 시간 확인' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
    expect(mocks.requestCurrentPosition).not.toHaveBeenCalled()
    expect(mocks.fetchDrivingEstimate).not.toHaveBeenCalled()
  })

  it('가용성, 위치, 예상 경로 순으로 요청한다', async () => {
    const order: string[] = []
    mocks.fetchDirectionsAvailability.mockImplementation(async () => {
      order.push('availability')
      return { status: 'AVAILABLE' }
    })
    mocks.requestCurrentPosition.mockImplementation(async () => {
      order.push('geolocation')
      return { latitude: 37.5, longitude: 127 }
    })
    mocks.fetchDrivingEstimate.mockImplementation(async () => {
      order.push('estimate')
      return successfulEstimate
    })
    const user = userEvent.setup()
    renderCard()

    await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))
    await screen.findByText('1시간 15분')

    expect(order).toEqual(['availability', 'geolocation', 'estimate'])
    expect(mocks.fetchDrivingEstimate.mock.calls[0]?.[0]).toEqual({
      originLatitude: 37.5,
      originLongitude: 127,
      destinationContentId: destination.contentId,
    })
  })

  it('성공한 예상 경로의 시간, 거리, 통행료를 표시한다', async () => {
    mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'AVAILABLE' })
    mocks.requestCurrentPosition.mockResolvedValue({ latitude: 37.5, longitude: 127 })
    mocks.fetchDrivingEstimate.mockResolvedValue(successfulEstimate)
    const user = userEvent.setup()
    renderCard()

    await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))

    expect(await screen.findByText('1시간 15분')).toBeInTheDocument()
    expect(screen.getByText('84.2km')).toBeInTheDocument()
    expect(screen.getByText('통행료 3,200원')).toBeInTheDocument()
  })

  it('위치 권한이 거부되면 안내와 재시도, 네이버 지도 버튼을 보여준다', async () => {
    mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'AVAILABLE' })
    mocks.requestCurrentPosition.mockRejectedValue(new GeolocationError('PERMISSION_DENIED'))
    const user = userEvent.setup()
    renderCard()

    await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))

    expect(await screen.findByText(/\uc704\uce58 \uad8c\ud55c/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '네이버 지도에서 길찾기' })).toBeInTheDocument()
  })

  it('목적지 contentId가 바뀌면 이전 예상 경로를 초기화한다', async () => {
    mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'AVAILABLE' })
    mocks.requestCurrentPosition.mockResolvedValue({ latitude: 37.5, longitude: 127 })
    mocks.fetchDrivingEstimate.mockResolvedValue(successfulEstimate)
    const user = userEvent.setup()
    const view = renderCard()
    await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))
    expect(await screen.findByText('1시간 15분')).toBeInTheDocument()

    view.rerender(
      <RecommendationDirectionsCard
        destination={{ ...destination, contentId: '264337', title: '불국사' }}
        mobileInline={false}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByRole('heading', { name: '불국사' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '자동차 시간 확인' })).toBeInTheDocument()
    expect(screen.queryByText('1시간 15분')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '네이버 지도에서 길찾기' })).not.toBeInTheDocument()
  })

  it('위치 확인 중 목적지가 바뀌면 이전 목적지의 예상 경로를 요청하지 않는다', async () => {
    let resolvePosition: ((position: { latitude: number; longitude: number }) => void) | undefined
    const positionPromise = new Promise<{ latitude: number; longitude: number }>((resolve) => {
      resolvePosition = resolve
    })
    mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'AVAILABLE' })
    mocks.requestCurrentPosition.mockReturnValue(positionPromise)
    mocks.fetchDrivingEstimate.mockResolvedValue(successfulEstimate)
    const user = userEvent.setup()
    const view = renderCard()

    await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))
    await waitFor(() => expect(mocks.requestCurrentPosition).toHaveBeenCalledOnce())

    view.rerender(
      <RecommendationDirectionsCard
        destination={{ ...destination, contentId: '264337', title: '불국사' }}
        mobileInline={false}
        onClose={vi.fn()}
      />,
    )

    await act(async () => {
      resolvePosition?.({ latitude: 37.5, longitude: 127 })
      await positionPromise
    })

    expect(mocks.fetchDrivingEstimate).not.toHaveBeenCalled()
  })

  it('닫기 버튼에 접근 가능한 이름을 제공하고 onClose를 호출한다', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderCard({ onClose })

    await user.click(screen.getByRole('button', { name: '길찾기 카드 닫기' }))

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('가용해도 목적지 좌표가 없으면 위치와 경로를 요청하지 않고 장소 검색을 사용한다', async () => {
    mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'AVAILABLE' })
    const user = userEvent.setup()
    renderCard({
      destination: { ...destination, latitude: null, longitude: null },
    })

    await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))

    const link = await screen.findByRole('link', { name: '네이버 지도에서 길찾기' })
    const url = new URL(link.getAttribute('href') ?? '')
    expect(url.pathname).toBe('/p/search')
    expect(url.searchParams.get('query')).toBe(destination.title)
    expect(url.searchParams.has('dlat')).toBe(false)
    expect(url.searchParams.has('dlng')).toBe(false)
    expect(mocks.fetchDirectionsAvailability).toHaveBeenCalledOnce()
    expect(mocks.requestCurrentPosition).not.toHaveBeenCalled()
    expect(mocks.fetchDrivingEstimate).not.toHaveBeenCalled()
  })

  it('mobileInline이면 모바일 modifier와 네이버 지도 앱 링크를 사용한다', async () => {
    mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'NAVER_MAP_REQUIRED' })
    const user = userEvent.setup()
    const { container } = renderCard({ mobileInline: true })

    await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))

    expect(container.firstElementChild).toHaveClass('directions-card--mobile-inline')
    const appLink = await screen.findByRole('link', { name: '네이버 지도에서 길찾기' })
    expect(appLink).toHaveAttribute(
      'href',
      expect.stringMatching(/^nmap:\/\/route\/car\?/),
    )
    expect(appLink).toHaveAttribute('target', '_blank')
    expect(appLink).toHaveAttribute('rel', 'noopener noreferrer')

    const webFallbackLink = screen.getByRole('link', {
      name: '앱이 열리지 않으면 웹에서 확인',
    })
    const webFallbackUrl = new URL(webFallbackLink.getAttribute('href') ?? '')
    expect(webFallbackUrl.origin).toBe('https://map.naver.com')
    expect(webFallbackUrl.pathname).toBe('/p/directions')
    expect(webFallbackUrl.searchParams.get('dlat')).toBe('37.579617')
    expect(webFallbackUrl.searchParams.get('dlng')).toBe('126.977041')
    expect(webFallbackUrl.searchParams.get('dname')).toBe('경복궁')
    expect(webFallbackLink).toHaveAttribute('target', '_blank')
    expect(webFallbackLink).toHaveAttribute('rel', 'noopener noreferrer')
  })
})
