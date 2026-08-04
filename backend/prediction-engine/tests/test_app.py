import importlib

import pytest


@pytest.fixture()
def engine(tmp_path, monkeypatch):
    """Load the Flask app with an isolated model directory for the test run."""
    app_module = importlib.import_module("app")
    model_module = importlib.import_module("models.sklearn_model")
    monkeypatch.setattr(app_module, "MODEL_STORAGE_PATH", str(tmp_path))
    monkeypatch.setattr(model_module, "MODEL_STORAGE_PATH", str(tmp_path))
    app_module.app.config.update(TESTING=True)
    with app_module.app.test_client() as client:
        yield client, app_module


def test_health_and_algorithm_catalog(engine):
    client, _ = engine

    health = client.get("/api/v1/predictions/health")
    assert health.status_code == 200
    assert health.get_json()["status"] == "healthy"

    algorithms = client.get("/api/v1/predictions/algorithms")
    body = algorithms.get_json()
    assert algorithms.status_code == 200
    assert body["count"] >= 4
    assert "lstm" in body["algorithms"]
    assert "random_forest" in body["algorithms"]


def test_train_request_validation(engine):
    client, _ = engine

    response = client.post(
        "/api/v1/predictions/train",
        json={"algorithm": "random_forest", "task_type": "regression"},
    )

    assert response.status_code == 400
    assert "Dataset not provided" in response.get_json()["error"]


def test_real_train_save_and_predict_flow(engine):
    client, _ = engine
    features = [[float(i), float(i % 3)] for i in range(24)]
    target = [2.0 * row[0] + row[1] for row in features]

    trained = client.post(
        "/api/v1/predictions/train",
        json={
            "algorithm": "random_forest",
            "task_type": "regression",
            "X": features,
            "y": target,
            "params": {"n_estimators": 3, "random_state": 42},
        },
    )

    assert trained.status_code == 201
    model_id = trained.get_json()["data"]["model_id"]
    assert model_id

    prediction = client.post(
        "/api/v1/predictions/predict",
        json={"model_id": model_id, "X": [[24.0, 0.0], [25.0, 1.0]]},
    )
    prediction_body = prediction.get_json()
    assert prediction.status_code == 200
    assert prediction_body["status"] == "success"
    assert prediction_body["count"] == 2
    assert len(prediction_body["predictions"]) == 2

    models = client.get("/api/v1/predictions/models")
    assert models.status_code == 200
    assert any(item["model_id"] == model_id for item in models.get_json()["models"])


def test_predict_unknown_model_returns_not_found(engine):
    client, _ = engine

    response = client.post(
        "/api/v1/predictions/predict",
        json={"model_id": "does-not-exist", "X": [[1.0]]},
    )

    assert response.status_code == 404
