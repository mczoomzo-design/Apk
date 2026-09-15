import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// dev: proxy /api ไปยัง FastAPI เพื่อให้ frontend + API อยู่ origin เดียวกัน
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.API_URL || "http://localhost:8000",
        changeOrigin: true,
      },
    },
  },
});
