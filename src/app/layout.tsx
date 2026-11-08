import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/toaster";
import { ThemeProvider } from "@/components/theme-provider";
import { RegisterSW } from "@/components/pwa/register-sw";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

const APP_NAME = "SetBD Scanner";
const APP_TITLE = "SetBD Scanner — Website Source Scraper";
const APP_DESCRIPTION =
  "Scrape any public website: download HTML, CSS, JS and assets in a clean structure, edit the source live, preview it, and export everything as ZIP.";

export const metadata: Metadata = {
  title: APP_TITLE,
  description: APP_DESCRIPTION,
  applicationName: APP_NAME,
  manifest: "/manifest.json",
  keywords: ["website scraper", "source code downloader", "SetBD", "scanner", "zip export"],
  authors: [{ name: "SetBD" }],
  appleWebApp: {
    capable: true,
    statusBarStyle: "black-translucent",
    title: APP_NAME,
  },
  formatDetection: { telephone: false },
  icons: {
    icon: [
      { url: "/icons/favicon-32.png", sizes: "32x32", type: "image/png" },
      { url: "/icons/favicon-16.png", sizes: "16x16", type: "image/png" },
      { url: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
    ],
    apple: [
      { url: "/icons/apple-touch-icon.png", sizes: "180x180", type: "image/png" },
    ],
  },
  openGraph: {
    title: APP_TITLE,
    description: APP_DESCRIPTION,
    siteName: APP_NAME,
    type: "website",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#050810" },
    { media: "(prefers-color-scheme: light)", color: "#f2f7fd" },
  ],
};

/* iOS splash screens (generated from brand assets) */
const IOS_SPLASHES: Array<{ w: number; h: number; dpr: number }> = [
  { w: 1290, h: 2796, dpr: 3 },
  { w: 1284, h: 2778, dpr: 3 },
  { w: 1170, h: 2532, dpr: 3 },
  { w: 1125, h: 2436, dpr: 3 },
  { w: 1242, h: 2688, dpr: 3 },
  { w: 1242, h: 2208, dpr: 3 },
  { w: 828, h: 1792, dpr: 2 },
  { w: 750, h: 1334, dpr: 2 },
  { w: 1488, h: 2266, dpr: 2 },
  { w: 1640, h: 2360, dpr: 2 },
  { w: 1668, h: 2388, dpr: 2 },
  { w: 1668, h: 2224, dpr: 2 },
  { w: 1536, h: 2048, dpr: 2 },
  { w: 2048, h: 2732, dpr: 2 },
];

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        {IOS_SPLASHES.map(({ w, h, dpr }) => (
          <link
            key={`${w}x${h}`}
            rel="apple-touch-startup-image"
            href={`/splash/ios-${w}x${h}.png`}
            media={`(device-width: ${Math.round(w / dpr)}px) and (device-height: ${Math.round(
              h / dpr
            )}px) and (-webkit-device-pixel-ratio: ${dpr})`}
          />
        ))}
      </head>
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased bg-background text-foreground firefox-scroll`}
      >
        <ThemeProvider attribute="class" defaultTheme="dark" enableSystem disableTransitionOnChange>
          {children}
          <Toaster />
          <RegisterSW />
        </ThemeProvider>
      </body>
    </html>
  );
}
