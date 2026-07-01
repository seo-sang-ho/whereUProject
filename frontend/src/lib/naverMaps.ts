const NAVER_MAPS_SCRIPT_ID = 'naver-maps-sdk'

let loadingPromise: Promise<void> | null = null

export function loadNaverMaps(clientId: string): Promise<void> {
  if (window.naver?.maps) {
    return Promise.resolve()
  }

  if (loadingPromise) {
    return loadingPromise
  }

  loadingPromise = new Promise((resolve, reject) => {
    const existingScript = document.getElementById(NAVER_MAPS_SCRIPT_ID) as HTMLScriptElement | null
    const script = existingScript ?? document.createElement('script')

    const handleLoad = () => {
      if (window.naver?.maps) {
        resolve()
        return
      }
      loadingPromise = null
      reject(new Error('Naver Maps SDK did not initialize.'))
    }
    const handleError = () => {
      loadingPromise = null
      reject(new Error('Failed to load Naver Maps SDK.'))
    }

    script.addEventListener('load', handleLoad, { once: true })
    script.addEventListener('error', handleError, { once: true })

    if (!existingScript) {
      script.id = NAVER_MAPS_SCRIPT_ID
      script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(clientId)}`
      script.async = true
      document.head.appendChild(script)
    }
  })

  return loadingPromise
}
