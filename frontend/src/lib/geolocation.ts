import type { Coordinates } from '../types/directions'

export type GeolocationErrorCode =
  | 'PERMISSION_DENIED'
  | 'POSITION_UNAVAILABLE'
  | 'TIMEOUT'

export class GeolocationError extends Error {
  readonly code: GeolocationErrorCode

  constructor(code: GeolocationErrorCode) {
    super(code)
    this.name = 'GeolocationError'
    this.code = code
  }
}

const POSITION_OPTIONS: PositionOptions = {
  enableHighAccuracy: false,
  timeout: 10_000,
  maximumAge: 60_000,
}

export function requestCurrentPosition(): Promise<Coordinates> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new GeolocationError('POSITION_UNAVAILABLE'))
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => resolve({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
      }),
      (error) => reject(new GeolocationError(toErrorCode(error.code))),
      POSITION_OPTIONS,
    )
  })
}

function toErrorCode(code: number): GeolocationErrorCode {
  switch (code) {
    case 1:
      return 'PERMISSION_DENIED'
    case 3:
      return 'TIMEOUT'
    default:
      return 'POSITION_UNAVAILABLE'
  }
}
