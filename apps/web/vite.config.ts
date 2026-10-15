import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    open: false,
    proxy: {
      "/api": {
        target: "https://agent-api-191983702527.us-central1.run.app",
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
