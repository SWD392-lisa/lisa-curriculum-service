# LISA Platform

Hệ thống microservices cho LISA Language Learning App (SWD392).

## Kiến trúc

```
                    ┌──────────────────┐
                    │   Nginx Gateway  │  :80
                    └────────┬─────────┘
                  ┌──────────┴──────────┐
                  ▼                     ▼
     ┌────────────────────┐  ┌───────────────────┐
     │ Curriculum Service │  │  Repo Service     │
     │  Java Spring :8080 │  │  Java Spring :8082│
     └─────────┬──────────┘  └───────────────────┘
               │
     ┌─────────▼──────────┐
     │    PostgreSQL       │  :5432
     │    lisadb           │
     └────────────────────┘
```

## Services

| Service | Port | Mô tả |
|---|---|---|
| `lisa-curriculum-service` | 8080 | Parse 8 file Word → DB, cung cấp curriculum API |
| `lisa-repo-service` | 8082 | Tự động tạo repo GitHub + GitLab |
| `postgres` | 5432 | PostgreSQL database |
| `nginx` | 80 | API Gateway |

## Chạy nhanh

```bash
# 1. Copy và điền token
cp .env.example .env
# Mở .env, điền GITHUB_TOKEN, GITHUB_ORG, GITLAB_TOKEN, GITLAB_NAMESPACE_ID

# 2. Build và chạy tất cả
docker-compose up --build

# 3. Kiểm tra
curl http://localhost/health
```

## API chính

### Curriculum Service
| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/curriculum/import?language=ENGLISH&overwrite=true` | Import file .docx |
| GET | `/api/curriculum/levels?language=ENGLISH` | Lấy tất cả levels |
| GET | `/api/curriculum/levels?language=ENGLISH&stage=1` | Lấy levels theo stage |
| GET | `/api/curriculum/levels/{id}` | Chi tiết 1 level |
| GET | `/api/curriculum/levels/by-number?language=JAPANESE&levelNumber=5` | Tìm theo số level |
| GET | `/api/curriculum/stats` | Thống kê số level đã import |

### Repo Service
| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/repos/provision/all` | Tạo tất cả 6 repos trên GitHub + GitLab |
| POST | `/api/repos/provision/{serviceName}` | Tạo 1 service cụ thể |
| POST | `/api/repos/custom` | Tạo repo tùy chỉnh |
| GET | `/api/repos/services` | Xem danh sách services |

## Import 8 file curriculum

```bash
# Import từng file
curl -X POST "http://localhost/api/curriculum/import?language=ENGLISH&overwrite=true" \
     -F "file=@Eng_-_STAGE_1__LEVELS_1-30_.docx"

curl -X POST "http://localhost/api/curriculum/import?language=ENGLISH&overwrite=false" \
     -F "file=@Eng_-_STAGE_2__LEVEL_31-60_.docx"

curl -X POST "http://localhost/api/curriculum/import?language=CHINESE&overwrite=true" \
     -F "file=@Chinese_-_level_1-30.docx"

curl -X POST "http://localhost/api/curriculum/import?language=CHINESE&overwrite=false" \
     -F "file=@Chinese_-_level_31-60.docx"

curl -X POST "http://localhost/api/curriculum/import?language=JAPANESE&overwrite=true" \
     -F "file=@Janpanes_-_stage1_level1-30.docx"

# Xem thống kê sau khi import
curl http://localhost/api/curriculum/stats
```

## Database

- **Host:** localhost:5432
- **DB:** lisadb  |  **User:** lisa  |  **Pass:** (xem .env)
- **H2 Console (dev):** http://localhost:8080/h2-console

### Bảng chính
- `levels` — 100 levels (English/Chinese/Japanese)
- `sub_levels` — 6 sub-levels mỗi level
- `speaking_tasks` — Các prompt/Q&A trong mỗi sub-level
- `curriculum_stats` — View thống kê tổng hợp

## Development (không dùng Docker)

```bash
# Chạy curriculum service với H2 (không cần PostgreSQL)
cd lisa-curriculum-service
mvn spring-boot:run
# → http://localhost:8080/h2-console

# Chạy tests
mvn test
```
