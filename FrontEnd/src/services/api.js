import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8080/api/v1",
  headers: { "Content-Type": "application/json" },
  timeout: 15000,
});

// Response interceptor — unwrap nested data where backend returns
// { workflow, steps, stepCount } for single workflow
api.interceptors.response.use(
  (res) => res,
  (err) => {
    console.error("[API Error]", err?.response?.status, err?.config?.url);
    return Promise.reject(err);
  }
);

export default api;