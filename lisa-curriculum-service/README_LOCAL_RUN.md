# LMS Local Run

## 1. Create local env

```bash
cp .env.example .env
```

Then replace these placeholders in `.env`:

- `JWT_SECRET`: copy the DEV ONLY value from `../lisa-user-payment-service/ProjectLucy.API/appsettings.json`
- `REDIS_HOST` / `REDIS_PORT`: only needed when `SPRING_CACHE_TYPE=redis`
- `USER_SERVICE_BASE_URL`: use `http://localhost:5149` for `dotnet run`, or `http://localhost:8080` for Docker

## 2. Start required infrastructure

PostgreSQL only:

```bash
docker run --name lisa-postgres \
  -e POSTGRES_USER=lisa \
  -e POSTGRES_PASSWORD=lisa_secret \
  -e POSTGRES_DB=lisadb \
  -p 5432:5432 \
  -d postgres:16-alpine
```

Optional Redis when you want `SPRING_CACHE_TYPE=redis`:

```bash
docker run --name lisa-redis -p 6379:6379 -d redis:7-alpine
```

## 3. Run LMS

Spring now imports `.env` automatically, so a plain Spring Boot run is enough:

```bash
mvn clean test
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 4. Local URLs

- Health: `http://localhost:8081/actuator/health`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- Curriculum stats: `http://localhost:8081/api/curriculum/stats`

## 5. Realtime room binding

Realtime generates its own `roomId`, so LMS cannot derive it from `sessionId`.
Use the LMS binding endpoint after a Realtime room has been created:

```bash
curl -X POST http://localhost:8081/api/lms/room-sessions/{sessionId}/realtime-binding \
  -H "Authorization: Bearer <mentor_or_admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "realtimeRoomId": "room-id-from-realtime",
    "realtimeAgoraChannelName": "lucy-room-..."
  }'
```

To remove the link:

```bash
curl -X DELETE http://localhost:8081/api/lms/room-sessions/{sessionId}/realtime-binding \
  -H "Authorization: Bearer <mentor_or_admin_token>"
```

`GET /api/lms/room-sessions/{sessionId}/state` now returns:

- bound `realtimeRoomId`
- bound `realtimeAgoraChannelName`
- `mappingStatus` = `UNBOUND` or `BOUND`
- Realtime lookup endpoint templates for room and participants

## 6. Notes

- `USER_SERVICE_BASE_URL` depends on how the .NET service is started: `http://localhost:5149` for `dotnet run`, `http://localhost:8080` for Docker
- `REALTIME_SERVICE_BASE_URL` and `REALTIME_SOCKET_URL` are the current local Realtime URL from `lisa-realtime-service/README.md`: `http://localhost:3000`
- LMS local default uses `http://localhost:8081` to avoid colliding with User Service on `http://localhost:8080`
- If you keep `SPRING_CACHE_TYPE=simple`, Redis is not required for local boot
