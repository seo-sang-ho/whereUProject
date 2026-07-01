declare namespace naver.maps {
  class LatLng {
    constructor(latitude: number, longitude: number)
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
    destroy(): void
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
