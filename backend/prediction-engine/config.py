import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

MODEL_STORAGE_PATH = os.environ.get(
    "MODEL_STORAGE_PATH",
    os.path.join(BASE_DIR, "saved_models")
)

os.makedirs(MODEL_STORAGE_PATH, exist_ok=True)

HOST = os.environ.get("HOST", "0.0.0.0")
PORT = int(os.environ.get("PORT", "5000"))
DEBUG = os.environ.get("DEBUG", "false").lower() == "true"

DEFAULT_LSTM_PARAMS = {
    "hidden_size": 64,
    "num_layers": 2,
    "dropout": 0.2,
    "learning_rate": 0.001,
    "epochs": 100,
    "batch_size": 32,
    "sequence_length": 10,
    "patience": 10
}

DEFAULT_TRANSFORMER_PARAMS = {
    "d_model": 64,
    "num_heads": 4,
    "num_layers": 2,
    "dropout": 0.1,
    "learning_rate": 0.001,
    "epochs": 100,
    "batch_size": 32,
    "sequence_length": 10,
    "patience": 10
}

DEFAULT_MLP_PARAMS = {
    "hidden_layers": [128, 64, 32],
    "dropout_rate": 0.2,
    "learning_rate": 0.001,
    "epochs": 100,
    "batch_size": 32,
    "patience": 10
}

DEFAULT_SKLEARN_PARAMS = {
    "n_estimators": 100,
    "max_depth": None,
    "learning_rate": 0.1,
    "C": 1.0,
    "random_state": 42
}

SUPPORTED_ALGORITHMS = {
    "lstm": {
        "name": "LSTM",
        "description": "Long Short-Term Memory network for time series forecasting",
        "type": "sequence",
        "tasks": ["regression", "classification"],
        "params": DEFAULT_LSTM_PARAMS
    },
    "transformer": {
        "name": "Transformer",
        "description": "Transformer-based model for sequence prediction",
        "type": "sequence",
        "tasks": ["regression", "classification"],
        "params": DEFAULT_TRANSFORMER_PARAMS
    },
    "mlp": {
        "name": "MLP",
        "description": "Multi-Layer Perceptron using TensorFlow/Keras",
        "type": "feature",
        "tasks": ["regression", "classification"],
        "params": DEFAULT_MLP_PARAMS
    },
    "random_forest": {
        "name": "Random Forest",
        "description": "Random Forest ensemble classifier/regressor",
        "type": "feature",
        "tasks": ["regression", "classification"],
        "params": DEFAULT_SKLEARN_PARAMS
    },
    "gradient_boosting": {
        "name": "Gradient Boosting",
        "description": "Gradient Boosting ensemble classifier/regressor",
        "type": "feature",
        "tasks": ["regression", "classification"],
        "params": DEFAULT_SKLEARN_PARAMS
    },
    "svm": {
        "name": "SVM",
        "description": "Support Vector Machine classifier/regressor",
        "type": "feature",
        "tasks": ["regression", "classification"],
        "params": {"C": 1.0, "kernel": "rbf", "random_state": 42}
    }
}

METADATA_FILENAME = "metadata.json"
MODEL_FILENAME = "model.pkl"
TF_MODEL_FILENAME = "model.keras"
