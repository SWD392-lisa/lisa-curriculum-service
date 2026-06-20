# Debug Session: lms-role-id-1 [OPEN]

## Symptom
JWT thật từ User Service có role=1 nhưng LMS runtime trả `Unknown role ID: 1`.

## Scope
- Chỉ sửa repo: /Users/phamhongphuc/Documents/Lisa/lisa-curriculum-service/lisa-curriculum-service
- Không sửa User Service / Realtime Service / JWT contract

## Hypotheses
1. LMS trên 8081 là process stale / build cũ.
2. Có env/config override role-id-map ở runtime.
3. Claim role bị parse sai key hoặc value ở runtime.
4. App đang chạy profile/config khác dev.
5. Có nhiều instance/process khiến request đi vào app không đúng.

## Plan
1. Xác định và dừng toàn bộ process trên 8081.
2. Kiểm tra application.yml / application-dev.yml / .env và env runtime.
3. Chạy test sạch, boot đúng source mới với profile dev.
4. Login User Service lấy JWT thật.
5. Gọi learner endpoint và mentor endpoint để verify.

## Status
OPEN
