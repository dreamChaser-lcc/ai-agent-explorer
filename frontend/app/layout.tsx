import "./globals.css";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Research Agent",
  description: "Task-driven research agent workspace"
};

type RootLayoutProps = {
  children: ReactNode;
};

/**
 * 定义前端应用的全局布局壳子。
 * 侧栏固定不随主内容滚动，任何页面都能通过侧栏回到任务创建页。
 */
export default function RootLayout({ children }: RootLayoutProps) {
  return (
    <html lang="zh-CN">
      <body>
        <div className="app-shell">
          <aside className="sidebar">
            <Link className="brand" href="/">
              Research Agent
            </Link>
            <nav className="nav">
              <Link href="/">任务创建</Link>
            </nav>
            <span className="nav-hint">创建任务后可进入详情页和报告页；左侧入口随时可回到本页。</span>
          </aside>
          <main className="content">{children}</main>
        </div>
      </body>
    </html>
  );
}
