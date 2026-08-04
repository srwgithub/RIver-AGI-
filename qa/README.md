# Local QA Environment

All Python QA packages are installed in `backend/prediction-engine/.venv`.

## Verify the environment

```bash
cd backend
prediction-engine/.venv/bin/python -c "import tensorflow, torch, pytest, selenium; print(tensorflow.__version__, torch.__version__, pytest.__version__, selenium.__version__)"
```

## Run Python tests

```bash
cd backend
prediction-engine/.venv/bin/pytest -q
```

## Run browser checks

The frontend already has the local Playwright package in `frontend/node_modules`.
The test runner uses the installed Google Chrome executable because this macOS
environment does not provide a compatible Playwright-downloaded Chromium build:

```text
/Applications/Google Chrome.app/Contents/MacOS/Google Chrome
```

No browser binary is downloaded to a temporary directory.
