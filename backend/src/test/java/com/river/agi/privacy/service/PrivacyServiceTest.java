package com.river.agi.privacy.service;

import com.river.agi.auth.entity.User;
import com.river.agi.auth.mapper.UserMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.privacy.entity.PrivacyConsent;
import com.river.agi.privacy.mapper.PrivacyConsentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("隐私政策服务测试")
class PrivacyServiceTest {

    @Mock
    private PrivacyConsentMapper privacyConsentMapper;
    @Mock
    private UserMapper userMapper;

    private PrivacyService privacyService;

    @BeforeEach
    void setUp() {
        privacyService = new PrivacyService(privacyConsentMapper, userMapper);
    }

    @Test
    @DisplayName("getPrivacyPolicy - 返回完整隐私政策")
    void getPrivacyPolicy_returnsFullPolicy() {
        Map<String, Object> policy = privacyService.getPrivacyPolicy();
        assertNotNull(policy);
        assertNotNull(policy.get("version"));
        assertNotNull(policy.get("title"));
        assertNotNull(policy.get("applicableLaws"));
        assertTrue(((List<?>) policy.get("applicableLaws")).size() > 0);
        assertNotNull(policy.get("sections"));
        assertTrue(((List<?>) policy.get("sections")).size() >= 6);
        assertNotNull(policy.get("dataSubjectRights"));
        assertEquals(365, policy.get("dataRetentionDays"));
    }

    @Test
    @DisplayName("getRetentionPolicy - 返回留存策略")
    void getRetentionPolicy_returnsPolicy() {
        Map<String, Object> policy = privacyService.getRetentionPolicy();
        assertNotNull(policy);
        assertEquals(365, policy.get("personalDataRetentionDays"));
        assertEquals(180, policy.get("auditLogRetentionDays"));
        assertEquals(true, policy.get("autoCleanupEnabled"));
    }

    @Test
    @DisplayName("recordConsent - 记录同意并落库")
    void recordConsent_insertsRecord() {
        when(privacyConsentMapper.insert(any())).thenReturn(1);

        PrivacyConsent consent = privacyService.recordConsent(1L, "alice", "REGISTER", "127.0.0.1", "ua");

        assertNotNull(consent);
        assertEquals(1L, consent.getUserId());
        assertEquals("alice", consent.getUsername());
        assertEquals("REGISTER", consent.getConsentType());
        assertEquals("127.0.0.1", consent.getIpAddress());
        assertNotNull(consent.getPolicyVersion());
        assertNotNull(consent.getConsentAt());

        ArgumentCaptor<PrivacyConsent> captor = ArgumentCaptor.forClass(PrivacyConsent.class);
        verify(privacyConsentMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("getConsentHistory - 查询用户同意历史")
    void getConsentHistory_returnsList() {
        PrivacyConsent c = new PrivacyConsent();
        c.setUserId(1L);
        when(privacyConsentMapper.selectList(any())).thenReturn(List.of(c));

        List<PrivacyConsent> history = privacyService.getConsentHistory(1L);
        assertNotNull(history);
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("hasCurrentConsent - 有记录返回 true")
    void hasCurrentConsent_true() {
        when(privacyConsentMapper.selectCount(any())).thenReturn(2L);
        assertTrue(privacyService.hasCurrentConsent(1L));
    }

    @Test
    @DisplayName("hasCurrentConsent - 无记录返回 false")
    void hasCurrentConsent_false() {
        when(privacyConsentMapper.selectCount(any())).thenReturn(0L);
        assertFalse(privacyService.hasCurrentConsent(1L));
    }

    @Test
    @DisplayName("exportPersonalData - 用户存在返回数据")
    void exportPersonalData_found() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPhone("13812348888");
        user.setRealName("Alice");
        user.setCreatedAt(LocalDateTime.now());
        when(userMapper.selectById(1L)).thenReturn(user);
        when(privacyConsentMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = privacyService.exportPersonalData(1L);
        assertNotNull(data);
        assertEquals(1L, data.get("userId"));
        assertEquals("alice", data.get("username"));
        assertNotNull(data.get("exportedAt"));
    }

    @Test
    @DisplayName("exportPersonalData - 用户不存在抛异常")
    void exportPersonalData_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> privacyService.exportPersonalData(99L));
    }

    @Test
    @DisplayName("deletePersonalData - 清除可识别字段")
    void deletePersonalData_clearsFields() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setPhone("13812348888");
        user.setRealName("Alice");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any())).thenReturn(1);

        Map<String, Object> result = privacyService.deletePersonalData(1L);
        assertEquals("DELETED", result.get("status"));
        assertNull(user.getEmail());
        assertNull(user.getPhone());
        assertNull(user.getRealName());
        verify(userMapper).updateById(user);
    }

    @Test
    @DisplayName("deletePersonalData - 用户不存在抛异常")
    void deletePersonalData_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> privacyService.deletePersonalData(99L));
    }

    @Test
    @DisplayName("getPolicyVersion - 返回版本号")
    void getPolicyVersion_returnsVersion() {
        assertNotNull(privacyService.getPolicyVersion());
    }
}
