"""
python-app 단위 테스트
pytest + httpx (FastAPI TestClient)
실행: pytest tests/ -v
"""

import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app, raise_server_exceptions=False)


# ----------------------------------------------------------------
# GET / — 기본 정보 응답 테스트
# ----------------------------------------------------------------
class TestRoot:
    def test_get_root_returns_200(self):
        res = client.get("/")
        assert res.status_code == 200

    def test_get_root_content_type_is_json(self):
        res = client.get("/")
        assert "application/json" in res.headers["content-type"]

    def test_get_root_response_fields(self):
        res = client.get("/")
        body = res.json()
        assert body["app"] == "python-app"
        assert body["language"] == "Python"
        assert body["framework"] == "FastAPI"
        assert isinstance(body["port"], int)
        assert "version" in body
        assert "environment" in body

    def test_post_root_returns_405(self):
        res = client.post("/")
        assert res.status_code == 405
        body = res.json()
        assert body["status"] == "error"
        assert body["code"] == 405


# ----------------------------------------------------------------
# GET /health — 헬스체크 응답 테스트
# ----------------------------------------------------------------
class TestHealth:
    def test_get_health_returns_200(self):
        res = client.get("/health")
        assert res.status_code == 200

    def test_get_health_status_is_ok(self):
        res = client.get("/health")
        body = res.json()
        assert body["status"] == "ok"
        assert body["app"] == "python-app"
        assert "version" in body

    def test_post_health_returns_405(self):
        res = client.post("/health")
        assert res.status_code == 405


# ----------------------------------------------------------------
# 404 — 존재하지 않는 경로 테스트
# ----------------------------------------------------------------
class TestNotFound:
    def test_unknown_path_returns_404(self):
        res = client.get("/unknown-path")
        assert res.status_code == 404

    def test_unknown_path_error_format(self):
        res = client.get("/no-such-endpoint")
        body = res.json()
        assert body["status"] == "error"
        assert body["code"] == 404
        assert body["message"] == "Not Found"
        assert "/no-such-endpoint" in body["path"]
