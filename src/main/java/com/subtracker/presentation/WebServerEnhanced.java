package com.subtracker.presentation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.subtracker.application.ComparisonResult;
import com.subtracker.application.SubscriptionManager;
import com.subtracker.domain.model.*;
import com.subtracker.infrastructure.database.DatabaseManager;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

/**
 * 웹 인터페이스 서버 (데이터 영속성 포함)
 */
@Slf4j
public class WebServerEnhanced {

    private final SubscriptionManager subscriptionManager;
    private final Gson gson;

    public WebServer() {
        // 데이터베이스 초기화
        DatabaseManager.initialize();

        this.subscriptionManager = new SubscriptionManager();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
    }

    /**
     * 서버 시작
     */
    public void start() {
        port(8080);
        staticFiles.location("/public");
        staticFiles.expireTime(600);

        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        setupRoutes();

        log.info("웹 서버 시작: http://localhost:8080");
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

        // CSV 파일 업로드 및 분석 (데이터 저장 포함)
        post("/api/analyze", (req, res) -> {
            res.type("application/json");

            try {
                req.attribute("org.eclipse.jetty.multipartConfig",
                        new MultipartConfigElement("/temp"));

                Part filePart = req.raw().getPart("file");
                boolean hasHeader = Boolean.parseBoolean(req.queryParams("hasHeader"));

                String fileName = filePart.getSubmittedFileName();
                Path tempFile = Files.createTempFile("upload-", ".csv");

                try (InputStream input = filePart.getInputStream()) {
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // 분석 및 저장
                AnalysisHistory history = subscriptionManager.analyzeAndSave(
                        tempFile.toString(), fileName, hasHeader);

                // 요약 정보 생성
                SubscriptionSummary summary = SubscriptionSummary.from(history.getSubscriptions());

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("historyId", history.getId());
                result.put("summary", summary);
                result.put("subscriptions", history.getSubscriptions());
                result.put("transactionCount", history.getTransactionCount());

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

        // 분석 이력 목록 조회
        get("/api/history", (req, res) -> {
            res.type("application/json");

            String limitParam = req.queryParams("limit");
            int limit = limitParam != null ? Integer.parseInt(limitParam) : 10;

            List<AnalysisHistory> histories = subscriptionManager.getRecentHistory(limit);
            return gson.toJson(histories);
        });

        // 특정 분석 이력 상세 조회
        get("/api/history/:id", (req, res) -> {
            res.type("application/json");

            String id = req.params("id");
            AnalysisHistory history = subscriptionManager.getHistoryById(id);

            if (history == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "분석 이력을 찾을 수 없습니다"));
            }

            return gson.toJson(history);
        });

        // 두 분석 이력 비교
        get("/api/compare/:id1/:id2", (req, res) -> {
            res.type("application/json");

            String id1 = req.params("id1");
            String id2 = req.params("id2");

            ComparisonResult comparison = subscriptionManager.compareHistory(id1, id2);

            if (comparison == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "비교할 분석 이력을 찾을 수 없습니다"));
            }

            return gson.toJson(comparison);
        });

        // 특정 서비스의 이력 조회
        get("/api/subscription-history/:serviceName", (req, res) -> {
            res.type("application/json");

            String serviceName = req.params("serviceName");
            List<Subscription> history = subscriptionManager.getSubscriptionHistory(serviceName);

            return gson.toJson(history);
        });

        // 최근 변화 이력 조회
        get("/api/changes", (req, res) -> {
            res.type("application/json");

            String limitParam = req.queryParams("limit");
            int limit = limitParam != null ? Integer.parseInt(limitParam) : 20;

            List<SubscriptionChange> changes = subscriptionManager.getRecentChanges(limit);
            return gson.toJson(changes);
        });

        // 분석 이력 삭제
        delete("/api/history/:id", (req, res) -> {
            res.type("application/json");

            String id = req.params("id");

            try {
                subscriptionManager.deleteHistory(id);
                return gson.toJson(Map.of("success", true, "message", "삭제되었습니다"));
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // 보고서 다운로드
        get("/api/download-report/:id", (req, res) -> {
            String id = req.params("id");
            AnalysisHistory history = subscriptionManager.getHistoryById(id);

            if (history == null) {
                res.status(404);
                return "분석 이력을 찾을 수 없습니다";
            }

            SubscriptionSummary summary = SubscriptionSummary.from(history.getSubscriptions());
            String report = summary.generateReport();

            res.type("text/plain");
            res.header("Content-Disposition",
                    "attachment; filename=subscription_report.txt");

            return report;
        });
    }

    /**
     * 메인 HTML 페이지 (이력 관리 기능 추가)
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
                            max-width: 1400px;
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

                        .tabs {
                            display: flex;
                            gap: 10px;
                            margin-bottom: 20px;
                        }

                        .tab {
                            background: rgba(255, 255, 255, 0.2);
                            color: white;
                            border: none;
                            padding: 12px 30px;
                            border-radius: 10px;
                            font-size: 16px;
                            cursor: pointer;
                            transition: all 0.3s;
                        }

                        .tab.active {
                            background: white;
                            color: #667eea;
                        }

                        .tab:hover {
                            transform: translateY(-2px);
                        }

                        .tab-content {
                            display: none;
                        }

                        .tab-content.active {
                            display: block;
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

                        .btn-secondary {
                            background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
                        }

                        .btn-danger {
                            background: linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%);
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

                        .history-list {
                            background: white;
                            border-radius: 15px;
                            padding: 25px;
                            box-shadow: 0 5px 20px rgba(0, 0, 0, 0.1);
                        }

                        .history-item {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            padding: 15px;
                            border-bottom: 1px solid #eee;
                            cursor: pointer;
                            transition: background 0.2s;
                        }

                        .history-item:hover {
                            background: #f8f9fa;
                        }

                        .history-item:last-child {
                            border-bottom: none;
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

                        .change-item {
                            padding: 12px;
                            border-left: 3px solid #667eea;
                            margin-bottom: 10px;
                            background: #f8f9ff;
                            border-radius: 5px;
                        }

                        .change-type {
                            font-weight: bold;
                            color: #667eea;
                            margin-bottom: 5px;
                        }

                        .change-date {
                            font-size: 0.9em;
                            color: #999;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <header>
                            <h1>📊 구독 서비스 관리 도우미</h1>
                            <p>구독 분석 및 이력 추적 시스템</p>
                        </header>

                        <div class="tabs">
                            <button class="tab active" onclick="showTab('analyze')">📁 새 분석</button>
                            <button class="tab" onclick="showTab('history')">📜 분석 이력</button>
                            <button class="tab" onclick="showTab('changes')">🔄 변화 추적</button>
                        </div>

                        <!-- 새 분석 탭 -->
                        <div id="analyze-tab" class="tab-content active">
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

                            <div id="resultsSection" style="display: none;">
                                <div class="summary-card">
                                    <h2>📈 구독 현황 요약</h2>
                                    <div class="stat-grid" id="statsGrid"></div>
                                    <div style="text-align: center; margin-top: 20px;">
                                        <button class="btn" onclick="downloadReport()">
                                            📥 보고서 다운로드
                                        </button>
                                    </div>
                                </div>

                                <div class="summary-card">
                                    <h2>💳 구독 서비스 목록</h2>
                                    <div id="subscriptionList"></div>
                                </div>
                            </div>
                        </div>

                        <!-- 분석 이력 탭 -->
                        <div id="history-tab" class="tab-content">
                            <div class="history-list">
                                <h2>📜 분석 이력</h2>
                                <div id="historyList"></div>
                            </div>
                        </div>

                        <!-- 변화 추적 탭 -->
                        <div id="changes-tab" class="tab-content">
                            <div class="summary-card">
                                <h2>🔄 구독 변화 이력</h2>
                                <div id="changesList"></div>
                            </div>
                        </div>
                    </div>

                    <script>
                        let currentHistoryId = null;

                        // 탭 전환
                        function showTab(tabName) {
                            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

                            event.target.classList.add('active');
                            document.getElementById(tabName + '-tab').classList.add('active');

                            if (tabName === 'history') {
                                loadHistory();
                            } else if (tabName === 'changes') {
                                loadChanges();
                            }
                        }

                        // 파일 업로드
                        const uploadArea = document.getElementById('uploadArea');
                        const fileInput = document.getElementById('fileInput');

                        uploadArea.addEventListener('click', () => fileInput.click());
                        fileInput.addEventListener('change', (e) => {
                            if (e.target.files[0]) uploadFile(e.target.files[0]);
                        });

                        uploadArea.addEventListener('dragover', (e) => {
                            e.preventDefault();
                            uploadArea.style.borderColor = '#667eea';
                        });

                        uploadArea.addEventListener('drop', (e) => {
                            e.preventDefault();
                            const file = e.dataTransfer.files[0];
                            if (file && file.name.endsWith('.csv')) {
                                uploadFile(file);
                            }
                        });

                        async function uploadFile(file) {
                            const formData = new FormData();
                            formData.append('file', file);
                            const hasHeader = document.getElementById('hasHeader').checked;

                            document.getElementById('loading').style.display = 'block';
                            document.getElementById('resultsSection').style.display = 'none';

                            try {
                                const response = await fetch(`/api/analyze?hasHeader=${hasHeader}`, {
                                    method: 'POST',
                                    body: formData
                                });

                                const data = await response.json();

                                if (data.success) {
                                    currentHistoryId = data.historyId;
                                    displayResults(data);
                                } else {
                                    alert('분석 실패: ' + data.error);
                                }
                            } catch (error) {
                                alert('오류 발생: ' + error.message);
                            } finally {
                                document.getElementById('loading').style.display = 'none';
                            }
                        }

                        function displayResults(data) {
                            const summary = data.summary;
                            const subscriptions = data.subscriptions;

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

                            const subscriptionList = document.getElementById('subscriptionList');
                            subscriptionList.innerHTML = subscriptions.map(sub => `
                                <div class="subscription-item">
                                    <div>
                                        <div style="font-weight: 600; font-size: 1.1em;">${sub.serviceName}</div>
                                        <small style="color: #999;">
                                            ${sub.billingCycle.korean} · 총 ${sub.transactionCount}회 결제
                                        </small>
                                    </div>
                                    <div style="display: flex; gap: 20px; align-items: center;">
                                        <span class="badge active">${sub.status.korean}</span>
                                        <div style="font-size: 1.2em; color: #667eea; font-weight: bold;">
                                            ₩${sub.monthlyAmount.toLocaleString()}/월
                                        </div>
                                    </div>
                                </div>
                            `).join('');

                            document.getElementById('resultsSection').style.display = 'block';
                        }

                        // 분석 이력 로드
                        async function loadHistory() {
                            try {
                                const response = await fetch('/api/history?limit=20');
                                const histories = await response.json();

                                const historyList = document.getElementById('historyList');
                                historyList.innerHTML = histories.map(h => `
                                    <div class="history-item" onclick="viewHistory('${h.id}')">
                                        <div>
                                            <div style="font-weight: 600;">${h.fileName || '분석 결과'}</div>
                                            <small style="color: #999;">
                                                ${new Date(h.analysisDate).toLocaleString('ko-KR')} ·
                                                ${h.subscriptionCount}개 구독 · ₩${h.monthlyTotal.toLocaleString()}/월
                                            </small>
                                        </div>
                                        <button class="btn btn-danger" onclick="deleteHistory('${h.id}', event)">삭제</button>
                                    </div>
                                `).join('');
                            } catch (error) {
                                console.error('이력 로드 실패:', error);
                            }
                        }

                        // 변화 이력 로드
                        async function loadChanges() {
                            try {
                                const response = await fetch('/api/changes?limit=30');
                                const changes = await response.json();

                                const changesList = document.getElementById('changesList');
                                changesList.innerHTML = changes.map(c => `
                                    <div class="change-item">
                                        <div class="change-type">${c.changeType.korean}</div>
                                        <div>${c.notes}</div>
                                        ${c.oldValue && c.newValue ? `<div>${c.oldValue} → ${c.newValue}</div>` : ''}
                                        <div class="change-date">${new Date(c.changeDate).toLocaleString('ko-KR')}</div>
                                    </div>
                                `).join('');
                            } catch (error) {
                                console.error('변화 이력 로드 실패:', error);
                            }
                        }

                        function viewHistory(id) {
                            // 상세 보기 구현
                            alert('분석 이력 상세 보기: ' + id);
                        }

                        async function deleteHistory(id, event) {
                            event.stopPropagation();
                            if (!confirm('정말 삭제하시겠습니까?')) return;

                            try {
                                await fetch(`/api/history/${id}`, { method: 'DELETE' });
                                loadHistory();
                            } catch (error) {
                                alert('삭제 실패: ' + error.message);
                            }
                        }

                        function downloadReport() {
                            if (currentHistoryId) {
                                window.location.href = `/api/download-report/${currentHistoryId}`;
                            }
                        }
                    </script>
                </body>
                </html>
                """;
    }
}