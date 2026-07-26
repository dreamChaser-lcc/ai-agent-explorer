"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { getReport, type ResearchReport } from "../../../lib/api";

type ReportPageProps = {
  params: {
    taskId: string;
  };
};

/**
 * 渲染研究报告详情页。
 */
export default function ReportPage({ params }: ReportPageProps) {
  const [report, setReport] = useState<ResearchReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    void loadReport();
  }, [params.taskId]);

  const findings = useMemo(() => {
    const rawFindings = report?.keyFindings?.summaryFindings as
      | { findings?: string[] }
      | undefined;
    return rawFindings?.findings ?? [];
  }, [report?.keyFindings]);

  const citations = useMemo(() => {
    const rawCitations = report?.keyFindings?.citations as
      | { citations?: Array<{ title?: string; url?: string; snippet?: string }> }
      | undefined;
    return rawCitations?.citations ?? [];
  }, [report?.keyFindings]);

  async function loadReport() {
    try {
      setLoading(true);
      setReport(await getReport(params.taskId));
      setErrorMessage("");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "加载研究报告失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="page">
      <header className="page-header">
        <h1>研究报告</h1>
        <p>任务 ID：{params.taskId}</p>
      </header>

      <div className="button-row">
        <Link className="secondary-button link-button" href={`/tasks/${params.taskId}`}>
          返回任务详情
        </Link>
        <button className="secondary-button" onClick={() => void loadReport()} type="button">
          刷新报告
        </button>
      </div>

      {loading ? <p className="muted-text">正在加载研究报告...</p> : null}
      {errorMessage ? <p className="error-message">{errorMessage}</p> : null}

      {report ? (
        <>
          <section className="panel">
            <div className="section-header">
              <h2>摘要</h2>
              <span className={`badge ${report.status.toLowerCase()}`}>{report.status}</span>
            </div>
            <p>{report.summary}</p>
          </section>

          <div className="grid">
            <section className="panel">
              <h2>关键发现</h2>
              {findings.length === 0 ? <p className="muted-text">当前没有提取到结构化发现。</p> : null}
              <ul className="timeline">
                {findings.map((finding, index) => (
                  <li key={`${finding}-${index}`}>
                    <span className="timeline-dot done" />
                    <div>
                      <strong>发现 {index + 1}</strong>
                      <p>{finding}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </section>

            <section className="panel">
              <h2>引用来源</h2>
              {citations.length === 0 ? <p className="muted-text">当前没有可展示的引用来源。</p> : null}
              <ul className="timeline">
                {citations.map((citation, index) => (
                  <li key={`${citation.url ?? citation.title ?? "citation"}-${index}`}>
                    <span className="timeline-dot waiting" />
                    <div>
                      <strong>{citation.title ?? "未命名来源"}</strong>
                      <p>{citation.snippet ?? "暂无摘要"}</p>
                      {citation.url ? (
                        <a className="inline-link" href={citation.url} rel="noreferrer" target="_blank">
                          {citation.url}
                        </a>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          </div>

          <section className="panel">
            <h2>建议</h2>
            <p>{report.finalRecommendation ?? "当前未生成最终建议。"}</p>
          </section>

          <section className="panel">
            <h2>Markdown 视图</h2>
            <pre className="markdown-preview">{report.reportMarkdown ?? "当前未生成 Markdown 报告。"}</pre>
          </section>
        </>
      ) : null}
    </section>
  );
}
