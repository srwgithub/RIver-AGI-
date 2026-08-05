"""
Prediction models package - lazy loading to allow graceful degradation
when optional ML frameworks (PyTorch, TensorFlow) are not installed.
"""

def __getattr__(name):
    if name == "LSTMModel":
        from .lstm_model import LSTMModel
        return LSTMModel
    elif name == "TransformerModel":
        from .transformer_model import TransformerModel
        return TransformerModel
    elif name == "MLPModel":
        from .mlp_model import MLPModel
        return MLPModel
    elif name == "SklearnModel":
        from .sklearn_model import SklearnModel
        return SklearnModel
    raise AttributeError(f"module 'models' has no attribute {name!r}")


__all__ = ["LSTMModel", "TransformerModel", "MLPModel", "SklearnModel"]
