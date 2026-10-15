import React, { useState, useEffect } from "react";
import type { UserProfile, BroadcasterStation } from "../types.ts";
import { COUNTRIES } from "../data/countries.ts";
import { copyText } from "../lib/clipboard.ts";
import {
  LogIn,
  LogOut,
  Radio,
  User,
  Edit3,
  ChevronDown,
  Layers,
  Copy,
  Check,
  Crown,
  Headphones,
} from "lucide-react";

interface HeaderProps {
  user: UserProfile | null;
  loadingAuth: boolean;
  onSignIn: () => void;
  onSignOut: () => void;
  onEditProfile: () => void;
  isStreaming: boolean;
  listenerCount?: number;
  currentStation: BroadcasterStation | null;
  onOpenStationDirectory: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  user,
  loadingAuth,
  onSignIn,
  onSignOut,
  onEditProfile,
  isStreaming,
  listenerCount = 0,
  currentStation,
  onOpenStationDirectory,
}) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);

  const countryObj = user
    ? COUNTRIES.find((c) => c.name === user.country || c.code === user.country)
    : null;

  // Close the profile menu on Escape (touch-safe dismissal is the backdrop below).
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMenuOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen]);

  const handleCopyStationUrl = async (e: React.MouseEvent) => {
    e.stopPropagation();
    const ok = await copyText(window.location.href);
    if (ok) {
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2000);
    }
  };

  return (
    <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        {/* Left: Brand Identity & Active Broadcaster Station */}
        <div className="flex items-center gap-3 min-w-0">
          <div className="relative flex items-center justify-center flex-shrink-0">
            {logoFailed ? (
              <div className="flex w-10 h-10 rounded-xl bg-blue-600 text-white items-center justify-center">
                <Radio className="w-5 h-5" />
              </div>
            ) : (
              <img
                src="/logo.png"
                alt="Airwave Agents"
                className="w-10 h-10 rounded-xl object-contain shadow-sm border border-slate-100"
                onError={() => setLogoFailed(true)}
              />
            )}
            {isStreaming && (
              <span className="absolute -top-1 -right-1 flex h-3 w-3">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-3 w-3 bg-red-500"></span>
              </span>
            )}
          </div>

          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="text-sm sm:text-base font-bold tracking-tight text-slate-900 truncate">
                {currentStation?.stationTitle || "AIRWAVE AGENTS"}
              </h1>
              {currentStation?.broadcasterName && (
                <span className="hidden md:inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200/60">
                  DJ: {currentStation.broadcasterName}
                </span>
              )}
            </div>
            <p className="text-[11px] text-slate-500 truncate flex items-center gap-2">
              <span>Public Listener Page</span>
              <button
                onClick={handleCopyStationUrl}
                className="inline-flex items-center gap-1 text-blue-600 hover:text-blue-700 font-medium cursor-pointer"
                title="Copy shareable station page link"
              >
                {copiedLink ? (
                  <>
                    <Check className="w-3 h-3 text-emerald-600" />
                    <span className="text-emerald-600 font-semibold">Link Copied!</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>Copy Link</span>
                  </>
                )}
              </button>
            </p>
          </div>
        </div>

        {/* Center: Station Switcher & Live indicator */}
        <div className="flex items-center gap-2 sm:gap-3 flex-shrink-0">
          {/* Switch Station button */}
          <button
            onClick={onOpenStationDirectory}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-200 transition"
            title="Browse other broadcaster stations"
          >
            <Layers className="w-3.5 h-3.5 text-blue-600" />
            <span className="hidden sm:inline">Stations</span>
          </button>

          <div
            className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold tracking-wide border transition ${
              isStreaming
                ? "bg-red-50 text-red-600 border-red-200"
                : "bg-slate-100 text-slate-600 border-slate-200"
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full ${
                isStreaming ? "bg-red-500 animate-pulse" : "bg-slate-400"
              }`}
            />
            <span>{isStreaming ? "ON AIR" : "IDLE"}</span>
          </div>

          {listenerCount > 0 && (
            <span className="text-[11px] text-slate-500 hidden lg:inline-flex items-center gap-1">
              <Headphones className="w-3.5 h-3.5 text-slate-400" />
              {listenerCount} listening
            </span>
          )}
        </div>

        {/* Right: Auth Profile / Google Sign-in */}
        <div className="relative flex-shrink-0">
          {loadingAuth ? (
            <div className="w-24 h-9 bg-slate-100 rounded-full animate-pulse" />
          ) : user ? (
            <div className="relative">
              <button
                onClick={() => setMenuOpen(!menuOpen)}
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                aria-label="Account menu"
                className="flex items-center gap-2.5 p-1.5 pr-3 rounded-full hover:bg-slate-100 border border-slate-200/80 transition"
              >
                {user.photoURL ? (
                  <img
                    src={user.photoURL}
                    alt={user.displayName}
                    className="w-7 h-7 rounded-full object-cover border border-white"
                  />
                ) : (
                  <div className="w-7 h-7 rounded-full bg-blue-600 text-white flex items-center justify-center text-xs font-semibold">
                    {user.displayName.charAt(0).toUpperCase()}
                  </div>
                )}
                <div className="text-left hidden sm:block">
                  <div className="flex items-center gap-1">
                    <span className="text-xs font-semibold text-slate-800 line-clamp-1 max-w-[100px]">
                      {user.displayName}
                    </span>
                    {countryObj && <span className="text-xs">{countryObj.flag}</span>}
                  </div>
                  <span className="text-[10px] font-medium text-slate-400 block -mt-0.5">
                    {user.userType === "broadcaster" ? "Broadcaster" : "Listener"}
                  </span>
                </div>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
              </button>

              {/* Touch-safe click-away backdrop */}
              {menuOpen && (
                <div
                  className="fixed inset-0 z-40"
                  aria-hidden="true"
                  onClick={() => setMenuOpen(false)}
                />
              )}

              {/* Profile Dropdown */}
              {menuOpen && (
                <div
                  role="menu"
                  className="absolute right-0 mt-2 w-60 bg-white rounded-xl shadow-xl border border-slate-100 py-1.5 z-50 animate-in fade-in zoom-in-95"
                >
                  <div className="px-3.5 py-2.5 border-b border-slate-100">
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs font-bold text-slate-900 truncate">
                        {user.displayName}
                      </span>
                      <span
                        className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider flex items-center gap-1 ${
                          user.userType === "broadcaster"
                            ? "bg-purple-100 text-purple-700"
                            : "bg-blue-100 text-blue-700"
                        }`}
                      >
                        {user.userType === "broadcaster" ? (
                          <>
                            <Crown className="w-3 h-3" />
                            Broadcaster
                          </>
                        ) : (
                          <>
                            <Headphones className="w-3 h-3" />
                            Listener
                          </>
                        )}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-500 truncate">{user.email}</p>
                    {countryObj && (
                      <p className="text-[11px] text-slate-600 mt-1 flex items-center gap-1 font-medium">
                        <span>{countryObj.flag}</span> {countryObj.name}
                      </p>
                    )}
                  </div>

                  <button
                    onClick={() => {
                      setMenuOpen(false);
                      onEditProfile();
                    }}
                    className="w-full px-3.5 py-2 text-left text-xs text-slate-700 hover:bg-slate-50 flex items-center gap-2"
                  >
                    <Edit3 className="w-3.5 h-3.5 text-blue-600" />
                    Edit Listener Profile
                  </button>

                  <button
                    onClick={() => {
                      setMenuOpen(false);
                      onSignOut();
                    }}
                    className="w-full px-3.5 py-2 text-left text-xs text-red-600 hover:bg-red-50 flex items-center gap-2"
                  >
                    <LogOut className="w-3.5 h-3.5" />
                    Sign Out
                  </button>
                </div>
              )}
            </div>
          ) : (
            <button
              onClick={onSignIn}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-xs font-semibold rounded-full shadow-sm shadow-blue-500/20 transition"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>Sign in with Google</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
