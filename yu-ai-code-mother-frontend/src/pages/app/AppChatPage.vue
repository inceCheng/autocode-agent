<template>
  <div id="appChatPage">
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
      </div>
      <div class="header-right">
        <a-button type="default" @click="showAppDetail">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>
        <a-button
          type="primary"
          ghost
          @click="downloadCode"
          :loading="downloading"
          :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>
        <a-button type="primary" @click="deployApp" :loading="deploying">
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署
        </a-button>
      </div>
    </div>

    <div class="main-content">
      <div class="chat-section">
        <div class="messages-container" ref="messagesContainer">
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <template v-for="(block, bIndex) in parseAiMessage(message.content)" :key="bIndex">
                  <MarkdownRenderer v-if="block.type === 'text'" :content="block.content" />

                  <div v-else-if="block.type === 'file'" class="file-action-block">
                    <div
                      class="file-action-header"
                      :class="{ 'is-clickable': block.status === 'done' }"
                      @click="block.status === 'done' && toggleFile(index, block.path)"
                    >
                      <div class="file-info">
                        <SettingOutlined
                          v-if="block.status === 'writing'"
                          spin
                          style="color: #1890ff"
                        />
                        <CheckCircleFilled v-else style="color: #52c41a" />
                        <span class="file-path">{{ block.path }}</span>
                        <span v-if="block.status === 'writing'" class="file-status-text"
                          >正在写入...</span
                        >
                      </div>
                      <div v-if="block.status === 'done'" class="file-toggle-icon">
                        <DownOutlined
                          :class="{ 'is-expanded': isFileExpanded(index, block.path) }"
                        />
                      </div>
                    </div>

                    <div
                      v-if="isFileExpanded(index, block.path) && block.status === 'done'"
                      class="file-code-container"
                    >
                      <MarkdownRenderer
                        :content="'```' + getFileLanguage(block.path) + '\n' + block.code + '\n```'"
                      />
                    </div>
                  </div>
                </template>

                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>AI 正在思考...</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <a-alert
          v-if="selectedElementInfo"
          class="selected-element-alert"
          type="info"
          closable
          @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.prevent="sendMessage"
                :disabled="isGenerating || isAutoDeploying || !isOwner"
              />
            </a-tooltip>
            <a-textarea
              v-else
              v-model:value="userInput"
              :placeholder="getInputPlaceholder()"
              :rows="4"
              :maxlength="1000"
              @keydown.enter.prevent="sendMessage"
              :disabled="isGenerating || isAutoDeploying"
            />
            <div class="input-actions">
              <a-button
                type="primary"
                @click="sendMessage"
                :loading="isGenerating || isAutoDeploying"
                :disabled="!isOwner"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>
      <div class="preview-section">
        <div class="preview-header">
          <h3>生成后的网页展示</h3>
          <div class="preview-actions">
            <a-button
              v-if="isOwner && previewUrl"
              type="link"
              :danger="isEditMode"
              @click="toggleEditMode"
              :class="{ 'edit-mode-active': isEditMode }"
              style="padding: 0; height: auto; margin-right: 12px"
            >
              <template #icon>
                <EditOutlined />
              </template>
              {{ isEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="!previewUrl && !isGenerating && !isAutoDeploying" class="preview-placeholder">
            <div class="placeholder-icon">🌐</div>
            <p>网站文件生成完成后将在这里展示</p>
          </div>
          <div v-else-if="isGenerating" class="preview-loading">
            <a-spin size="large" />
            <p>正在生成代码...</p>
          </div>
          <div v-else-if="isAutoDeploying" class="preview-loading">
            <a-spin size="large" />
            <p style="color: #1890ff; margin-top: 16px">代码生成完毕，正在自动部署网站...</p>
          </div>
          <iframe
            v-else
            :key="previewUrl"
            :src="previewUrl"
            class="preview-iframe"
            frameborder="0"
            @load="onIframeLoad"
          ></iframe>
        </div>
      </div>
    </div>

    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { CHAT_API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'
import { SettingOutlined, CheckCircleFilled, DownOutlined } from '@ant-design/icons-vue'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<string>('')

// 对话相关
interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const isAutoDeploying = ref(false) // 💡 新增：记录自动部署状态
const messagesContainer = ref<HTMLElement>()

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// --- 打字机效果相关变量 ---
const typingSpeed = 2 // 每个字符出现的间隔（毫秒），建议 15-30 之间
let typingTimer: any = null
let contentBuffer = '' // 待显示的字符缓冲区

// 下载相关
const downloading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  if (!appInfo.value?.userId || !loginUserStore.loginUser.id) return false
  return String(appInfo.value.userId) === String(loginUserStore.loginUser.id)
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

// 应用详情相关
const appDetailVisible = ref(false)

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value,
      pageSize: 10,
    }
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)

    if (res.data.code === 0) {
      if (res.data.data) {
        const chatHistories = res.data.data.records || []
        if (chatHistories.length > 0) {
          const historyMessages: Message[] = chatHistories
            .map((chat) => ({
              type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
              content: chat.message || '',
              createTime: chat.createTime,
            }))
            .reverse()
          if (isLoadMore) {
            messages.value.unshift(...historyMessages)
          } else {
            messages.value = historyMessages
          }
          lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
          hasMoreHistory.value = chatHistories.length === 10
        } else {
          hasMoreHistory.value = false
        }
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 获取应用信息
const fetchAppInfo = async () => {
  const taskId = route.params.id as string
  const realAppId = route.query.appId as string
  if (!taskId || !realAppId) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = realAppId

  try {
    const res = await getAppVoById({ id: realAppId })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      // 先加载对话历史
      await loadChatHistory()
      updatePreview()

      if (isOwner.value && historyLoaded.value) {
        if (messages.value.length === 0 && appInfo.value.initPrompt) {
          await sendInitialMessage(appInfo.value.initPrompt)
        } else if (messages.value.length > 0) {
          const lastMessage = messages.value[messages.value.length - 1]
          if (lastMessage.type === 'user') {
            console.log('检测到最后一条是用户消息，准备触发 AI 接口...')
            const aiMessageIndex = messages.value.length
            messages.value.push({
              type: 'ai',
              content: '',
              loading: true,
            })
            await nextTick()
            scrollToBottom()
            isGenerating.value = true
            await generateCode(lastMessage.content, aiMessageIndex)
          }
        }
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  isGenerating.value = true
  await generateCode(prompt, aiMessageIndex)
}

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value || isAutoDeploying.value) {
    return
  }

  let message = userInput.value.trim()
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    message += elementContext
  }
  userInput.value = ''
  messages.value.push({
    type: 'user',
    content: message,
  })

  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }

  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  isGenerating.value = true
  await generateCode(message, aiMessageIndex)
}

// 生成代码
const generateCode = async (_userMessage: string, aiMessageIndex: number) => {
  let streamCompleted = false

  contentBuffer = ''
  messages.value[aiMessageIndex].content = ''
  messages.value[aiMessageIndex].loading = true

  startTyping(aiMessageIndex)

  try {
    const baseURL = CHAT_API_BASE_URL
    const currentToken = route.query.token as string
    const taskId = route.params.id as string
    const appIdStr = route.query.appId as string

    const response = await fetch(`${baseURL}/api/ai/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ task_id: taskId, token: currentToken, app_id: appIdStr }),
      credentials: 'include',
    })

    if (!response.ok || !response.body) {
      throw new Error('网络请求失败或响应体为空')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (!streamCompleted) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split(/\r?\n\r?\n/)
      buffer = parts.pop() || ''

      for (const part of parts) {
        if (streamCompleted) continue
        const trimmed = part.trim()
        if (!trimmed) continue

        const lines = trimmed.split(/\r?\n/)
        let data = ''
        let eventType = 'message'

        for (const line of lines) {
          if (line.startsWith('event:')) eventType = line.slice(6).trim()
          else if (line.startsWith('data:')) data = line.slice(5).trimStart()
        }

        if (eventType === 'done' || data === '[DONE]') {
          streamCompleted = true
          isGenerating.value = false

          // 💡 核心修改：移除 setInterval 轮询，替换为立刻自动部署
          isAutoDeploying.value = true
          try {
            const deployRes = await deployAppApi({
              appId: appId.value,
            })

            if (deployRes.data.code === 0 && deployRes.data.data) {
              // message.success('自动部署成功！')
              deployUrl.value = deployRes.data.data // 保存链接以供后续使用

              // 部署成功后，重新获取信息并刷新 Iframe
              await fetchAppInfo()
              updatePreview()
            } else {
              message.error('自动部署失败：' + deployRes.data.message)
            }
          } catch (deployError) {
            console.error('自动部署异常：', deployError)
            message.error('自动部署异常，请重试')
          } finally {
            isAutoDeploying.value = false // 结束部署 loading 状态，iframe 此时会显示
          }
        } else if (eventType === 'business-error') {
          streamCompleted = true
          isGenerating.value = false
          messages.value[aiMessageIndex].content = `❌ 发生错误: ${data}`
          messages.value[aiMessageIndex].loading = false
        } else if (data) {
          try {
            const parsed = JSON.parse(data)
            const delta = parsed.choices?.[0]?.delta

            if (delta) {
              if (delta.content) {
                const isToolBlock =
                  delta.content.includes('{"type": "tool_call"') ||
                  delta.content.includes('{"type": "tool_result"')

                if (isToolBlock) {
                  if (contentBuffer.length > 0) {
                    messages.value[aiMessageIndex].content += contentBuffer
                    contentBuffer = ''
                  }
                  messages.value[aiMessageIndex].content += delta.content
                  messages.value[aiMessageIndex].loading = false

                  nextTick(() => {
                    scrollToBottom()
                  })
                } else {
                  contentBuffer += delta.content
                  messages.value[aiMessageIndex].loading = false
                }
              }

              if (delta.metadata?.files && Array.isArray(delta.metadata.files)) {
                delta.metadata.files.forEach((file: any) => {
                  contentBuffer += `\n\n✅ **[已创建文件]** \`${file.filename}\``
                })
              }
            }
          } catch (e) {
            contentBuffer += data
          }
        }
      }
    }
  } catch (error) {
    console.error('流式生成失败:', error)
    isGenerating.value = false
    messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误。'
    messages.value[aiMessageIndex].loading = false
  }
}

const startTyping = (aiMessageIndex: number) => {
  if (typingTimer) return

  typingTimer = setInterval(() => {
    if (contentBuffer.length > 0) {
      const dynamicChunkSize = Math.max(1, Math.floor(contentBuffer.length / 10))
      const chunk = contentBuffer.substring(0, dynamicChunkSize)
      messages.value[aiMessageIndex].content += chunk
      contentBuffer = contentBuffer.substring(dynamicChunkSize)
      scrollToBottom()
    } else {
      if (!isGenerating.value) {
        clearInterval(typingTimer)
        typingTimer = null
      }
    }
  }, typingSpeed)
}

const updatePreview = () => {
  if (appId.value) {
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
    const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value, appInfo.value)
    previewUrl.value = newPreviewUrl
    previewReady.value = true
  }
}

const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 点击顶部手动部署的逻辑（原样保留弹窗交互）
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true // 手动点击部署后继续弹窗提示
      message.success('部署成功')
      // 部署成功后，重新获取信息并刷新 Iframe
      await fetchAppInfo()
      updatePreview()
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

const expandedFiles = ref<Record<string, boolean>>({})

const toggleFile = (messageIndex: number, path: string) => {
  const key = `${messageIndex}_${path}`
  expandedFiles.value[key] = !expandedFiles.value[key]
}

const isFileExpanded = (messageIndex: number, path: string) => {
  return !!expandedFiles.value[`${messageIndex}_${path}`]
}

const getFileLanguage = (path: string) => {
  const ext = path.split('.').pop()?.toLowerCase()
  const map: Record<string, string> = {
    js: 'javascript',
    ts: 'typescript',
    vue: 'vue',
    html: 'html',
    css: 'css',
    json: 'json',
  }
  return map[ext || ''] || ''
}

const parseAiMessage = (content: string) => {
  if (!content) return []

  const blocks: any[] = []
  let currentText = ''
  let i = 0

  const flushText = () => {
    if (currentText.trim()) blocks.push({ type: 'text', content: currentText })
    currentText = ''
  }

  while (i < content.length) {
    const remaining = content.slice(i)
    const match = remaining.match(/\{\s*"type"\s*:\s*"(tool_call|tool_result)"/)

    if (!match || match.index === undefined) {
      currentText += remaining
      break
    }

    const startIndex = i + match.index
    currentText += content.slice(i, startIndex)

    let braceCount = 0
    let inString = false
    let escape = false
    let endIndex = -1

    for (let j = startIndex; j < content.length; j++) {
      const char = content[j]
      if (escape) {
        escape = false
        continue
      }
      if (char === '\\') {
        escape = true
        continue
      }
      if (char === '"') {
        inString = !inString
        continue
      }
      if (!inString) {
        if (char === '{') braceCount++
        if (char === '}') {
          braceCount--
          if (braceCount === 0) {
            endIndex = j
            break
          }
        }
      }
    }

    if (endIndex !== -1) {
      flushText()
      const jsonStr = content.slice(startIndex, endIndex + 1)
      try {
        const jsonObj = JSON.parse(jsonStr)

        if (jsonObj.type === 'tool_call' && jsonObj.action === 'write_file') {
          blocks.push({
            type: 'file',
            path: jsonObj.path || 'unknown_file',
            status: 'writing',
            code: '',
          })
        } else if (jsonObj.type === 'tool_result' && jsonObj.action === 'write_file') {
          const fileBlock = blocks.find((b) => b.type === 'file' && b.path === jsonObj.path)
          if (fileBlock) {
            fileBlock.status = 'done'
            fileBlock.code = jsonObj.content || ''
          } else {
            blocks.push({
              type: 'file',
              path: jsonObj.path,
              status: 'done',
              code: jsonObj.content || '',
            })
          }
        }
      } catch (e) {
        currentText += jsonStr
      }
      i = endIndex + 1
    } else {
      break
    }
  }
  flushText()
  return blocks
}

const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

const onIframeLoad = () => {
  previewReady.value = true
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

const toggleEditMode = () => {
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (!iframe) {
    message.warning('请等待页面加载完成')
    return
  }
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

const onWindowMessage = (event: MessageEvent) => {
  visualEditor.handleIframeMessage(event)
}

onMounted(() => {
  fetchAppInfo()
  window.addEventListener('message', onWindowMessage)
})

onUnmounted(() => {
  if (typingTimer) {
    clearInterval(typingTimer)
    typingTimer = null
  }
  window.removeEventListener('message', onWindowMessage)
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fdfdfd;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  padding-right: 50px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
}

/* 右侧预览区域 */
.preview-section {
  flex: 3;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-iframe {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: none;
}

.selected-element-alert {
  margin: 0 16px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: 50vh;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px 16px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
  }

  .message-content {
    max-width: 85%;
  }

  /* 选中元素信息样式 */
  .selected-element-alert {
    margin: 0 16px;
  }

  .selected-element-info {
    line-height: 1.4;
  }

  .element-header {
    margin-bottom: 8px;
  }

  .element-details {
    margin-top: 8px;
  }

  .element-item {
    margin-bottom: 4px;
    font-size: 13px;
  }

  .element-item:last-child {
    margin-bottom: 0;
  }

  .element-tag {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #007bff;
  }

  .element-id {
    color: #28a745;
    margin-left: 4px;
  }

  .element-class {
    color: #ffc107;
    margin-left: 4px;
  }

  .element-selector-code {
    font-family: 'Monaco', 'Menlo', monospace;
    background: #f6f8fa;
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: #d73a49;
    border: 1px solid #e1e4e8;
  }

  /* 编辑模式按钮样式 */
  .edit-mode-active {
    background-color: #52c41a !important;
    border-color: #52c41a !important;
    color: white !important;
  }

  .edit-mode-active:hover {
    background-color: #73d13d !important;
    border-color: #73d13d !important;
  }
}

/* 文件操作块样式 */
.file-action-block {
  margin: 12px 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.file-action-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f8fafc;
  transition: background 0.2s ease;
}

.file-action-header.is-clickable {
  cursor: pointer;
}

.file-action-header.is-clickable:hover {
  background: #f1f5f9;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-path {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  font-weight: 500;
  color: #334155;
}

.file-status-text {
  font-size: 12px;
  color: #64748b;
  animation: pulse 1.5s infinite;
}

.file-toggle-icon {
  color: #94a3b8;
  font-size: 12px;
  transition: transform 0.3s ease;
}

.file-toggle-icon .is-expanded {
  transform: rotate(180deg);
}

.file-code-container {
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
  padding: 0;
  max-height: 500px;
  overflow-y: auto;
}

.file-code-container pre {
  margin: 0;
  white-space: pre-wrap;
}

.file-code-container code {
  color: #e2e8f0;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
}

@keyframes pulse {
  0% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0.6;
  }
}
</style>
