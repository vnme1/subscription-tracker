# 📊 구독 서비스 관리 도우미 (Subscription Tracker)

> 은행/카드 거래 내역에서 구독 서비스를 자동으로 감지하고 관리하는 Java 애플리케이션

## 🎯 주요 기능

- **자동 구독 감지**: CSV 형식의 거래 내역에서 정기 결제 패턴을 자동으로 분석
- **구독 분류**: 월간, 분기, 반기, 연간 등 결제 주기 자동 판별
- **지출 분석**: 월별/연간 구독 비용 계산 및 통계
- **취소 추천**: 장기간 미사용 구독 서비스 식별
- **결제 예측**: 다음 결제 예정일 및 금액 예측
- **보고서 생성**: 구독 현황 요약 보고서 내보내기

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

```bash
# 방법 1: Maven으로 실행
mvn exec:java -Dexec.mainClass="com.subtracker.SubscriptionTrackerApplication"

# 방법 2: JAR 파일 실행
java -jar target/subscription-tracker-1.0.0-jar-with-dependencies.jar
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

### 2. 애플리케이션 실행 및 분석

1. 애플리케이션 실행
2. 메뉴에서 "1. CSV 파일 불러오기" 선택
3. CSV 파일 경로 입력
4. "2. 구독 서비스 분석" 실행
5. "3. 요약 보고서 보기"로 결과 확인

### 3. 샘플 데이터 테스트

```bash
# 샘플 데이터 위치
src/main/resources/sample-data/sample_transactions.csv
```

## 🏗️ 프로젝트 구조

```
subscription-tracker/
├── src/main/java/com/subtracker/
│   ├── domain/
│   │   ├── model/          # 도메인 모델 (Transaction, Subscription)
│   │   └── service/        # 비즈니스 로직 (Parser, Detector)
│   ├── presentation/       # UI 레이어 (ConsoleInterface)
│   └── SubscriptionTrackerApplication.java  # 메인 클래스
├── src/main/resources/
│   └── sample-data/        # 샘플 데이터
└── pom.xml                 # Maven 설정
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

## 📈 확장 가능성

### 단기 개선사항

- [ ] REST API 추가 (Spring Boot)
- [ ] 웹 UI 개발
- [ ] 데이터베이스 연동
- [ ] 다양한 은행 CSV 포맷 지원

### 장기 로드맵

- [ ] 머신러닝 기반 구독 예측
- [ ] 구독 서비스별 사용 빈도 분석
- [ ] 예산 관리 및 알림 기능
- [ ] 다중 사용자 지원

## 🛠️ 기술 스택

- **Language**: Java 17
- **Build Tool**: Maven
- **Libraries**:
  - OpenCSV: CSV 파싱
  - Lombok: 보일러플레이트 코드 감소
  - SLF4J + Logback: 로깅
  - JUnit 5: 테스트

## 📄 라이선스

MIT License

## 👥 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📞 문의

프로젝트에 대한 문의사항이 있으시면 이슈를 등록해주세요.

---
