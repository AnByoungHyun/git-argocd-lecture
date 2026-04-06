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
# GET / — HTML 웹 페이지 테스트 (신규)
# ----------------------------------------------------------------
class TestRoot:
    def test_get_root_returns_200(self):
        res = client.get("/")
        assert res.status_code == 200

    def test_get_root_content_type_is_html(self):
        res = client.get("/")
        assert "text/html" in res.headers["content-type"]

    def test_get_root_body_contains_app_info(self):
        res = client.get("/")
        assert "python-app" in res.text
        assert "Running"   in res.text
        assert "FastAPI"   in res.text
        assert "#306998"   in res.text   # python-app 고유 배경색

    def test_post_root_returns_405(self):
        res = client.post("/")
        assert res.status_code == 405
        body = res.json()
        assert body["status"] == "error"
        assert body["code"] == 405


# ----------------------------------------------------------------
# GET /api — JSON 응답 테스트 (GET / 에서 이동)
# ----------------------------------------------------------------
class TestApi:
    def test_get_api_returns_200(self):
        res = client.get("/api")
        assert res.status_code == 200

    def test_get_api_content_type_is_json(self):
        res = client.get("/api")
        assert "application/json" in res.headers["content-type"]

    def test_get_api_response_fields(self):
        res = client.get("/api")
        body = res.json()
        assert body["app"]       == "python-app"
        assert body["language"]  == "Python"
        assert body["framework"] == "FastAPI"
        assert isinstance(body["port"], int)
        assert "version"     in body
        assert "environment" in body

    def test_post_api_returns_405(self):
        res = client.post("/api")
        assert res.status_code == 405


# ----------------------------------------------------------------
# GET /health — 헬스체크 응답 테스트 (변경 없음)
# ----------------------------------------------------------------
class TestHealth:
    def test_get_health_returns_200(self):
        res = client.get("/health")
        assert res.status_code == 200

    def test_get_health_status_is_ok(self):
        res = client.get("/health")
        body = res.json()
        assert body["status"] == "ok"
        assert body["app"]    == "python-app"
        assert "version" in body

    def test_post_health_returns_405(self):
        res = client.post("/health")
        assert res.status_code == 405


# ----------------------------------------------------------------
# 404 — 존재하지 않는 경로 테스트 (변경 없음)
# ----------------------------------------------------------------
class TestNotFound:
    def test_unknown_path_returns_404(self):
        res = client.get("/unknown-path")
        assert res.status_code == 404

    def test_unknown_path_error_format(self):
        res = client.get("/no-such-endpoint")
        body = res.json()
        assert body["status"]  == "error"
        assert body["code"]    == 404
        assert body["message"] == "Not Found"
        assert "/no-such-endpoint" in body["path"]
