"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  cancelTask,
  confirmPlan,
  createTaskEventSource,
  getTaskDetail,
  getTaskEvents,
  getTaskExecutions,
  pauseTask,
  resumeTask,
  type StepExecution,
  type TaskDetail,
  type TaskEvent
} from "../../../lib/api";

type TaskDetailPageProps = {
  params: {
    taskId: string;
  };
};

/**
 * 渲染任务详情页，展示任务状态、时间线和实时进度。
 */
export default function TaskDetailPage({ params }: TaskDetailPageProps) {
  const [task, setTask] = useState<TaskDetail | null>(null);
  const [events, setEvents] = useState<TaskEvent[]>([]);
  const [executions, setExecutions] = useState<StepExecution[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const canConfirm = task?.status === "WAITING_FOR_CONFIRMATION";
  const canPause = task?.status === "RUNNING";
  const canResume = task?.status === "PAUSED";
  const canCancel = task?.status !== "COMPLETED" && task?.status !== "CANCELLED";

  useEffect(() => {
    void reloadAll();
    const eventSource = createTaskEventSource(params.taskId);
    const reload = () => void reloadAll(false);

    for (const eventName of [
      "connected",
      "task-event",
      "task-status",
      "step-running",
      "step-completed",
      "plan-generated"
    ]) {
      eventSource.addEventListener(eventName, reload);
    }

    eventSource.onerror = () => {
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [params.taskId]);

  const timelineItems = useMemo(() => {
    if (task?.plannedSteps?.length) {
      return task.plannedSteps;
    }
    return ["等待研究计划生成"];
  }, [task?.plannedSteps]);

  async function reloadAll(showLoading = true) {
    try {
      if (showLoading) {
        setLoading(true);
      }
      const [taskDetail, taskEvents, taskExecutions] = await Promise.all([
        getTaskDetail(params.taskId),
        getTaskEvents(params.taskId),
        getTaskExecutions(params.taskId)
      ]);
      setTask(taskDetail);
      setEvents(taskEvents);
      setExecutions(taskExecutions);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "加载任务详情失败");
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  }

  async function runAction(action: () => Promise<void>) {
    try {
      setActionLoading(true);
      await action();
      await reloadAll(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "任务操作失败");
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <section className="page">
      <header className="page-header">
        <h1>任务详情</h1>
        <p>任务 ID：{params.taskId}</p>
      </header>

      {errorMessage ? <p className="error-message">{errorMessage}</p> : null}
      {loading ? <p className="muted-text">正在加载任务详情...</p> : null}

      {task ? (
        <>
          <div className="grid">
            <section className="panel">
              <div className="section-header">
                <h2>{task.title}</h2>
                <span className={`badge ${task.status.toLowerCase()}`}>{task.status}</span>
              </div>

              <p>{task.goal}</p>
              <div className="detail-list">
                <div>
                  <strong>执行模式</strong>
                  <p>{task.executionMode}</p>
                </div>
                <div>
                  <strong>当前阶段</strong>
                  <p>{task.currentStage}</p>
                </div>
                <div>
                  <strong>计划摘要</strong>
                  <p>{task.latestPlanSummary ?? "研究计划尚未生成"}</p>
                </div>
              </div>

              <div className="button-row">
                {canConfirm ? (
                  <>
                    <button
                      className="primary-button"
                      disabled={actionLoading}
                      onClick={() => void runAction(() => confirmPlan(params.taskId, true, "继续执行"))}
                      type="button"
                    >
                      确认并继续
                    </button>
                    <button
                      className="secondary-button"
                      disabled={actionLoading}
                      onClick={() => void runAction(() => confirmPlan(params.taskId, false, "暂不执行"))}
                      type="button"
                    >
                      拒绝计划
                    </button>
                  </>
                ) : null}

                {canPause ? (
                  <button
                    className="secondary-button"
                    disabled={actionLoading}
                    onClick={() => void runAction(() => pauseTask(params.taskId))}
                    type="button"
                  >
                    暂停任务
                  </button>
                ) : null}

                {canResume ? (
                  <button
                    className="primary-button"
                    disabled={actionLoading}
                    onClick={() => void runAction(() => resumeTask(params.taskId))}
                    type="button"
                  >
                    恢复任务
                  </button>
                ) : null}

                {canCancel ? (
                  <button
                    className="secondary-button"
                    disabled={actionLoading}
                    onClick={() => void runAction(() => cancelTask(params.taskId))}
                    type="button"
                  >
                    取消任务
                  </button>
                ) : null}

                {task.status === "COMPLETED" ? (
                  <Link className="primary-button link-button" href={`/reports/${params.taskId}`}>
                    查看研究报告
                  </Link>
                ) : null}
              </div>
            </section>

            <section className="panel">
              <h2>研究计划步骤</h2>
              <ul className="timeline">
                {timelineItems.map((item, index) => (
                  <li key={`${item}-${index}`}>
                    <span className={`timeline-dot ${index === 0 ? "done" : "pending"}`} />
                    <div>
                      <strong>{item}</strong>
                      <p>步骤 {index + 1}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          </div>

          <div className="grid">
            <section className="panel">
              <h2>任务事件时间线</h2>
              {events.length === 0 ? <p className="muted-text">暂时没有事件记录。</p> : null}
              <ul className="timeline">
                {events.map((event) => (
                  <li key={event.id}>
                    <span className="timeline-dot done" />
                    <div>
                      <strong>{event.eventType}</strong>
                      <p>{event.eventMessage}</p>
                      <p className="muted-text">{formatTime(event.createdAt)}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </section>

            <section className="panel">
              <h2>步骤执行记录</h2>
              {executions.length === 0 ? <p className="muted-text">当前还没有执行记录。</p> : null}
              <ul className="timeline">
                {executions.map((execution) => (
                  <li key={execution.id}>
                    <span className={`timeline-dot ${execution.status === "SUCCESS" ? "done" : "waiting"}`} />
                    <div>
                      <strong>{execution.toolName ?? execution.executorType}</strong>
                      <p>
                        状态：{execution.status}
                        {execution.durationMs ? ` · 耗时 ${execution.durationMs}ms` : ""}
                      </p>
                      {execution.errorMessage ? <p className="error-message">{execution.errorMessage}</p> : null}
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        </>
      ) : null}
    </section>
  );
}

/**
 * 将 ISO 时间格式化为本地可读时间。
 */
function formatTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    hour12: false,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date(value));
}
