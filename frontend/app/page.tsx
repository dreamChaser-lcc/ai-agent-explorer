"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { createTask, listTasks, type ExecutionMode, type TaskSummary } from "../lib/api";

const executionModes: Array<{
  label: string;
  value: ExecutionMode;
  description: string;
}> = [
  { label: "同步执行", value: "SYNC", description: "适合短任务，等待结果返回" },
  { label: "异步执行", value: "ASYNC", description: "适合长任务，后台持续执行" }
];

/** 任务列表每页条数，与后端 PageResponse 的默认 size 保持一致 */
const TASK_PAGE_SIZE = 20;

/**
 * 渲染任务创建页，并展示最近的任务列表。
 */
export default function HomePage() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [goal, setGoal] = useState("");
  const [executionMode, setExecutionMode] = useState<ExecutionMode>("ASYNC");
  const [tasks, setTasks] = useState<TaskSummary[]>([]);
  const [pageState, setPageState] = useState({
    currentPage: 0,
    totalPages: 0,
    totalElements: 0
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoadingTasks, setIsLoadingTasks] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    void loadTasks();
  }, []);

  const canSubmit = useMemo(() => {
    return title.trim().length > 0 && goal.trim().length > 0 && !isSubmitting;
  }, [goal, isSubmitting, title]);

  async function loadTasks(targetPage = 0) {
    try {
      setIsLoadingTasks(true);
      setErrorMessage("");
      const pageResult = await listTasks({ page: targetPage, size: TASK_PAGE_SIZE });
      setTasks(pageResult.content);
      setPageState({
        currentPage: pageResult.page,
        totalPages: pageResult.totalPages,
        totalElements: pageResult.totalElements
      });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "加载任务列表失败");
    } finally {
      setIsLoadingTasks(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }

    try {
      setIsSubmitting(true);
      setErrorMessage("");
      const task = await createTask({
        title: title.trim(),
        goal: goal.trim(),
        executionMode
      });
      router.push(`/tasks/${task.id}`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "创建任务失败");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="page">
      <header className="page-header">
        <div>
          <h1>创建研究任务</h1>
          <p>输入研究目标后，系统会先生成研究计划，待你确认后再执行完整研究流程。</p>
        </div>
      </header>

      <div className="grid">
        <form className="panel" onSubmit={handleSubmit}>
          <label className="field">
            <span>任务标题</span>
            <input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="例如：Java 技术栈 Research Agent 方案调研"
            />
          </label>

          <label className="field">
            <span>研究目标</span>
            <textarea
              value={goal}
              onChange={(event) => setGoal(event.target.value)}
              placeholder="例如：调研 Java 生态下构建 AI Agent 系统的主流方案，并给出 MVP 技术选型建议"
              rows={8}
            />
          </label>

          <div className="field radio-field">
            <span>执行模式</span>
            <div className="radio-grid">
              {executionModes.map((mode) => (
                <label key={mode.value} className="radio-card">
                  <input
                    checked={executionMode === mode.value}
                    name="execution-mode"
                    type="radio"
                    value={mode.value}
                    onChange={() => setExecutionMode(mode.value)}
                  />
                  <div>
                    <strong>{mode.label}</strong>
                    <p>{mode.description}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          {errorMessage ? <p className="error-message">{errorMessage}</p> : null}

          <button className="primary-button" disabled={!canSubmit} type="submit">
            {isSubmitting ? "创建中..." : "创建任务"}
          </button>
        </form>

        <section className="panel scroll-panel">
          <div className="section-header">
            <h2>最近任务</h2>
            <button
              className="secondary-button"
              onClick={() => void loadTasks(pageState.currentPage)}
              type="button"
            >
              刷新
            </button>
          </div>

          <div className="scroll-panel-body">
            {isLoadingTasks ? <p className="muted-text">正在加载任务列表...</p> : null}

            {!isLoadingTasks && tasks.length === 0 ? (
              <p className="muted-text">还没有任务，先创建一个研究任务吧。</p>
            ) : null}

            <ul className="task-list">
              {tasks.map((task) => (
                <li key={task.id} className="task-card">
                  <div className="task-card-header">
                    <strong>{task.title}</strong>
                    <span className={`badge ${task.status.toLowerCase()}`}>{task.status}</span>
                  </div>
                  <p className="mono-text">{task.taskNo}</p>
                  <p>
                    模式：{task.executionMode} · 阶段：{task.currentStage}
                  </p>
                  <div className="button-row compact">
                    <button
                      className="secondary-button"
                      onClick={() => router.push(`/tasks/${task.id}`)}
                      type="button"
                    >
                      查看详情
                    </button>
                    <button
                      className="secondary-button"
                      onClick={() => router.push(`/reports/${task.id}`)}
                      type="button"
                    >
                      查看报告
                    </button>
                  </div>
                </li>
              ))}
            </ul>

            {pageState.totalPages > 1 ? (
              <>
                <p className="muted-text">
                  第 {pageState.currentPage + 1} / {pageState.totalPages} 页 · 共 {pageState.totalElements} 条
                </p>
                <div className="button-row compact">
                  <button
                    className="secondary-button"
                    disabled={pageState.currentPage <= 0}
                    onClick={() => void loadTasks(pageState.currentPage - 1)}
                    type="button"
                  >
                    上一页
                  </button>
                  <button
                    className="secondary-button"
                    disabled={pageState.currentPage >= pageState.totalPages - 1}
                    onClick={() => void loadTasks(pageState.currentPage + 1)}
                    type="button"
                  >
                    下一页
                  </button>
                </div>
              </>
            ) : null}
          </div>
        </section>
      </div>
    </section>
  );
}
