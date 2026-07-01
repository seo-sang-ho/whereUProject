# whereU Frontend

React, TypeScript, Vite 기반의 whereU 지도 화면입니다.

## Naver Maps 설정

`frontend/.env.local` 파일에 Naver Maps JavaScript API Client ID를 설정합니다.

```dotenv
VITE_NAVER_MAP_CLIENT_ID=[REDACTED]
```

Naver Cloud Platform 콘솔의 Maps 애플리케이션에서 Web 서비스 URL에 로컬 주소를 등록해야 합니다.

```text
http://localhost:5173
http://127.0.0.1:5173
```

## 실행

```bash
npm install
npm run dev
```
