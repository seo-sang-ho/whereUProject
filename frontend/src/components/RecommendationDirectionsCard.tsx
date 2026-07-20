import { useEffect, useId, useRef, useState } from 'react'
import { useDirectionsAvailability } from '../hooks/useDirectionsAvailability'
import { useDrivingEstimate } from '../hooks/useDrivingEstimate'
import {
  GeolocationError,
  requestCurrentPosition,
  type GeolocationErrorCode,
} from '../lib/geolocation'
import { buildNaverDirectionsLink } from '../lib/naverDirectionsLink'
import type { Coordinates, DirectionsEstimate } from '../types/directions'
import type { RecommendedTourismContent } from '../types/tourismContent'

interface RecommendationDirectionsCardProps {
  destination: RecommendedTourismContent
  mobileInline: boolean
  onClose: () => void
}

type DirectionsCardPhase =
  | 'READY'
  | 'CHECKING_AVAILABILITY'
  | 'LOCATING'
  | 'LOADING_ROUTE'
  | 'AVAILABLE'
  | 'NAVER_MAP_REQUIRED'

type FallbackReason = GeolocationErrorCode | 'AVAILABILITY' | 'ROUTE'

interface DirectionsCardState {
  phase: DirectionsCardPhase
  origin?: Coordinates
  estimate?: DirectionsEstimate
  fallbackReason?: FallbackReason
}

const INITIAL_STATE: DirectionsCardState = { phase: 'READY' }

export function RecommendationDirectionsCard(
  props: RecommendationDirectionsCardProps,
) {
  return (
    <RecommendationDirectionsCardContent
      key={props.destination.contentId}
      {...props}
    />
  )
}

function RecommendationDirectionsCardContent({
  destination,
  mobileInline,
  onClose,
}: RecommendationDirectionsCardProps) {
  const titleId = useId()
  const availability = useDirectionsAvailability(false)
  const estimateMutation = useDrivingEstimate()
  const [state, setState] = useState<DirectionsCardState>(INITIAL_STATE)
  const activeRef = useRef(true)

  useEffect(() => {
    activeRef.current = true
    return () => {
      activeRef.current = false
    }
  }, [])

  const checkDrivingTime = async () => {
    setState({ phase: 'CHECKING_AVAILABILITY' })

    const availabilityResult = await availability.refetch()
    if (!activeRef.current) {
      return
    }
    if (availabilityResult.error || availabilityResult.data?.status !== 'AVAILABLE') {
      setState({
        phase: 'NAVER_MAP_REQUIRED',
        fallbackReason: 'AVAILABILITY',
      })
      return
    }

    setState({ phase: 'LOCATING' })

    let origin: Coordinates
    try {
      origin = await requestCurrentPosition()
    } catch (error) {
      if (!activeRef.current) {
        return
      }
      setState({
        phase: 'NAVER_MAP_REQUIRED',
        fallbackReason: toGeolocationFallbackReason(error),
      })
      return
    }

    if (!activeRef.current) {
      return
    }
    setState({ phase: 'LOADING_ROUTE', origin })

    try {
      const estimate = await estimateMutation.mutateAsync({
        originLatitude: origin.latitude,
        originLongitude: origin.longitude,
        destinationContentId: destination.contentId,
      })

      if (!activeRef.current) {
        return
      }
      if (estimate.status === 'AVAILABLE') {
        setState({ phase: 'AVAILABLE', origin, estimate })
        return
      }

      setState({
        phase: 'NAVER_MAP_REQUIRED',
        origin,
        fallbackReason: 'ROUTE',
      })
    } catch {
      if (!activeRef.current) {
        return
      }
      setState({
        phase: 'NAVER_MAP_REQUIRED',
        origin,
        fallbackReason: 'ROUTE',
      })
    }
  }

  const naverLink = buildNaverDirectionsLink(
    mobileInline ? 'mobile' : 'desktop',
    {
      latitude: destination.latitude,
      longitude: destination.longitude,
      name: destination.title,
    },
    state.origin,
    window.location.origin,
  )

  return (
    <section
      className={`directions-card${mobileInline ? ' directions-card--mobile-inline' : ''}`}
      aria-labelledby={titleId}
    >
      <header className="directions-card__header">
        <div>
          <span className="directions-card__eyebrow">자동차 길찾기</span>
          <h2 id={titleId}>{destination.title}</h2>
        </div>
        <button
          className="directions-card__close"
          type="button"
          aria-label="길찾기 카드 닫기"
          onClick={onClose}
        >
          ×
        </button>
      </header>

      {destination.address && (
        <p className="directions-card__address">{destination.address}</p>
      )}

      <div className="directions-card__status" aria-live="polite">
        {state.phase === 'READY' && (
          <button
            className="directions-card__button directions-card__button--primary"
            type="button"
            onClick={checkDrivingTime}
          >
            자동차 시간 확인
          </button>
        )}

        {isProgressPhase(state.phase) && (
          <div className="directions-card__loading">
            <span className="directions-card__spinner" aria-hidden="true" />
            <p>{progressMessage(state.phase)}</p>
          </div>
        )}

        {state.phase === 'AVAILABLE' && state.estimate && (
          <>
            <dl className="directions-card__metrics">
              <div className="directions-card__metric directions-card__metric--primary">
                <dt>예상 시간</dt>
                <dd>{formatTravelTime(state.estimate.travelTimeMinutes)}</dd>
              </div>
              <div className="directions-card__metric">
                <dt>예상 거리</dt>
                <dd>{formatDistance(state.estimate.distanceMeters)}</dd>
              </div>
              <div className="directions-card__metric">
                <dt>비용 안내</dt>
                <dd>{formatTollFare(state.estimate.tollFare)}</dd>
              </div>
            </dl>
            <NaverDirectionsLink href={naverLink} />
          </>
        )}

        {state.phase === 'NAVER_MAP_REQUIRED' && (
          <div className="directions-card__fallback">
            <p>{fallbackMessage(state.fallbackReason)}</p>
            <div className="directions-card__actions">
              {isRetryableGeolocationFailure(state.fallbackReason) && (
                <button
                  className="directions-card__button directions-card__button--secondary"
                  type="button"
                  onClick={checkDrivingTime}
                >
                  다시 시도
                </button>
              )}
              <NaverDirectionsLink href={naverLink} />
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

function NaverDirectionsLink({ href }: { href: string }) {
  return (
    <a
      className="directions-card__button directions-card__button--naver"
      href={href}
      target="_blank"
      rel="noopener noreferrer"
    >
      네이버 지도에서 길찾기
    </a>
  )
}

function isProgressPhase(
  phase: DirectionsCardPhase,
): phase is 'CHECKING_AVAILABILITY' | 'LOCATING' | 'LOADING_ROUTE' {
  return phase === 'CHECKING_AVAILABILITY'
    || phase === 'LOCATING'
    || phase === 'LOADING_ROUTE'
}

function progressMessage(
  phase: 'CHECKING_AVAILABILITY' | 'LOCATING' | 'LOADING_ROUTE',
): string {
  switch (phase) {
    case 'CHECKING_AVAILABILITY':
      return '길찾기 가능 여부를 확인하고 있어요.'
    case 'LOCATING':
      return '현재 위치를 확인하고 있어요.'
    case 'LOADING_ROUTE':
      return '자동차 예상 시간을 계산하고 있어요.'
  }
}

function toGeolocationFallbackReason(error: unknown): GeolocationErrorCode {
  if (error instanceof GeolocationError) {
    return error.code
  }
  return 'POSITION_UNAVAILABLE'
}

function isRetryableGeolocationFailure(
  reason: FallbackReason | undefined,
): reason is GeolocationErrorCode {
  return reason === 'PERMISSION_DENIED'
    || reason === 'POSITION_UNAVAILABLE'
    || reason === 'TIMEOUT'
}

function fallbackMessage(reason: FallbackReason | undefined): string {
  switch (reason) {
    case 'PERMISSION_DENIED':
      return '위치 권한이 없어 현재 위치에서의 시간을 계산할 수 없어요.'
    case 'POSITION_UNAVAILABLE':
      return '현재 위치를 확인하지 못했어요.'
    case 'TIMEOUT':
      return '현재 위치 확인 시간이 초과됐어요.'
    case 'ROUTE':
      return '자동차 예상 시간을 불러오지 못했어요.'
    default:
      return '네이버 지도에서 길찾기를 계속해 주세요.'
  }
}

function formatTravelTime(minutes: number | null): string {
  if (minutes === null) {
    return '시간 정보 없음'
  }

  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  if (hours === 0) {
    return `${remainingMinutes}분`
  }
  if (remainingMinutes === 0) {
    return `${hours}시간`
  }
  return `${hours}시간 ${remainingMinutes}분`
}

function formatDistance(distanceMeters: number | null): string {
  if (distanceMeters === null) {
    return '거리 정보 없음'
  }

  return `${new Intl.NumberFormat('ko-KR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(distanceMeters / 1_000)}km`
}

function formatTollFare(tollFare: number | null): string {
  if (tollFare === null) {
    return '통행료 정보 없음'
  }

  return `통행료 ${new Intl.NumberFormat('ko-KR').format(tollFare)}원`
}
