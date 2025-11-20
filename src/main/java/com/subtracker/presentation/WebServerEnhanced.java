package com.subtracker.presentation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.subtracker.application.ComparisonResult;
import com.subtracker.application.SubscriptionManager;
import com.subtracker.config.AppConfig;
import com.subtracker.domain.model.*;
import com.subtracker.infrastructure.database.DatabaseManager;
import com.subtracker.presentation.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static spark.Spark.*;

/**
 * 웹 인터페이스 서버 (동적 로고 및 색상 지원)
 */
@Slf4j
public class WebServerEnhanced {

    private final SubscriptionManager subscriptionManager;
    private final Gson gson;

    private static final long MAX_FILE_SIZE = AppConfig.getUploadMaxSize();
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(
            Arrays.asList(AppConfig.getUploadAllowedExtensions()));
    private static final String TEMP_UPLOAD_DIR = AppConfig.getUploadTempDir();

    public WebServerEnhanced() {
        DatabaseManager.initialize();
        this.subscriptionManager = new SubscriptionManager();
        this.gson = createGsonInstance();
    }

    private Gson createGsonInstance() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
    }

    public void start() {
        port(AppConfig.getServerPort());
        staticFiles.location("/public");
        staticFiles.expireTime(600);

        configureCors();
        setupRoutes();

        log.info("웹 서버 시작: http://localhost:{}", AppConfig.getServerPort());
        System.out.println("\n🌐 웹 브라우저에서 접속하세요: http://localhost:" + AppConfig.getServerPort() + "\n");
    }

    private void configureCors() {
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        options("/*", (req, res) -> {
            res.status(200);
            return "OK";
        });
    }

    public void stop() {
        spark.Spark.stop();
        DatabaseManager.shutdown();
        log.info("웹 서버 종료");
    }

    private void setupRoutes() {
        // 메인 페이지
        get("/", (req, res) -> {
            res.type("text/html; charset=utf-8");
            return getIndexHtml();
        });

        // CSV 파일 업로드 및 분석
        post("/api/analyze", (req, res) -> {
            res.type("application/json; charset=utf-8");
            return handleFileUpload(req);
        });

        // 분석 이력 API
        get("/api/history", (req, res) -> {
            res.type("application/json; charset=utf-8");
            return handleGetHistory(req);
        });

        get("/api/history/:id", (req, res) -> {
            res.type("application/json; charset=utf-8");
            return handleGetHistoryById(req, res);
        });

        // 이력 비교
        get("/api/compare/:id1/:id2", (req, res) -> {
            res.type("application/json; charset=utf-8");
            return handleCompareHistory(req, res);
        });

        // 서비스별 이력
        get("/api/subscription-history/:serviceName", (req, res) -> {
            res.type("application/json; charset=utf-8");
            String serviceName = req.params("serviceName");
            List<Subscription> history = subscriptionManager.getSubscriptionHistory(serviceName);
            return gson.toJson(ApiResponse.success(history));
        });

        // 변화 이력
        get("/api/changes", (req, res) -> {
            res.type("application/json; charset=utf-8");
            return handleGetChanges(req);
        });

        // 이력 삭제
        delete("/api/history/:id", (req, res) -> {
            res.type("application/json; charset=utf-8");
            return handleDeleteHistory(req, res);
        });

        // 보고서 다운로드
        get("/api/download-report/:id", (req, res) -> {
            return handleDownloadReport(req, res);
        });

        // 에러 핸들링
        exception(Exception.class, (e, req, res) -> {
            log.error("서버 오류 발생", e);
            res.status(500);
            res.type("application/json; charset=utf-8");
            res.body(gson.toJson(ApiResponse.error("서버 오류가 발생했습니다")));
        });
    }

    /**
     * 파일 업로드 검증
     */
    private void validateUploadedFile(Part filePart) throws IOException {
        // 1. 파일 크기 검증
        if (filePart.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("파일 크기가 너무 큽니다 (최대: %dMB)", MAX_FILE_SIZE / 1024 / 1024));
        }

        if (filePart.getSize() == 0) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다");
        }

        // 2. 파일명 검증
        String fileName = filePart.getSubmittedFileName();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("파일 이름이 없습니다");
        }

        // 3. 확장자 검증
        String lowerFileName = fileName.toLowerCase();
        boolean hasValidExtension = ALLOWED_EXTENSIONS.stream()
                .anyMatch(ext -> lowerFileName.endsWith("." + ext.toLowerCase()));

        if (!hasValidExtension) {
            throw new IllegalArgumentException(
                    "CSV 파일만 업로드 가능합니다 (허용: " + String.join(", ", ALLOWED_EXTENSIONS) + ")");
        }

        // 4. MIME 타입 검증 (경고만)
        String contentType = filePart.getContentType();
        if (contentType != null &&
                !contentType.contains("text/csv") &&
                !contentType.contains("application/csv") &&
                !contentType.contains("text/plain") &&
                !contentType.contains("application/vnd.ms-excel")) {
            log.warn("예상치 못한 Content-Type: {} (파일: {})", contentType, fileName);
        }

        log.info("파일 검증 완료: {} ({}bytes)", fileName, filePart.getSize());
    }

    /**
     * 파일 업로드 처리 (검증 강화)
     */
    private String handleFileUpload(spark.Request req) {
        Path tempFile = null;
        try {
            req.attribute("org.eclipse.jetty.multipartConfig",
                    new MultipartConfigElement(TEMP_UPLOAD_DIR));

            Part filePart = req.raw().getPart("file");

            // 파일 검증
            validateUploadedFile(filePart);

            boolean hasHeader = Boolean.parseBoolean(req.queryParams("hasHeader"));

            String fileName = filePart.getSubmittedFileName();
            tempFile = Files.createTempFile("upload-", ".csv");

            try (InputStream input = filePart.getInputStream()) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // 분석 실행
            AnalysisHistory history = subscriptionManager.analyzeAndSave(
                    tempFile.toString(), fileName, hasHeader);

            SubscriptionSummary summary = SubscriptionSummary.from(history.getSubscriptions());

            Map<String, Object> result = new HashMap<>();
            result.put("historyId", history.getId());
            result.put("summary", summary);
            result.put("subscriptions", history.getSubscriptions());
            result.put("transactionCount", history.getTransactionCount());

            return gson.toJson(ApiResponse.success(result, "분석이 완료되었습니다"));

        } catch (IllegalArgumentException e) {
            log.warn("파일 검증 실패: {}", e.getMessage());
            return gson.toJson(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("파일 분석 중 오류", e);
            return gson.toJson(ApiResponse.error("파일 처리 중 오류가 발생했습니다: " + e.getMessage()));
        } finally {
            // 임시 파일 정리
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("임시 파일 삭제 실패: {}", tempFile, e);
                }
            }
        }
    }

    private String handleGetHistory(spark.Request req) {
        try {
            String limitParam = req.queryParams("limit");
            int limit = limitParam != null ? Integer.parseInt(limitParam) : 10;
            List<AnalysisHistory> histories = subscriptionManager.getRecentHistory(limit);
            return gson.toJson(ApiResponse.success(histories));
        } catch (IllegalArgumentException e) {
            return gson.toJson(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("이력 조회 실패", e);
            return gson.toJson(ApiResponse.error("이력 조회 중 오류가 발생했습니다"));
        }
    }

    private String handleGetHistoryById(spark.Request req, spark.Response res) {
        try {
            String id = req.params("id");
            AnalysisHistory history = subscriptionManager.getHistoryById(id);

            if (history == null) {
                res.status(404);
                return gson.toJson(ApiResponse.error("분석 이력을 찾을 수 없습니다"));
            }

            return gson.toJson(ApiResponse.success(history));
        } catch (Exception e) {
            log.error("이력 조회 실패", e);
            res.status(500);
            return gson.toJson(ApiResponse.error("이력 조회 중 오류가 발생했습니다"));
        }
    }

    private String handleCompareHistory(spark.Request req, spark.Response res) {
        try {
            String id1 = req.params("id1");
            String id2 = req.params("id2");

            ComparisonResult comparison = subscriptionManager.compareHistory(id1, id2);

            if (comparison == null) {
                res.status(404);
                return gson.toJson(ApiResponse.error("비교할 분석 이력을 찾을 수 없습니다"));
            }

            return gson.toJson(ApiResponse.success(comparison));
        } catch (Exception e) {
            log.error("이력 비교 실패", e);
            res.status(500);
            return gson.toJson(ApiResponse.error("이력 비교 중 오류가 발생했습니다"));
        }
    }

    private String handleGetChanges(spark.Request req) {
        try {
            String limitParam = req.queryParams("limit");
            int limit = limitParam != null ? Integer.parseInt(limitParam) : 20;
            List<SubscriptionChange> changes = subscriptionManager.getRecentChanges(limit);
            return gson.toJson(ApiResponse.success(changes));
        } catch (IllegalArgumentException e) {
            return gson.toJson(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("변화 이력 조회 실패", e);
            return gson.toJson(ApiResponse.error("변화 이력 조회 중 오류가 발생했습니다"));
        }
    }

    private String handleDeleteHistory(spark.Request req, spark.Response res) {
        try {
            String id = req.params("id");
            subscriptionManager.deleteHistory(id);
            return gson.toJson(ApiResponse.success(null, "삭제되었습니다"));
        } catch (IllegalArgumentException e) {
            res.status(400);
            return gson.toJson(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("이력 삭제 실패", e);
            res.status(500);
            return gson.toJson(ApiResponse.error("이력 삭제 중 오류가 발생했습니다"));
        }
    }

    private String handleDownloadReport(spark.Request req, spark.Response res) {
        try {
            String id = req.params("id");
            AnalysisHistory history = subscriptionManager.getHistoryById(id);

            if (history == null) {
                res.status(404);
                return "분석 이력을 찾을 수 없습니다";
            }

            SubscriptionSummary summary = SubscriptionSummary.from(history.getSubscriptions());
            String report = summary.generateReport();

            res.type("text/plain; charset=utf-8");
            res.header("Content-Disposition",
                    "attachment; filename=subscription_report_" + id + ".txt");

            return report;
        } catch (Exception e) {
            log.error("보고서 다운로드 실패", e);
            res.status(500);
            return "보고서 생성 중 오류가 발생했습니다";
        }
    }

    /**
     * 메인 HTML 페이지 (동적 로고 및 색상 지원)
     */
    private String getIndexHtml() {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>SubTracker - 구독 관리</title>
                    <script src="https://cdn.tailwindcss.com"></script>
                    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css" rel="stylesheet">
                    <style>
                        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&display=swap');
                        body { font-family: 'Inter', sans-serif; background-color: #F3F4F6; }
                        .glass-panel { background: white; border: 1px solid #E5E7EB; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.02); }
                        .upload-area { transition: all 0.3s; cursor: pointer; }
                        .upload-area:hover { border-color: #667eea; background: #f0f2ff; }
                        .upload-area.dragging { border-color: #4f46e5; background: #eef2ff; }
                    </style>
                </head>
                <body class="text-gray-800">

                    <!-- 네비게이션 -->
                    <nav class="bg-white border-b border-gray-200 sticky top-0 z-10">
                        <div class="max-w-6xl mx-auto px-6 py-4 flex justify-between items-center">
                            <div class="flex items-center gap-2">
                                <div class="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center text-white font-bold">S</div>
                                <span class="text-xl font-bold tracking-tight text-gray-900">SubTracker</span>
                            </div>
                            <div class="flex gap-4">
                                <button onclick="showTab('upload')" id="tab-upload" class="text-sm text-indigo-600 hover:text-indigo-700 font-medium border-b-2 border-indigo-600 pb-1">
                                    대시보드
                                </button>
                                <button onclick="showTab('history')" id="tab-history" class="text-sm text-gray-500 hover:text-indigo-600 font-medium pb-1">
                                    분석 이력
                                </button>
                                <button onclick="showTab('changes')" id="tab-changes" class="text-sm text-gray-500 hover:text-indigo-600 font-medium pb-1">
                                    변화 추적
                                </button>
                            </div>
                        </div>
                    </nav>

                    <main class="max-w-6xl mx-auto px-6 py-10 space-y-8">

                        <!-- 업로드 탭 -->
                        <div id="content-upload">
                            <!-- 파일 업로드 섹션 -->
                            <section class="glass-panel rounded-2xl p-8 text-center transition hover:border-indigo-300 border-dashed border-2 border-gray-300 group upload-area" id="uploadArea">
                                <input type="file" id="fileInput" class="hidden" accept=".csv,.CSV">
                                <div class="space-y-3">
                                    <div class="w-12 h-12 bg-indigo-50 text-indigo-600 rounded-full flex items-center justify-center mx-auto group-hover:scale-110 transition">
                                        <i class="fas fa-file-csv text-xl"></i>
                                    </div>
                                    <h3 class="text-lg font-semibold text-gray-900">은행 거래내역(CSV) 업로드</h3>
                                    <p class="text-sm text-gray-500">파일을 이곳에 드래그하거나 클릭하여 업로드하세요.<br>자동으로 구독 서비스를 감지합니다.</p>
                                    <div class="mt-4">
                                        <label class="inline-flex items-center">
                                            <input type="checkbox" id="hasHeader" checked class="form-checkbox h-4 w-4 text-indigo-600">
                                            <span class="ml-2 text-sm text-gray-600">첫 줄이 헤더입니다</span>
                                        </label>
                                    </div>
                                </div>
                            </section>

                            <!-- 로딩 -->
                            <div id="loading" class="hidden text-center py-8">
                                <div class="inline-block animate-spin rounded-full h-12 w-12 border-4 border-indigo-500 border-t-transparent"></div>
                                <p class="mt-4 text-gray-600">분석 중입니다...</p>
                            </div>

                            <!-- 결과 섹션 -->
                            <div id="resultsSection" class="hidden space-y-6">
                                <!-- 통계 카드 -->
                                <section class="grid grid-cols-1 md:grid-cols-3 gap-6" id="statsCards">
                                    <!-- 동적으로 생성 -->
                                </section>

                                <!-- 구독 목록 -->
                                <section class="glass-panel rounded-2xl overflow-hidden">
                                    <div class="px-6 py-5 border-b border-gray-100 flex justify-between items-center">
                                        <h3 class="font-bold text-gray-800">활성 구독 내역</h3>
                                        <button onclick="downloadReport()" class="text-xs bg-indigo-50 text-indigo-700 px-3 py-1.5 rounded-lg font-medium hover:bg-indigo-100 transition">
                                            <i class="fas fa-download mr-1"></i> 보고서 다운로드
                                        </button>
                                    </div>
                                    <div id="subscriptionList" class="p-6 space-y-3">
                                        <!-- 동적으로 생성 -->
                                    </div>
                                    <div class="bg-gray-50 px-6 py-3 border-t border-gray-100 text-right">
                                        <span class="text-xs text-gray-500">최근 업데이트: <span id="lastUpdate"></span></span>
                                    </div>
                                </section>
                            </div>
                        </div>

                        <!-- 분석 이력 탭 -->
                        <div id="content-history" class="hidden">
                            <section class="glass-panel rounded-2xl p-6">
                                <h2 class="text-xl font-bold mb-4">📜 분석 이력</h2>
                                <div id="historyList" class="space-y-3">
                                    <!-- 동적으로 생성 -->
                                </div>
                            </section>
                        </div>

                        <!-- 변화 추적 탭 -->
                        <div id="content-changes" class="hidden">
                            <section class="glass-panel rounded-2xl p-6">
                                <h2 class="text-xl font-bold mb-4">🔄 구독 변화 이력</h2>
                                <div id="changesList" class="space-y-3">
                                    <!-- 동적으로 생성 -->
                                </div>
                            </section>
                        </div>

                    </main>

                    <script>
                        let currentHistoryId = null;

                        // Enum 한국어 매핑
                        const billingCycleMap = {
                            'MONTHLY': '월간',
                            'QUARTERLY': '분기',
                            'SEMI_ANNUAL': '반기',
                            'ANNUAL': '연간',
                            'UNKNOWN': '미확인'
                        };

                        const statusMap = {
                            'ACTIVE': '활성',
                            'INACTIVE': '비활성',
                            'PENDING': '대기중',
                            'CANCELLED': '취소됨'
                        };

                        // 동적 색상 생성 (문자열 해시 기반)
                        function getColorFromString(str) {
                            let hash = 0;
                            for (let i = 0; i < str.length; i++) {
                                hash = str.charCodeAt(i) + ((hash << 5) - hash);
                            }
                            const colors = [
                                'bg-red-600', 'bg-blue-600', 'bg-green-600',
                                'bg-purple-600', 'bg-pink-600', 'bg-indigo-600',
                                'bg-yellow-600', 'bg-teal-600', 'bg-orange-600',
                                'bg-rose-600', 'bg-cyan-600', 'bg-lime-600',
                                'bg-fuchsia-600', 'bg-violet-600', 'bg-sky-600'
                            ];
                            return colors[Math.abs(hash) % colors.length];
                        }

                        // 로고 URL 가져오기
                        function getLogoUrl(serviceName) {
                            const domainMap = {
                                '넷플릭스': 'netflix.com',
                                'netflix': 'netflix.com',
                                '스포티파이': 'spotify.com',
                                'spotify': 'spotify.com',
                                '유튜브': 'youtube.com',
                                'youtube': 'youtube.com',
                                '쿠팡': 'coupang.com',
                                'coupang': 'coupang.com',
                                '네이버': 'naver.com',
                                'naver': 'naver.com',
                                'apple': 'apple.com',
                                'google': 'google.com',
                                'microsoft': 'microsoft.com',
                                'amazon': 'amazon.com',
                                'disney': 'disneyplus.com',
                                '디즈니': 'disneyplus.com',
                                'wavve': 'wavve.com',
                                '웨이브': 'wavve.com',
                                'tving': 'tving.com',
                                '티빙': 'tving.com',
                                'watcha': 'watcha.com',
                                '왓챠': 'watcha.com',
                                'adobe': 'adobe.com',
                                'dropbox': 'dropbox.com',
                                'notion': 'notion.so',
                                '노션': 'notion.so'
                            };

                            const lowerName = serviceName.toLowerCase();
                            for (const [key, domain] of Object.entries(domainMap)) {
                                if (lowerName.includes(key)) {
                                    return `https://logo.clearbit.com/${domain}`;
                                }
                            }

                            return null;
                        }

                        // 탭 전환
                        function showTab(tabName) {
                            ['upload', 'history', 'changes'].forEach(tab => {
                                document.getElementById('content-' + tab).classList.add('hidden');
                                const tabBtn = document.getElementById('tab-' + tab);
                                tabBtn.classList.remove('text-indigo-600', 'border-b-2', 'border-indigo-600');
                                tabBtn.classList.add('text-gray-500');
                            });

                            document.getElementById('content-' + tabName).classList.remove('hidden');
                            const activeTab = document.getElementById('tab-' + tabName);
                            activeTab.classList.remove('text-gray-500');
                            activeTab.classList.add('text-indigo-600', 'border-b-2', 'border-indigo-600');

                            if (tabName === 'history') loadHistory();
                            else if (tabName === 'changes') loadChanges();
                        }

                        // 파일 업로드 이벤트
                        const uploadArea = document.getElementById('uploadArea');
                        const fileInput = document.getElementById('fileInput');

                        uploadArea.addEventListener('click', () => fileInput.click());

                        fileInput.addEventListener('change', (e) => {
                            if (e.target.files[0]) uploadFile(e.target.files[0]);
                        });

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
                            if (file?.name.toLowerCase().endsWith('.csv')) uploadFile(file);
                            else alert('CSV 파일만 업로드 가능합니다.');
                        });

                        // 파일 업로드 및 분석
                        async function uploadFile(file) {
                            const formData = new FormData();
                            formData.append('file', file);
                            const hasHeader = document.getElementById('hasHeader').checked;

                            document.getElementById('loading').classList.remove('hidden');
                            document.getElementById('resultsSection').classList.add('hidden');

                            try {
                                const response = await fetch(`/api/analyze?hasHeader=${hasHeader}`, {
                                    method: 'POST',
                                    body: formData
                                });

                                const apiResponse = await response.json();

                                if (apiResponse.success) {
                                    currentHistoryId = apiResponse.data.historyId;
                                    displayResults(apiResponse.data);
                                } else {
                                    alert('분석 실패: ' + apiResponse.error);
                                }
                            } catch (error) {
                                alert('오류 발생: ' + error.message);
                            } finally {
                                document.getElementById('loading').classList.add('hidden');
                            }
                        }

                        // 결과 표시
                        function displayResults(data) {
                            const summary = data.summary;
                            const subscriptions = data.subscriptions;

                            // 통계 카드
                            const statsHtml = `
                                <div class="glass-panel rounded-2xl p-6 flex flex-col justify-between">
                                    <div class="text-gray-500 text-sm font-medium mb-2">이번 달 총 구독료</div>
                                    <div class="text-3xl font-bold text-gray-900">₩ ${summary.monthlyTotal.toLocaleString()}</div>
                                    <div class="mt-4 text-xs text-gray-400">총 ${summary.totalSubscriptions}개 구독</div>
                                </div>
                                <div class="glass-panel rounded-2xl p-6 flex flex-col justify-between">
                                    <div class="text-gray-500 text-sm font-medium mb-2">연간 예상 지출</div>
                                    <div class="text-3xl font-bold text-gray-900">₩ ${summary.annualProjection.toLocaleString()}</div>
                                    <div class="mt-4 text-xs text-gray-400">고정 지출 분석 기반</div>
                                </div>
                                ${summary.cancellationCandidates.length > 0 ? `
                                <div class="glass-panel rounded-2xl p-6 border-l-4 border-l-red-500 flex flex-col justify-between bg-red-50/30">
                                    <div class="flex justify-between items-start">
                                        <div class="text-red-600 text-sm font-bold mb-2">⚠️ 취소 추천 감지</div>
                                        <span class="bg-red-100 text-red-700 text-[10px] px-2 py-1 rounded-full font-bold">확인 필요</span>
                                    </div>
                                    <div class="text-lg font-semibold text-gray-800">${summary.cancellationCandidates[0].serviceName}</div>
                                    <div class="mt-2 text-xs text-gray-500">장기간 사용 이력 없음</div>
                                </div>
                                ` : `
                                <div class="glass-panel rounded-2xl p-6 border-l-4 border-l-green-500 flex flex-col justify-between bg-green-50/30">
                                    <div class="text-green-600 text-sm font-bold mb-2">✅ 모든 구독 활성</div>
                                    <div class="text-lg font-semibold text-gray-800">정상 운영 중</div>
                                    <div class="mt-2 text-xs text-gray-500">미사용 구독 없음</div>
                                </div>
                                `}
                            `;
                            document.getElementById('statsCards').innerHTML = statsHtml;

                            // 구독 카드 리스트
                            const listHtml = subscriptions.map(sub => {
                                const brandColor = getColorFromString(sub.serviceName);
                                const logoUrl = getLogoUrl(sub.serviceName);

                                const statusColors = {
                                    'ACTIVE': 'bg-green-100 text-green-700',
                                    'INACTIVE': 'bg-gray-100 text-gray-600',
                                    'PENDING': 'bg-yellow-100 text-yellow-700'
                                };

                                const billingCycleKorean = billingCycleMap[sub.billingCycle] || sub.billingCycle;
                                const statusKorean = statusMap[sub.status] || sub.status;

                                return `
                                    <div class="glass-panel rounded-xl p-4 hover:shadow-md transition flex items-center justify-between">
                                        <div class="flex items-center gap-4 flex-1">
                                            <div class="w-12 h-12 rounded-xl ${brandColor} text-white flex items-center justify-center font-bold text-lg shadow-sm overflow-hidden relative">
                                                ${logoUrl ?
                                                    `<img src="${logoUrl}"
                                                          alt="${sub.serviceName}"
                                                          class="w-full h-full object-cover absolute inset-0"
                                                          onerror="this.style.display='none';">
                                                     <span class="relative z-10">${sub.serviceName.charAt(0).toUpperCase()}</span>`
                                                    :
                                                    sub.serviceName.charAt(0).toUpperCase()
                                                }
                                            </div>
                                            <div class="flex-1">
                                                <div class="font-bold text-gray-900 text-lg">${sub.serviceName}</div>
                                                <div class="text-sm text-gray-500 mt-1">
                                                    <span class="inline-flex items-center gap-1">
                                                        <span class="px-2 py-0.5 bg-gray-100 rounded text-xs">${billingCycleKorean}</span>
                                                        ${sub.nextChargeDate ?
                                                            `<span class="text-indigo-600">• ${formatNextPayment(sub.nextChargeDate)}</span>`
                                                            : ''}
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="text-right">
                                            <div class="text-2xl font-bold text-gray-900">₩${sub.monthlyAmount.toLocaleString()}</div>
                                            <span class="${statusColors[sub.status] || 'bg-gray-100 text-gray-600'} px-2 py-1 rounded-full text-xs mt-1 inline-block">
                                                ${statusKorean}
                                            </span>
                                        </div>
                                    </div>
                                `;
                            }).join('');
                            document.getElementById('subscriptionList').innerHTML = listHtml;

                            document.getElementById('lastUpdate').textContent = new Date().toLocaleString('ko-KR');
                            document.getElementById('resultsSection').classList.remove('hidden');
                        }

                        function formatNextPayment(dateStr) {
                            const date = new Date(dateStr);
                            const today = new Date();
                            const diffDays = Math.ceil((date - today) / (1000 * 60 * 60 * 24));

                            if (diffDays < 0) return '결제 예정';
                            if (diffDays === 0) return '오늘 결제';
                            return `${date.getMonth() + 1}월 ${date.getDate()}일 (D-${diffDays})`;
                        }

                        async function loadHistory() {
                            try {
                                const response = await fetch('/api/history?limit=20');
                                const apiResponse = await response.json();

                                if (!apiResponse.success) {
                                    throw new Error(apiResponse.error);
                                }

                                const histories = apiResponse.data;

                                const historyHtml = histories.map(h => `
                                    <div class="glass-panel rounded-lg p-4 flex justify-between items-center hover:shadow-md transition">
                                        <div>
                                            <div class="font-semibold text-gray-900">${h.fileName || '분석 결과'}</div>
                                            <div class="text-sm text-gray-500 mt-1">
                                                ${new Date(h.analysisDate).toLocaleString('ko-KR')} ·
                                                ${h.subscriptionCount}개 구독 · ₩${h.monthlyTotal.toLocaleString()}/월
                                            </div>
                                        </div>
                                        <button onclick="deleteHistory('${h.id}', event)"
                                                class="text-red-500 hover:text-red-700 transition">
                                            <i class="fas fa-trash-alt"></i>
                                        </button>
                                    </div>
                                `).join('');

                                document.getElementById('historyList').innerHTML = historyHtml ||
                                    '<div class="text-center text-gray-500 py-8">분석 이력이 없습니다</div>';
                            } catch (error) {
                                console.error('이력 로드 실패:', error);
                                document.getElementById('historyList').innerHTML =
                                    '<div class="text-center text-red-500 py-8">이력을 불러오는데 실패했습니다</div>';
                            }
                        }

                        async function loadChanges() {
                            try {
                                const response = await fetch('/api/changes?limit=30');
                                const apiResponse = await response.json();

                                if (!apiResponse.success) {
                                    throw new Error(apiResponse.error);
                                }

                                const changes = apiResponse.data;

                                const changeTypeMap = {
                                    'CREATED': { icon: '✨', text: '신규 구독' },
                                    'AMOUNT_CHANGED': { icon: '💰', text: '금액 변경' },
                                    'STATUS_CHANGED': { icon: '🔄', text: '상태 변경' },
                                    'CYCLE_CHANGED': { icon: '📅', text: '주기 변경' },
                                    'CANCELLED': { icon: '❌', text: '구독 취소' }
                                };

                                const changesHtml = changes.map(c => {
                                    const changeInfo = changeTypeMap[c.changeType] || { icon: '📝', text: c.changeType };

                                    return `
                                    <div class="glass-panel rounded-lg p-4 border-l-4 border-l-indigo-500">
                                        <div class="flex items-start justify-between">
                                            <div class="flex-1">
                                                <div class="font-semibold text-gray-900 mb-1">
                                                    ${changeInfo.icon} ${changeInfo.text}
                                                </div>
                                                <div class="text-sm text-gray-600">${c.notes}</div>
                                                ${c.oldValue && c.newValue ?
                                                    `<div class="text-sm text-gray-500 mt-2">${c.oldValue} → ${c.newValue}</div>`
                                                    : ''}
                                            </div>
                                            <div class="text-xs text-gray-400 ml-4">
                                                ${new Date(c.changeDate).toLocaleDateString('ko-KR')}
                                            </div>
                                        </div>
                                    </div>
                                `}).join('');

                                document.getElementById('changesList').innerHTML = changesHtml ||
                                    '<div class="text-center text-gray-500 py-8">변화 이력이 없습니다</div>';
                            } catch (error) {
                                console.error('변화 이력 로드 실패:', error);
                                document.getElementById('changesList').innerHTML =
                                    '<div class="text-center text-red-500 py-8">변화 이력을 불러오는데 실패했습니다</div>';
                            }
                        }

                        async function deleteHistory(id, event) {
                            event.stopPropagation();
                            if (!confirm('정말 삭제하시겠습니까?')) return;

                            try {
                                const response = await fetch(`/api/history/${id}`, { method: 'DELETE' });
                                const apiResponse = await response.json();

                                if (apiResponse.success) {
                                    loadHistory();
                                } else {
                                    alert('삭제 실패: ' + apiResponse.error);
                                }
                            } catch (error) {
                                alert('삭제 실패: ' + error.message);
                            }
                        }

                        function downloadReport() {
                            if (currentHistoryId) {
                                window.location.href = `/api/download-report/${currentHistoryId}`;
                            } else {
                                alert('먼저 CSV 파일을 분석해주세요.');
                            }
                        }
                    </script>
                </body>
                </html>
                """;
    }
}