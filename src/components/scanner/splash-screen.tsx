"use client";

import { useEffect } from "react";
import Image from "next/image";
import { motion, useReducedMotion, type Variants } from "framer-motion";

export interface SplashScreenProps {
  /** Called when the intro finishes, is skipped, or is tapped. */
  onFinish: () => void;
}

const TITLE = "SetBD Scanner";
const AUTO_FINISH_MS = 2400;
const BAR_DURATION_S = 2.2;

const letterContainer: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.05, delayChildren: 0.25 } },
};

const letter: Variants = {
  hidden: { opacity: 0, y: 14, filter: "blur(4px)" },
  show: {
    opacity: 1,
    y: 0,
    filter: "blur(0px)",
    transition: { type: "spring", stiffness: 320, damping: 24 },
  },
};

export function SplashScreen({ onFinish }: SplashScreenProps) {
  const reducedMotion = useReducedMotion();

  useEffect(() => {
    const timer = window.setTimeout(onFinish, AUTO_FINISH_MS);
    return () => window.clearTimeout(timer);
  }, [onFinish]);

  return (
    <motion.div
      role="button"
      tabIndex={0}
      aria-label="SetBD Scanner intro — activate to skip"
      onClick={onFinish}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onFinish();
        }
      }}
      initial={{ opacity: 1, scale: 1 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 1.06, transition: { duration: 0.45, ease: "easeInOut" } }}
      className="fixed inset-0 z-[100] flex cursor-pointer flex-col items-center justify-center overflow-hidden bg-[#050810] px-6"
    >
      {/* Backdrop */}
      <Image
        src="/images/bg-dark.webp"
        alt=""
        fill
        priority
        sizes="100vw"
        className="object-cover opacity-60"
      />
      <div
        aria-hidden
        className="absolute inset-0 bg-gradient-to-b from-[#050810]/40 via-transparent to-[#050810]"
      />

      {/* Center block */}
      <div className="relative z-10 flex flex-col items-center gap-6">
        <motion.div
          initial={{ scale: 0.6, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: "spring", stiffness: 260, damping: 20, delay: 0.1 }}
          className={reducedMotion ? "" : "animate-float"}
        >
          <div className="glow-ring relative h-28 w-28 overflow-hidden rounded-2xl md:h-36 md:w-36">
            <Image
              src="/images/logo.webp"
              alt="SetBD Scanner logo"
              fill
              sizes="(min-width: 768px) 144px, 112px"
              className="object-cover"
            />
          </div>
        </motion.div>

        {reducedMotion ? (
          <motion.h1
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.4 }}
            className="text-4xl font-extrabold tracking-tight md:text-5xl"
          >
            <span className="text-gradient">{TITLE}</span>
          </motion.h1>
        ) : (
          <motion.h1
            variants={letterContainer}
            initial="hidden"
            animate="show"
            className="text-4xl font-extrabold tracking-tight md:text-5xl"
          >
            {TITLE.split("").map((ch, i) => (
              <motion.span key={`${i}-${ch}`} variants={letter} className="text-gradient inline-block">
                {ch === " " ? "\u00A0" : ch}
              </motion.span>
            ))}
          </motion.h1>
        )}

        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.9, duration: 0.5 }}
          className="text-sm text-slate-300/80 md:text-base"
        >
          Scan • Scrape • Own the Source
        </motion.p>

        <motion.div
          initial={{ opacity: 0, scale: 0.85 }}
          animate={{ opacity: 0.8, scale: 1 }}
          transition={{ delay: 1.05, duration: 0.4 }}
        >
          <Image
            src="/images/scanner.gif"
            alt=""
            width={128}
            height={64}
            className="h-16 w-auto rounded-lg"
          />
        </motion.div>

        <div className="mt-2 h-1 w-56 overflow-hidden rounded-full bg-white/10">
          <motion.div
            className="h-full rounded-full bg-gradient-to-r from-cyan-400 to-blue-500"
            initial={{ width: "0%" }}
            animate={{ width: "100%" }}
            transition={{ duration: BAR_DURATION_S, ease: "linear" }}
          />
        </div>
      </div>

      <p className="absolute bottom-6 text-[11px] uppercase tracking-widest text-slate-400/70">
        Tap to skip
      </p>
    </motion.div>
  );
}
