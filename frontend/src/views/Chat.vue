<template>
  <div class="chat-page">
    <header class="chat-header">
      <div class="header-copy">
        <div class="eyebrow">CONVERSATIONAL ANALYTICS</div>
        <h1>AI 对话分析</h1>
        <p>围绕当前数据集进行数据画像、质量检查、图表生成和需求预测。</p>
      </div>
      <div class="header-actions">
        <el-tag effect="dark" type="success">在线</el-tag>
        <el-tag effect="dark" type="info">{{ currentSession ? '会话已就绪' : '等待创建会话' }}</el-tag>
        <el-button type="primary" plain @click="createSession">
          <el-icon><Plus /></el-icon>
          新建对话
        </el-button>
      </div>
    </header>

    <div class="chat-layout">
      <aside class="sidebar">
        <section class="side-panel">
          <div class="panel-head">
            <span>数据集</span>
            <el-tag size="small" type="info">{{ datasets.length }} 个</el-tag>
          </div>
          <el-select v-model="selectedDataset" placeholder="选择数据集" class="dataset-select" filterable>
            <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
          <div class="selected-card">
            <strong>{{ selectedDatasetName || '未选择数据集' }}</strong>
            <span>先选数据，再开始提问</span>
          </div>
        </section>

        <section class="side-panel">
          <div class="panel-head">
            <span>历史会话</span>
            <el-button text type="primary" @click="loadSessions">刷新</el-button>
          </div>
          <div class="session-list">
            <button
              v-for="s in sessions"
              :key="s.id"
              :class="['session-item', { active: s.id === sessionId }]"
              @click="selectSession(s.id)"
            >
              <span class="session-dot"></span>
              <span class="session-copy">
                <strong>{{ s.title }}</strong>
                <small>{{ s.updatedAt || '最近对话' }}</small>
              </span>
            </button>
            <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="56" />
          </div>
        </section>

        <section class="side-panel">
          <div class="panel-head">
            <span>快捷提问</span>
          </div>
          <div class="quick-questions">
            <button v-for="question in quickQuestions" :key="question" class="quick-chip" @click="sendQuickQuestion(question)">
              <el-icon><MagicStick /></el-icon>
              <span>{{ question }}</span>
            </button>
          </div>
        </section>
      </aside>

      <main class="chat-main">
        <div class="chat-shell">
          <div class="chat-toolbar">
            <div class="chat-title">
              <div class="assistant-mark">R</div>
              <div>
                <strong>{{ currentSession?.title || 'RIver AGI 助手' }}</strong>
                <small><i></i>在线 · 已连接数据分析引擎</small>
              </div>
            </div>
            <el-button text class="new-chat-button" @click="createSession">
              <el-icon><Plus /></el-icon>
              新对话
            </el-button>
          </div>

          <div class="chat-messages" ref="messagesRef">
            <div v-if="messages.length === 0" class="empty-hint">
              <div class="welcome-icon"><el-icon><ChatDotRound /></el-icon></div>
              <h3>你好，我是 RIver AGI 助手</h3>
              <p>你可以直接让我分析数据质量、生成图表、扫描风险，或者预测未来趋势。</p>
              <div class="welcome-prompts">
                <button v-for="question in quickQuestions" :key="question" :disabled="!sessionId" @click="sendQuickQuestion(question)">
                  {{ question }}
                  <el-icon><ArrowUp /></el-icon>
                </button>
              </div>
            </div>

            <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
              <div class="avatar">{{ msg.role === 'USER' ? '我' : 'R' }}</div>
              <div class="message-body">
                <span class="message-role">{{ msg.role === 'USER' ? '你' : 'RIver AGI' }}</span>
                <div class="content">{{ msg.content }}</div>
              </div>
            </div>

            <div v-if="sending" class="message ASSISTANT">
              <div class="avatar">R</div>
              <div class="message-body">
                <span class="message-role">RIver AGI</span>
                <div class="content typing"><i></i><i></i><i></i></div>
              </div>
            </div>
          </div>

          <div class="chat-input">
            <div class="input-shell">
              <el-icon class="input-attachment"><Paperclip /></el-icon>
              <el-input
                v-model="inputMessage"
                type="textarea"
                :rows="3"
                resize="none"
                :placeholder="sessionId ? '输入你的问题...' : '请先创建或选择一个会话'"
                :disabled="!sessionId"
                @keyup.enter.ctrl="sendMessage"
              />
            </div>
            <div class="composer-actions">
              <el-button class="send-button" type="primary" :disabled="!sessionId || sending" @click="sendMessage">
                <el-icon><ArrowUp /></el-icon>
              </el-button>
              <small>Ctrl + Enter 发送，AI 结果请结合实际数据核验</small>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, ArrowUp, Paperclip, MagicStick, ChatDotRound } from '@element-plus/icons-vue'
import request from '../utils/request'
import { getActiveDatasetId, onDatasetSync } from '../utils/workspaceSync'

const datasets = ref([])
const selectedDataset = ref('')
const messages = ref([])
const inputMessage = ref('')
const sessionId = ref(null)
const sessions = ref([])
const messagesRef = ref(null)
const sending = ref(false)
const quickQuestions = ['分析数据质量', '推荐合适的图表', '检测异常值', '预测未来趋势']
const currentSession = computed(() => sessions.value.find(s => s.id === sessionId.value))
const selectedDatasetName = computed(() => datasets.value.find(ds => ds.id === selectedDataset.value)?.name || '')

onMounted(async () => {
  try {
    const data = await request.get('/v1/datasets?page=1&size=20')
    datasets.value = data.records || []
  } catch (e) {
    datasets.value = []
    ElMessage.error(`数据集加载失败：${e.message || '后端接口不可用'}`)
  }
  
  // 加载历史会话列表
  await loadSessions()
  const activeId = getActiveDatasetId()
  if (activeId) selectedDataset.value = Number(activeId)
  
  // 恢复上次的会话
  const savedSessionId = localStorage.getItem('chat_session_id')
  if (savedSessionId) {
    sessionId.value = parseInt(savedSessionId)
    await loadMessages(sessionId.value)
  }
})

const loadSessions = async () => {
  try {
    const data = await request.get('/v1/chat/sessions?page=1&size=50')
    sessions.value = data.records || []
  } catch (e) {
    sessions.value = []
  }
}

const loadMessages = async (sid) => {
  try {
    const data = await request.get(`/v1/chat/sessions/${sid}/messages`)
    messages.value = data || []
    await nextTick()
    scrollToBottom()
  } catch (e) {
    messages.value = []
  }
}

const selectSession = async (sid) => {
  sessionId.value = sid
  localStorage.setItem('chat_session_id', sid)
  await loadMessages(sid)
}

onDatasetSync(datasetId => {
  if (datasetId) selectedDataset.value = Number(datasetId)
})

const createSession = async () => {
  try {
    const url = selectedDataset.value 
      ? `/v1/chat/sessions?datasetId=${selectedDataset.value}` 
      : '/v1/chat/sessions'
    const session = await request.post(url)
    sessionId.value = session.id
    localStorage.setItem('chat_session_id', session.id)
    messages.value = []
    await loadSessions()
    ElMessage.success('会话已创建')
  } catch (e) {
    ElMessage.error('创建会话失败')
  }
}

const scrollToBottom = () => {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim()) return
  if (!sessionId.value) {
    ElMessage.warning('请先创建或选择一个会话')
    return
  }
  
  const userMsg = { id: Date.now(), role: 'USER', content: inputMessage.value }
  const messageToSend = inputMessage.value
  messages.value.push(userMsg)
  inputMessage.value = ''
  sending.value = true
  await nextTick()
  scrollToBottom()
  
  try {
    const res = await request.post(`/v1/chat/sessions/${sessionId.value}/messages`, {
      message: messageToSend
    })
    messages.value.push({ id: Date.now() + 1, role: 'ASSISTANT', content: res.reply })
    await nextTick()
    scrollToBottom()
  } catch (e) {
    messages.value.push({ id: Date.now() + 1, role: 'ASSISTANT', content: '抱歉，我暂时无法回答这个问题。' })
    await nextTick()
    scrollToBottom()
  }
  
  sending.value = false
}

const sendQuickQuestion = (question) => {
  if (!sessionId.value) {
    createSession().then(() => {
      inputMessage.value = question
      sendMessage()
    })
  } else {
    inputMessage.value = question
    sendMessage()
  }
}
</script>

<style scoped>
.chat-page {
  min-height: calc(100vh - 60px);
  color: #1f2937;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid #dbe5ec;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.eyebrow {
  margin-bottom: 8px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.chat-header h1 {
  margin: 0;
  font-size: 24px;
  color: #18212d;
}

.chat-header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chat-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-panel {
  padding: 16px;
  border: 1px solid #dbe5ec;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  color: #18212d;
  font-weight: 700;
}

.dataset-select {
  width: 100%;
}

.selected-card {
  margin-top: 12px;
  padding: 12px;
  border-radius: 12px;
  background: #f8fbfd;
  border: 1px solid #e6edf3;
}

.selected-card strong {
  display: block;
  margin-bottom: 4px;
  color: #18212d;
  font-size: 13px;
}

.selected-card span {
  color: #64748b;
  font-size: 11px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow: auto;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid #e6edf3;
  border-radius: 12px;
  background: #f9fbfc;
  color: #334155;
  cursor: pointer;
  text-align: left;
  transition: all 0.16s ease;
}

.session-item:hover {
  transform: translateX(2px);
  border-color: #b8dfd8;
  background: #eef9f7;
}

.session-item.active {
  border-color: #0f766e;
  background: #e2f3f0;
}

.session-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #0f766e;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.session-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.session-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.session-copy small {
  color: #94a3b8;
  font-size: 10px;
}

.quick-questions {
  display: grid;
  gap: 8px;
}

.quick-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e6edf3;
  border-radius: 12px;
  background: #fff;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition: all 0.16s ease;
}

.quick-chip:hover {
  transform: translateY(-1px);
  border-color: #b8dfd8;
  background: #f3fbfa;
}

.quick-chip .el-icon {
  color: #0f766e;
}

.chat-main {
  min-width: 0;
}

.chat-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 196px);
  min-height: 620px;
  border: 1px solid #dbe5ec;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid #e6edf3;
  background: #fbfdfe;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.assistant-mark,
.avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: #e2f3f0;
  color: #0f766e;
  font-weight: 800;
  flex-shrink: 0;
}

.chat-title strong {
  display: block;
  color: #18212d;
  font-size: 14px;
}

.chat-title small {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-size: 10px;
}

.chat-title small i {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 5px;
  border-radius: 50%;
  background: #22c55e;
}

.new-chat-button {
  color: #0f766e;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 26px clamp(18px, 4vw, 48px);
  background: #fff;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 22px;
  align-items: flex-start;
}

.message.USER {
  flex-direction: row-reverse;
}

.message.USER .content {
  background: #0f766e;
  color: #fff;
}

.message.USER .message-body {
  text-align: right;
}

.message-role {
  display: block;
  margin-bottom: 4px;
  color: #94a3b8;
  font-size: 10px;
}

.content {
  max-width: min(72vw, 760px);
  padding: 12px 15px;
  border-radius: 12px 16px 16px 16px;
  background: #f3f7fa;
  color: #334155;
  white-space: pre-wrap;
  line-height: 1.7;
  border: 1px solid #e6edf3;
}

.message.USER .content {
  border-radius: 16px 12px 16px 16px;
  display: inline-block;
  text-align: left;
}

.message-body {
  max-width: min(78%, 820px);
}

.typing {
  display: flex;
  gap: 4px;
  align-items: center;
  width: 54px;
}

.typing i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #0f766e;
  animation: typing 1.2s infinite ease-in-out;
}

.typing i:nth-child(2) {
  animation-delay: .15s;
}

.typing i:nth-child(3) {
  animation-delay: .3s;
}

@keyframes typing {
  0%, 60%, 100% {
    opacity: .3;
    transform: translateY(0);
  }

  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.empty-hint {
  text-align: center;
  color: #64748b;
  padding: clamp(56px, 13vh, 130px) 20px 36px;
  font-size: 13px;
}

.welcome-icon {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: #e2f3f0;
  color: #0f766e;
  font-size: 24px;
}

.empty-hint h3 {
  color: #18212d;
  font-size: 18px;
  margin-bottom: 8px;
}

.empty-hint p {
  max-width: 520px;
  margin: 0 auto;
  line-height: 1.7;
}

.welcome-prompts {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.welcome-prompts button {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 12px;
  border: 1px solid #dbe5ec;
  border-radius: 999px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  transition: all .16s;
}

.welcome-prompts button:hover:not(:disabled) {
  border-color: #b8dfd8;
  color: #0f766e;
  background: #f3fbfa;
  transform: translateY(-1px);
}

.welcome-prompts button:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 14px 18px 18px;
  border-top: 1px solid #e6edf3;
  background: #fbfdfe;
}

.input-shell {
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #dbe5ec;
  border-radius: 14px;
  background: #fff;
  transition: border-color .16s, box-shadow .16s;
}

.input-shell:focus-within {
  border-color: #0f766e;
  box-shadow: 0 0 0 3px #e2f3f0;
}

.input-attachment {
  color: #94a3b8;
  margin-top: 4px;
}

.input-shell :deep(.el-textarea__inner) {
  box-shadow: none;
  background: transparent;
  color: #18212d;
  border: none;
  padding: 0;
  min-height: 72px !important;
}

.input-shell :deep(.el-textarea__inner::placeholder) {
  color: #94a3b8;
}

.composer-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.send-button {
  width: 44px;
  height: 44px;
  border-radius: 14px;
}

.composer-actions small {
  color: #94a3b8;
  font-size: 10px;
  text-align: center;
}

@media (max-width: 1080px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }

  .sidebar {
    order: 2;
  }

  .chat-main {
    order: 1;
  }
}

@media (max-width: 760px) {
  .chat-header {
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }

  .chat-shell {
    height: auto;
    min-height: 0;
  }

  .chat-messages {
    padding: 20px 14px;
  }

  .chat-input {
    padding: 12px 14px 14px;
    flex-direction: column;
    align-items: stretch;
  }

  .composer-actions {
    align-items: flex-end;
  }

  .message-body {
    max-width: 86%;
  }

  .welcome-prompts {
    display: grid;
  }

  .welcome-prompts button {
    justify-content: space-between;
  }
}
</style>
