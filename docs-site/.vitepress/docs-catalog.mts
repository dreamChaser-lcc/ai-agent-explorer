import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/** 当前文件所在目录（docs-site/.vitepress） */
const VITEPRESS_DIRECTORY_PATH = dirname(fileURLToPath(import.meta.url))

/** 文档内容根目录：仓库根目录下的 docs/，与 srcDir 配置保持一致 */
const DOCUMENT_ROOT_DIRECTORY = resolve(VITEPRESS_DIRECTORY_PATH, '../../docs')

/** 扫描时跳过的目录名：构建产物与静态资源不计入侧边栏 */
const SKIPPED_DIRECTORY_NAMES = new Set(['.vitepress', 'node_modules', 'public', 'assets'])

/** 扫描时跳过的文件名：首页由 layout: home 单独渲染，不进入侧边栏 */
const SKIPPED_FILE_NAMES = new Set(['index.md'])

/** 一级目录对应的中文分组标题，未命中的目录直接使用目录名 */
const SECTION_TITLE_MAP: Record<string, string> = {
  'Java基础实战': 'Java 基础实战',
  'SQL基础实战': 'SQL 基础实战',
  '业务场景实战': '业务场景实战',
  '服务架构': '服务架构与运维'
}

/** 根目录下散落文档统一归入的分组标题 */
const ROOT_DOCUMENT_SECTION_TITLE = '通用笔记'

/**
 * 子目录分组的展示顺序，按学习路径编排。
 * 未出现在列表中的目录会按名称排序追加到末尾，新增目录无需改动此处即可出现在侧边栏。
 */
const SECTION_DISPLAY_ORDER: string[] = [
  'Java基础实战',
  'SQL基础实战',
  '业务场景实战',
  '服务架构'
]

/** 侧边栏单个条目 */
export interface DocumentSidebarItem {
  /** 侧边栏展示文本，优先取文档标题 */
  text: string
  /** 站内路由，已去掉 .md 后缀 */
  link: string
}

/** 侧边栏分组 */
export interface DocumentSidebarGroup {
  text: string
  /** 默认收起，包含当前页面的分组会由 VitePress 自动展开 */
  collapsed: boolean
  items: DocumentSidebarItem[]
}

/**
 * 读取文档标题：优先 frontmatter 的 title，其次正文首个一级标题，最后回退文件名。
 * 标题统一去掉 markdown 强调符号，避免侧边栏出现多余星号与反引号。
 */
function resolveDocumentTitle(absoluteFilePath: string, fallbackTitle: string): string {
  const fileContent = readFileSync(absoluteFilePath, 'utf-8')

  const frontmatterMatch = fileContent.match(/^---\r?\n([\s\S]*?)\r?\n---/)
  if (frontmatterMatch) {
    const frontmatterTitleMatch = frontmatterMatch[1].match(/^title:\s*(.+)$/m)
    if (frontmatterTitleMatch) {
      return frontmatterTitleMatch[1].trim().replace(/^['"]|['"]$/g, '')
    }
  }

  const headingMatch = fileContent.match(/^#\s+(.+)$/m)
  if (headingMatch) {
    const cleanedHeading = headingMatch[1].replace(/[*`]/g, '').trim()
    if (cleanedHeading) {
      return cleanedHeading
    }
  }

  return fallbackTitle
}

/**
 * 把绝对路径转换为站内路由：
 * 保留中文与「·」等原始字符，交由 VitePress 统一处理编码。
 */
function buildDocumentRoute(absoluteFilePath: string): string {
  const relativePath = absoluteFilePath
    .slice(DOCUMENT_ROOT_DIRECTORY.length)
    .replace(/\\/g, '/')
    .replace(/\.md$/, '')

  return relativePath.startsWith('/') ? relativePath : `/${relativePath}`
}

/** 按中文拼音与数字大小排序，保证 J1→J9、S1→S7 这类编号文档顺序稳定 */
function compareDocumentName(firstName: string, secondName: string): number {
  return firstName.localeCompare(secondName, 'zh-Hans-CN', { numeric: true })
}

/** 按预设学习路径排序子目录，未配置的目录统一排在已配置目录之后 */
function compareSectionName(firstName: string, secondName: string): number {
  const firstConfiguredIndex = SECTION_DISPLAY_ORDER.indexOf(firstName)
  const secondConfiguredIndex = SECTION_DISPLAY_ORDER.indexOf(secondName)
  const firstRank = firstConfiguredIndex === -1 ? Number.MAX_SAFE_INTEGER : firstConfiguredIndex
  const secondRank = secondConfiguredIndex === -1 ? Number.MAX_SAFE_INTEGER : secondConfiguredIndex

  if (firstRank !== secondRank) {
    return firstRank - secondRank
  }

  return compareDocumentName(firstName, secondName)
}

/** 收集单个目录下的全部 markdown 文档条目 */
function collectDirectoryItems(absoluteDirectoryPath: string): DocumentSidebarItem[] {
  const directoryEntries = readdirSync(absoluteDirectoryPath)

  return directoryEntries
    .filter((entryName) => extname(entryName) === '.md')
    .filter((entryName) => !SKIPPED_FILE_NAMES.has(entryName))
    .sort(compareDocumentName)
    .map((entryName) => {
      const absoluteFilePath = join(absoluteDirectoryPath, entryName)
      const fallbackTitle = entryName.replace(/\.md$/, '')

      return {
        text: resolveDocumentTitle(absoluteFilePath, fallbackTitle),
        link: buildDocumentRoute(absoluteFilePath)
      }
    })
}

/**
 * 扫描 docs 目录生成侧边栏：
 * 根目录散落文档归入「通用笔记」，每个子目录生成一个独立分组。
 * 新增 markdown 文件后无需改动配置，构建时自动进入侧边栏。
 */
export function buildDocumentSidebar(): DocumentSidebarGroup[] {
  const rootEntries = readdirSync(DOCUMENT_ROOT_DIRECTORY)
  const rootDocumentItems = collectDirectoryItems(DOCUMENT_ROOT_DIRECTORY)

  const subDirectoryGroups: DocumentSidebarGroup[] = rootEntries
    .filter((entryName) => !SKIPPED_DIRECTORY_NAMES.has(entryName))
    .filter((entryName) => statSync(join(DOCUMENT_ROOT_DIRECTORY, entryName)).isDirectory())
    .sort(compareSectionName)
    .map((directoryName) => ({
      text: SECTION_TITLE_MAP[directoryName] ?? directoryName,
      collapsed: true,
      items: collectDirectoryItems(join(DOCUMENT_ROOT_DIRECTORY, directoryName))
    }))
    .filter((sidebarGroup) => sidebarGroup.items.length > 0)

  const sidebarGroups: DocumentSidebarGroup[] = []

  if (rootDocumentItems.length > 0) {
    sidebarGroups.push({
      text: ROOT_DOCUMENT_SECTION_TITLE,
      collapsed: true,
      items: rootDocumentItems
    })
  }

  return sidebarGroups.concat(subDirectoryGroups)
}
