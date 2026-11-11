import { http, type ApiResponse, type RequestOptions } from "@/api/http";
import { responseMeta } from '@/api/request-meta';
import { parseExact, printExact } from "./exact";
export async function ruleGet<T>(
  path: string,
  params?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  return (
    await http.get<ApiResponse<T>>("/rule" + path, {
      params,
      signal,
      transformResponse: [
        (text) => (typeof text === "string" ? parseExact(text) : text),
      ],
    })
  ).data.data;
}
export async function ruleWrite<T>(
  path: string,
  body: unknown,
  method: "POST" | "PUT" = "POST",
  options?: RequestOptions,
): Promise<T> {
  const response = await http.request<ApiResponse<T>>({
      url: "/rule" + path,
      method,
      data: printExact(body),
      headers: { "Content-Type": "application/json", ...(options?.idempotencyKey ? { 'Idempotency-Key': options.idempotencyKey } : {}) },
      signal: options?.signal,
      transformResponse: [
        (text) => (typeof text === "string" ? parseExact(text) : text),
      ],
    });
  options?.onResponse?.(responseMeta(response));
  return response.data.data;
}
export function operationKey() {
  return crypto.randomUUID().replaceAll("-", "_");
}
