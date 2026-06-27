import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL
});
export default api;

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