# Debug Session: dataset-load-fail

## [OPEN] Status

## Symptom
- User reports "数据集加载失败" (Dataset loading failed)
- Happens when navigating to the Datasets page in the frontend

## Expected Behavior
- Datasets page should display a list of uploaded datasets
- Should show table with ID, name, file type, row count, column count, status, upload time

## Actual Behavior
- Page shows "加载失败" error message
- No dataset data is displayed

## Hypotheses (Falsifiable)

### H1: Invalid/Expired Token
- The user's browser has an old or expired JWT token in localStorage
- Backend returns 401, frontend shows "加载失败"
- **How to verify**: Check browser console for 401 errors; Check localStorage token value

### H2: Frontend API Response Handling Mismatch
- The frontend expects `data.records` but backend returns `data.records` with different structure
- The axios interceptor may not be passing the correct data shape
- **How to verify**: Check actual HTTP response from browser DevTools

### H3: CORS/Proxy Issue
- The Vite proxy is not correctly forwarding requests
- Or the request.baseURL causes double `/api/api` prefix
- **How to verify**: Check Network tab for actual requested URL

### H4: Frontend JavaScript Error
- There's a runtime error preventing data from being rendered
- e.g., null reference when accessing `data.records`
- **How to verify**: Check browser Console for JS errors

### H5: Backend Database Empty
- H2 in-memory database was reset and has no datasets
- But the API returns valid response with data, so this is less likely
- **How to verify**: Already verified via curl - data exists

## Evidence Collected So Far
- Direct curl to backend: ✅ Works, returns dataset data
- curl through frontend proxy: ✅ Works, returns dataset data
- Frontend dev server: ✅ Running on port 3000
- Backend API: ✅ Running on port 8080

## Next Steps
1. Use browser to access the page and capture console errors
2. Check localStorage token
3. Compare actual vs expected API response structure
