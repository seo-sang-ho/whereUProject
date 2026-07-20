import { describe, expect, it } from 'vitest'
import {
  buildNaverDirectionsLink,
  buildNaverPlaceSearchLink,
} from './naverDirectionsLink'

const destination = {
  latitude: 37.579617,
  longitude: 126.977041,
  name: '경복궁',
}

describe('buildNaverDirectionsLink', () => {
  it('includes mobile start parameters only when an origin is present', () => {
    const withOrigin = new URL(buildNaverDirectionsLink(
      'mobile',
      destination,
      { latitude: 37.5665, longitude: 126.978 },
      'https://whereu.example',
    ))
    const withoutOrigin = new URL(buildNaverDirectionsLink(
      'mobile',
      destination,
      undefined,
      'https://whereu.example',
    ))

    expect(withOrigin.href).toContain('nmap://route/car')
    expect(withOrigin.searchParams.get('slat')).toBe('37.5665')
    expect(withOrigin.searchParams.get('slng')).toBe('126.978')
    expect(withOrigin.searchParams.get('sname')).toBe('내 위치')
    expect(withoutOrigin.searchParams.has('slat')).toBe(false)
    expect(withoutOrigin.searchParams.has('slng')).toBe(false)
    expect(withoutOrigin.searchParams.has('sname')).toBe(false)
  })

  it('encodes destination names and Korean start names', () => {
    const link = new URL(buildNaverDirectionsLink(
      'mobile',
      destination,
      { latitude: 37.5665, longitude: 126.978 },
      'https://whereu.example',
    ))

    expect(link.href).not.toContain('경복궁')
    expect(link.href).not.toContain('내 위치')
    expect(link.searchParams.get('dname')).toBe('경복궁')
    expect(link.searchParams.get('sname')).toBe('내 위치')
  })

  it('creates a desktop web directions URL with destination coordinates and name', () => {
    const link = new URL(buildNaverDirectionsLink(
      'desktop',
      destination,
      undefined,
      'https://whereu.example',
    ))

    expect(link.origin).toBe('https://map.naver.com')
    expect(link.pathname).toBe('/p/directions')
    expect(link.searchParams.get('dlat')).toBe('37.579617')
    expect(link.searchParams.get('dlng')).toBe('126.977041')
    expect(link.searchParams.get('dname')).toBe('경복궁')
  })

  it('uses place search rather than invalid route coordinates when destination coordinates are missing', () => {
    const link = buildNaverDirectionsLink(
      'desktop',
      { latitude: null, longitude: null, name: '경복궁' },
      undefined,
      'https://whereu.example',
    )

    expect(link).toBe(buildNaverPlaceSearchLink('경복궁'))
    expect(link).toContain('/p/search')
    expect(link).toContain(encodeURIComponent('경복궁'))
    expect(link).not.toContain('dlat=')
    expect(link).not.toContain('dlng=')
  })
})
