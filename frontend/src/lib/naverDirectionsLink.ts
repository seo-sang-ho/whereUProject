import type { Coordinates } from '../types/directions'

export type NaverDirectionsDevice = 'mobile' | 'desktop'

export interface NaverDirectionsDestination {
  latitude: number | null
  longitude: number | null
  name: string
}

export function buildNaverDirectionsLink(
  device: NaverDirectionsDevice,
  destination: NaverDirectionsDestination,
  origin: Coordinates | undefined,
  appName: string,
): string {
  if (!hasValidCoordinates(destination)) {
    return buildNaverPlaceSearchLink(destination.name)
  }

  const parameters = new URLSearchParams({
    dlat: String(destination.latitude),
    dlng: String(destination.longitude),
    dname: destination.name,
  })

  if (origin) {
    parameters.set('slat', String(origin.latitude))
    parameters.set('slng', String(origin.longitude))
    parameters.set('sname', '내 위치')
  }

  if (device === 'mobile') {
    parameters.set('appname', appName)
    return buildUrl('nmap://route/car', parameters)
  }

  parameters.set('mode', 'car')
  return buildUrl('https://map.naver.com/p/directions', parameters)
}

export function buildNaverPlaceSearchLink(name: string): string {
  return buildUrl(
    'https://map.naver.com/p/search',
    new URLSearchParams({ query: name }),
  )
}

function hasValidCoordinates(destination: NaverDirectionsDestination): destination is {
  latitude: number
  longitude: number
  name: string
} {
  return isLatitude(destination.latitude) && isLongitude(destination.longitude)
}

function isLatitude(value: number | null): value is number {
  return value !== null && Number.isFinite(value) && value >= -90 && value <= 90
}

function isLongitude(value: number | null): value is number {
  return value !== null && Number.isFinite(value) && value >= -180 && value <= 180
}

function buildUrl(base: string, parameters: URLSearchParams): string {
  const url = new URL(base)
  url.search = parameters.toString()
  return url.toString()
}
