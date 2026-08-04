"""
MLP (Multi-Layer Perceptron) deep learning model using TensorFlow/Keras.
Supports both regression and classification tasks on tabular data.
"""

import json
import os
import uuid
import numpy as np
import joblib
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import MODEL_STORAGE_PATH, MODEL_FILENAME, TF_MODEL_FILENAME, METADATA_FILENAME


class MLPModel:
    """
    MLP model wrapper using TensorFlow/Keras for training, prediction, and evaluation.

    Supports regression and classification tasks on feature-based data.
    """

    def __init__(self):
        self.model_id = str(uuid.uuid4())
        self.model = None
        self.is_trained = False
        self.task_type = None
        self.input_shape = None
        self.output_size = None
        self.params = {}
        self.metadata = {}
        self.scaler_mean = None
        self.scaler_std = None
        self.class_to_idx = None
        self.idx_to_class = None

    def _preprocess(self, X, y=None, fit_scaler=False):
        X = np.array(X, dtype=np.float32)
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
                y = np.array(y, dtype=np.float32)
            return X_scaled, y
        return X_scaled

    def _build_model(self, input_shape, hidden_layers, dropout_rate, output_size, task_type):
        model = keras.Sequential()
        model.add(layers.Input(shape=(input_shape,)))

        for units in hidden_layers:
            model.add(layers.Dense(units, activation="relu"))
            model.add(layers.BatchNormalization())
            model.add(layers.Dropout(dropout_rate))

        if task_type == "regression":
            model.add(layers.Dense(1, activation="linear"))
        else:
            if output_size == 2:
                model.add(layers.Dense(1, activation="sigmoid"))
            else:
                model.add(layers.Dense(output_size, activation="softmax"))

        return model

    def train(self, X, y, task_type="regression", params=None):
        """
        Train the MLP model on provided data.

        Args:
            X: Input features (array-like, shape: (n_samples, n_features))
            y: Target values (array-like)
            task_type: 'regression' or 'classification'
            params: Dictionary of model hyperparameters

        Returns:
            dict with training metrics and model ID
        """
        self.task_type = task_type
        self.params = params or {}

        hidden_layers = self.params.get("hidden_layers", [128, 64, 32])
        dropout_rate = self.params.get("dropout_rate", 0.2)
        learning_rate = self.params.get("learning_rate", 0.001)
        epochs = self.params.get("epochs", 100)
        batch_size = self.params.get("batch_size", 32)
        patience = self.params.get("patience", 10)

        X_scaled, y = self._preprocess(X, y, fit_scaler=True)

        if task_type == "classification":
            classes = np.unique(y)
            self.class_to_idx = {c: i for i, c in enumerate(classes)}
            self.idx_to_class = {i: c for i, c in enumerate(classes)}
            y_idx = np.array([self.class_to_idx[yi] for yi in y])

            if len(classes) == 2:
                y_processed = y_idx.astype(np.float32)
                num_classes = 2
            else:
                y_processed = tf.keras.utils.to_categorical(y_idx, num_classes=len(classes))
                num_classes = len(classes)
        else:
            y_processed = y.astype(np.float32)
            num_classes = 1

        self.input_shape = X_scaled.shape[1]
        self.output_size = num_classes

        self.model = self._build_model(
            self.input_shape, hidden_layers, dropout_rate, num_classes, task_type
        )

        if task_type == "regression":
            loss = "mse"
            metrics = ["mae", tf.keras.metrics.RootMeanSquaredError(name="rmse")]
        else:
            if num_classes == 2:
                loss = "binary_crossentropy"
            else:
                loss = "categorical_crossentropy"
            metrics = ["accuracy"]

        self.model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
            loss=loss,
            metrics=metrics
        )

        split_idx = int(len(X_scaled) * 0.8)
        X_train, X_val = X_scaled[:split_idx], X_scaled[split_idx:]
        y_train, y_val = y_processed[:split_idx], y_processed[split_idx:]

        early_stopping = keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=patience,
            restore_best_weights=True,
            verbose=0
        )

        history = self.model.fit(
            X_train, y_train,
            validation_data=(X_val, y_val),
            epochs=epochs,
            batch_size=batch_size,
            callbacks=[early_stopping],
            verbose=0
        )

        self.is_trained = True

        train_preds = self.model.predict(X_train, verbose=0)
        val_preds = self.model.predict(X_val, verbose=0)

        if task_type == "classification" and num_classes > 2:
            train_preds = np.argmax(train_preds, axis=1)
            val_preds = np.argmax(val_preds, axis=1)
        elif task_type == "classification" and num_classes == 2:
            train_preds = (train_preds.flatten() > 0.5).astype(int)
            val_preds = (val_preds.flatten() > 0.5).astype(int)
        else:
            train_preds = train_preds.flatten()
            val_preds = val_preds.flatten()

        if task_type == "classification":
            train_y_true = np.array([self.idx_to_class[yi] for yi in y_idx[:split_idx]])
            val_y_true = np.array([self.idx_to_class[yi] for yi in y_idx[split_idx:]])
            train_preds_decoded = np.array([self.idx_to_class[p] for p in train_preds])
            val_preds_decoded = np.array([self.idx_to_class[p] for p in val_preds])
            train_metrics = self._classification_metrics(train_y_true, train_preds_decoded)
            val_metrics = self._classification_metrics(val_y_true, val_preds_decoded)
        else:
            train_metrics = self._regression_metrics(y_processed[:split_idx], train_preds)
            val_metrics = self._regression_metrics(y_processed[split_idx:], val_preds)

        self.metadata = {
            "model_id": self.model_id,
            "algorithm": "mlp",
            "task_type": task_type,
            "input_shape": self.input_shape,
            "output_size": self.output_size,
            "params": self.params,
            "training_metrics": train_metrics,
            "validation_metrics": val_metrics,
            "training_history": {
                "loss": history.history["loss"],
                "val_loss": history.history["val_loss"]
            },
            "epochs_trained": len(history.history["loss"]),
            "is_trained": True
        }

        return {
            "model_id": self.model_id,
            "algorithm": "mlp",
            "task_type": task_type,
            "training_metrics": train_metrics,
            "validation_metrics": val_metrics,
            "epochs_trained": len(history.history["loss"]),
            "best_val_loss": float(min(history.history["val_loss"]))
        }

    def predict(self, X):
        if not self.is_trained:
            raise ValueError("Model must be trained before prediction.")

        X_scaled = self._preprocess(X)
        preds = self.model.predict(X_scaled, verbose=0)

        if self.task_type == "classification":
            if self.output_size > 2:
                predictions = np.argmax(preds, axis=1)
            else:
                predictions = (preds.flatten() > 0.5).astype(int)
            predictions = np.array([self.idx_to_class[p] for p in predictions])
        else:
            predictions = preds.flatten()

        return predictions

    @staticmethod
    def _regression_metrics(y_true, y_pred):
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

    @staticmethod
    def _classification_metrics(y_true, y_pred):
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

    def save(self, path=None):
        if path is None:
            path = os.path.join(MODEL_STORAGE_PATH, self.model_id)
        os.makedirs(path, exist_ok=True)

        model_path = os.path.join(path, TF_MODEL_FILENAME)
        self.model.save(model_path)

        preprocessor_data = {
            "scaler_mean": self.scaler_mean.tolist() if self.scaler_mean is not None else None,
            "scaler_std": self.scaler_std.tolist() if self.scaler_std is not None else None,
            "class_to_idx": self.class_to_idx,
            "idx_to_class": self.idx_to_class,
            "input_shape": self.input_shape,
            "output_size": self.output_size,
            "task_type": self.task_type,
            "params": self.params
        }
        joblib.dump(preprocessor_data, os.path.join(path, "preprocessor.joblib"))

        metadata_path = os.path.join(path, METADATA_FILENAME)
        with open(metadata_path, "w") as f:
            json.dump(self.metadata, f, indent=2, default=str)

        return path

    def load(self, path):
        model_path = os.path.join(path, TF_MODEL_FILENAME)
        # In Keras 3, loading the serialized Adam optimizer can fail when the
        # optimizer was written by a different TensorFlow/Keras minor version.
        # Inference only needs the compiled network weights, so skip optimizer
        # deserialization and keep prediction independent of that runtime detail.
        self.model = keras.models.load_model(model_path, compile=False)

        preprocessor_path = os.path.join(path, "preprocessor.joblib")
        if os.path.exists(preprocessor_path):
            data = joblib.load(preprocessor_path)
            self.scaler_mean = np.array(data["scaler_mean"]) if data["scaler_mean"] is not None else None
            self.scaler_std = np.array(data["scaler_std"]) if data["scaler_std"] is not None else None
            self.class_to_idx = data.get("class_to_idx")
            self.idx_to_class = data.get("idx_to_class")
            self.input_shape = data["input_shape"]
            self.output_size = data["output_size"]
            self.task_type = data["task_type"]
            self.params = data["params"]

        metadata_path = os.path.join(path, METADATA_FILENAME)
        if os.path.exists(metadata_path):
            with open(metadata_path, "r") as f:
                self.metadata = json.load(f)

        self.is_trained = True
        return self
