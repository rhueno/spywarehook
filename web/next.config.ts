import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  serverExternalPackages: ["sql.js"],
  devIndicators: false,
  poweredByHeader: false,
  productionBrowserSourceMaps: false,
};

export default nextConfig;
