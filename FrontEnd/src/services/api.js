import axios from "axios";

const api = axios.create({
  baseURL:
    import.meta.env.VITE_API_URL ||
    "http://localhost:8080/api/v1",
  headers: {
    "Content-Type": "application/json"
  }
});

// Response interceptor
api.interceptors.response.use(
  (res) => res,
  (err) => {
    console.error("[API Error]", err?.response?.status, err?.config?.url);
    return Promise.reject(err);
  }
);

export default api;