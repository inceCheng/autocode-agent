/**
 * 环境变量配置
 */
import { CodeGenTypeEnum } from '@/utils/codeGenTypes.ts'

// 应用部署域名
export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api/v1'

// 聊天生成 API 基础地址（SSE 流式接口）
// 开发环境建议留空走代理，生产环境配置为实际域名
export const CHAT_API_BASE_URL = import.meta.env.VITE_CHAT_API_BASE_URL || ''

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的完整URL
export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${deployKey}`
}

/** 与后端按日期分目录时一致；接口未带 preview 字段时用创建日兜底 */
function formatDatePathFromCreateTime(createTime: string | null | undefined): string | undefined {
  if (!createTime) return undefined
  const d = new Date(createTime)
  if (Number.isNaN(d.getTime())) return undefined
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}/${m}/${day}`
}

/**
 * 从应用 VO 取预览父路径：camelCase / snake_case 兼容；皆空则用 createTime 得到 yyyy/MM/dd
 */
export type StaticPreviewAppFields = {
  previewPath?: string | null
  /** Jackson PropertyNamingStrategies.SNAKE_CASE 等 */
  preview_path?: string | null
  createTime?: string | null
}

function pickStaticPreviewParent(app?: StaticPreviewAppFields | null): string | undefined {
  if (!app) return undefined
  const fromApi =
    (app.previewPath && String(app.previewPath).trim()) ||
    (app.preview_path && String(app.preview_path).trim()) ||
    ''
  if (fromApi) return fromApi.replace(/^\/+/, '').replace(/\/+$/, '')
  return formatDatePathFromCreateTime(app.createTime)
}

/**
 * 解析相对 /static/ 的资源目录。
 * 后端落盘：CODE_OUTPUT_ROOT_DIR / previewPath / {codeGenType}_{appId}（previewPath 多为日期目录如 2026/04/13）。
 * 若后端已返回包含项目目录的完整相对路径，则不再重复拼接。
 */
function resolveStaticRelativePath(
  codeGenType: string,
  appId: string,
  previewParent?: string | null,
): string {
  const projectDir = `${codeGenType}_${appId}`
  const raw = previewParent?.trim().replace(/^\/+/, '').replace(/\/+$/, '') || ''

  if (!raw) {
    return projectDir
  }

  if (raw.endsWith('.html')) {
    return raw
  }

  const alreadyHasProject =
    raw === projectDir || raw.endsWith(`/${projectDir}`) || raw.includes(`/${projectDir}/`)

  const dir = alreadyHasProject ? raw : `${raw}/${projectDir}`
  return dir
}

/**
 * @param app 传入接口返回的应用信息（含 previewPath / preview_path / createTime），用于拼静态预览根路径
 */
export const getStaticPreviewUrl = (
  codeGenType: string,
  appId: string,
  app?: StaticPreviewAppFields | null,
) => {
  const parent = pickStaticPreviewParent(app)
  const relative = resolveStaticRelativePath(codeGenType, appId, parent)
  const baseDir = `${STATIC_BASE_URL}/${relative}`

  const withTrailingDir = (u: string) => (u.endsWith('/') ? u : `${u}/`)

  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    if (relative.endsWith('.html')) {
      return baseDir.includes('#') ? baseDir : `${baseDir}#/`
    }
    console.log('url:' + `${withTrailingDir(baseDir)}index.html`)
    return `${withTrailingDir(baseDir)}dist/index.html#/`
  }

  return relative.endsWith('.html') ? baseDir : `${withTrailingDir(baseDir)}index.html`
}
