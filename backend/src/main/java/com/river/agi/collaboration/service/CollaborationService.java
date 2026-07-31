package com.river.agi.collaboration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CollaborationService {
    
    private final Map<String, LockInfo> activeLocks = new ConcurrentHashMap<>();
    private final Map<String, List<EditHistory>> editHistoryMap = new ConcurrentHashMap<>();
    private static final long LOCK_TIMEOUT_SECONDS = 300;
    
    public synchronized LockResult acquireLock(Long taskId, Long rowIndex, Long userId, String username) {
        String lockKey = taskId + ":" + rowIndex;
        
        LockInfo existingLock = activeLocks.get(lockKey);
        
        if (existingLock != null) {
            long elapsed = java.time.Duration.between(existingLock.acquiredAt, LocalDateTime.now()).getSeconds();
            
            if (elapsed < LOCK_TIMEOUT_SECONDS && !existingLock.userId.equals(userId)) {
                return new LockResult(false, "数据已被 " + existingLock.username + " 锁定", existingLock);
            }
            
            if (elapsed >= LOCK_TIMEOUT_SECONDS) {
                log.warn("Lock timeout for {} - forcing release", lockKey);
                releaseLockInternal(lockKey);
            }
        }
        
        LockInfo newLock = new LockInfo(userId, username, LocalDateTime.now(), taskId, rowIndex);
        activeLocks.put(lockKey, newLock);
        
        log.info("Lock acquired: taskId={}, rowIndex={}, userId={}", taskId, rowIndex, userId);
        return new LockResult(true, "锁定成功", newLock);
    }
    
    public synchronized boolean releaseLock(Long taskId, Long rowIndex, Long userId) {
        String lockKey = taskId + ":" + rowIndex;
        LockInfo lock = activeLocks.get(lockKey);
        
        if (lock == null) {
            return true;
        }
        
        if (!lock.userId.equals(userId)) {
            log.warn("User {} tried to release lock owned by {}", userId, lock.userId);
            return false;
        }
        
        releaseLockInternal(lockKey);
        log.info("Lock released: taskId={}, rowIndex={}, userId={}", taskId, rowIndex, userId);
        return true;
    }
    
    public synchronized LockInfo getLockStatus(Long taskId, Long rowIndex) {
        String lockKey = taskId + ":" + rowIndex;
        LockInfo lock = activeLocks.get(lockKey);
        
        if (lock == null) {
            return null;
        }
        
        long elapsed = java.time.Duration.between(lock.acquiredAt, LocalDateTime.now()).getSeconds();
        if (elapsed >= LOCK_TIMEOUT_SECONDS) {
            releaseLockInternal(lockKey);
            return null;
        }
        
        return lock;
    }
    
    public List<LockInfo> getActiveLocks(Long taskId) {
        List<LockInfo> locks = new ArrayList<>();
        
        for (Map.Entry<String, LockInfo> entry : activeLocks.entrySet()) {
            if (entry.getKey().startsWith(taskId + ":")) {
                LockInfo lock = entry.getValue();
                long elapsed = java.time.Duration.between(lock.acquiredAt, LocalDateTime.now()).getSeconds();
                if (elapsed < LOCK_TIMEOUT_SECONDS) {
                    locks.add(lock);
                } else {
                    releaseLockInternal(entry.getKey());
                }
            }
        }
        
        return locks;
    }
    
    public void recordEdit(Long taskId, Long rowIndex, Long userId, String username, 
                           String action, String oldValue, String newValue) {
        String historyKey = taskId + ":" + rowIndex;
        EditHistory edit = new EditHistory(
            userId, username, rowIndex, action, oldValue, newValue, LocalDateTime.now()
        );
        
        editHistoryMap.computeIfAbsent(historyKey, k -> Collections.synchronizedList(new ArrayList<>()))
                      .add(edit);
        
        if (editHistoryMap.get(historyKey).size() > 100) {
            editHistoryMap.get(historyKey).remove(0);
        }
    }
    
    public List<EditHistory> getEditHistory(Long taskId, Long rowIndex) {
        String historyKey = taskId + ":" + rowIndex;
        return editHistoryMap.getOrDefault(historyKey, new ArrayList<>());
    }
    
    public List<EditHistory> getTaskEditHistory(Long taskId) {
        List<EditHistory> history = new ArrayList<>();
        
        for (Map.Entry<String, List<EditHistory>> entry : editHistoryMap.entrySet()) {
            if (entry.getKey().startsWith(taskId + ":")) {
                history.addAll(entry.getValue());
            }
        }
        
        history.sort(Comparator.comparing(EditHistory::timestamp));
        return history;
    }
    
    public ConflictResult resolveConflict(Long taskId, Long rowIndex, 
                                           String currentValue, Long userId) {
        String historyKey = taskId + ":" + rowIndex;
        List<EditHistory> history = editHistoryMap.get(historyKey);
        
        if (history == null || history.isEmpty()) {
            return new ConflictResult(false, null, "无历史记录，保留当前修改");
        }
        
        EditHistory lastEdit = history.get(history.size() - 1);
        
        if (lastEdit.userId.equals(userId)) {
            return new ConflictResult(false, null, "最后修改人是你自己，无冲突");
        }
        
        return new ConflictResult(
            true,
            lastEdit,
            "检测到冲突：字段最近由 " + lastEdit.username + " 在 " + lastEdit.timestamp + " 修改"
        );
    }
    
    public void cleanupExpiredLocks() {
        int cleaned = 0;
        for (Map.Entry<String, LockInfo> entry : activeLocks.entrySet()) {
            long elapsed = java.time.Duration.between(entry.getValue().acquiredAt, LocalDateTime.now()).getSeconds();
            if (elapsed >= LOCK_TIMEOUT_SECONDS) {
                releaseLockInternal(entry.getKey());
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("Cleaned up {} expired locks", cleaned);
        }
    }
    
    private void releaseLockInternal(String lockKey) {
        activeLocks.remove(lockKey);
    }
    
    public record LockInfo(Long userId, String username, LocalDateTime acquiredAt, 
                           Long taskId, Long rowIndex) {}
    
    public record LockResult(boolean success, String message, LockInfo lockInfo) {}
    
    public record EditHistory(Long userId, String username, Long rowIndex, 
                               String action, String oldValue, String newValue, 
                               LocalDateTime timestamp) {}
    
    public record ConflictResult(boolean hasConflict, EditHistory lastEdit, String message) {}
}
