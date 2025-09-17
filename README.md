# Antlr-Server

이 저장소는 Oracle PL/SQL 소스(SQL/PLSQL 파일)를 업로드 받아 ANTLR 기반 파서로 구조를 분석하고 JSON으로 저장하는 Spring Boot 애플리케이션입니다. 프런트/다른 서비스에서 파일을 업로드하고 분석을 트리거하는 간단한 HTTP API를 제공합니다.

## 빠른 시작(요약)
- Java 17, Maven 필요
- 로컬 실행: `./mvnw spring-boot:run` 또는 `mvn spring-boot:run`
- 기본 포트: `8081`
- 헬스체크: `GET /` → `OK`
- 주요 API:
  - `POST /fileUpload` 다중 파일 업로드(헤더 `Session-UUID` 필수)
  - `POST /parsing` 업로드된 `.sql` 파일 분석(헤더 `Session-UUID` 필수)
  - `POST /testsample` 절차명 목록으로 파일 조회 및(필요 시) 분석

## 이 프로젝트는 무엇을 하나요?
- **PL/SQL 구조 분석**: 업로드된 `.sql`(패키지/프로시저/함수/트리거 등)에서 구문 트리를 구성하고, 주요 구문 노드(PROCEDURE/FUNCTION/SPEC/SELECT/INSERT/UPDATE/DELETE/MERGE/IF/LOOP/EXCEPTION 등)의 **시작/끝 라인**을 트리 형태 JSON으로 생성합니다.
- **파일/세션 단위 관리**: 클라이언트가 보낸 `Session-UUID`로 작업 디렉터리를 분리하여 **여러 세션이 동시에** 안전하게 작업할 수 있습니다.
- **분석 결과 캐싱**: 동일 파일에 대한 분석 결과는 세션의 `analysis/`에 JSON으로 저장되어, 필요 시 재활용할 수 있습니다.
- **간단한 API**: 업로드 → 분석 트리거 → 결과 파일 확인의 단순한 흐름만 제공합니다. 프런트/백엔드 어디서든 쉽게 연동 가능합니다.

## 전체 동작 흐름
1) **업로드** (`POST /fileUpload`)
   - `PlSqlFileParserService.saveFile`이 파일을 `{BASE_DIR}/{Session-UUID}/{src|ddl|sequence}`에 저장합니다.
   - 파일명에 `DDL`/`SEQ` 포함 여부로 서브 디렉터리를 결정합니다.
   - 저장 후 내용 인코딩은 UTF-8 → EUC-KR → MS949 순으로 시도하여 읽습니다.
   - 내용에서 정규식으로 SQL 객체명(패키지/프로시저/함수)을 추출합니다.

2) **분석 트리거** (`POST /parsing`)
   - 각 파일을 ANTLR 파서로 파싱합니다(`PlSqlLexer`/`PlSqlParser`).
   - `CustomPlSqlListener`가 구문 트리를 순회하여 `Node` 트리를 빌드합니다.
   - 결과는 `{BASE_DIR}/{Session-UUID}/analysis/<파일명>.json`으로 저장됩니다.

3) **테스트 샘플** (`POST /testsample`)
   - `src/`에서 프로시저명과 일치하는 `.sql`을 찾아(대소문자 무시) 분석 결과가 없다면 즉시 분석하여, 메타 정보와 함께 반환합니다.

> BASE_DIR은 `DOCKER_COMPOSE_CONTEXT` 환경 변수가 있으면 그 값을, 없으면 `프로젝트 상위/data`를 사용합니다.

## 요구사항
- Java 17
- Maven 3.8+
- (선택) Docker 24+, Docker Compose

## 로컬 실행
1) 의존성 설치 및 실행
```bash
mvn spring-boot:run
```
또는 패키징 후 JAR 실행
```bash
mvn package -DskipTests
java -jar target/parser-0.0.1-SNAPSHOT.jar
```

2) 애플리케이션 설정(`src/main/resources/application.properties`)
- 포트: `server.port=8081`
- 업로드 제한: `spring.servlet.multipart.max-file-size=3MB`, `spring.servlet.multipart.max-request-size=3MB`
- CORS: `WebConfig`에서 `http://localhost:8080` 허용

## 데이터 저장 구조
서비스는 업로드/분석 산출물을 세션 UUID 단위로 디렉터리에 저장합니다. 기본 베이스 디렉터리는 다음 우선순위로 결정됩니다.
1. 환경변수 `DOCKER_COMPOSE_CONTEXT`가 설정된 경우 해당 경로 사용
2. 그렇지 않으면 `프로젝트 상위 디렉터리/data` 사용

세션별 하위 구조
```
{BASE_DIR}/{Session-UUID}/
  ├─ src/         # 기본 업로드 대상 (PL/SQL)
  ├─ ddl/         # 파일명에 'DDL' 포함 시 저장
  ├─ sequence/    # 파일명에 'SEQ' 포함 시 저장
  └─ analysis/    # 분석 결과 JSON 저장
```
- 파일 타입 결정 로직: 파일명에 대소문자 무시하고 `DDL` 포함 → ddl, `SEQ` 포함 → sequence, 그 외 → src
- 분석 결과 파일명: `<원본파일명(확장자제외)>.json`

## API 명세
모든 API 중 파일 처리/분석 관련 엔드포인트는 요청 헤더에 `Session-UUID`가 필요합니다.

### 1) 헬스체크
- 메서드/경로: `GET /`
- 응답: `200 OK`, 바디: `OK`

### 2) 파일 업로드
- 메서드/경로: `POST /fileUpload`
- 헤더: `Session-UUID: <uuid>`
- 요청: `multipart/form-data`, 필드명 `files` (다중 업로드 가능)
- 동작:
  - 파일 저장 (이미 존재하면 저장 생략)
  - `.sql` 파일의 경우 객체명 추출 시도 후 성공 목록에 포함
- 응답 예시(성공)
```json
{
  "successFiles": [
    {
      "objectName": "MY_PROCEDURE",
      "fileContent": "CREATE OR REPLACE PROCEDURE ...",
      "fileName": "MY_PROCEDURE.sql"
    }
  ]
}
```
- 응답 예시(일부 실패)
```json
{
  "successFiles": [ ... ],
  "failedFiles": [
    { "fileName": "bad.sql", "error": "파일 업로드 및 저장 실패: ..." }
  ]
}
```

### 3) 파일 분석 트리거
- 메서드/경로: `POST /parsing`
- 헤더: `Session-UUID: <uuid>`
- 요청(JSON):
```json
{
  "fileNames": [ { "fileName": "MY_PROCEDURE.sql" }, { "fileName": "MY_FUNC.sql" } ]
}
```
- 동작:
  - 각 `.sql` 파일을 파싱하고 구조를 `analysis/<base>.json`에 저장
- 응답:
  - 모두 성공: `200 OK`, 바디: `OK`
  - 일부 실패: `500`, 바디: `일부 파일 분석 실패 (성공/총개수)`

### 4) 테스트 샘플 처리
- 메서드/경로: `POST /testsample`
- 헤더: `Session-UUID: <uuid>`
- 요청(JSON):
```json
{ "procedureName": "MY_PROCEDURE" }
```
또는
```json
{ "procedureName": ["MY_PROCEDURE", "MY_FUNC"] }
```
- 동작:
  - `src/` 디렉터리에서 해당 이름의 `.sql`을 찾고(대소문자 무시), 분석 결과가 없으면 분석 수행
  - 파일 메타(객체명/내용/파일명/타입/분석존재여부) 목록 반환
- 응답 예시
```json
{
  "successFiles": [
    {
      "objectName": "MY_PROCEDURE",
      "fileContent": "CREATE OR REPLACE PROCEDURE ...",
      "fileName": "MY_PROCEDURE.sql",
      "fileType": "PLSQL",
      "analysisExists": "true"
    }
  ]
}
```

## 이 프로젝트의 내부 구조(아키텍처)
- `controller`
  - `HealthCheckController`: `GET /` 헬스체크
  - `FileUploadController`: 업로드/분석/테스트샘플 API 구현
- `service`
  - `PlSqlFileParserService`: 파일 저장/인코딩 처리/객체명 추출/디렉터리 관리/ANTLR 파싱/JSON 저장의 핵심 로직
- `antlr`
  - `CustomPlSqlListener`: 파스 트리를 순회하며 구문 노드 트리를 구성
  - `Node`: `{type, startLine, endLine, children}` 구조의 트리 노드 및 `toJson()`
  - `plsql/*`: ANTLR가 생성한 Lexer/Parser 소스
- `config`
  - `WebConfig`: CORS 설정

## JSON 결과 예시(요약)
```json
{
  "type": "ROOT",
  "startLine": 0,
  "endLine": 0,
  "children": [
    { "type": "PROCEDURE", "startLine": 1, "endLine": 42, "children": [
      { "type": "SPEC", "startLine": 1, "endLine": 5, "children": [] },
      { "type": "SELECT", "startLine": 10, "endLine": 15, "children": [] },
      { "type": "INSERT", "startLine": 20, "endLine": 25, "children": [] },
      { "type": "EXCEPTION", "startLine": 30, "endLine": 40, "children": [] }
    ]}
  ]
}
```

## 수정/확장 가이드
> 기존 로직 호환성을 유지하려면, 조건 분기 추가 대신 **수정된 단일 로직으로 교체**하는 방식을 권장합니다.

- **새 구문 타입 인식 추가(Listener 확장)**
  1. `CustomPlSqlListener`에서 해당 구문 컨텍스트의 `enter*/exit*` 메서드를 오버라이드합니다.
  2. 진입 시 `enterStatement("NEW_TYPE", ctx.getStart().getLine())`, 종료 시 `exitStatement("NEW_TYPE", ctx.getStop().getLine())`를 호출합니다.
  3. 동일 구문에 대한 기존 타입과 충돌 시, 분기 추가 대신 **기존 타입을 NEW_TYPE으로 교체**하세요.

- **JSON 스키마 확장(Node 확장)**
  1. `Node`에 필요한 필드를 추가합니다(예: `name`, `text`).
  2. 생성자/`toJson()`를 업데이트합니다. 문자열 생성 시 특수문자 이스케이프에 유의하세요.
  3. 대규모 확장이 필요하면 Jackson 등 JSON 라이브러리로 직렬화를 교체하는 것을 고려하세요.

- **객체명 추출 규칙 변경**
  - `PlSqlFileParserService.SQL_OBJECT_PATTERN`을 수정합니다.
  - 예: PACKAGE BODY/PROCEDURE/FUNCTION 외 추가 타입을 인지하려면 정규식을 교체(분기 추가가 아닌 **정규식 자체 교체**)하세요.

- **저장 디렉터리 규칙 변경**
  - `getTargetDirectory`/`getFileType`의 구현을 교체하여 파일명/메타데이터 기반 라우팅 규칙을 정의합니다.
  - 새로운 하위 디렉터리가 필요하면 상수(`PLSQL_DIR`, `DDL_DIR`, `SEQ_DIR`)와 사용처를 동일 기준으로 교체하세요.

- **인코딩 처리 정책 조정**
  - `readFileContent`의 인코딩 시도 순서를 교체하여 환경에 맞게 최적화합니다.

- **용량/업로드 정책 변경**
  - `application.properties`의 `spring.servlet.multipart.*` 값을 교체합니다.

- **CORS/포트 변경**
  - 포트: `server.port` 값 교체
  - CORS: `WebConfig`의 `allowedOrigins` 값을 교체

- **API 확장**
  - `controller`에 엔드포인트를 추가하고, 서비스 로직을 `PlSqlFileParserService`에 배치합니다.
  - 가능하면 기존 요청/응답 스키마를 교체 방식으로 정리하여 클라이언트 호환성을 유지하세요.

## 실행 예시
- 헬스체크
```bash
curl -i http://localhost:8081/
```
- 업로드 (다중 파일)
```bash
curl -X POST http://localhost:8081/fileUpload \
  -H "Session-UUID: 123e4567-e89b-12d3-a456-426614174000" \
  -F files=@/path/PROC1.sql \
  -F files=@/path/DDL_TABLE.sql
```
- 분석 트리거
```bash
curl -X POST http://localhost:8081/parsing \
  -H "Content-Type: application/json" \
  -H "Session-UUID: 123e4567-e89b-12d3-a456-426614174000" \
  -d '{"fileNames":[{"fileName":"PROC1.sql"}]}'
```
- 테스트 샘플
```bash
curl -X POST http://localhost:8081/testsample \
  -H "Content-Type: application/json" \
  -H "Session-UUID: 123e4567-e89b-12d3-a456-426614174000" \
  -d '{"procedureName":["PROC1","FUNC1"]}'
```

## Docker로 실행
### 1) 이미지 빌드(옵션)
이미지 직접 빌드 시
```bash
docker build -t antlr-server:local .
```
- 컨테이너 기본 포트: `8081`
- JAR 빌드: `mvn package -DskipTests`

### 2) Docker Compose
`docker-compose.yml` 예시에서 서비스 이름은 `antlr`이며, 다음 볼륨/환경변수가 사용됩니다.
- 볼륨:
  - `antlr-plsql-data:/app/data/plsql`
  - `antlr-analysis-data:/app/data/analysis`
- 환경변수(컨테이너 내부 참조용):
  - `PLSQL_DIR=/app/data/plsql`
  - `ANALYSIS_DIR=/app/data/analysis`

실행
```bash
docker compose up -d
```
접속: `http://localhost:8081/`

컨테이너 내부 베이스 디렉터리 결정은 기본적으로 `DOCKER_COMPOSE_CONTEXT`가 없으면 `/app` 기준 작업 디렉터리 상위 `data`를 사용합니다. Compose에서 명시적 세션별 경로를 사용하려면 업로드 요청 시 `Session-UUID`를 구분해서 보내면 됩니다.

## 개발 팁
- CORS는 `http://localhost:8080`만 허용되어 있습니다. 다른 오리진에서 호출 시 `WebConfig` 수정 필요
- 파일 인코딩은 UTF-8 → EUC-KR → MS949 순으로 자동 시도
- SQL 객체명 추출 정규식은 `CREATE OR REPLACE (PACKAGE BODY|PACKAGE|PROCEDURE|FUNCTION) [schema.]name` 패턴을 사용

## 트러블슈팅
- 400 Bad Request: `Session-UUID` 헤더 누락 여부 확인
- 413 Payload Too Large: 파일 크기 제한(`3MB`) 초과 여부 확인 또는 설정값 상향
- 404 Not Found(@testsample): 지정한 프로시저명과 실제 파일명이 일치하는지, `src/`에 존재하는지 확인
- 분석 파일이 생성되지 않음: 요청한 파일이 `.sql` 확장자인지 확인, 로그에서 파서 에러 여부 확인

## 라이선스
내부 프로젝트 용도(사내 배포). 별도 라이선스 고지 없으면 본 저장소 외부 배포 금지.
