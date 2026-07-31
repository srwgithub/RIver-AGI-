"""
Prediction Engine - Flask-based deep learning prediction microservice.

Provides REST API endpoints for training, prediction, model management,
cross-validation, and comparison using TensorFlow/PyTorch/scikit-learn.
"""

import json
import os
import shutil
import traceback
import numpy as np
import pandas as pd

from flask import Flask, request, jsonify
from flask_cors import CORS

from config import (
    HOST, PORT, DEBUG, MODEL_STORAGE_PATH,
    SUPPORTED_ALGORITHMS, METADATA_FILENAME, MODEL_FILENAME
)
from models.lstm_model import LSTMModel
from models.transformer_model import TransformerModel
from models.mlp_model import MLPModel
from models.sklearn_model import SklearnModel

app = Flask(__name__)
CORS(app)


@app.route("/", methods=["GET"])
def root():
    """Provide a friendly service landing response instead of a 404."""
    return jsonify({
        "service": "prediction-engine",
        "status": "healthy",
        "health": "/health",
        "apiHealth": "/api/v1/predictions/health",
        "algorithms": "/api/v1/predictions/algorithms"
    })


def _get_model_class(algorithm):
    """Factory function to get the appropriate model class."""
    algorithm_map = {
        "lstm": LSTMModel,
        "transformer": TransformerModel,
        "mlp": MLPModel,
        "random_forest": SklearnModel,
        "gradient_boosting": SklearnModel,
        "svm": SklearnModel
    }
    model_class = algorithm_map.get(algorithm)
    if model_class is None:
        raise ValueError(f"Unsupported algorithm: {algorithm}")
    return model_class


def _validate_request(data, required_fields):
    """Validate that all required fields are present in request data."""
    missing = [f for f in required_fields if f not in data]
    if missing:
        return {"error": f"Missing required fields: {missing}"}, 400
    return None


def _parse_dataset(data):
    """Parse dataset from request, supporting 'dataset', 'X', 'features' keys."""
    if "dataset" in data:
        return np.array(data["dataset"])
    elif "X" in data:
        return np.array(data["X"])
    elif "features" in data:
        return np.array(data["features"])
    else:
        raise ValueError("Dataset not provided. Use 'dataset', 'X', or 'features' key.")


def _parse_target(data):
    """Parse target from request, supporting 'target', 'y', 'labels' keys."""
    if "target" in data:
        return np.array(data["target"])
    elif "y" in data:
        return np.array(data["y"])
    elif "labels" in data:
        return np.array(data["labels"])
    else:
        raise ValueError("Target not provided. Use 'target', 'y', or 'labels' key.")


def _list_saved_models():
    """List all saved models in the storage directory."""
    models = []
    if not os.path.exists(MODEL_STORAGE_PATH):
        return models

    for model_id in os.listdir(MODEL_STORAGE_PATH):
        model_path = os.path.join(MODEL_STORAGE_PATH, model_id)
        metadata_path = os.path.join(model_path, METADATA_FILENAME)
        if os.path.isdir(model_path) and os.path.exists(metadata_path):
            try:
                with open(metadata_path, "r") as f:
                    metadata = json.load(f)
                models.append(metadata)
            except (json.JSONDecodeError, IOError):
                continue
    return models


def _load_model_by_id(model_id):
    """Load a model from disk by its ID."""
    model_path = os.path.join(MODEL_STORAGE_PATH, model_id)
    if not os.path.exists(model_path):
        return None

    metadata_path = os.path.join(model_path, METADATA_FILENAME)
    if not os.path.exists(metadata_path):
        return None

    with open(metadata_path, "r") as f:
        metadata = json.load(f)

    algorithm = metadata.get("algorithm")
    model_class = _get_model_class(algorithm)
    model = model_class()
    model.load(model_path)
    return model


@app.route("/api/v1/predictions/algorithms", methods=["GET"])
def list_algorithms():
    """List all available prediction algorithms and their parameters."""
    algorithms = {}
    for key, value in SUPPORTED_ALGORITHMS.items():
        algorithms[key] = {
            "name": value["name"],
            "description": value["description"],
            "type": value["type"],
            "tasks": value["tasks"],
            "default_params": value["params"]
        }
    return jsonify({"algorithms": algorithms, "count": len(algorithms)})


@app.route("/api/v1/predictions/train", methods=["POST"])
def train_model():
    """
    Train a deep learning or classical ML model.

    Request JSON body:
        - algorithm: str (required) - Algorithm name (lstm, transformer, mlp, etc.)
        - task_type: str (required) - 'regression' or 'classification'
        - dataset/X/features: list (required) - Input data
        - target/y/labels: list (required) - Target values
        - params: dict (optional) - Model hyperparameters
        - feature_names: list (optional) - Feature names for importance analysis
    """
    try:
        data = request.get_json()
        if data is None:
            return jsonify({"error": "Request body must be JSON"}), 400

        error = _validate_request(data, ["algorithm", "task_type"])
        if error:
            return jsonify(error[0]), error[1]

        algorithm = data["algorithm"]
        task_type = data["task_type"]

        if algorithm not in SUPPORTED_ALGORITHMS:
            return jsonify({
                "error": f"Unsupported algorithm: {algorithm}",
                "supported": list(SUPPORTED_ALGORITHMS.keys())
            }), 400

        if task_type not in SUPPORTED_ALGORITHMS[algorithm]["tasks"]:
            return jsonify({
                "error": f"Algorithm '{algorithm}' does not support task '{task_type}'"
            }), 400

        X = _parse_dataset(data)
        y = _parse_target(data)
        params = data.get("params", {})
        feature_names = data.get("feature_names", None)

        model_class = _get_model_class(algorithm)
        model = model_class()

        if algorithm in ("random_forest", "gradient_boosting", "svm"):
            result = model.train(
                X=X, y=y, task_type=task_type,
                algorithm=algorithm, params=params
            )
        else:
            result = model.train(
                X=X, y=y, task_type=task_type, params=params
            )

        if feature_names and hasattr(model, "set_feature_names"):
            model.set_feature_names(feature_names)

        model.save()

        return jsonify({
            "status": "success",
            "message": "Model trained successfully",
            "data": result
        }), 201

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": f"Training failed: {str(e)}"}), 500


@app.route("/api/v1/predictions/predict", methods=["POST"])
def predict():
    """
    Run prediction using a trained model.

    Request JSON body:
        - model_id: str (required) - Model ID to use
        - dataset/X/features: list (required) - Input data for prediction
    """
    try:
        data = request.get_json()
        if data is None:
            return jsonify({"error": "Request body must be JSON"}), 400

        error = _validate_request(data, ["model_id"])
        if error:
            return jsonify(error[0]), error[1]

        model_id = data["model_id"]
        model = _load_model_by_id(model_id)

        if model is None:
            return jsonify({"error": f"Model not found: {model_id}"}), 404

        X = _parse_dataset(data)

        predictions = model.predict(X)

        return jsonify({
            "status": "success",
            "model_id": model_id,
            "algorithm": model.metadata.get("algorithm"),
            "task_type": model.metadata.get("task_type"),
            "predictions": predictions.tolist() if isinstance(predictions, np.ndarray) else list(predictions),
            "count": len(predictions)
        })

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": f"Prediction failed: {str(e)}"}), 500


@app.route("/api/v1/predictions/models", methods=["GET"])
def list_models():
    """List all trained models with their metadata."""
    try:
        models = _list_saved_models()
        return jsonify({
            "status": "success",
            "models": models,
            "count": len(models)
        })
    except Exception as e:
        return jsonify({"error": f"Failed to list models: {str(e)}"}), 500


@app.route("/api/v1/predictions/models/<model_id>", methods=["GET"])
def get_model(model_id):
    """
    Get detailed information about a specific model.

    Includes training metrics, validation metrics, and model parameters.
    """
    try:
        model_path = os.path.join(MODEL_STORAGE_PATH, model_id)
        metadata_path = os.path.join(model_path, METADATA_FILENAME)

        if not os.path.exists(metadata_path):
            return jsonify({"error": f"Model not found: {model_id}"}), 404

        with open(metadata_path, "r") as f:
            metadata = json.load(f)

        return jsonify({
            "status": "success",
            "model": metadata
        })

    except Exception as e:
        return jsonify({"error": f"Failed to get model: {str(e)}"}), 500


@app.route("/api/v1/predictions/models/<model_id>", methods=["DELETE"])
def delete_model(model_id):
    """
    Delete a trained model and its associated files.
    """
    try:
        model_path = os.path.join(MODEL_STORAGE_PATH, model_id)

        if not os.path.exists(model_path):
            return jsonify({"error": f"Model not found: {model_id}"}), 404

        shutil.rmtree(model_path)

        return jsonify({
            "status": "success",
            "message": f"Model {model_id} deleted successfully"
        })

    except Exception as e:
        return jsonify({"error": f"Failed to delete model: {str(e)}"}), 500


@app.route("/api/v1/predictions/models/<model_id>/compare", methods=["POST"])
def compare_models(model_id):
    """
    Compare two models by their performance metrics.

    Request JSON body:
        - compare_model_id: str (required) - Second model ID to compare with

    Returns comparison of training and validation metrics.
    """
    try:
        data = request.get_json()
        if data is None:
            return jsonify({"error": "Request body must be JSON"}), 400

        error = _validate_request(data, ["compare_model_id"])
        if error:
            return jsonify(error[0]), error[1]

        compare_model_id = data["compare_model_id"]

        model1_path = os.path.join(MODEL_STORAGE_PATH, model_id)
        model2_path = os.path.join(MODEL_STORAGE_PATH, compare_model_id)

        metadata1_path = os.path.join(model1_path, METADATA_FILENAME)
        metadata2_path = os.path.join(model2_path, METADATA_FILENAME)

        if not os.path.exists(metadata1_path):
            return jsonify({"error": f"Model not found: {model_id}"}), 404
        if not os.path.exists(metadata2_path):
            return jsonify({"error": f"Model not found: {compare_model_id}"}), 404

        with open(metadata1_path, "r") as f:
            metadata1 = json.load(f)
        with open(metadata2_path, "r") as f:
            metadata2 = json.load(f)

        comparison = {
            "model_1": {
                "model_id": model_id,
                "algorithm": metadata1.get("algorithm"),
                "task_type": metadata1.get("task_type"),
                "training_metrics": metadata1.get("training_metrics"),
                "validation_metrics": metadata1.get("validation_metrics")
            },
            "model_2": {
                "model_id": compare_model_id,
                "algorithm": metadata2.get("algorithm"),
                "task_type": metadata2.get("task_type"),
                "training_metrics": metadata2.get("training_metrics"),
                "validation_metrics": metadata2.get("validation_metrics")
            },
            "recommendation": _generate_recommendation(metadata1, metadata2)
        }

        return jsonify({
            "status": "success",
            "comparison": comparison
        })

    except Exception as e:
        return jsonify({"error": f"Comparison failed: {str(e)}"}), 500


def _generate_recommendation(metadata1, metadata2):
    """Generate model comparison recommendation based on validation metrics."""
    val1 = metadata1.get("validation_metrics", {})
    val2 = metadata2.get("validation_metrics", {})
    task = metadata1.get("task_type", "regression")

    if task == "regression":
        rmse1 = val1.get("rmse", float("inf"))
        rmse2 = val2.get("rmse", float("inf"))
        r2_1 = val1.get("r2", float("-inf"))
        r2_2 = val2.get("r2", float("-inf"))

        better_rmse = 1 if rmse1 < rmse2 else 2
        better_r2 = 1 if r2_1 > r2_2 else 2

        if better_rmse == better_r2:
            recommended = f"Model {better_rmse} ({metadata1['algorithm'] if better_rmse == 1 else metadata2['algorithm']})"
        else:
            recommended = f"Model {better_r2} ({metadata1['algorithm'] if better_r2 == 1 else metadata2['algorithm']})"

        return {
            "better_rmse": f"Model {better_rmse}",
            "better_r2": f"Model {better_r2}",
            "recommended": recommended,
            "reason": f"Based on validation RMSE and R², {recommended} is recommended."
        }
    else:
        acc1 = val1.get("accuracy", 0)
        acc2 = val2.get("accuracy", 0)
        f1_1 = val1.get("f1", 0)
        f1_2 = val2.get("f1", 0)

        better_acc = 1 if acc1 > acc2 else 2
        better_f1 = 1 if f1_1 > f1_2 else 2

        if better_acc == better_f1:
            recommended = f"Model {better_acc} ({metadata1['algorithm'] if better_acc == 1 else metadata2['algorithm']})"
        else:
            recommended = f"Model {better_f1} ({metadata1['algorithm'] if better_f1 == 1 else metadata2['algorithm']})"

        return {
            "better_accuracy": f"Model {better_acc}",
            "better_f1": f"Model {better_f1}",
            "recommended": recommended,
            "reason": f"Based on validation accuracy and F1, {recommended} is recommended."
        }


@app.route("/api/v1/predictions/cross-validate", methods=["POST"])
def cross_validate():
    """
    Walk-forward cross validation for time series or tabular data.

    Request JSON body:
        - algorithm: str (required) - Algorithm to use
        - task_type: str (required) - 'regression' or 'classification'
        - dataset/X/features: list (required) - Input data
        - target/y/labels: list (required) - Target values
        - params: dict (optional) - Model hyperparameters
        - n_splits: int (optional, default=5) - Number of CV splits
        - window_size: int (optional) - Window size for walk-forward
    """
    try:
        data = request.get_json()
        if data is None:
            return jsonify({"error": "Request body must be JSON"}), 400

        error = _validate_request(data, ["algorithm", "task_type"])
        if error:
            return jsonify(error[0]), error[1]

        algorithm = data["algorithm"]
        task_type = data["task_type"]
        params = data.get("params", {})
        n_splits = data.get("n_splits", 5)
        window_size = data.get("window_size", None)

        if algorithm not in SUPPORTED_ALGORITHMS:
            return jsonify({
                "error": f"Unsupported algorithm: {algorithm}",
                "supported": list(SUPPORTED_ALGORITHMS.keys())
            }), 400

        X = _parse_dataset(data)
        y = _parse_target(data)

        X = np.array(X)
        y = np.array(y)

        n_samples = len(X)
        split_size = n_samples // (n_splits + 1)

        cv_metrics = []
        fold_metrics = []

        for fold in range(n_splits):
            train_end = split_size * (fold + 1)
            test_start = train_end
            test_end = min(train_end + split_size, n_samples)

            if window_size:
                train_start = max(0, train_end - window_size)
            else:
                train_start = 0

            if test_end <= test_start:
                break

            X_train, X_test = X[train_start:train_end], X[test_start:test_end]
            y_train, y_test = y[train_start:train_end], y[test_start:test_end]

            if len(X_train) == 0 or len(X_test) == 0:
                continue

            model_class = _get_model_class(algorithm)
            model = model_class()

            if algorithm in ("random_forest", "gradient_boosting", "svm"):
                result = model.train(
                    X=X_train, y=y_train, task_type=task_type,
                    algorithm=algorithm, params=params
                )
            else:
                result = model.train(
                    X=X_train, y=y_train, task_type=task_type, params=params
                )

            preds = model.predict(X_test)

            if task_type == "regression":
                fold_metric = _compute_regression_metrics(y_test, preds)
            else:
                fold_metric = _compute_classification_metrics(y_test, preds)

            fold_metric["fold"] = fold + 1
            fold_metrics.append(fold_metric)

            if task_type == "regression":
                cv_metrics.append(fold_metric["rmse"])
            else:
                cv_metrics.append(fold_metric["accuracy"])

        avg_metrics = {}
        if fold_metrics:
            if task_type == "regression":
                for key in ["mae", "rmse", "mape", "r2"]:
                    values = [fm[key] for fm in fold_metrics if key in fm]
                    if values:
                        avg_metrics[key] = float(np.mean(values))
            else:
                for key in ["accuracy", "precision", "recall", "f1"]:
                    values = [fm[key] for fm in fold_metrics if key in fm]
                    if values:
                        avg_metrics[key] = float(np.mean(values))

        return jsonify({
            "status": "success",
            "algorithm": algorithm,
            "task_type": task_type,
            "n_splits": n_splits,
            "folds_completed": len(fold_metrics),
            "average_metrics": avg_metrics,
            "fold_metrics": fold_metrics
        })

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": f"Cross-validation failed: {str(e)}"}), 500


def _compute_regression_metrics(y_true, y_pred):
    """Compute regression metrics for cross-validation."""
    mae = float(np.mean(np.abs(y_true - y_pred)))
    rmse = float(np.sqrt(np.mean((y_true - y_pred) ** 2)))
    mask = y_true != 0
    if mask.sum() > 0:
        mape = float(np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100)
    else:
        mape = 0.0
    ss_res = float(np.sum((y_true - y_pred) ** 2))
    ss_tot = float(np.sum((y_true - np.mean(y_true)) ** 2))
    r2 = float(1 - ss_res / ss_tot) if ss_tot > 0 else 0.0

    return {"mae": mae, "rmse": rmse, "mape": mape, "r2": r2}


def _compute_classification_metrics(y_true, y_pred):
    """Compute classification metrics for cross-validation."""
    accuracy = float(np.mean(y_true == y_pred))
    classes = np.unique(y_true)
    precisions, recalls, f1s = [], [], []

    for cls in classes:
        tp = np.sum((y_pred == cls) & (y_true == cls))
        fp = np.sum((y_pred == cls) & (y_true != cls))
        fn = np.sum((y_pred != cls) & (y_true == cls))

        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

        precisions.append(precision)
        recalls.append(recall)
        f1s.append(f1)

    return {
        "accuracy": accuracy,
        "precision": float(np.mean(precisions)),
        "recall": float(np.mean(recalls)),
        "f1": float(np.mean(f1s))
    }


@app.route("/health", methods=["GET"])
def health_check():
    """Health check endpoint for monitoring."""
    return jsonify({
        "status": "healthy",
        "service": "prediction-engine",
        "version": "1.0.0",
        "models_count": len(_list_saved_models())
    })


@app.route("/api/v1/predictions/health", methods=["GET"])
def api_health_check():
    """Expose health under the path used by the Java client."""
    return health_check()


@app.errorhandler(404)
def not_found(e):
    return jsonify({"error": "Not Found", "message": str(e)}), 404


@app.errorhandler(405)
def method_not_allowed(e):
    return jsonify({"error": "Method Not Allowed", "message": str(e)}), 405


@app.errorhandler(500)
def internal_error(e):
    return jsonify({"error": "Internal Server Error", "message": str(e)}), 500


if __name__ == "__main__":
    print(f"Starting Prediction Engine on {HOST}:{PORT}")
    print(f"Model storage path: {MODEL_STORAGE_PATH}")
    print(f"Available algorithms: {list(SUPPORTED_ALGORITHMS.keys())}")
    app.run(host=HOST, port=PORT, debug=DEBUG)
