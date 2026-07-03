declare namespace naver.maps {
  class LatLng {
    constructor(latitude: number, longitude: number)
    lat(): number
    lng(): number
  }

  class Point {
    constructor(x: number, y: number)
  }

  interface LatLngBounds {
    getSW(): LatLng
    getNE(): LatLng
  }

  interface MapOptions {
    center: LatLng
    zoom: number
    minZoom?: number
    maxZoom?: number
    zoomControl?: boolean
    zoomControlOptions?: {
      position: string
    }
    mapDataControl?: boolean
    scaleControl?: boolean
  }

  class Map {
    constructor(element: HTMLElement, options: MapOptions)
    getBounds(): LatLngBounds
    getZoom(): number
    morph(center: LatLng, zoom: number): void
    destroy(): void
  }

  interface MarkerIcon {
    content: string
    anchor: Point
  }

  interface MarkerOptions {
    map: Map
    position: LatLng
    title?: string
    zIndex?: number
    icon?: MarkerIcon
  }

  class Marker {
    constructor(options: MarkerOptions)
    setMap(map: Map | null): void
  }

  interface MapEventListener {
    readonly listenerName?: string
  }

  const Event: {
    addListener(
      target: Map | Marker,
      eventName: string,
      handler: () => void,
    ): MapEventListener
    removeListener(listener: MapEventListener): void
  }

  const Position: {
    TOP_RIGHT: string
  }
}

interface Window {
  naver: {
    maps: typeof naver.maps
  }
}
