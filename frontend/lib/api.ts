export type ExecutionMode = "SYNC" | "ASYNC";
export type TaskStatus =
  | "DRAFT"
  | "QUEUED"
  | "PLANNING"
  | "WAITING_FOR_CONFIRMATION"
  | "RUNNING"
  | "PAUSED"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type TaskStage = "PLANNING" | "EXECUTING" | "REPORTING";

export type TaskSummary = {
  id: string;
  taskNo: string;
  title: string;
  executionMode: ExecutionMode;
  status: TaskStatus;
  currentStage: TaskStage;
  requiresConfirmation: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TaskDetail = {
  id: string;
  taskNo: string;
  title: string;
  goal: string;
  executionMode: ExecutionMode;
  status: TaskStatus;
  currentStage: TaskStage;
  requiresConfirmation: boolean;
  latestPlanSummary: string | null;
  plannedSteps: string[];
  createdAt: string;
  updatedAt: string;
};

export type TaskEvent = {
  id: string;
  taskId: string;
  stepId: string | null;
  eventType: string;
  eventMessage: string;
  operatorType: string;
  operatorId: string | null;
  createdAt: string;
};

export type StepExecution = {
  id: string;
  stepId: string;
  attemptNo: number;
  executorType: string;
  toolName: string | null;
  status: string;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
};

export type ResearchReport = {
  id: string;
  taskId: string;
  summary: string;
  keyFindings: Record<string, unknown> | null;
  finalRecommendation: string | null;
  reportMarkdown: string | null;
  status: string;
  generatedAt: string | null;
  updatedAt: string;
};

type RequestOptions = RequestInit & {
  searchParams?: URLSearchParams;
};

/**
 * 返回前端访问后端接口所使用的基础地址。
 */
function getApiBaseUrl() {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
}

/**
 * 统一发起接口请求并在失败时抛出可读错误。
 */
async function request<T>(path: string, options?: RequestOptions): Promise<T> {
  const url = new URL(path, getApiBaseUrl());
  if (options?.searchParams) {
    url.search = options.searchParams.toString();
  }

  const response = await fetch(url.toString(), {
    cache: "no-store",
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options?.headers ?? {})
    }
  });

  if (!response.ok) {
    let message = "请求失败";
    try {
      const payload = (await response.json()) as { message?: string };
      message = payload.message ?? message;
    } catch {
      message = response.statusText || message;
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export async function createTask(input: {
  title: string;
  goal: string;
  executionMode: ExecutionMode;
}) {
  return request<TaskSummary>("/api/tasks", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function listTasks() {
  return request<TaskSummary[]>("/api/tasks");
}

export async function getTaskDetail(taskId: string) {
  return request<TaskDetail>(`/api/tasks/${taskId}`);
}

export async function getTaskEvents(taskId: string) {
  return request<TaskEvent[]>(`/api/tasks/${taskId}/events`);
}

export async function getTaskExecutions(taskId: string) {
  return request<StepExecution[]>(`/api/tasks/${taskId}/executions`);
}

export async function confirmPlan(taskId: string, approved: boolean, responseMessage: string) {
  return request<void>(`/api/tasks/${taskId}/confirm`, {
    method: "POST",
    body: JSON.stringify({ approved, responseMessage })
  });
}

export async function pauseTask(taskId: string) {
  return request<void>(`/api/tasks/${taskId}/pause`, { method: "POST" });
}

export async function resumeTask(taskId: string) {
  return request<void>(`/api/tasks/${taskId}/resume`, { method: "POST" });
}

export async function cancelTask(taskId: string) {
  return request<void>(`/api/tasks/${taskId}/cancel`, { method: "POST" });
}

export async function getReport(taskId: string) {
  return request<ResearchReport>(`/api/tasks/${taskId}/report`);
}

/**
 * 创建任务进度 SSE 连接。
 */
export function createTaskEventSource(taskId: string) {
  const url = new URL(`/api/tasks/${taskId}/stream`, getApiBaseUrl());
  return new EventSource(url.toString());
}
