"""
Transformer-based deep learning model for sequence prediction.
Supports both regression and classification tasks using PyTorch.
"""

import json
import os
import uuid
import math
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config import MODEL_STORAGE_PATH, MODEL_FILENAME, METADATA_FILENAME


class PositionalEncoding(nn.Module):
    """Positional encoding for transformer."""

    def __init__(self, d_model, max_len=5000):
        super(PositionalEncoding, self).__init__()
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model))
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        pe = pe.unsqueeze(0)
        self.register_buffer("pe", pe)

    def forward(self, x):
        return x + self.pe[:, :x.size(1), :]


class TransformerNetwork(nn.Module):
    """PyTorch Transformer network for sequence modeling."""

    def __init__(self, input_size, d_model, num_heads, num_layers, dropout, output_size):
        super(TransformerNetwork, self).__init__()
        self.input_proj = nn.Linear(input_size, d_model)
        self.pos_encoder = PositionalEncoding(d_model)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=num_heads,
            dim_feedforward=d_model * 4,
            dropout=dropout,
            batch_first=True
        )
        self.transformer_encoder = nn.TransformerEncoder(
            encoder_layer, num_layers=num_layers
        )

        self.output_proj = nn.Sequential(
            nn.Linear(d_model, d_model // 2),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(d_model // 2, output_size)
        )

    def forward(self, x):
        x = self.input_proj(x)
        x = self.pos_encoder(x)
        x = self.transformer_encoder(x)
        x = x[:, -1, :]
        x = self.output_proj(x)
        return x


class TransformerModel:
    """
    Transformer model wrapper for training, prediction, and evaluation.

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
            y = np.array(y, dtype=np.float32 if self.task_type == "regression" else np.int64)
            return X_scaled, y
        return X_scaled

    def _create_sequences(self, X, y, sequence_length):
        X_seq, y_seq = [], []
        for i in range(len(X) - sequence_length):
            X_seq.append(X[i:i + sequence_length])
            y_seq.append(y[i + sequence_length])
        return np.array(X_seq), np.array(y_seq)

    @staticmethod
    def _prediction_sequences(X, sequence_length):
        """Build inference windows and left-pad short requests with the first row."""
        if len(X) == 0:
            raise ValueError("Prediction dataset must contain at least one row")
        if len(X) < sequence_length:
            padding = np.repeat(X[[0]], sequence_length - len(X), axis=0)
            return np.array([np.concatenate([padding, X], axis=0)])
        return np.array([X[i:i + sequence_length]
                         for i in range(len(X) - sequence_length + 1)])

    def train(self, X, y, task_type="regression", params=None):
        """
        Train the Transformer model on provided data.

        Args:
            X: Input features (array-like)
            y: Target values (array-like)
            task_type: 'regression' or 'classification'
            params: Dictionary of model hyperparameters

        Returns:
            dict with training metrics and model ID
        """
        self.task_type = task_type
        self.params = params or {}

        d_model = self.params.get("d_model", 64)
        num_heads = self.params.get("num_heads", 4)
        num_layers = self.params.get("num_layers", 2)
        dropout = self.params.get("dropout", 0.1)
        learning_rate = self.params.get("learning_rate", 0.001)
        epochs = self.params.get("epochs", 100)
        batch_size = self.params.get("batch_size", 32)
        sequence_length = self.params.get("sequence_length", 10)
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

        self.network = TransformerNetwork(
            input_size=self.input_size,
            d_model=d_model,
            num_heads=num_heads,
            num_layers=num_layers,
            dropout=dropout,
            output_size=self.output_size
        )

        optimizer = torch.optim.Adam(self.network.parameters(), lr=learning_rate)
        criterion = nn.CrossEntropyLoss() if task_type == "classification" else nn.MSELoss()

        best_val_loss = float("inf")
        epochs_no_improve = 0
        training_history = {"train_loss": [], "val_loss": []}
        epoch = 0

        for epoch in range(epochs):
            self.network.train()
            train_loss = 0.0
            for batch_X, batch_y in train_loader:
                optimizer.zero_grad()
                output = self.network(batch_X)
                loss = criterion(
                    output if task_type == "classification" else output.squeeze(-1),
                    batch_y if task_type == "classification" else batch_y.squeeze(-1)
                )
                loss.backward()
                torch.nn.utils.clip_grad_norm_(self.network.parameters(), max_norm=1.0)
                optimizer.step()
                train_loss += loss.item()

            train_loss /= len(train_loader)

            self.network.eval()
            val_loss = 0.0
            with torch.no_grad():
                for batch_X, batch_y in val_loader:
                    output = self.network(batch_X)
                    loss = criterion(
                        output if task_type == "classification" else output.squeeze(-1),
                        batch_y if task_type == "classification" else batch_y.squeeze(-1)
                    )
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

        train_metrics = self._compute_metrics(self.network, train_loader, task_type)
        val_metrics = self._compute_metrics(self.network, val_loader, task_type)

        self.metadata = {
            "model_id": self.model_id,
            "algorithm": "transformer",
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
            "algorithm": "transformer",
            "task_type": task_type,
            "training_metrics": train_metrics,
            "validation_metrics": val_metrics,
            "epochs_trained": epoch + 1,
            "best_val_loss": best_val_loss
        }

    def predict(self, X, sequence_length=None):
        if not self.is_trained:
            raise ValueError("Model must be trained before prediction.")

        seq_len = sequence_length or self.metadata.get("sequence_length", 10)
        X_scaled = self._preprocess(X)

        X_seq = self._prediction_sequences(X_scaled, seq_len)

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
                    preds = output.squeeze().numpy()
                    targets = batch_y.squeeze().numpy()
                all_preds.extend(preds)
                all_targets.extend(targets)

        all_preds = np.array(all_preds)
        all_targets = np.array(all_targets)

        if task_type == "regression":
            return self._regression_metrics(all_targets, all_preds)
        else:
            return self._classification_metrics(all_targets, all_preds)

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

        model_path = os.path.join(path, MODEL_FILENAME)
        torch.save({
            "network_state_dict": self.network.state_dict() if self.network else None,
            "input_size": self.input_size,
            "output_size": self.output_size,
            "task_type": self.task_type,
            "params": self.params,
            "scaler_mean": self.scaler_mean.tolist() if self.scaler_mean is not None else None,
            "scaler_std": self.scaler_std.tolist() if self.scaler_std is not None else None,
            "class_to_idx": self.class_to_idx,
            "idx_to_class": self.idx_to_class
        }, model_path)

        metadata_path = os.path.join(path, METADATA_FILENAME)
        with open(metadata_path, "w") as f:
            json.dump(self.metadata, f, indent=2, default=str)

        return path

    def load(self, path):
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

        d_model = self.params.get("d_model", 64)
        num_heads = self.params.get("num_heads", 4)
        num_layers = self.params.get("num_layers", 2)
        dropout = self.params.get("dropout", 0.1)

        self.network = TransformerNetwork(
            input_size=self.input_size,
            d_model=d_model,
            num_heads=num_heads,
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
