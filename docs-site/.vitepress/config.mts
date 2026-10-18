import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitepress'
import { buildDocumentSidebar } from './docs-catalog.mts'

/**
 * 站点工程自身的 vue 包目录。
 * srcDir 指向仓库根部的 docs/，该目录下没有 node_modules，
 * Rollup 从文档文件出发向上找不到 vue，会在 SSR 构建时报
 * "failed to resolve import vue/server-renderer"。
 * 因此把 vue 的子路径导入显式指回本工程的 node_modules。
 */
const SITE_VUE_PACKAGE_PATH = fileURLToPath(new URL('../node_modules/vue/', import.meta.url))

/**
 * 站点部署根路径。
 * GitHub Pages 项目站点地址为 https://<用户名>.github.io/ai-agent-explorer/，
 * 因此默认使用仓库名作为 base；如需部署到自定义域名或根路径，用 DOCS_SITE_BASE 覆盖。
 */
const SITE_BASE_PATH = process.env.DOCS_SITE_BASE ?? '/ai-agent-explorer/'

/**
 * 本地搜索分词器：
 * MiniSearch 默认按空格切词，中文长句会被当成一个整词导致搜不到。
 * 这里对中文做单字 + 双字切分，英文数字保持整词。
 *
 * 注意：该函数会被序列化后在浏览器执行，函数体内不能引用模块级常量，
 * 因此分隔符正则必须写成字面量。
 */
function tokenizeChineseSearchText(rawText: string): string[] {
  const textSegments = rawText.toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(Boolean)
  const searchTokens: string[] = []

  for (const textSegment of textSegments) {
    // 纯英文或数字保持整词，避免把 java、spring 拆碎
    if (/^[a-z0-9]+$/.test(textSegment)) {
      searchTokens.push(textSegment)
      continue
    }

    for (let characterIndex = 0; characterIndex < textSegment.length; characterIndex += 1) {
      searchTokens.push(textSegment[characterIndex])

      if (characterIndex < textSegment.length - 1) {
        searchTokens.push(textSegment.slice(characterIndex, characterIndex + 2))
      }
    }
  }

  return searchTokens
}

export default defineConfig({
  // 内容源指向仓库根目录的 docs/，本目录只放站点工程与配置
  srcDir: '../docs',
  // 构建产物固定输出到本项目的 dist/，避免写回 docs/
  outDir: './dist',
  base: SITE_BASE_PATH,
  lang: 'zh-CN',
  title: 'AI Agent Explorer',
  description: 'Java 后端 · 数据库 · 服务架构 —— 实战沉淀的学习笔记',
  // 开启后构建会读取 git 提交时间，CI 中需 checkout 完整历史（fetch-depth: 0）
  lastUpdated: true,
  // 文档之间暂无内部链接，放宽校验避免构建被历史路径阻断
  ignoreDeadLinks: true,
  markdown: {
    theme: { light: 'github-light', dark: 'github-dark' }
  },
  vite: {
    resolve: {
      alias: {
        // 字符串别名只匹配 "vue" 本身和 "vue/xxx" 子路径，不会误伤 vuex 之类的包名
        vue: SITE_VUE_PACKAGE_PATH
      }
    }
  },
  sitemap: {
    hostname: 'https://dreamchaser-lcc.github.io/ai-agent-explorer/'
  },
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: 'Java 基础', link: '/Java基础实战/00·课程总纲' },
      { text: 'SQL 基础', link: '/SQL基础实战/00·课程总纲' },
      { text: '业务场景', link: '/业务场景实战/00·实战总纲' },
      { text: '服务架构', link: '/服务架构/学习路线规划' }
    ],
    sidebar: buildDocumentSidebar(),
    search: {
      provider: 'local',
      options: {
        miniSearch: {
          options: {
            tokenize: tokenizeChineseSearchText
          }
        },
        translations: {
          button: {
            buttonText: '搜索文档',
            buttonAriaLabel: '搜索文档'
          },
          modal: {
            displayDetails: '展开详情',
            resetButtonTitle: '清空查询',
            backButtonTitle: '返回',
            noResultsText: '没有找到结果',
            footer: {
              selectText: '选择',
              navigateText: '切换',
              closeText: '关闭'
            }
          }
        }
      }
    },
    outline: {
      level: [2, 3],
      label: '本页目录'
    },
    docFooter: {
      prev: '上一篇',
      next: '下一篇'
    },
    lastUpdated: {
      text: '最后更新于',
      formatOptions: { dateStyle: 'short', timeStyle: 'short' }
    },
    returnToTopLabel: '回到顶部',
    sidebarMenuLabel: '目录',
    darkModeSwitchLabel: '深色模式',
    socialLinks: [
      { icon: 'github', link: 'https://github.com/dreamChaser-lcc/ai-agent-explorer' }
    ]
  }
})
