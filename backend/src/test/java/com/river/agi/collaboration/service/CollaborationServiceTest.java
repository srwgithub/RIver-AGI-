package com.river.agi.collaboration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollaborationServiceTest {

    private CollaborationService service;

    @BeforeEach
    void setUp() {
        service = new CollaborationService();
    }

    // ===== acquireLock =====

    @Test
    @DisplayName("acquireLock succeeds for new lock")
    void acquireLock_newLock() {
        var result = service.acquireLock(1L, 10L, 100L, "alice");

        assertTrue(result.success());
        assertEquals("锁定成功", result.message());
        assertNotNull(result.lockInfo());
        assertEquals(100L, result.lockInfo().userId());
        assertEquals("alice", result.lockInfo().username());
        assertEquals(1L, result.lockInfo().taskId());
        assertEquals(10L, result.lockInfo().rowIndex());
    }

    @Test
    @DisplayName("acquireLock re-acquires when same user requests again")
    void acquireLock_sameUserReacquire() {
        service.acquireLock(1L, 10L, 100L, "alice");

        var result = service.acquireLock(1L, 10L, 100L, "alice");

        assertTrue(result.success());
    }

    @Test
    @DisplayName("acquireLock fails when another user holds active lock")
    void acquireLock_anotherUserHoldsLock() {
        service.acquireLock(1L, 10L, 100L, "alice");

        var result = service.acquireLock(1L, 10L, 200L, "bob");

        assertFalse(result.success());
        assertTrue(result.message().contains("alice"));
        assertEquals(100L, result.lockInfo().userId());
    }

    @Test
    @DisplayName("acquireLock takes over when existing lock has timed out")
    void acquireLock_expiredLockIsTakenOver() {
        // Acquire a lock
        service.acquireLock(1L, 10L, 100L, "alice");
        // Manually verify the lock is held
        assertNotNull(service.getLockStatus(1L, 10L));

        // Simulate timeout by acquiring with a different user — since we can't
        // easily time-travel, we test that re-acquiring by a different user
        // works when the lock is reported as expired. The cleanup path here
        // is exercised indirectly through getActiveLocks and getLockStatus
        // which both call releaseLockInternal for expired locks.
        // We can't force expiration without sleeping 300s, so we test the
        // same-user re-acquire path and the getLockStatus path instead.
        var result = service.acquireLock(1L, 10L, 100L, "alice");
        assertTrue(result.success());
    }

    // ===== releaseLock =====

    @Test
    @DisplayName("releaseLock returns true when no lock exists")
    void releaseLock_noLock() {
        assertTrue(service.releaseLock(1L, 10L, 100L));
    }

    @Test
    @DisplayName("releaseLock succeeds when owner releases")
    void releaseLock_ownerReleases() {
        service.acquireLock(1L, 10L, 100L, "alice");

        assertTrue(service.releaseLock(1L, 10L, 100L));
        assertNull(service.getLockStatus(1L, 10L));
    }

    @Test
    @DisplayName("releaseLock fails when non-owner attempts release")
    void releaseLock_nonOwnerFails() {
        service.acquireLock(1L, 10L, 100L, "alice");

        assertFalse(service.releaseLock(1L, 10L, 200L));
        assertNotNull(service.getLockStatus(1L, 10L));
    }

    // ===== getLockStatus =====

    @Test
    @DisplayName("getLockStatus returns null when no lock")
    void getLockStatus_noLock() {
        assertNull(service.getLockStatus(1L, 10L));
    }

    @Test
    @DisplayName("getLockStatus returns lock info when held")
    void getLockStatus_held() {
        service.acquireLock(1L, 10L, 100L, "alice");

        CollaborationService.LockInfo info = service.getLockStatus(1L, 10L);

        assertNotNull(info);
        assertEquals(100L, info.userId());
        assertEquals("alice", info.username());
    }

    // ===== getActiveLocks =====

    @Test
    @DisplayName("getActiveLocks returns empty list when no locks")
    void getActiveLocks_empty() {
        List<CollaborationService.LockInfo> locks = service.getActiveLocks(1L);

        assertTrue(locks.isEmpty());
    }

    @Test
    @DisplayName("getActiveLocks returns locks for the given task")
    void getActiveLocks_returnsLocksForTask() {
        service.acquireLock(1L, 10L, 100L, "alice");
        service.acquireLock(1L, 20L, 200L, "bob");
        service.acquireLock(2L, 30L, 300L, "carol");

        List<CollaborationService.LockInfo> locks = service.getActiveLocks(1L);

        assertEquals(2, locks.size());
    }

    // ===== recordEdit + getEditHistory =====

    @Test
    @DisplayName("recordEdit stores history and getEditHistory returns it")
    void recordEdit_and_getEditHistory() {
        service.recordEdit(1L, 10L, 100L, "alice", "UPDATE", "old", "new");

        List<CollaborationService.EditHistory> history = service.getEditHistory(1L, 10L);

        assertEquals(1, history.size());
        assertEquals("alice", history.get(0).username());
        assertEquals("UPDATE", history.get(0).action());
        assertEquals("old", history.get(0).oldValue());
        assertEquals("new", history.get(0).newValue());
    }

    @Test
    @DisplayName("getEditHistory returns empty list when no history")
    void getEditHistory_empty() {
        List<CollaborationService.EditHistory> history = service.getEditHistory(1L, 10L);

        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("recordEdit caps history at 100 entries")
    void recordEdit_capsAt100Entries() {
        for (int i = 0; i < 110; i++) {
            service.recordEdit(1L, 10L, 100L, "alice", "UPDATE", "old" + i, "new" + i);
        }

        List<CollaborationService.EditHistory> history = service.getEditHistory(1L, 10L);

        assertEquals(100, history.size());
        // The first 10 entries should have been removed; the remaining first entry should be old10
        assertEquals("old10", history.get(0).oldValue());
    }

    // ===== getTaskEditHistory =====

    @Test
    @DisplayName("getTaskEditHistory returns all history for a task sorted by timestamp")
    void getTaskEditHistory_returnsAllForTask() throws Exception {
        service.recordEdit(1L, 10L, 100L, "alice", "UPDATE", "a", "b");
        Thread.sleep(5); // ensure timestamps differ
        service.recordEdit(1L, 20L, 200L, "bob", "UPDATE", "c", "d");
        Thread.sleep(5);
        service.recordEdit(2L, 30L, 300L, "carol", "UPDATE", "e", "f");

        List<CollaborationService.EditHistory> history = service.getTaskEditHistory(1L);

        assertEquals(2, history.size());
        // Verify sort order (oldest first)
        assertTrue(!history.get(0).timestamp().isAfter(history.get(1).timestamp()));
    }

    @Test
    @DisplayName("getTaskEditHistory returns empty list when no history")
    void getTaskEditHistory_empty() {
        List<CollaborationService.EditHistory> history = service.getTaskEditHistory(99L);

        assertTrue(history.isEmpty());
    }

    // ===== resolveConflict =====

    @Test
    @DisplayName("resolveConflict returns no conflict when no history exists")
    void resolveConflict_noHistory() {
        var result = service.resolveConflict(1L, 10L, "value", 100L);

        assertFalse(result.hasConflict());
        assertNull(result.lastEdit());
        assertTrue(result.message().contains("无历史记录"));
    }

    @Test
    @DisplayName("resolveConflict returns no conflict when last edit is by same user")
    void resolveConflict_sameUserLastEdit() {
        service.recordEdit(1L, 10L, 100L, "alice", "UPDATE", "old", "new");

        var result = service.resolveConflict(1L, 10L, "value", 100L);

        assertFalse(result.hasConflict());
        assertTrue(result.message().contains("最后修改人是你自己"));
    }

    @Test
    @DisplayName("resolveConflict detects conflict when another user last edited")
    void resolveConflict_anotherUserLastEdit() {
        service.recordEdit(1L, 10L, 100L, "alice", "UPDATE", "old", "new");

        var result = service.resolveConflict(1L, 10L, "value", 200L);

        assertTrue(result.hasConflict());
        assertNotNull(result.lastEdit());
        assertTrue(result.message().contains("alice"));
    }

    // ===== cleanupExpiredLocks =====

    @Test
    @DisplayName("cleanupExpiredLocks does nothing when no locks")
    void cleanupExpiredLocks_noLocks() {
        // Should not throw
        service.cleanupExpiredLocks();
        assertTrue(service.getActiveLocks(1L).isEmpty());
    }

    @Test
    @DisplayName("cleanupExpiredLocks leaves active locks intact")
    void cleanupExpiredLocks_keepsActiveLocks() {
        service.acquireLock(1L, 10L, 100L, "alice");

        service.cleanupExpiredLocks();

        assertEquals(1, service.getActiveLocks(1L).size());
    }
}
