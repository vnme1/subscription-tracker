# 📊 구독 서비스 관리 도우미 (Subscription Tracker) v1.1

> 은행/카드 거래 내역에서 구독 서비스를 자동으로 감지하고 이력을 추적하는 Java 애플리케이션

## 🎯 주요 기능

### ✅ 핵심 기능

- **자동 구독 감지**: CSV 형식의 거래 내역에서 정기 결제 패턴을 자동으로 분석
- **구독 분류**: 월간, 분기, 반기, 연간 등 결제 주기 자동 판별
- **지출 분석**: 월별/연간 구독 비용 계산 및 통계
- **취소 추천**: 장기간 미사용 구독 서비스 식별
- **결제 예측**: 다음 결제 예정일 및 금액 예측
- **보고서 생성**: 구독 현황 요약 보고서 내보내기

### 🆕 NEW! 데이터 영속성 기능 (v1.1)

- **분석 이력 저장**: 모든 분석 결과를 H2 데이터베이스에 자동 저장
- **이력 추적**: 과거 분석 결과 조회 및 관리
- **변화 감지**: 구독 서비스의 금액, 상태, 주기 변화 자동 추적
- **이력 비교**: 두 시점 간 구독 변화 비교 분석
- **시계열 분석**: 구독 지출 트렌드 추적

## 🚀 시작하기

### 필요 환경

- Java 17 이상
- Maven 3.6 이상
- VSCode (또는 IntelliJ IDEA)

### 설치 및 실행

1. **프로젝트 클론**

```bash
git clone https://github.com/yourusername/subscription-tracker.git
cd subscription-tracker
```

2. **의존성 설치 및 빌드**

```bash
mvn clean install
```

3. **실행**

#### 🌐 방법 1: 웹 인터페이스 (권장)

```bash
# Windows
run-web.bat

# Mac/Linux
./run-web.sh

# 또는 직접 실행
mvn exec:java -Dexec.mainClass="com.subtracker.SubscriptionTrackerApplication"
# 메뉴에서 1번 선택
```

브라우저에서 http://localhost:8080 접속

**웹 인터페이스 기능:**

- 📁 새 분석: CSV 파일 업로드 및 실시간 분석
- 📜 분석 이력: 과거 분석 결과 조회 및 관리
- 🔄 변화 추적: 구독 변화 이력 확인

#### 💻 방법 2: 콘솔 인터페이스

```bash
mvn exec:java -Dexec.mainClass="com.subtracker.SubscriptionTrackerApplication"
# 메뉴에서 2번 선택
```

## 📝 사용법

### 1. CSV 파일 준비

은행/카드사에서 거래 내역을 CSV 형식으로 다운로드합니다.
필수 컬럼: 거래일자, 가맹점명, 금액

**CSV 예시:**

```csv
거래일자,가맹점명,금액,카테고리,설명,카드번호
2024-01-05,넷플릭스,17000,엔터테인먼트,월간구독,1234****5678
2024-01-10,스포티파이,9900,엔터테인먼트,프리미엄,1234****5678
```

### 2. 웹 인터페이스 사용

1. 브라우저에서 http://localhost:8080 접속
2. **새 분석** 탭에서 CSV 파일 업로드
3. 실시간으로 분석 결과 확인
4. **분석 이력** 탭에서 과거 분석 조회
5. **변화 추적** 탭에서 구독 변화 확인

### 3. 이력 비교하기

```bash
# API로 두 분석 이력 비교
GET http://localhost:8080/api/compare/{id1}/{id2}
```

## 🏗️ 프로젝트 구조

```
subscription-tracker/
├── src/main/java/com/subtracker/
│   ├── application/
│   │   ├── SubscriptionManager.java        # 비즈니스 로직
│   │   └── ComparisonResult.java           # 이력 비교
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Transaction.java            # 거래 내역
│   │   │   ├── Subscription.java           # 구독 정보
│   │   │   ├── SubscriptionSummary.java    # 요약 정보
│   │   │   ├── AnalysisHistory.java        # 분석 이력 (NEW)
│   │   │   └── SubscriptionChange.java     # 변화 추적 (NEW)
│   │   └── service/
│   │       ├── CsvParser.java              # CSV 파싱
│   │       └── SubscriptionDetector.java   # 구독 감지
│   ├── infrastructure/
│   │   ├── database/
│   │   │   └── DatabaseManager.java        # DB 연결 관리 (NEW)
│   │   └── repository/
│   │       ├── AnalysisHistoryRepository.java      # 이력 저장소 (NEW)
│   │       ├── SubscriptionRepository.java         # 구독 저장소 (NEW)
│   │       └── SubscriptionChangeRepository.java   # 변화 저장소 (NEW)
│   ├── presentation/
│   │   ├── ConsoleInterface.java           # 콘솔 UI
│   │   ├── WebServerEnhanced.java          # 웹 서버 (NEW)
│   │   ├── LocalDateAdapter.java           # JSON 변환
│   │   └── LocalDateTimeAdapter.java       # JSON 변환 (NEW)
│   └── SubscriptionTrackerApplication.java # 메인 클래스
├── data/                                    # H2 데이터베이스 파일 (자동 생성)
└── pom.xml                                  # Maven 설정
```

## 🔧 구독 감지 알고리즘

### 감지 기준

- **최소 발생 횟수**: 2회 이상
- **금액 일관성**: 오차 범위 5% 이내
- **주기 규칙성**: 일정한 간격으로 발생

### 결제 주기 분류

- **월간**: 30일 ± 5일
- **분기**: 90일 ± 10일
- **반기**: 180일 ± 15일
- **연간**: 365일 ± 25일

## 💾 데이터베이스 스키마

### analysis_history (분석 이력)

- 분석 ID, 날짜, 파일명
- 거래 건수, 구독 개수
- 월/연간 총액

### subscription_history (구독 이력)

- 구독 정보 (서비스명, 금액, 주기, 상태)
- 최초/최근/다음 결제일
- 거래 횟수, 총 지출액

### subscription_changes (변화 추적)

- 변화 유형 (신규, 금액변경, 상태변경, 취소 등)
- 이전/이후 값
- 변화 날짜 및 메모

## 🛠️ 기술 스택

- **Language**: Java 17
- **Build Tool**: Maven
- **Database**: H2 (임베디드)
- **Connection Pool**: HikariCP
- **Web Server**: SparkJava
- **Libraries**:
  - OpenCSV: CSV 파싱
  - Gson: JSON 처리
  - Lombok: 보일러플레이트 코드 감소
  - SLF4J + Logback: 로깅
  - JUnit 5: 테스트

## 📊 API 엔드포인트

### 분석

- `POST /api/analyze` - CSV 파일 분석 및 저장
- `GET /api/subscriptions` - 현재 구독 목록
- `GET /api/summary` - 요약 정보

### 이력 관리

- `GET /api/history` - 분석 이력 목록
- `GET /api/history/:id` - 특정 이력 조회
- `DELETE /api/history/:id` - 이력 삭제

### 비교 및 추적

- `GET /api/compare/:id1/:id2` - 두 이력 비교
- `GET /api/subscription-history/:serviceName` - 서비스별 이력
- `GET /api/changes` - 최근 변화 이력

### 보고서

- `GET /api/download-report/:id` - 보고서 다운로드

## 🎨 다음 단계

### ✅ 완료된 기능

- [x] 핵심 기능 (CSV 파싱, 구독 감지, 분석)
- [x] 웹 인터페이스
- [x] **데이터 영속성 (H2 데이터베이스)**
- [x] **분석 이력 저장**
- [x] **변화 추적**
- [x] **이력 비교**

### 🚧 진행 예정

- [ ] Option B: 시각화 개선 (Chart.js 그래프)
- [ ] Option C: 스마트 기능 (카테고리 분류, 절약 추천)
- [ ] Option D: CSV 포맷 확장 (다양한 은행 지원)

## 📄 라이선스

MIT License

## 👥 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

**v1.1 업데이트 내용:**

- ✨ H2 데이터베이스 연동으로 영구 저장
- 📊 분석 이력 관리 기능
- 🔄 구독 변화 자동 추적
- 📈 이력 비교 분석
- 🌐 웹 인터페이스 개선 (이력 탭 추가)
