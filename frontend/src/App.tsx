import { useEffect, useRef, useState } from 'react'
import Supercluster from 'supercluster'
import { useLatestStayStrengths } from './hooks/useLatestStayStrengths'
import { useValueRecommendations } from './hooks/useValueRecommendations'
import { loadNaverMaps } from './lib/naverMaps'
import type { GeoBounds, StayStrengthLevel, StayStrengthRegion } from './types/stayStrength'
import type { ValueRecommendation } from './types/valueRecommendation'
import './App.css'

type MapStatus = 'loading' | 'ready' | 'missing-key' | 'error'
type MapMode = 'strength' | 'recommendation'

interface MapOverlay {
  marker: naver.maps.Marker
  listener: naver.maps.MapEventListener
}

interface ClusterPointProperties {
  region: StayStrengthRegion
}

type ClusterMapFeature =
  | Supercluster.ClusterFeature<Record<string, never>>
  | Supercluster.PointFeature<ClusterPointProperties>

const naverMapClientId = import.meta.env.VITE_NAVER_MAP_CLIENT_ID?.trim()
const nationwideCenter = { latitude: 36.35, longitude: 127.8 }

const stayStrengthLevels = [
  { level: 'LOW', label: '낮음', className: 'low' },
  { level: 'MEDIUM', label: '보통', className: 'medium' },
  { level: 'HIGH', label: '높음', className: 'high' },
] as const

const markerColors: Record<StayStrengthLevel, string> = {
  LOW: '#31975b',
  MEDIUM: '#d8a91f',
  HIGH: '#cf4f46',
}

function toGeoBounds(map: naver.maps.Map): GeoBounds {
  const bounds = map.getBounds()
  const southWest = bounds.getSW()
  const northEast = bounds.getNE()

  return {
    minLatitude: southWest.lat(),
    maxLatitude: northEast.lat(),
    minLongitude: southWest.lng(),
    maxLongitude: northEast.lng(),
  }
}

function formatReferenceMonth(referenceDate: string | null | undefined): string {
  if (!referenceDate) {
    return '최신 기준월'
  }
  const [year, month] = referenceDate.split('-')
  return `${year}.${month} 기준`
}

function isClusterFeature(
  feature: ClusterMapFeature,
): feature is Supercluster.ClusterFeature<Record<string, never>> {
  return 'cluster' in feature.properties && feature.properties.cluster === true
}

function App() {
  const mapContainerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<naver.maps.Map | null>(null)
  const overlaysRef = useRef<MapOverlay[]>([])
  const [mapStatus, setMapStatus] = useState<MapStatus>(
    naverMapClientId ? 'loading' : 'missing-key',
  )
  const [mapMode, setMapMode] = useState<MapMode>('strength')
  const [bounds, setBounds] = useState<GeoBounds | null>(null)
  const [selectedRegion, setSelectedRegion] = useState<StayStrengthRegion | null>(null)
  const [selectedRecommendationCode, setSelectedRecommendationCode] = useState<string | null>(null)
  const stayStrengthQuery = useLatestStayStrengths(mapMode === 'strength' ? bounds : null)
  const recommendationQuery = useValueRecommendations(mapMode === 'recommendation')

  useEffect(() => {
    let map: naver.maps.Map | undefined
    let idleListener: naver.maps.MapEventListener | undefined
    let cancelled = false

    if (!naverMapClientId) {
      return
    }

    loadNaverMaps(naverMapClientId)
      .then(() => {
        if (cancelled || !mapContainerRef.current) {
          return
        }

        map = new window.naver.maps.Map(mapContainerRef.current, {
          center: new window.naver.maps.LatLng(
            nationwideCenter.latitude,
            nationwideCenter.longitude,
          ),
          zoom: 7,
          minZoom: 6,
          maxZoom: 18,
          zoomControl: true,
          zoomControlOptions: {
            position: window.naver.maps.Position.TOP_RIGHT,
          },
          mapDataControl: false,
          scaleControl: false,
        })
        mapRef.current = map

        const updateBounds = () => {
          if (map) {
            setBounds(toGeoBounds(map))
          }
        }

        idleListener = window.naver.maps.Event.addListener(map, 'idle', updateBounds)
        updateBounds()
        setMapStatus('ready')
      })
      .catch(() => {
        if (!cancelled) {
          setMapStatus('error')
        }
      })

    return () => {
      cancelled = true
      if (idleListener) {
        window.naver.maps.Event.removeListener(idleListener)
      }
      mapRef.current = null
      map?.destroy()
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current

    overlaysRef.current.forEach(({ marker, listener }) => {
      window.naver.maps.Event.removeListener(listener)
      marker.setMap(null)
    })
    overlaysRef.current = []

    if (!map) {
      return
    }

    if (mapMode === 'recommendation') {
      const recommendations = recommendationQuery.data?.recommendations ?? []
      overlaysRef.current = recommendations.flatMap((recommendation) => {
        if (recommendation.latitude === null || recommendation.longitude === null) {
          return []
        }
        const selected = recommendation.regionCode === selectedRecommendationCode
        const marker = new window.naver.maps.Marker({
          map,
          position: new window.naver.maps.LatLng(
            recommendation.latitude,
            recommendation.longitude,
          ),
          title: `${recommendation.rank}위 ${recommendation.regionName}`,
          zIndex: selected ? 80 : 60 - recommendation.rank,
          icon: {
            content: `<span class="recommendation-marker${selected ? ' is-selected' : ''}" aria-label="${recommendation.rank}위"><strong>${recommendation.rank}</strong></span>`,
            anchor: new window.naver.maps.Point(18, 45),
          },
        })
        const listener = window.naver.maps.Event.addListener(marker, 'click', () => {
          setSelectedRecommendationCode(recommendation.regionCode)
        })
        return [{ marker, listener }]
      })
    } else {
      const items = stayStrengthQuery.data?.stayStrengths
      if (!items || !bounds) {
        return
      }
      const points: Array<Supercluster.PointFeature<ClusterPointProperties>> = items.flatMap((region) => {
        if (region.latitude === null || region.longitude === null) {
          return []
        }
        return [{
          type: 'Feature',
          properties: { region },
          geometry: {
            type: 'Point',
            coordinates: [region.longitude, region.latitude],
          },
        }]
      })
      const clusterIndex = new Supercluster<ClusterPointProperties, Record<string, never>>({
        radius: 62,
        maxZoom: 14,
        minPoints: 3,
      }).load(points)
      const clusters = clusterIndex.getClusters(
        [bounds.minLongitude, bounds.minLatitude, bounds.maxLongitude, bounds.maxLatitude],
        Math.round(map.getZoom()),
      )

      overlaysRef.current = clusters.map((feature) => {
        const [longitude, latitude] = feature.geometry.coordinates
        if (isClusterFeature(feature)) {
          const { cluster_id: clusterId, point_count: pointCount } = feature.properties
          const sizeClass = pointCount >= 30 ? 'large' : pointCount >= 10 ? 'medium' : 'small'
          const marker = new window.naver.maps.Marker({
            map,
            position: new window.naver.maps.LatLng(latitude, longitude),
            zIndex: 40,
            icon: {
              content: `<span class="map-cluster map-cluster--${sizeClass}" aria-label="${pointCount}개 지역"><strong>${pointCount}</strong></span>`,
              anchor: new window.naver.maps.Point(22, 22),
            },
          })
          const listener = window.naver.maps.Event.addListener(marker, 'click', () => {
            const expansionZoom = Math.min(clusterIndex.getClusterExpansionZoom(clusterId), 15)
            map.morph(new window.naver.maps.LatLng(latitude, longitude), expansionZoom)
          })
          return { marker, listener }
        }

        const { region } = feature.properties
        const marker = new window.naver.maps.Marker({
          map,
          position: new window.naver.maps.LatLng(latitude, longitude),
          title: region.regionName,
          zIndex: region.level === 'HIGH' ? 30 : region.level === 'MEDIUM' ? 20 : 10,
          icon: {
            content: `<span class="strength-marker strength-marker--${region.level.toLowerCase()}" aria-label="${region.levelLabel}"></span>`,
            anchor: new window.naver.maps.Point(10, 10),
          },
        })
        const listener = window.naver.maps.Event.addListener(marker, 'click', () => {
          setSelectedRegion(region)
        })
        return { marker, listener }
      })
    }

    return () => {
      overlaysRef.current.forEach(({ marker, listener }) => {
        window.naver.maps.Event.removeListener(listener)
        marker.setMap(null)
      })
      overlaysRef.current = []
    }
  }, [
    bounds,
    mapMode,
    recommendationQuery.data,
    selectedRecommendationCode,
    stayStrengthQuery.data,
  ])

  const changeMapMode = (mode: MapMode) => {
    setMapMode(mode)
    setSelectedRegion(null)
    setSelectedRecommendationCode(null)
    if (mode === 'recommendation' && mapRef.current) {
      mapRef.current.morph(
        new window.naver.maps.LatLng(nationwideCenter.latitude, nationwideCenter.longitude),
        7,
      )
    }
  }

  const selectRecommendation = (recommendation: ValueRecommendation) => {
    setSelectedRecommendationCode(recommendation.regionCode)
    if (
      mapRef.current
      && recommendation.latitude !== null
      && recommendation.longitude !== null
    ) {
      mapRef.current.morph(
        new window.naver.maps.LatLng(recommendation.latitude, recommendation.longitude),
        12,
      )
    }
  }

  const visibleCount = stayStrengthQuery.data?.stayStrengths.length ?? 0
  const recommendations = recommendationQuery.data?.recommendations ?? []
  const hasDataError = mapMode === 'strength'
    ? stayStrengthQuery.isError
    : recommendationQuery.isError

  return (
    <main className="app-shell">
      <header className="app-header">
        <a className="brand" href="/" aria-label="whereU 홈">
          <span className="brand-mark" aria-hidden="true">W</span>
          <span>whereU</span>
        </a>
        <div className="view-title">
          <span className="view-title-dot" aria-hidden="true" />
          {mapMode === 'strength' ? '관광 체류강도' : '가성비 여행지'}
        </div>
        <span className="data-source">한국관광공사 데이터</span>
      </header>

      <section className="map-workspace" aria-label="관광 데이터 지도">
        <div ref={mapContainerRef} className="map-canvas" />

        <aside className={`map-panel${mapMode === 'recommendation' ? ' map-panel--recommendation' : ''}`}>
          <div className="map-mode-tabs" role="tablist" aria-label="지도 데이터 선택">
            <button
              type="button"
              role="tab"
              aria-selected={mapMode === 'strength'}
              className={mapMode === 'strength' ? 'is-active' : ''}
              onClick={() => changeMapMode('strength')}
            >
              체류강도
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mapMode === 'recommendation'}
              className={mapMode === 'recommendation' ? 'is-active' : ''}
              onClick={() => changeMapMode('recommendation')}
            >
              가성비 추천
            </button>
          </div>

          {mapMode === 'strength' ? (
            <>
              <div className="panel-heading">
                <span className="panel-eyebrow">화면 내 {visibleCount}개 지역</span>
                <h1>관광 체류강도</h1>
                <p>{formatReferenceMonth(stayStrengthQuery.data?.referenceDate)}</p>
              </div>

              <div className="legend" aria-label="체류강도 범례">
                {stayStrengthLevels.map((item) => (
                  <div className="legend-item" key={item.level}>
                    <span className={`legend-swatch ${item.className}`} aria-hidden="true" />
                    <span>{item.label}</span>
                  </div>
                ))}
              </div>

              {selectedRegion && (
                <div className="region-detail">
                  <button
                    className="detail-close"
                    type="button"
                    aria-label="지역 상세 닫기"
                    title="닫기"
                    onClick={() => setSelectedRegion(null)}
                  >
                    ×
                  </button>
                  <span
                    className="detail-level"
                    style={{ color: markerColors[selectedRegion.level] }}
                  >
                    {selectedRegion.levelLabel}
                  </span>
                  <strong>{selectedRegion.regionName}</strong>
                  <p>{selectedRegion.interpretation}</p>
                  <dl>
                    <div>
                      <dt>원본 지수</dt>
                      <dd>{selectedRegion.rawStayStrength.toFixed(2)}</dd>
                    </div>
                    <div>
                      <dt>상대 수준</dt>
                      <dd>{Math.round(selectedRegion.normalizedStayStrength * 100)}%</dd>
                    </div>
                  </dl>
                </div>
              )}
            </>
          ) : (
            <>
              <div className="panel-heading recommendation-heading">
                <span className="panel-eyebrow">전국 추천 TOP {recommendations.length}</span>
                <h1>가성비 여행지</h1>
                <p>{formatReferenceMonth(recommendationQuery.data?.referenceDate)}</p>
              </div>

              <ol className="recommendation-list" aria-label="가성비 여행지 추천 순위">
                {recommendations.map((recommendation) => {
                  const selected = recommendation.regionCode === selectedRecommendationCode
                  return (
                    <li key={recommendation.regionCode}>
                      <button
                        type="button"
                        className={selected ? 'recommendation-item is-selected' : 'recommendation-item'}
                        aria-pressed={selected}
                        onClick={() => selectRecommendation(recommendation)}
                      >
                        <span className="recommendation-rank">{recommendation.rank}</span>
                        <span className="recommendation-copy">
                          <strong>{recommendation.regionName}</strong>
                          <span>{recommendation.interpretation}</span>
                        </span>
                        <span className="recommendation-score">
                          {recommendation.recommendationScorePercent}
                          <small>점</small>
                        </span>
                        {selected && (
                          <span className="recommendation-metrics">
                            <span>서비스 수요 <b>{Math.round(recommendation.normalizedServiceDemand * 100)}</b></span>
                            <span>체류강도 <b>{Math.round(recommendation.normalizedStayStrength * 100)}</b></span>
                          </span>
                        )}
                      </button>
                    </li>
                  )
                })}
              </ol>

              {!recommendationQuery.isLoading && recommendations.length === 0 && !recommendationQuery.isError && (
                <div className="empty-recommendations">추천 계산에 필요한 공통 기준월 데이터가 없습니다.</div>
              )}
            </>
          )}

          {(stayStrengthQuery.isFetching || recommendationQuery.isFetching) && (
            <div className="panel-progress" role="status">
              <span className="panel-spinner" aria-hidden="true" />
              {mapMode === 'strength' ? '지역 데이터 갱신 중' : '추천 순위 계산 중'}
            </div>
          )}
        </aside>

        {hasDataError && mapStatus === 'ready' && (
          <div className="data-error" role="alert">
            {mapMode === 'strength'
              ? '체류강도 데이터를 불러오지 못했습니다. 백엔드 서버를 확인해 주세요.'
              : '추천 결과를 불러오지 못했습니다. 데이터 동기화 상태를 확인해 주세요.'}
          </div>
        )}

        {mapStatus !== 'ready' && (
          <div className="map-status" role="status">
            {mapStatus === 'loading' && (
              <>
                <span className="loading-indicator" aria-hidden="true" />
                <strong>지도를 불러오는 중</strong>
              </>
            )}
            {mapStatus === 'missing-key' && (
              <>
                <span className="status-symbol" aria-hidden="true">!</span>
                <strong>지도 인증 정보가 필요합니다</strong>
                <p><code>VITE_NAVER_MAP_CLIENT_ID</code>를 설정해 주세요.</p>
              </>
            )}
            {mapStatus === 'error' && (
              <>
                <span className="status-symbol" aria-hidden="true">!</span>
                <strong>지도를 불러오지 못했습니다</strong>
                <p>Client ID와 Web 서비스 URL을 확인해 주세요.</p>
              </>
            )}
          </div>
        )}
      </section>
    </main>
  )
}

export default App
