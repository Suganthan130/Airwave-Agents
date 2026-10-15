/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#eff6ff",
          100: "#dbeafe",
          200: "#bfdbfe",
          300: "#93c5fd",
          400: "#60a5fa",
          500: "#3b82f6",
          600: "#2563eb", // Professional primary blue
          700: "#1d4ed8",
          800: "#1e40af",
          900: "#1e3a8a",
          950: "#172554",
        },
        studio: {
          dark: "#0b0f19",
          card: "#111827",
          border: "#1f2937",
          text: "#f9fafb",
          muted: "#9ca3af",
          accent: "#3b82f6",
          ai: "#8b5cf6",
        },
      },
      fontFamily: {
        sans: [
          "Inter",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "Roboto",
          "sans-serif",
        ],
      },
      animation: {
        "pulse-subtle": "pulse 2.5s cubic-bezier(0.4, 0, 0.6, 1) infinite",
        "equalizer-1": "eq1 1s ease-in-out infinite alternate",
        "equalizer-2": "eq2 0.8s ease-in-out infinite alternate",
        "equalizer-3": "eq3 1.2s ease-in-out infinite alternate",
        "equalizer-4": "eq4 0.9s ease-in-out infinite alternate",
      },
      keyframes: {
        eq1: { "0%": { height: "20%" }, "100%": { height: "90%" } },
        eq2: { "0%": { height: "80%" }, "100%": { height: "30%" } },
        eq3: { "0%": { height: "40%" }, "100%": { height: "100%" } },
        eq4: { "0%": { height: "90%" }, "100%": { height: "25%" } },
      },
    },
  },
  plugins: [],
};
