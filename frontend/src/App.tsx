import { useEffect, useRef, useState } from 'react'
import { loadNaverMaps } from './lib/naverMaps'
import './App.css'

type MapStatus = 'loading' | 'ready' | 'missing-key' | 'error'

const naverMapClientId = import.meta.env.VITE_NAVER_MAP_CLIENT_ID?.trim()

const stayStrengthLevels = [
  { label: '낮음', className: 'low' },
  { label: '보통', className: 'medium' },
  { label: '높음', className: 'high' },
]

function App() {
  const mapContainerRef = useRef<HTMLDivElement>(null)
  const [mapStatus, setMapStatus] = useState<MapStatus>(
    naverMapClientId ? 'loading' : 'missing-key',
  )

  useEffect(() => {
    let map: naver.maps.Map | undefined
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
          center: new window.naver.maps.LatLng(36.35, 127.8),
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
        setMapStatus('ready')
      })
      .catch(() => {
        if (!cancelled) {
          setMapStatus('error')
        }
      })

    return () => {
      cancelled = true
      map?.destroy()
    }
  }, [])

  return (
    <main className="app-shell">
      <header className="app-header">
        <a className="brand" href="/" aria-label="whereU 홈">
          <span className="brand-mark" aria-hidden="true">W</span>
          <span>whereU</span>
        </a>
        <div className="view-title">
          <span className="view-title-dot" aria-hidden="true" />
          관광 체류강도
        </div>
        <span className="data-source">한국관광공사 데이터</span>
      </header>

      <section className="map-workspace" aria-label="전국 관광 체류강도 지도">
        <div ref={mapContainerRef} className="map-canvas" />

        <aside className="map-panel">
          <div className="panel-heading">
            <span className="panel-eyebrow">전국 시군구</span>
            <h1>관광 체류강도</h1>
            <p>최신 기준월</p>
          </div>

          <div className="legend" aria-label="체류강도 범례">
            {stayStrengthLevels.map((level) => (
              <div className="legend-item" key={level.className}>
                <span className={`legend-swatch ${level.className}`} aria-hidden="true" />
                <span>{level.label}</span>
              </div>
            ))}
          </div>
        </aside>

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
