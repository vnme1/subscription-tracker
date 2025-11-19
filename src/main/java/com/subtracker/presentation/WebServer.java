package com.subtracker.presentation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.subtracker.domain.model.Subscription;
import com.subtracker.domain.model.SubscriptionSummary;
import com.subtracker.domain.model.Transaction;
import com.subtracker.domain.service.CsvParser;
import com.subtracker.domain.service.SubscriptionDetector;
import lombok.extern.slf4j.Slf4j;
// import spark.Request;
// import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

/**
 * 웹 인터페이스를 제공하는 서버
 */
@Slf4j
public class WebServer {

    private final CsvParser csvParser;
    private final SubscriptionDetector detector;
    private final Gson gson;

    // 현재 세션의 데이터 (실제로는 세션별로 관리해야 함)
    private List<Transaction> currentTransactions;
    private List<Subscription> currentSubscriptions;

    public WebServer() {
        this.csvParser = new CsvParser();
        this.detector = new SubscriptionDetector();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .setPrettyPrinting()
                .create();
    }

    /**
     * 서버 시작
     */
    public void start() {
        // 포트 설정
        port(8080);

        // 정적 파일 경로
        staticFiles.location("/public");
        staticFiles.expireTime(600);

        // CORS 설정
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        // 라우트 설정
        setupRoutes();

        // 서버 시작 메시지
        log.info("웹 서버가 시작되었습니다: http://localhost:8080");
        System.out.println("\n🌐 웹 브라우저에서 접속하세요: http://localhost:8080\n");
    }

    /**
     * API 라우트 설정
     */
    private void setupRoutes() {
        // 메인 페이지
        get("/", (req, res) -> {
            res.type("text/html");
            return getIndexHtml();
        });

        // CSV 파일 업로드 및 분석
        post("/api/analyze", (req, res) -> {
            res.type("application/json");

            try {
                // 파일 업로드 처리
                req.attribute("org.eclipse.jetty.multipartConfig",
                        new MultipartConfigElement("/temp"));

                Part filePart = req.raw().getPart("file");
                boolean hasHeader = Boolean.parseBoolean(req.queryParams("hasHeader"));

                // 임시 파일로 저장
                Path tempFile = Files.createTempFile("upload-", ".csv");
                try (InputStream input = filePart.getInputStream()) {
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // CSV 파싱
                currentTransactions = csvParser.parseTransactions(
                        tempFile.toString(), hasHeader);

                // 구독 감지
                currentSubscriptions = detector.detectSubscriptions(currentTransactions);

                // 요약 정보 생성
                SubscriptionSummary summary = SubscriptionSummary.from(currentSubscriptions);

                // 결과 반환
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("summary", summary);
                result.put("subscriptions", currentSubscriptions);
                result.put("transactionCount", currentTransactions.size());

                // 임시 파일 삭제
                Files.deleteIfExists(tempFile);

                return gson.toJson(result);

            } catch (Exception e) {
                log.error("파일 분석 중 오류", e);
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", e.getMessage());
                return gson.toJson(error);
            }
        });

        // 현재 구독 목록 조회
        get("/api/subscriptions", (req, res) -> {
            res.type("application/json");

            if (currentSubscriptions == null) {
                return gson.toJson(Map.of("error", "먼저 CSV 파일을 업로드해주세요"));
            }

            return gson.toJson(currentSubscriptions);
        });

        // 요약 보고서 조회
        get("/api/summary", (req, res) -> {
            res.type("application/json");

            if (currentSubscriptions == null) {
                return gson.toJson(Map.of("error", "먼저 CSV 파일을 업로드해주세요"));
            }

            SubscriptionSummary summary = SubscriptionSummary.from(currentSubscriptions);
            return gson.toJson(summary);
        });

        // 보고서 다운로드
        get("/api/download-report", (req, res) -> {
            if (currentSubscriptions == null) {
                res.status(400);
                return "No data available";
            }

            SubscriptionSummary summary = SubscriptionSummary.from(currentSubscriptions);
            String report = summary.generateReport();

            res.type("text/plain");
            res.header("Content-Disposition",
                    "attachment; filename=subscription_report.txt");

            return report;
        });
    }

    /**
     * 메인 HTML 페이지
     */
    private String getIndexHtml() {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>구독 서비스 관리 도우미</title>
                    <style>
                        * {
                            margin: 0;
                            padding: 0;
                            box-sizing: border-box;
                        }

                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            min-height: 100vh;
                            padding: 20px;
                        }

                        .container {
                            max-width: 1200px;
                            margin: 0 auto;
                        }

                        header {
                            text-align: center;
                            color: white;
                            margin-bottom: 30px;
                        }

                        header h1 {
                            font-size: 2.5em;
                            margin-bottom: 10px;
                        }

                        header p {
                            font-size: 1.2em;
                            opacity: 0.9;
                        }

                        .upload-section {
                            background: white;
                            border-radius: 15px;
                            padding: 30px;
                            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
                            margin-bottom: 30px;
                        }

                        .upload-area {
                            border: 3px dashed #ddd;
                            border-radius: 10px;
                            padding: 40px;
                            text-align: center;
                            transition: all 0.3s;
                            cursor: pointer;
                        }

                        .upload-area:hover {
                            border-color: #667eea;
                            background: #f8f9ff;
                        }

                        .upload-area.dragging {
                            border-color: #667eea;
                            background: #f0f2ff;
                        }

                        .upload-icon {
                            font-size: 48px;
                            margin-bottom: 20px;
                        }

                        .file-input {
                            display: none;
                        }

                        .checkbox-group {
                            margin: 20px 0;
                            text-align: center;
                        }

                        .btn {
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            color: white;
                            border: none;
                            padding: 12px 30px;
                            border-radius: 25px;
                            font-size: 16px;
                            cursor: pointer;
                            transition: transform 0.2s;
                            margin: 5px;
                        }

                        .btn:hover {
                            transform: translateY(-2px);
                        }

                        .btn:disabled {
                            opacity: 0.5;
                            cursor: not-allowed;
                        }

                        .results-section {
                            display: none;
                        }

                        .summary-card {
                            background: white;
                            border-radius: 15px;
                            padding: 25px;
                            margin-bottom: 20px;
                            box-shadow: 0 5px 20px rgba(0, 0, 0, 0.1);
                        }

                        .stat-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                            gap: 20px;
                            margin: 20px 0;
                        }

                        .stat-card {
                            background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
                            padding: 20px;
                            border-radius: 10px;
                            text-align: center;
                        }

                        .stat-value {
                            font-size: 2em;
                            font-weight: bold;
                            color: #333;
                        }

                        .stat-label {
                            color: #666;
                            margin-top: 5px;
                        }

                        .subscription-list {
                            background: white;
                            border-radius: 15px;
                            padding: 25px;
                            box-shadow: 0 5px 20px rgba(0, 0, 0, 0.1);
                        }

                        .subscription-item {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            padding: 15px;
                            border-bottom: 1px solid #eee;
                        }

                        .subscription-item:last-child {
                            border-bottom: none;
                        }

                        .subscription-name {
                            font-weight: 600;
                            font-size: 1.1em;
                        }

                        .subscription-details {
                            display: flex;
                            gap: 20px;
                            align-items: center;
                        }

                        .price {
                            font-size: 1.2em;
                            color: #667eea;
                            font-weight: bold;
                        }

                        .badge {
                            padding: 4px 12px;
                            border-radius: 15px;
                            font-size: 0.9em;
                            font-weight: 500;
                        }

                        .badge.active {
                            background: #d4f4dd;
                            color: #22c55e;
                        }

                        .badge.inactive {
                            background: #fee2e2;
                            color: #ef4444;
                        }

                        .badge.pending {
                            background: #fef3c7;
                            color: #f59e0b;
                        }

                        .loading {
                            display: none;
                            text-align: center;
                            padding: 20px;
                        }

                        .spinner {
                            border: 3px solid #f3f3f3;
                            border-top: 3px solid #667eea;
                            border-radius: 50%;
                            width: 40px;
                            height: 40px;
                            animation: spin 1s linear infinite;
                            margin: 0 auto;
                        }

                        @keyframes spin {
                            0% { transform: rotate(0deg); }
                            100% { transform: rotate(360deg); }
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <header>
                            <h1>📊 구독 서비스 관리 도우미</h1>
                            <p>카드 내역을 업로드하면 구독 서비스를 자동으로 찾아드립니다</p>
                        </header>

                        <div class="upload-section">
                            <div class="upload-area" id="uploadArea">
                                <div class="upload-icon">📁</div>
                                <h3>CSV 파일을 드래그하거나 클릭하여 업로드</h3>
                                <p style="margin-top: 10px; color: #999;">
                                    은행/카드사에서 다운받은 거래내역 CSV 파일
                                </p>
                            </div>
                            <input type="file" id="fileInput" class="file-input" accept=".csv">

                            <div class="checkbox-group">
                                <label>
                                    <input type="checkbox" id="hasHeader" checked>
                                    첫 줄이 헤더입니다
                                </label>
                            </div>

                            <div class="loading" id="loading">
                                <div class="spinner"></div>
                                <p style="margin-top: 10px;">분석 중입니다...</p>
                            </div>
                        </div>

                        <div class="results-section" id="resultsSection">
                            <div class="summary-card">
                                <h2>📈 구독 현황 요약</h2>
                                <div class="stat-grid" id="statsGrid">
                                    <!-- 통계 카드들이 여기에 추가됨 -->
                                </div>
                                <div style="text-align: center; margin-top: 20px;">
                                    <button class="btn" onclick="downloadReport()">
                                        📥 보고서 다운로드
                                    </button>
                                </div>
                            </div>

                            <div class="subscription-list">
                                <h2>💳 구독 서비스 목록</h2>
                                <div id="subscriptionList">
                                    <!-- 구독 목록이 여기에 추가됨 -->
                                </div>
                            </div>
                        </div>
                    </div>

                    <script>
                        const uploadArea = document.getElementById('uploadArea');
                        const fileInput = document.getElementById('fileInput');
                        const loading = document.getElementById('loading');
                        const resultsSection = document.getElementById('resultsSection');

                        // 클릭으로 파일 선택
                        uploadArea.addEventListener('click', () => {
                            fileInput.click();
                        });

                        // 파일 선택 시
                        fileInput.addEventListener('change', (e) => {
                            const file = e.target.files[0];
                            if (file) {
                                uploadFile(file);
                            }
                        });

                        // 드래그 앤 드롭
                        uploadArea.addEventListener('dragover', (e) => {
                            e.preventDefault();
                            uploadArea.classList.add('dragging');
                        });

                        uploadArea.addEventListener('dragleave', () => {
                            uploadArea.classList.remove('dragging');
                        });

                        uploadArea.addEventListener('drop', (e) => {
                            e.preventDefault();
                            uploadArea.classList.remove('dragging');

                            const file = e.dataTransfer.files[0];
                            if (file && file.name.endsWith('.csv')) {
                                uploadFile(file);
                            } else {
                                alert('CSV 파일만 업로드 가능합니다.');
                            }
                        });

                        // 파일 업로드 및 분석
                        async function uploadFile(file) {
                            const formData = new FormData();
                            formData.append('file', file);

                            const hasHeader = document.getElementById('hasHeader').checked;

                            loading.style.display = 'block';
                            resultsSection.style.display = 'none';

                            try {
                                const response = await fetch(`/api/analyze?hasHeader=${hasHeader}`, {
                                    method: 'POST',
                                    body: formData
                                });

                                const data = await response.json();

                                if (data.success) {
                                    displayResults(data);
                                } else {
                                    alert('분석 실패: ' + data.error);
                                }
                            } catch (error) {
                                alert('오류 발생: ' + error.message);
                            } finally {
                                loading.style.display = 'none';
                            }
                        }

                        // 결과 표시
                        function displayResults(data) {
                            const summary = data.summary;
                            const subscriptions = data.subscriptions;

                            // 통계 표시
                            const statsGrid = document.getElementById('statsGrid');
                            statsGrid.innerHTML = `
                                <div class="stat-card">
                                    <div class="stat-value">${data.transactionCount}</div>
                                    <div class="stat-label">총 거래 건수</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-value">${summary.totalSubscriptions}</div>
                                    <div class="stat-label">발견된 구독</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-value">₩${summary.monthlyTotal.toLocaleString()}</div>
                                    <div class="stat-label">월 지출액</div>
                                </div>
                                <div class="stat-card">
                                    <div class="stat-value">₩${summary.annualProjection.toLocaleString()}</div>
                                    <div class="stat-label">연간 예상액</div>
                                </div>
                            `;

                            // 구독 목록 표시
                            const subscriptionList = document.getElementById('subscriptionList');
                            subscriptionList.innerHTML = subscriptions.map(sub => {
                                const statusBadge = getStatusBadge(sub.status);
                                return `
                                    <div class="subscription-item">
                                        <div>
                                            <div class="subscription-name">${sub.serviceName}</div>
                                            <small style="color: #999;">
                                                ${sub.billingCycle.korean} ·
                                                총 ${sub.transactionCount}회 결제
                                            </small>
                                        </div>
                                        <div class="subscription-details">
                                            <span class="badge ${statusBadge.class}">${statusBadge.text}</span>
                                            <div class="price">₩${sub.monthlyAmount.toLocaleString()}/월</div>
                                        </div>
                                    </div>
                                `;
                            }).join('');

                            resultsSection.style.display = 'block';
                        }

                        // 상태 배지 가져오기
                        function getStatusBadge(status) {
                            switch(status) {
                                case 'ACTIVE':
                                    return { class: 'active', text: '활성' };
                                case 'INACTIVE':
                                    return { class: 'inactive', text: '비활성' };
                                case 'PENDING':
                                    return { class: 'pending', text: '대기중' };
                                default:
                                    return { class: '', text: status };
                            }
                        }

                        // 보고서 다운로드
                        function downloadReport() {
                            window.location.href = '/api/download-report';
                        }
                    </script>
                </body>
                </html>
                """;
    }

    /**
     * 메인 메소드 - 웹 서버만 실행
     */
    public static void main(String[] args) {
        WebServer server = new WebServer();
        server.start();
    }
}