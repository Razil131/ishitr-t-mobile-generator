export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1/generator';

export const BACKEND_PROXY_URL = API_BASE_URL.replace(/\/generator\/?$/, '');

export interface ApiResponse<T = unknown> {
  data: T;
  success: boolean;
  message?: string;
  timestamp?: string;
}

export class ApiError extends Error {
  status: number;
  data?: unknown;

  constructor(message: string, status: number, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {},
  useBackendProxy: boolean = false
): Promise<T> {
  const baseUrl = useBackendProxy ? BACKEND_PROXY_URL : API_BASE_URL;

  const token = localStorage.getItem('auth_token');
  const headers = new Headers(options.headers || {});
  
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const url = `${baseUrl.replace(/\/$/, '')}/${endpoint.replace(/^\//, '')}`;

  try {
    const response = await fetch(url, {
      ...options,
      headers
    });

    if (!response.ok) {
      const errorBody = await response.json().catch(() => null);
      throw new ApiError(
        errorBody?.message || `HTTP Error ${response.status}: ${response.statusText}`,
        response.status,
        errorBody
      );
    }

    if (response.status === 204) {
      return {} as T;
    }

    return (await response.json()) as T;
  } catch (err: unknown) {
    if (err instanceof ApiError) {
      throw err;
    }
    const message = err instanceof Error ? err.message : 'Unknown network error';
    throw new ApiError(message, 0);
  }
}
