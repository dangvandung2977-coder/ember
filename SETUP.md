# EMBERHOLD — build & run setup

> Trạng thái: **PHASE A (Core) HOÀN THÀNH — T0→T8 ✅**, build xanh trên máy hiện tại.
> `./gradlew build -Werror` pass: **32 tests, 0 failures** (3 integration test bỏ qua khi thiếu env DB).

## 1. Tiên quyết tuyệt đối

| Yêu cầu | Ghi chú |
|---|---|
| **JDK 25** | Paper 26.1+ yêu cầu Java 25. Máy này đã cài sẵn bản portable tại `D:\deepseek\dsh\minecraft\.toolchain\jdk-25.0.4.1+1`. |
| **Gradle 9.x** | Pin `gradle-9.1.0`, đã có tại `D:\deepseek\dsh\minecraft\.toolchain\gradle-9.1.0`. |
| **Network** | Cần `repo.papermc.io` + Maven Central. |
| **Paper 26.2 + Postgres** | Chạy thật cần Paper 26.2 + Postgres 16/17 (PG16 đang chạy tại máy này). |

> ⚠️ **Version Paper đúng:** dòng 26.x dùng **build-numbered** `26.2.build.117-stable` (KHÔNG phải `26.2-R0.1-SNAPSHOT` — cái đó chỉ dành cho dòng 1.x). Đã verify trực tiếp từ `repo.papermc.io` metadata.

## 2. Cách build

```bash
cd ember
./gradlew build          # wrapper đã được generate (gradle-wrapper.jar có sẵn)
# hoặc nếu chưa có wrapper:
# .toolchain\gradle-9.1.0\bin\gradle.bat --no-daemon build -Werror
```

Kết quả mong đợi: toàn bộ module compile **xanh** với `-Werror`; `core` unit tests pass; fat jar tại `ember/dist/build/libs/dist-0.1.0-all.jar` (~4.8MB) — file duy nhất đặt vào `plugins/`.

Nếu dùng Gradle CLI thay wrapper, set env:
```powershell
$env:JAVA_HOME="D:\deepseek\dsh\minecraft\.toolchain\jdk-25.0.4.1+1"
$env:Path="$env:JAVA_HOME\bin;D:\deepseek\dsh\minecraft\.toolchain\gradle-9.1.0\bin;"
```

## 3. DB integration tests (run thật trên Postgres)

Có Postgres 16 đang chạy (localhost:5432). Test nằm trong `core/src/test` và bị gate bởi env:

```powershell
$env:PGPASSWORD="postgres"
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -U postgres -d postgres -c "DROP DATABASE IF EXISTS ember_dev;" 
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -U postgres -d postgres -c "CREATE DATABASE ember_dev;"
$env:EMBER_TEST_DB_URL="jdbc:postgresql://localhost:5432/ember_dev"
$env:EMBER_TEST_DB_USER="postgres"
$env:EMBER_TEST_DB_PASSWORD="postgres"
./gradlew :core:test --tests "*DbWriteIntegrationTest*" --tests "*DbImplIntegrationTest*"
```

Kết quả đã verified trên máy này: cả 3 integrate test PASSED (migration V1, nightly stats_daily write, audit_log + seasons persist). **Không có env đó → các test này bị skip, build vẫn xanh.**

## 4. Cấu trúc module (sau Phase A)

```
ember/
├── settings.gradle.kts            include core,temperature,storm,shelter,expedition,mobs,events,settlement,economy,dist
├── build.gradle.kts               subprojects: java-library, toolchain 25, -Werror, JUnit5
├── gradle.properties              ember.paper.api.version=26.2.build.117-stable
├── gradle/wrapper/                gradle-wrapper.jar (đã sinh) + properties
├── core/                          EmberApi interface + impls (service registry, event bus, schedulers, db+flyway, config, metrics, audit, commands, seasons, stats job, player listener)
│   ├── src/main/resources/db/migration/V1__init.sql
│   └── src/main/resources/config.yml
├── temperature/ storm/ shelter/ expedition/ mobs/ events/ settlement/ economy/   (module rỗng chờ Phase B+)
└── dist/                          EmberPlugin + paper-plugin.yml (commands/permissions) + fat-jar task
```

## 5. Còn thiếu / lưu ý
- **`api-version`** trong `paper-plugin.yml` đang để `1.21` (ước lượng). Khi chạy trên Paper 26.2 thật nếu bị warn → đổi theo build.
- **Smoke-boot `/ember diag`** trên Paper 26.2 thật chưa chạy ở đây (cần download Paper server + chạy). Đây là bước cuối của **Gate A**.
- Flyway 10 bắt buộc `flyway-database-postgresql` (không chỉ `flyway-core`) — đã thêm dep, PG16 support nhờ đó.
