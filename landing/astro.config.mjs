// @ts-check
import { defineConfig } from "astro/config";
import sitemap from "@astrojs/sitemap";

export default defineConfig({
  site: "https://ourtoday-app.vercel.app",
  trailingSlash: "never",
  build: { format: "file" },
  integrations: [sitemap({ filter: (page) => !page.includes("/404") })],
});
