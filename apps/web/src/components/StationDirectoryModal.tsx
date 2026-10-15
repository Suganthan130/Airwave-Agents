import React, { useEffect } from "react";
import type { BroadcasterStation } from "../types.ts";
import { Radio, Music, X, ExternalLink, Check, Signal } from "lucide-react";

interface StationDirectoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  stations: BroadcasterStation[];
  activeStationId: string | null;
  onSelectStation: (stationId: string) => void;
}

export const StationDirectoryModal: React.FC<StationDirectoryModalProps> = ({
  isOpen,
  onClose,
  stations,
  activeStationId,
  onSelectStation,
}) => {
  useEffect(() => {
    if (!isOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Broadcaster stations"
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-slate-100 overflow-hidden animate-in fade-in zoom-in-95 duration-200 flex flex-col max-h-[85vh]"
      >
        {/* Header */}
        <div className="bg-blue-600 p-5 text-white flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-white/10 flex items-center justify-center border border-white/20">
              <Radio className="w-5 h-5 text-white" />
            </div>
            <div>
              <h3 className="font-bold text-base">Broadcaster Stations</h3>
              <p className="text-xs text-blue-100">
                Created by broadcasters on the Airwave Transmitter app (1 per broadcaster)
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Close station directory"
            className="p-2.5 rounded-full hover:bg-white/10 text-white/80 hover:text-white transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Stations List */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3">
          {stations.length === 0 ? (
            <div className="text-center py-12 text-slate-400">
              <Signal className="w-8 h-8 mx-auto mb-2 text-slate-300 animate-pulse" />
              <p className="text-sm font-semibold text-slate-700">No active broadcaster stations found</p>
              <p className="text-xs text-slate-500 mt-1 max-w-xs mx-auto">
                Broadcasters create their unique public listener station automatically when logging in to the Airwave Transmitter Android app.
              </p>
            </div>
          ) : (
            stations.map((st) => {
              const isActive = st.stationId === activeStationId;
              return (
                <div
                  key={st.stationId}
                  className={`p-4 rounded-xl border transition flex items-center justify-between gap-4 ${
                    isActive
                      ? "bg-blue-50/70 border-blue-300 ring-2 ring-blue-500/20"
                      : "bg-white hover:bg-slate-50 border-slate-200"
                  }`}
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-bold text-sm text-slate-900 truncate">
                        {st.stationTitle}
                      </span>
                      {st.isLive ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-700">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping" />
                          LIVE
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-100 text-slate-500">
                          OFFLINE
                        </span>
                      )}
                    </div>

                    {st.broadcasterName && (
                      <p className="text-xs text-slate-500 flex items-center gap-1.5">
                        <span>Broadcaster:</span>
                        <span className="font-medium text-slate-800">{st.broadcasterName}</span>
                      </p>
                    )}

                    {st.isLive && st.nowPlayingTrack && (
                      <p className="text-[11px] text-blue-600 truncate mt-1 flex items-center gap-1 font-medium">
                        <Music className="w-3 h-3 flex-shrink-0" />
                        <span>Now: {st.nowPlayingTrack}</span>
                      </p>
                    )}
                  </div>

                  <div className="flex items-center gap-2 flex-shrink-0">
                    {isActive ? (
                      <span className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-blue-600 text-white shadow-xs">
                        <Check className="w-3.5 h-3.5" />
                        Tuned In
                      </span>
                    ) : (
                      <button
                        onClick={() => {
                          onSelectStation(st.stationId);
                          onClose();
                        }}
                        className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-white border border-slate-200 hover:border-blue-400 text-slate-700 hover:text-blue-600 transition shadow-xs"
                      >
                        Tune In
                      </button>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer info */}
        <div className="p-4 bg-slate-50 border-t border-slate-100 text-center text-xs text-slate-500">
          Want your own station? Log in to the <strong className="text-slate-800">Airwave Transmitter App</strong> to broadcast live with your personal AI Co-Host.
        </div>
      </div>
    </div>
  );
};
