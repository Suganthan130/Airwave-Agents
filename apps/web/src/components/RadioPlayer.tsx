import React, { useState, useRef, useEffect } from "react";
import {
  Play,
  Pause,
  Volume2,
  VolumeX,
  Radio,
  Music,
  Sparkles,
  WifiOff,
  Timer,
  Activity,
  CheckCircle2,
} from "lucide-react";
import type { RadioStatus, BroadcasterStation } from "../types.ts";

interface RadioPlayerProps {
  radioStatus: RadioStatus;
  currentStation?: BroadcasterStation | null;
  activeRjAudioUrl?: string | null;
  activeRjText?: string | null;
  onRjAudioFinished?: () => void;
  streamUrl: string;
}

export const RadioPlayer: React.FC<RadioPlayerProps> = ({
  radioStatus,
  currentStation,
  activeRjAudioUrl,
  activeRjText,
  onRjAudioFinished,
  streamUrl,
}) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [isBuffering, setIsBuffering] = useState(false);
  const [volume, setVolume] = useState(0.85);
  const [isMuted, setIsMuted] = useState(false);
  const [streamError, setStreamError] = useState<string | null>(null);

  // 20-second live buffer delay sync timer
  const BUFFER_DURATION_SEC = 20;
  const [syncSeconds, setSyncSeconds] = useState<number>(BUFFER_DURATION_SEC);
  const [syncProgress, setSyncProgress] = useState<number>(0);
  const pageOpenedAt = useRef<number>(Date.now());

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const rjAudioRef = useRef<HTMLAudioElement | null>(null);

  // Countdown timer based on station live timestamp or default 20s
  useEffect(() => {
    if (!radioStatus.isLive) {
      setSyncSeconds(BUFFER_DURATION_SEC);
      setSyncProgress(0);
      return;
    }

    if (isPlaying && !isBuffering) {
      setSyncSeconds(0);
      setSyncProgress(100);
      return;
    }

    // Determine initial remaining seconds:
    // If broadcaster went live within last 20s, use that remaining;
    // Otherwise calculate from when user opened page (up to 20s).
    const now = Date.now();
    let initialRemaining = BUFFER_DURATION_SEC;
    if (currentStation?.updatedAt) {
      const stationElapsed = Math.floor((now - currentStation.updatedAt) / 1000);
      if (stationElapsed >= 0 && stationElapsed < BUFFER_DURATION_SEC) {
        initialRemaining = BUFFER_DURATION_SEC - stationElapsed;
      } else {
        const pageElapsed = Math.floor((now - pageOpenedAt.current) / 1000);
        initialRemaining = Math.max(0, BUFFER_DURATION_SEC - pageElapsed);
      }
    } else {
      const pageElapsed = Math.floor((now - pageOpenedAt.current) / 1000);
      initialRemaining = Math.max(0, BUFFER_DURATION_SEC - pageElapsed);
    }

    setSyncSeconds(initialRemaining);
    setSyncProgress(Math.round(((BUFFER_DURATION_SEC - initialRemaining) / BUFFER_DURATION_SEC) * 100));

    const interval = setInterval(() => {
      setSyncSeconds((prev) => {
        if (prev <= 1) {
          setSyncProgress(100);
          return 0;
        }
        const next = prev - 1;
        setSyncProgress(Math.round(((BUFFER_DURATION_SEC - next) / BUFFER_DURATION_SEC) * 100));
        return next;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [radioStatus.isLive, isPlaying, isBuffering, currentStation?.updatedAt]);

  // Sync volume with audio element
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = isMuted ? 0 : volume;
    }
  }, [volume, isMuted]);

  // Auto-play stream when broadcaster goes live
  useEffect(() => {
    if (radioStatus.isLive && streamUrl && !isPlaying) {
      const trimmedUrl = streamUrl.trim();
      const isSourceMount =
        trimmedUrl.includes("/source") ||
        trimmedUrl.includes("-broadcast.") ||
        trimmedUrl.includes("broadcastmyself");

      if (trimmedUrl && !isSourceMount && audioRef.current) {
        setStreamError(null);
        setIsBuffering(true);
        const liveSrc = trimmedUrl.includes("?")
          ? `${trimmedUrl}&_t=${Date.now()}`
          : `${trimmedUrl}?_t=${Date.now()}`;
        audioRef.current.src = liveSrc;
        audioRef.current
          .play()
          .then(() => {
            setIsPlaying(true);
            setIsBuffering(false);
          })
          .catch((err) => {
            console.log("Autoplay waiting for listener interaction:", err);
            setIsBuffering(false);
          });
      }
    }
  }, [radioStatus.isLive, streamUrl]);

  // Audio ducking when AI RJ audio is playing
  useEffect(() => {
    if (!activeRjAudioUrl) return;

    // When an RJ audio line plays, duck background radio stream to 25% volume
    if (audioRef.current) {
      audioRef.current.volume = isMuted ? 0 : volume * 0.25;
    }

    if (rjAudioRef.current) {
      rjAudioRef.current.src = activeRjAudioUrl;
      rjAudioRef.current.volume = isMuted ? 0 : 1.0;
      rjAudioRef.current
        .play()
        .catch((e) => console.warn("RJ auto-play prevented by browser policy:", e));
    }
  }, [activeRjAudioUrl]);

  const handleRjEnded = () => {
    // Restore music volume smoothly
    if (audioRef.current) {
      audioRef.current.volume = isMuted ? 0 : volume;
    }
    if (onRjAudioFinished) {
      onRjAudioFinished();
    }
  };

  const togglePlay = () => {
    if (!audioRef.current) return;

    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
      setIsBuffering(false);
    } else {
      const trimmedUrl = streamUrl.trim();
      const isSourceMount = trimmedUrl.includes("/source") || trimmedUrl.includes("-broadcast.") || trimmedUrl.includes("broadcastmyself");

      if (!trimmedUrl || isSourceMount) {
        setStreamError(
          currentStation
            ? `No valid public listening stream URL configured for "${currentStation.stationTitle}". Check station settings.`
            : "No active radio stream URL configured yet."
        );
        return;
      }
      setStreamError(null);
      setIsBuffering(true);
      // Append cache buster to avoid browser caching a stale live stream segment
      const liveSrc = trimmedUrl.includes("?")
        ? `${trimmedUrl}&_t=${Date.now()}`
        : `${trimmedUrl}?_t=${Date.now()}`;
      audioRef.current.src = liveSrc;
      audioRef.current
        .play()
        .then(() => {
          setIsPlaying(true);
          setIsBuffering(false);
        })
        .catch((err) => {
          console.warn("Playback waiting or interrupted:", err);
          setIsPlaying(false);
          setIsBuffering(false);
        });
    }
  };

  // Retry playback after a stream error (listener-facing recovery)
  const retryPlayback = () => {
    setStreamError(null);
    if (!isPlaying) togglePlay();
  };

  // Playback truth: the ONLY source for a "live / in sync" affirmative state.
  // Never infer "playing" from a timer — the stream is audible only when the
  // <audio> element is actually playing and not buffering.
  const isListening = isPlaying && !isBuffering;

  return (
    <div className="bg-white rounded-2xl border border-slate-200/90 shadow-sm overflow-hidden">
      {/* Hidden audio element for live radio stream */}
      <audio
        ref={audioRef}
        preload="none"
        onWaiting={() => setIsBuffering(true)}
        onPlaying={() => {
          setIsBuffering(false);
          setIsPlaying(true);
          setSyncSeconds(0);
          setSyncProgress(100);
        }}
        onCanPlay={() => setIsBuffering(false)}
        onError={() => {
          setIsBuffering(false);
          setIsPlaying(false);
          setStreamError("Stream disconnected or unavailable.");
        }}
      />

      {/* Hidden audio element for RJ Voice Breaks */}
      <audio ref={rjAudioRef} onEnded={handleRjEnded} />

      {/* Main Player Display */}
      <div className="p-6 sm:p-8 bg-slate-900 text-white relative">
        <div className="flex flex-col md:flex-row items-center justify-between gap-6 relative z-10">
          {/* Left: Now Playing Metadata */}
          <div className="flex items-center gap-4 sm:gap-6 w-full md:w-auto">
            <div className="relative group flex-shrink-0">
              <div className="w-20 h-20 sm:w-24 sm:h-24 rounded-2xl bg-blue-600 flex items-center justify-center shadow-lg shadow-blue-500/20 border border-white/10 overflow-hidden">
                <Music className="w-10 h-10 text-white/90" />
                {isPlaying && !isBuffering && (
                  <div className="absolute inset-0 bg-black/40 backdrop-blur-[1px] flex items-end justify-center gap-1 p-3">
                    <span className="w-1.5 bg-white rounded-full h-8 animate-equalizer-1" />
                    <span className="w-1.5 bg-white rounded-full h-8 animate-equalizer-2" />
                    <span className="w-1.5 bg-white rounded-full h-8 animate-equalizer-3" />
                    <span className="w-1.5 bg-white rounded-full h-8 animate-equalizer-4" />
                  </div>
                )}
              </div>
            </div>

            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 mb-1">
                {radioStatus.isLive ? (
                  isListening ? (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold tracking-wide uppercase bg-emerald-500/20 text-emerald-300 border border-emerald-400/30">
                      <Radio className="w-3 h-3 text-emerald-400" />
                      LIVE ON AIR
                    </span>
                  ) : isBuffering ? (
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wide uppercase bg-amber-500/20 text-amber-300 border border-amber-400/40 animate-pulse">
                      <Timer className="w-3 h-3 text-amber-300 animate-spin" />
                      SYNCING LIVE STREAM{syncSeconds > 0 ? ` · ${syncSeconds}s` : ""}
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wide uppercase bg-blue-500/25 text-blue-200 border border-blue-400/40">
                      <Play className="w-3 h-3 text-blue-200 fill-blue-200" />
                      TAP TO LISTEN
                    </span>
                  )
                ) : (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium tracking-wide uppercase bg-slate-700/60 text-slate-400 border border-slate-600/30">
                    <Radio className="w-3 h-3 text-slate-400" />
                    STATION OFFLINE
                  </span>
                )}
                {isListening && (
                  <span className="text-[10px] text-emerald-400 font-semibold tracking-wider flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
                    LIVE DATA IN SYNC
                  </span>
                )}
              </div>
              <h2 className="text-lg sm:text-xl font-bold truncate text-white tracking-tight">
                {radioStatus.nowPlayingTrack
                  ? radioStatus.nowPlayingTrack
                  : currentStation?.stationTitle || "Airwave Agents"}
              </h2>
              {radioStatus.nowPlayingArtist ? (
                <p className="text-xs sm:text-sm text-slate-300 truncate">
                  {radioStatus.nowPlayingArtist}
                </p>
              ) : currentStation?.broadcasterName ? (
                <p className="text-xs sm:text-sm text-slate-400 truncate">
                  Host: {currentStation.broadcasterName}
                </p>
              ) : null}
            </div>
          </div>

          {/* Right: Controls & Volume */}
          <div className="flex items-center gap-4 w-full md:w-auto justify-between md:justify-end">
            {/* Play/Pause Button */}
            <button
              onClick={togglePlay}
              disabled={isBuffering}
              aria-label={isPlaying ? "Pause live stream" : "Play live stream"}
              className={`w-14 h-14 sm:w-16 sm:h-16 rounded-full flex items-center justify-center transition shadow-lg transform active:scale-95 ${
                isPlaying
                  ? "bg-red-500 hover:bg-red-600 text-white shadow-red-500/30"
                  : "bg-blue-600 hover:bg-blue-500 text-white shadow-blue-500/30"
              }`}
              title={isPlaying ? "Pause Stream" : "Listen Live"}
            >
              {isBuffering ? (
                <div className="w-6 h-6 border-3 border-white border-t-transparent rounded-full animate-spin" />
              ) : isPlaying ? (
                <Pause className="w-7 h-7 fill-white" />
              ) : (
                <Play className="w-7 h-7 fill-white translate-x-0.5" />
              )}
            </button>

            {/* Volume Control */}
            <div className="flex items-center gap-2 bg-white/10 backdrop-blur-md px-3.5 py-2.5 rounded-xl border border-white/10">
              <button
                onClick={() => setIsMuted(!isMuted)}
                aria-label={isMuted ? "Unmute" : "Mute"}
                aria-pressed={isMuted}
                className="text-slate-300 hover:text-white transition"
              >
                {isMuted || volume === 0 ? (
                  <VolumeX className="w-5 h-5 text-red-400" />
                ) : (
                  <Volume2 className="w-5 h-5" />
                )}
              </button>
              <input
                type="range"
                min="0"
                max="1"
                step="0.01"
                value={isMuted ? 0 : volume}
                aria-label="Volume"
                aria-valuetext={`${Math.round((isMuted ? 0 : volume) * 100)} percent`}
                onChange={(e) => {
                  setVolume(parseFloat(e.target.value));
                  if (isMuted) setIsMuted(false);
                }}
                className="w-20 sm:w-28 accent-blue-500 cursor-pointer h-1.5 bg-slate-700 rounded-lg"
              />
            </div>
          </div>
        </div>

        {/* Stream Error Alert */}
        {streamError && (
          <div className="mt-4 p-3 rounded-xl bg-red-500/20 border border-red-500/30 text-red-200 text-xs flex items-center justify-between">
            <div className="flex items-center gap-2">
              <WifiOff className="w-4 h-4 text-red-400 flex-shrink-0" />
              <span>{streamError}</span>
            </div>
            <button
              onClick={retryPlayback}
              className="underline hover:text-white ml-2 flex-shrink-0"
            >
              Retry
            </button>
          </div>
        )}

        {/* Live AI RJ On-Air Speaking Notice */}
        {activeRjText && (
          <div className="mt-4 p-3.5 rounded-xl bg-white/5 border border-white/10 text-white text-xs flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-purple-500/20 border border-purple-400/40 flex items-center justify-center flex-shrink-0">
              <Sparkles className="w-4 h-4 text-purple-300" />
            </div>
            <div className="min-w-0">
              <p className="font-semibold text-purple-200 uppercase text-[10px] tracking-wider">
                AI RJ On-Air Live Speech
              </p>
              <p className="text-slate-100 truncate italic">"{activeRjText}"</p>
            </div>
          </div>
        )}
      </div>

      {/* Live Stream Buffer & Data Sync Indicator */}
      {radioStatus.isLive && (
        <div className="border-t border-slate-100 bg-slate-50/90 px-6 py-3.5 flex flex-col sm:flex-row items-center justify-between gap-4">
          {isBuffering ? (
            <>
              <div className="flex items-center gap-3 w-full sm:w-auto">
                <div className="w-9 h-9 rounded-xl bg-amber-500/10 text-amber-600 flex items-center justify-center flex-shrink-0 border border-amber-200">
                  <Timer className="w-4 h-4 animate-spin text-amber-600" />
                </div>
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-slate-800">
                      Receiving Live Audio Stream
                    </span>
                    {syncSeconds > 0 && (
                      <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 border border-amber-300">
                        ~{syncSeconds}s remaining
                      </span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-500 truncate">
                    Icecast live audio relay buffer is syncing (~20s delay). Audio packets arriving shortly.
                  </p>
                </div>
              </div>

              <div className="w-full sm:w-64 flex flex-col gap-1.5 flex-shrink-0">
                <div className="flex items-center justify-between text-[11px] font-medium text-slate-600">
                  <span className="flex items-center gap-1 text-[10px] text-amber-600 font-semibold">
                    <Activity className="w-3 h-3 animate-pulse" />
                    Buffering live data
                  </span>
                  <span className="font-mono font-bold text-amber-700">{syncProgress}%</span>
                </div>
                <div className="w-full h-2 bg-slate-200 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-amber-500 rounded-full transition-all duration-1000 ease-linear"
                    style={{ width: `${Math.max(5, syncProgress)}%` }}
                  />
                </div>
              </div>
            </>
          ) : isListening ? (
            <div className="flex items-center justify-between w-full">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center flex-shrink-0 border border-emerald-200">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                </div>
                <div>
                  <span className="text-xs font-bold text-emerald-800 flex items-center gap-1.5">
                    Live Broadcast In Sync
                    <span className="inline-block w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
                  </span>
                  <p className="text-[11px] text-slate-500">
                    Connected to studio stream. Live audio data playing in real time.
                  </p>
                </div>
              </div>

              <span className="px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-emerald-100 text-emerald-800 border border-emerald-300">
                0s BUFFER LAG • LIVE
              </span>
            </div>
          ) : (
            // Live, but audio isn't playing yet (autoplay blocked or paused).
            // Never claim "in sync" here — prompt the listener to start playback.
            <button
              onClick={retryPlayback}
              className="flex items-center gap-3 w-full text-left group"
            >
              <div className="w-9 h-9 rounded-xl bg-blue-600 text-white flex items-center justify-center flex-shrink-0 shadow-sm group-hover:bg-blue-700 transition">
                <Play className="w-4 h-4 fill-white translate-x-0.5" />
              </div>
              <div className="min-w-0">
                <span className="text-xs font-bold text-slate-800">
                  Tap to start listening
                </span>
                <p className="text-[11px] text-slate-500 truncate">
                  This station is live. Your browser needs a tap to begin playback.
                </p>
              </div>
            </button>
          )}
        </div>
      )}
    </div>
  );
};
