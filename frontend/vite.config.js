import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

/**
 * Vite configuration
 *
 * Key features:
 *
 * 1. Dev proxy: in development, requests to /api/* and /notifications/*
 *    are proxied to the Spring Boot backend. This means:
 *    - CORS is never an issue in local dev (the browser talks to
 *      localhost:5173, which proxies to localhost:8080 server-side).
 *    - The VITE_API_URL in .env.local is still used by the axios instance,
 *      but you can set it to /api/v1 (relative) in dev so the proxy handles
 *      routing. The fallback "http://localhost:8080/api/v1" in api.js means
 *      things work even if VITE_API_URL is not set.
 *
 * 2. Build output: dist/ folder ready for deployment to any static host.
 *
 * 3. Source maps: enabled in development only (not in production builds
 *    to avoid leaking source code to end users).
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");

  return {
    plugins: [react()],

    // ── Dev server ────────────────────────────────────────────────────────
    server: {
      port: 5173,
      proxy: {
        // /api/* → Spring Boot /api/*
        "/api": {
          target:      env.VITE_API_URL?.replace(/\/api\/v1\/?$/, "") || "http://localhost:8080",
          changeOrigin: true,
          secure:       false,
        },
        // /notifications/* → Spring Boot /notifications/*
        "/notifications": {
          target:      env.VITE_API_URL?.replace(/\/api\/v1\/?$/, "") || "http://localhost:8080",
          changeOrigin: true,
          secure:       false,
        },
      },
    },

    // ── Build ─────────────────────────────────────────────────────────────
    build: {
      outDir:        "dist",
      sourcemap:     false,   // disable in production
      rollupOptions: {
        output: {
          // Chunk splitting: vendor libs in a separate chunk for better caching
          manualChunks: {
            vendor: ["react", "react-dom", "react-router-dom"],
            http:   ["axios"],
          },
        },
      },
    },

    // ── Source maps in dev ────────────────────────────────────────────────
    css: {
      devSourcemap: true,
    },
  };
});
