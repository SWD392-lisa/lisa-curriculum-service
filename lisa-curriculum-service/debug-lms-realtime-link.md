# Debug Session: lms-realtime-link [OPEN]

## Symptom
Cần xác định LMS ↔ Realtime link end-to-end còn thiếu gì trong .env/config và liệu cần code thêm trong LMS hay không.

## Scope
- Chỉ sửa repo: /Users/phamhongphuc/Documents/Lisa/lisa-curriculum-service/lisa-curriculum-service
- Không sửa User Service / Realtime Service

## Hypotheses
1. .env của LMS thiếu hoặc sai REALTIME_* nên metadata không khớp runtime.
2. LMS hiện chỉ trả metadata cho frontend, chưa gọi HTTP thật sang Realtime.
3. Runtime Realtime không expose đúng route source kỳ vọng.
4. Thiếu USER_SERVICE_BASE_URL hoặc base URL dev chuẩn.
5. Nếu cần code, chỉ nên sửa integration/config trong LMS.

## Plan
1. Đọc .env/application và code integration của LMS.
2. Xác định contract metadata hiện tại.
3. Xác định LMS có HTTP client sang Realtime hay chỉ trả metadata.
4. Kết luận thiếu gì trong .env và có cần code LMS hay không.

## Status
OPEN
