import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchDrivingEstimate } from './directionsApi'
import type { DirectionsEstimateRequest } from '../types/directions'

const request: DirectionsEstimateRequest = {
  originLatitude: 37.5665,
  originLongitude: 126.978,
  destinationContentId: '126508',
}

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
}))

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: mocks.post,
  },
}))

describe('fetchDrivingEstimate', () => {
  beforeEach(() => {
    mocks.post.mockReset()
  })

  it('429를 DirectionsRateLimitError로 변환한다', async () => {
    mocks.post.mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 429,
        headers: { 'retry-after': '6' },
      },
    })

    await expect(fetchDrivingEstimate(request)).rejects.toMatchObject({
      name: 'DirectionsRateLimitError',
      retryAfterSeconds: 6,
    })
  })

  it.each([undefined, '', '0', '-1', '1.5', 'not-a-number'])(
    'Retry-After %s는 노출 가능한 초 단위 정수가 아니면 null로 처리한다',
    async (retryAfter) => {
      mocks.post.mockRejectedValue({
        isAxiosError: true,
        response: {
          status: 429,
          headers: retryAfter === undefined ? {} : { 'retry-after': retryAfter },
        },
      })

      await expect(fetchDrivingEstimate(request)).rejects.toMatchObject({
        name: 'DirectionsRateLimitError',
        retryAfterSeconds: null,
      })
    },
  )

  it('일반 네트워크 오류는 그대로 전달한다', async () => {
    const error = new Error('network unavailable')
    mocks.post.mockRejectedValue(error)

    await expect(fetchDrivingEstimate(request)).rejects.toBe(error)
  })

  it('503 Axios 오류는 rate-limit 오류로 바꾸지 않는다', async () => {
    const error = {
      isAxiosError: true,
      response: { status: 503, headers: {} },
    }
    mocks.post.mockRejectedValue(error)

    await expect(fetchDrivingEstimate(request)).rejects.toBe(error)
  })

  it('정상 estimate 응답은 기존 데이터 계약을 유지한다', async () => {
    const estimate = {
      status: 'AVAILABLE',
      destinationContentId: '126508',
      destinationName: '경복궁',
      travelTimeMinutes: 25,
      distanceMeters: 12_300,
      tollFare: 0,
      calculatedAt: '2026-07-20T15:00:00+09:00',
      fallbackReason: null,
    }
    mocks.post.mockResolvedValue({
      data: { success: true, data: estimate, message: null },
    })

    await expect(fetchDrivingEstimate(request)).resolves.toEqual(estimate)
  })
})
