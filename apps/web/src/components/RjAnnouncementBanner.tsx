import React from "react";
import { Sparkles, Volume2, X } from "lucide-react";

interface RjAnnouncementBannerProps {
  text: string;
  onPlay: () => void;
  onDismiss: () => void;
  isPlaying: boolean;
}

export const RjAnnouncementBanner: React.FC<RjAnnouncementBannerProps> = ({
  text,
  onPlay,
  onDismiss,
  isPlaying,
}) => {
  return (
    <div className="bg-blue-700 text-white px-4 py-3 shadow-md animate-in slide-in-from-top duration-300">
      <div className="max-w-7xl mx-auto flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center flex-shrink-0 animate-pulse">
            <Sparkles className="w-4 h-4 text-yellow-300" />
          </div>
          <div className="min-w-0">
            <p className="text-[11px] font-bold text-yellow-300 uppercase tracking-wider">
              Live AI Co-Host On-Air Shoutout
            </p>
            <p className="text-xs sm:text-sm font-medium text-white truncate italic">
              "{text}"
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 flex-shrink-0">
          <button
            onClick={onPlay}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold flex items-center gap-1.5 transition ${
              isPlaying
                ? "bg-yellow-400 text-slate-900 shadow-sm"
                : "bg-white/20 hover:bg-white/30 text-white"
            }`}
          >
            <Volume2 className="w-3.5 h-3.5" />
            <span>{isPlaying ? "Playing Voice" : "Hear On-Air"}</span>
          </button>
          <button
            onClick={onDismiss}
            aria-label="Dismiss announcement"
            className="p-2.5 rounded-full hover:bg-white/10 text-white/70 hover:text-white transition"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
