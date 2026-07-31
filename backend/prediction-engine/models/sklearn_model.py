"""
scikit-learn based model wrapper for classical machine learning algorithms.
Supports RandomForest, GradientBoosting, SVM for both regression and classification.
"""

import json
import os
import uuid
import numpy as np
import joblib
from sklearn.ensemble import RandomForestRegressor, RandomForestClassifier
from sklearn.ensemble import GradientBoostingRegressor, GradientBoostingClassifier
from sklearn.svm import SVR, SVC
from sklearn.metrics import (
    mean_absolute_error, mean_squared_error, r2_score,
    accuracy_score, precision_score, recall_score, f1_score
)

import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import MODEL_STORAGE_PATH, MODEL_FILENAME, METADATA_FILENAME


SKLEARN_MODELS = {
    "random_forest": {
        "regression": RandomForestRegressor,
        "classification": RandomForestClassifier
    },
    "gradient_boosting": {
        "regression": GradientBoostingRegressor,
        "classification": GradientBoostingClassifier
    },
    "svm": {
        "regression": SVR,
        "classification": SVC
    }
}


class SklearnModel:
    """
    scikit-learn model wrapper supporting multiple algorithms.

    Supports RandomForest, GradientBoosting, and SVM for both
    regression and classification tasks.
    """

    def __init__(self):
        self.model_id = str(uuid.uuid4())
        self.model = None
        self.algorithm = None
        self.task_type = None
        self.is_trained = False
        self.params = {}
        self.metadata = {}
        self.scaler_mean = None
        self.scaler_std = None
        self.feature_names = None

    def _get_model_class(self, algorithm, task_type):
        model_classes = SKLEARN_MODELS.get(algorithm)
        if model_classes is None:
            raise ValueError(
                f"Unsupported algorithm: {algorithm}. "
                f"Supported: {list(SKLEARN_MODELS.keys())}"
            )
        return model_classes[task_type]

    def _preprocess(self, X, y=None, fit_scaler=False):
        X = np.array(X, dtype=np.float64)
        if X.ndim == 1:
            X = X.reshape(-1, 1)

        if fit_scaler and self.scaler_mean is None:
            self.scaler_mean = X.mean(axis=0)
            self.scaler_std = X.std(axis=0) + 1e-8

        X_scaled = (X - self.scaler_mean) / self.scaler_std

        if y is not None:
            if self.task_type == "classification":
                y = np.array(y)
            else:
                y = np.array(y, dtype=np.float64)
            return X_scaled, y
        return X_scaled

    def train(self, X, y, task_type="regression", algorithm="random_forest", params=None):
        """
        Train the sklearn model on provided data.

        Args:
            X: Input features (array-like, shape: (n_samples, n_features))
            y: Target values (array-like)
            task_type: 'regression' or 'classification'
            algorithm: 'random_forest', 'gradient_boosting', or 'svm'
            params: Dictionary of model hyperparameters

        Returns:
            dict with training metrics and model ID
        """
        self.task_type = task_type
        self.algorithm = algorithm
        self.params = params or {}

        model_class = self._get_model_class(algorithm, task_type)

        X_scaled, y = self._preprocess(X, y, fit_scaler=True)

        model_kwargs = {}
        if algorithm in ("random_forest", "gradient_boosting"):
            model_kwargs["n_estimators"] = self.params.get("n_estimators", 100)
            if self.params.get("max_depth") is not None:
                model_kwargs["max_depth"] = self.params["max_depth"]
        if algorithm == "gradient_boosting":
            model_kwargs["learning_rate"] = self.params.get("learning_rate", 0.1)
        if algorithm == "svm":
            model_kwargs["C"] = self.params.get("C", 1.0)
            model_kwargs["kernel"] = self.params.get("kernel", "rbf")
        model_kwargs["random_state"] = self.params.get("random_state", 42)

        self.model = model_class(**model_kwargs)
        self.model.fit(X_scaled, y)
        self.is_trained = True

        split_idx = int(len(X_scaled) * 0.8)
        X_train, X_val = X_scaled[:split_idx], X_scaled[split_idx:]
        y_train, y_val = y[:split_idx], y[split_idx:]

        train_preds = self.model.predict(X_train)
        val_preds = self.model.predict(X_val)

        train_metrics = self._compute_metrics(y_train, train_preds, task_type)
        val_metrics = self._compute_metrics(y_val, val_preds, task_type)

        feature_importance = None
        if algorithm in ("random_forest", "gradient_boosting") and hasattr(self.model, "feature_importances_"):
            feature_importance = self._compute_feature_importance(X_scaled)

        self.metadata = {
            "model_id": self.model_id,
            "algorithm": algorithm,
            "task_type": task_type,
            "params": self.params,
            "training_metrics": train_metrics,
            "validation_metrics": val_metrics,
            "feature_importance": feature_importance,
            "n_features": X_scaled.shape[1],
            "n_samples": len(X_scaled),
            "is_trained": True
        }

        result = {
            "model_id": self.model_id,
            "algorithm": algorithm,
            "task_type": task_type,
            "training_metrics": train_metrics,
            "validation_metrics": val_metrics,
            "n_samples": len(X_scaled)
        }
        if feature_importance:
            result["feature_importance"] = feature_importance

        return result

    def predict(self, X):
        if not self.is_trained:
            raise ValueError("Model must be trained before prediction.")

        X_scaled = self._preprocess(X)
        predictions = self.model.predict(X_scaled)
        return predictions

    def _compute_feature_importance(self, X):
        if not hasattr(self.model, "feature_importances_"):
            return None

        importances = self.model.feature_importances_.tolist()
        feature_count = len(importances)

        if self.feature_names is None:
            self.feature_names = [f"feature_{i}" for i in range(feature_count)]

        importance_pairs = list(zip(self.feature_names, importances))
        importance_pairs.sort(key=lambda x: x[1], reverse=True)

        return [
            {"feature": name, "importance": float(round(imp, 6))}
            for name, imp in importance_pairs
        ]

    def set_feature_names(self, feature_names):
        self.feature_names = feature_names

    @staticmethod
    def _compute_metrics(y_true, y_pred, task_type):
        if task_type == "regression":
            mae = float(mean_absolute_error(y_true, y_pred))
            rmse = float(np.sqrt(mean_squared_error(y_true, y_pred)))
            mask = y_true != 0
            if mask.sum() > 0:
                mape = float(np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100)
            else:
                mape = 0.0
            r2 = float(r2_score(y_true, y_pred))
            return {"mae": mae, "rmse": rmse, "mape": mape, "r2": r2}
        else:
            accuracy = float(accuracy_score(y_true, y_pred))
            precision = float(precision_score(y_true, y_pred, average="weighted", zero_division=0))
            recall = float(recall_score(y_true, y_pred, average="weighted", zero_division=0))
            f1 = float(f1_score(y_true, y_pred, average="weighted", zero_division=0))
            return {
                "accuracy": accuracy,
                "precision": precision,
                "recall": recall,
                "f1": f1
            }

    def save(self, path=None):
        if path is None:
            path = os.path.join(MODEL_STORAGE_PATH, self.model_id)
        os.makedirs(path, exist_ok=True)

        model_path = os.path.join(path, MODEL_FILENAME)
        save_data = {
            "model": self.model,
            "scaler_mean": self.scaler_mean,
            "scaler_std": self.scaler_std,
            "algorithm": self.algorithm,
            "task_type": self.task_type,
            "params": self.params,
            "feature_names": self.feature_names
        }
        joblib.dump(save_data, model_path)

        metadata_path = os.path.join(path, METADATA_FILENAME)
        with open(metadata_path, "w") as f:
            json.dump(self.metadata, f, indent=2, default=str)

        return path

    def load(self, path):
        model_path = os.path.join(path, MODEL_FILENAME)
        save_data = joblib.load(model_path)

        self.model = save_data["model"]
        self.scaler_mean = save_data["scaler_mean"]
        self.scaler_std = save_data["scaler_std"]
        self.algorithm = save_data["algorithm"]
        self.task_type = save_data["task_type"]
        self.params = save_data["params"]
        self.feature_names = save_data.get("feature_names")

        metadata_path = os.path.join(path, METADATA_FILENAME)
        if os.path.exists(metadata_path):
            with open(metadata_path, "r") as f:
                self.metadata = json.load(f)

        self.is_trained = True
        return self
