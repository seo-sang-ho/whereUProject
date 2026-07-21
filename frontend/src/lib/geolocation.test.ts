import { afterEach, describe, expect, it, vi } from 'vitest'
import { requestCurrentPosition } from './geolocation'

function setGeolocation(
  getCurrentPosition: Geolocation['getCurrentPosition'],
) {
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: { getCurrentPosition },
  })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('requestCurrentPosition', () => {
  it('returns only plain coordinates from the browser position', async () => {
    const getCurrentPosition = vi.fn((success: PositionCallback) => {
      success({ coords: { latitude: 37.5665, longitude: 126.978 } } as GeolocationPosition)
    })
    setGeolocation(getCurrentPosition)

    await expect(requestCurrentPosition()).resolves.toEqual({
      latitude: 37.5665,
      longitude: 126.978,
    })
    expect(getCurrentPosition).toHaveBeenCalledWith(
      expect.any(Function),
      expect.any(Function),
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000 },
    )
  })

  it('maps a browser permission denial to PERMISSION_DENIED', async () => {
    setGeolocation(vi.fn((_success: PositionCallback, error?: PositionErrorCallback) => {
      error?.({ code: 1 } as GeolocationPositionError)
    }))

    await expect(requestCurrentPosition()).rejects.toMatchObject({
      code: 'PERMISSION_DENIED',
    })
  })

  it('maps a browser timeout to TIMEOUT', async () => {
    setGeolocation(vi.fn((_success: PositionCallback, error?: PositionErrorCallback) => {
      error?.({ code: 3 } as GeolocationPositionError)
    }))

    await expect(requestCurrentPosition()).rejects.toMatchObject({
      code: 'TIMEOUT',
    })
  })

  it('maps an unavailable browser position to POSITION_UNAVAILABLE', async () => {
    setGeolocation(vi.fn((_success: PositionCallback, error?: PositionErrorCallback) => {
      error?.({ code: 2 } as GeolocationPositionError)
    }))

    await expect(requestCurrentPosition()).rejects.toMatchObject({
      code: 'POSITION_UNAVAILABLE',
    })
  })
})
