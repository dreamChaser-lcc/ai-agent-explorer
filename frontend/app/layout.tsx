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
 */
export default function RootLayout({ children }: RootLayoutProps) {
  return (
    <html lang="zh-CN">
      <body>
        <div className="app-shell">
          <aside className="sidebar">
            <div className="brand">Research Agent</div>
            <nav className="nav">
              <Link href="/">任务创建</Link>
              <span className="nav-hint">创建任务后可进入详情页和报告页。</span>
            </nav>
          </aside>
          <main className="content">{children}</main>
        </div>
      </body>
    </html>
  );
}
