"""
LSTM-based deep learning model for time series forecasting.
Supports both regression and classification tasks using PyTorch.
"""

import json
import os
import uuid
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import MODEL_STORAGE_PATH, MODEL_FILENAME, METADATA_FILENAME


class LSTMNetwork(nn.Module):
    """PyTorch LSTM network for sequence modeling."""

    def __init__(self, input_size, hidden_size, num_layers, dropout, output_size):
        super(LSTMNetwork, self).__init__()
        self.hidden_size = hidden_size
        self.num_layers = num_layers

        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0
        )
        self.fc = nn.Sequential(
            nn.Linear(hidden_size, hidden_size // 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_size // 2, output_size)
        )

    def forward(self, x):
        lstm_out, _ = self.lstm(x)
        out = lstm_out[:, -1, :]
        out = self.fc(out)
        return out


class LSTMModel:
    """
    LSTM model wrapper for training, prediction, and evaluation.

    Supports regression and classification tasks on sequential data.
    """

    def __init__(self):
        self.model_id = str(uuid.uuid4())
        self.network = None
        self.is_trained = False
        self.task_type = None
        self.input_size = None
        self.output_size = None
        self.params = {}
        self.metadata = {}
        self.scaler_mean = None
        self.scaler_std = None

    def _preprocess(self, X, y=None, fit_scaler=False):
        """Preprocess input data: normalize and reshape for LSTM."""
        X = np.array(X, dtype=np.float32)
        if X.ndim == 1:
            X = X.reshape(-1, 1)

        if fit_scaler and self.scaler_mean is None:
            self.scaler_mean = X.mean(axis=0)
            self.scaler_std = X.std(axis=0) + 1e-8

        X_scaled = (X - self.scaler_mean) / self.scaler_std

        if y is not None:
            y = np.array(y, dtype=np.float32 if self.task_type == "regression" else np.int64)
            return X_scaled, y
        return X_scaled

    def _create_sequences(self, X, y, sequence_length):
        """Create input sequences for time series prediction."""
        X_seq, y_seq = [], []
        for i in range(len(X) - sequence_length):
            X_seq.append(X[i:i + sequence_length])
            y_seq.append(y[i + sequence_length])
        return np.array(X_seq), np.array(y_seq)

    def train(self, X, y, task_type="regression", params=None):
        """
        Train the LSTM model on provided data.

        Args:
            X: Input features (array-like, shape: (n_samples, n_features) or (n_samples,))
            y: Target values (array-like)
            task_type: 'regression' or 'classification'
            params: Dictionary of model hyperparameters

        Returns:
            dict with training metrics and model ID
        """
        self.task_type = task_type
        self.params = params or {}

        hidden_size = self.params.get("hidden_size", 64)
        num_layers = self.params.get("num_layers", 2)
        dropout = self.params.get("dropout", 0.2)
        learning_rate = self.params.get("learning_rate", 0.001)
        epochs = self.params.get("epochs", 100)
        batch_size = self.params.get("batch_size", 32)
        sequence_length = int(self.params.get("sequence_length", self.params.get("windowSize", 10)))
        # Keep a validation split available even for small real-world uploads.
        sequence_length = max(1, min(sequence_length, max(1, len(X) - 2)))
        patience = self.params.get("patience", 10)

        X_scaled, y = self._preprocess(X, y, fit_scaler=True)

        if task_type == "classification":
            classes = np.unique(y)
            self.class_to_idx = {c: i for i, c in enumerate(classes)}
            self.idx_to_class = {i: c for i, c in enumerate(classes)}
            y = np.array([self.class_to_idx[yi] for yi in y])
            num_classes = len(classes)
        else:
            num_classes = 1

        self.input_size = X_scaled.shape[1] if X_scaled.ndim > 1 else 1
        self.output_size = num_classes

        X_seq, y_seq = self._create_sequences(X_scaled, y, sequence_length)

        X_tensor = torch.FloatTensor(X_seq)
        y_tensor = torch.LongTensor(y_seq) if task_type == "classification" else torch.FloatTensor(y_seq).unsqueeze(-1)

        dataset = TensorDataset(X_tensor, y_tensor)
        train_size = int(len(dataset) * 0.8)
        val_size = len(dataset) - train_size
        train_dataset, val_dataset = torch.utils.data.random_split(dataset, [train_size, val_size])

        train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
        val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

        self.network = LSTMNetwork(
            input_size=self.input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            dropout=dropout,
            output_size=self.output_size
        )

        optimizer = torch.optim.Adam(self.network.parameters(), lr=learning_rate)
        criterion = nn.CrossEntropyLoss() if task_type == "classification" else nn.MSELoss()

        best_val_loss = float("inf")
        epochs_no_improve = 0
        training_history = {"train_loss": [], "val_loss": []}

        for epoch in range(epochs):
            self.network.train()
            train_loss = 0.0
            for batch_X, batch_y in train_loader:
                optimizer.zero_grad()
                output = self.network(batch_X)
                loss = criterion(output.squeeze() if task_type == "regression" else output, batch_y)
                loss.backward()
                optimizer.step()
                train_loss += loss.item()

            train_loss /= len(train_loader)

            self.network.eval()
            val_loss = 0.0
            with torch.no_grad():
                for batch_X, batch_y in val_loader:
                    output = self.network(batch_X)
                    loss = criterion(output.squeeze() if task_type == "regression" else output, batch_y)
                    val_loss += loss.item()

            val_loss /= len(val_loader)
            training_history["train_loss"].append(train_loss)
            training_history["val_loss"].append(val_loss)

            if val_loss < best_val_loss:
                best_val_loss = val_loss
                epochs_no_improve = 0
                best_state = {k: v.clone() for k, v in self.network.state_dict().items()}
            else:
                epochs_no_improve += 1
                if epochs_no_improve >= patience:
                    break

        self.network.load_state_dict(best_state)
        self.is_trained = True

        train_metrics = self._compute_metrics(
            self.network, train_loader, task_type
        )
        val_metrics = self._compute_metrics(
            self.network, val_loader, task_type
        )

        self.metadata = {
            "model_id": self.model_id,
            "algorithm": "lstm",
            "task_type": task_type,
            "input_size": self.input_size,
            "output_size": self.output_size,
            "params": self.params,
            "training_metrics": train_metrics,
            "validation_metrics": val_metrics,
            "training_history": training_history,
            "sequence_length": sequence_length,
            "is_trained": True
        }

        return {
            "model_id": self.model_id,
            "algorithm": "lstm",
            "task_type": task_type,
            "training_metrics": train_metrics,
            "validation_metrics": val_metrics,
            "epochs_trained": epoch + 1,
            "best_val_loss": best_val_loss
        }

    def predict(self, X, sequence_length=None):
        """
        Generate predictions using the trained model.

        Args:
            X: Input features (array-like)
            sequence_length: Sequence length (uses trained default if None)

        Returns:
            numpy array of predictions
        """
        if not self.is_trained:
            raise ValueError("Model must be trained before prediction.")

        seq_len = sequence_length or self.metadata.get("sequence_length", 10)
        X_scaled = self._preprocess(X)

        X_seq, _ = self._create_sequences(
            X_scaled, np.zeros(len(X_scaled)), seq_len
        )

        X_tensor = torch.FloatTensor(X_seq)
        self.network.eval()

        with torch.no_grad():
            output = self.network(X_tensor)

        if self.task_type == "classification":
            probs = torch.softmax(output, dim=-1).numpy()
            predictions = np.argmax(probs, axis=1)
            predictions = np.array([self.idx_to_class[p] for p in predictions])
        else:
            predictions = output.squeeze().numpy()
            if predictions.ndim == 0:
                predictions = np.array([float(predictions)])

        return predictions

    def _compute_metrics(self, network, loader, task_type):
        """Compute evaluation metrics."""
        network.eval()
        all_preds = []
        all_targets = []

        with torch.no_grad():
            for batch_X, batch_y in loader:
                output = network(batch_X)
                if task_type == "classification":
                    preds = torch.argmax(output, dim=1).numpy()
                    targets = batch_y.numpy()
                else:
                    # Keep the batch dimension when the final batch has one row.
                    preds = np.atleast_1d(output.squeeze().numpy())
                    targets = np.atleast_1d(batch_y.squeeze().numpy())
                all_preds.extend(preds.tolist())
                all_targets.extend(targets.tolist())

        all_preds = np.array(all_preds)
        all_targets = np.array(all_targets)

        if task_type == "regression":
            return self._regression_metrics(all_targets, all_preds)
        else:
            return self._classification_metrics(all_targets, all_preds)

    @staticmethod
    def _regression_metrics(y_true, y_pred):
        """Compute regression metrics."""
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
        """Compute classification metrics."""
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
        """Save model weights and metadata to disk."""
        if path is None:
            path = os.path.join(MODEL_STORAGE_PATH, self.model_id)
        os.makedirs(path, exist_ok=True)

        model_path = os.path.join(path, MODEL_FILENAME)
        torch.save({
            "network_state_dict": self.network.state_dict() if self.network else None,
            "input_size": self.input_size,
            "output_size": self.output_size,
            "task_type": self.task_type,
            "params": self.params,
            "scaler_mean": self.scaler_mean.tolist() if self.scaler_mean is not None else None,
            "scaler_std": self.scaler_std.tolist() if self.scaler_std is not None else None,
            "class_to_idx": getattr(self, "class_to_idx", None),
            "idx_to_class": getattr(self, "idx_to_class", None)
        }, model_path)

        metadata_path = os.path.join(path, METADATA_FILENAME)
        with open(metadata_path, "w") as f:
            json.dump(self.metadata, f, indent=2, default=str)

        return path

    def load(self, path):
        """Load model weights and metadata from disk."""
        model_path = os.path.join(path, MODEL_FILENAME)
        checkpoint = torch.load(model_path, map_location="cpu", weights_only=False)

        self.input_size = checkpoint["input_size"]
        self.output_size = checkpoint["output_size"]
        self.task_type = checkpoint["task_type"]
        self.params = checkpoint["params"]
        self.scaler_mean = np.array(checkpoint["scaler_mean"]) if checkpoint["scaler_mean"] is not None else None
        self.scaler_std = np.array(checkpoint["scaler_std"]) if checkpoint["scaler_std"] is not None else None
        self.class_to_idx = checkpoint.get("class_to_idx")
        self.idx_to_class = checkpoint.get("idx_to_class")

        hidden_size = self.params.get("hidden_size", 64)
        num_layers = self.params.get("num_layers", 2)
        dropout = self.params.get("dropout", 0.2)

        self.network = LSTMNetwork(
            input_size=self.input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            dropout=dropout,
            output_size=self.output_size
        )
        self.network.load_state_dict(checkpoint["network_state_dict"])
        self.is_trained = True

        metadata_path = os.path.join(path, METADATA_FILENAME)
        if os.path.exists(metadata_path):
            with open(metadata_path, "r") as f:
                self.metadata = json.load(f)

        return self
