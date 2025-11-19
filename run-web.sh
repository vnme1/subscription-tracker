#!/bin/bash

echo "========================================"
echo "   구독 서비스 관리 도우미 v1.0"
echo "========================================"
echo ""

# Maven 빌드
echo "[1/2] 프로젝트 빌드 중..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 빌드 실패! Maven이 설치되어 있는지 확인하세요."
    exit 1
fi

echo "✅ 빌드 완료!"
echo ""

# 웹 서버 실행
echo "[2/2] 웹 서버 시작 중..."
echo ""
echo "1" | mvn exec:java -Dexec.mainClass="com.subtracker.SubscriptionTrackerApplication" -q