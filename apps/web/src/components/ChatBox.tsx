import React, { useState, useEffect, useRef } from "react";
import type { ChatMessage, UserProfile, BroadcasterStation } from "../types.ts";
import { COUNTRIES } from "../data/countries.ts";
import {
  Send,
  Sparkles,
  Play,
  Pause,
  LogIn,
  AlertCircle,
  CheckCircle2,
  ShieldAlert,
  Crown,
  Headphones,
} from "lucide-react";

interface ChatBoxProps {
  messages: ChatMessage[];
  user: UserProfile | null;
  currentStation?: BroadcasterStation | null;
  onSendMessage: (text: string) => Promise<void>;
  onSignIn: () => void;
  onPromptProfile: () => void;
  onPlayRjAudio: (audioBase64: string, text: string) => void;
  activePlayingAudioText?: string | null;
}

export const ChatBox: React.FC<ChatBoxProps> = ({
  messages,
  user,
  currentStation,
  onSendMessage,
  onSignIn,
  onPromptProfile,
  onPlayRjAudio,
  activePlayingAudioText,
}) => {
  const [inputText, setInputText] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  // Scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) {
      onSignIn();
      return;
    }

    if (!user.country || !user.displayName) {
      onPromptProfile();
      return;
    }

    const trimmed = inputText.trim();
    if (!trimmed) return;

    try {
      setIsSubmitting(true);
      setErrorMessage(null);
      await onSendMessage(trimmed);
      setInputText("");
    } catch (err: any) {
      console.error("Failed to send message:", err);
      setErrorMessage(err?.message || "Failed to send message. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const getCountryFlag = (countryName: string) => {
    const found = COUNTRIES.find(
      (c) => c.name.toLowerCase() === countryName.toLowerCase() || c.code.toLowerCase() === countryName.toLowerCase()
    );
    return found ? found.flag : "🌐";
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-200/90 shadow-sm flex flex-col h-[70vh] min-h-[460px] lg:h-[650px] overflow-hidden">
      {/* Chat Header */}
      <div className="px-6 py-4 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="font-bold text-slate-900 text-sm tracking-tight flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-blue-600 animate-pulse" />
              Live Chat · {currentStation?.stationTitle || "Airwave Agents Studio"}
            </h3>
          </div>
          <p className="text-[11px] text-slate-500 mt-0.5">
            {currentStation?.broadcasterName
              ? `Chatting live on ${currentStation.broadcasterName}'s station. AI RJ replies on-air!`
              : "Send a shoutout or question. The AI Host replies with voice at the end of each track!"}
          </p>
        </div>

        {/* Live chat badge */}
        <div className="hidden sm:flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200/60">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          <span>Live Studio Chat</span>
        </div>
      </div>

      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-4 bg-slate-50/30">
        {messages.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-8 text-slate-400">
            <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mb-3">
              <Sparkles className="w-6 h-6" />
            </div>
            <p className="text-sm font-semibold text-slate-700">No on-air messages yet</p>
            <p className="text-xs text-slate-500 mt-1 max-w-xs">
              Be the first listener to say hello or request a song! The AI RJ will reply warmly with voice.
            </p>
          </div>
        ) : (
          messages.map((msg) => {
            const isMe = user?.uid === msg.userId;
            const flag = getCountryFlag(msg.userCountry || "Global");
            const isPlayingThisReply =
              activePlayingAudioText && activePlayingAudioText === msg.replyText;

            return (
              <div
                key={msg.id}
                className={`flex flex-col space-y-2.5 max-w-2xl ${
                  isMe ? "ml-auto" : "mr-auto"
                } w-full`}
              >
                {/* User Message Bubble */}
                <div
                  className={`p-4 rounded-2xl border transition ${
                    isMe
                      ? "bg-blue-600 text-white border-blue-600 rounded-br-xs shadow-sm"
                      : "bg-white text-slate-800 border-slate-200/80 rounded-bl-xs shadow-xs"
                  }`}
                >
                  <div className="flex items-center justify-between gap-3 mb-1.5 pb-1.5 border-b border-black/10">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className="text-sm">{flag}</span>
                      <span
                        className={`text-xs font-bold truncate ${
                          isMe ? "text-blue-100" : "text-slate-900"
                        }`}
                      >
                        {msg.userName}
                      </span>
                      <span
                        className={`text-[10px] px-1.5 py-0.2 rounded-md ${
                          isMe
                            ? "bg-blue-700/60 text-blue-200"
                            : "bg-slate-100 text-slate-500"
                        }`}
                      >
                        {msg.userCountry}
                      </span>
                      {msg.userType === "broadcaster" ? (
                        <span className="inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-md font-semibold bg-purple-100 text-purple-700 border border-purple-200/60">
                          <Crown className="w-2.5 h-2.5" />
                          Broadcaster
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-md bg-blue-50 text-blue-600 border border-blue-100">
                          <Headphones className="w-2.5 h-2.5" />
                          Listener
                        </span>
                      )}
                    </div>

                    <span
                      className={`text-[10px] ${
                        isMe ? "text-blue-200" : "text-slate-400"
                      }`}
                    >
                      {new Date(msg.createdAt).toLocaleTimeString([], {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </span>
                  </div>

                  <p className="text-xs sm:text-sm leading-relaxed whitespace-pre-wrap break-words">
                    {msg.text}
                  </p>
                </div>

                {/* AI RJ Reply Status & Voice Bubble */}
                {msg.status === "processing" && (
                  <div className="ml-4 p-3 bg-purple-50 border border-purple-200/80 rounded-xl rounded-tl-xs flex items-center gap-2.5 text-xs text-purple-700 animate-pulse">
                    <div className="w-5 h-5 rounded-full bg-purple-200 flex items-center justify-center flex-shrink-0">
                      <Sparkles className="w-3 h-3 text-purple-700" />
                    </div>
                    <span>AI Co-Host is scripting and voicing your on-air response...</span>
                  </div>
                )}

                {msg.status === "replied" && msg.replyText && (
                  <div className="ml-4 p-4 bg-purple-50/60 border border-purple-200 rounded-2xl rounded-tl-xs shadow-xs space-y-2.5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <div className="w-6 h-6 rounded-full bg-purple-600 text-white flex items-center justify-center text-[10px] font-bold shadow-xs">
                          AI
                        </div>
                        <span className="text-xs font-bold text-purple-950 flex items-center gap-1.5">
                          Airwave AI Co-Host
                          <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-purple-200/70 text-purple-800">
                            ON AIR
                          </span>
                        </span>
                      </div>

                      {msg.replyAudioBase64 && (
                        <button
                          onClick={() =>
                            onPlayRjAudio(msg.replyAudioBase64!, msg.replyText!)
                          }
                          className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold shadow-xs transition ${
                            isPlayingThisReply
                              ? "bg-purple-600 text-white"
                              : "bg-white hover:bg-purple-100 text-purple-700 border border-purple-200"
                          }`}
                        >
                          {isPlayingThisReply ? (
                            <>
                              <Pause className="w-3 h-3 fill-white" />
                              <span>Playing RJ Voice</span>
                            </>
                          ) : (
                            <>
                              <Play className="w-3 h-3 fill-purple-700 translate-x-0.2" />
                              <span>Listen RJ Voice</span>
                            </>
                          )}
                        </button>
                      )}
                    </div>

                    <p className="text-xs sm:text-sm text-slate-800 leading-relaxed italic border-l-2 border-purple-400 pl-3">
                      "{msg.replyText}"
                    </p>
                  </div>
                )}

                {msg.status === "skipped" && (
                  <div className="ml-4 p-3 bg-amber-50 border border-amber-200/80 rounded-xl rounded-tl-xs flex items-center gap-2.5 text-xs text-amber-800">
                    <ShieldAlert className="w-4 h-4 text-amber-600 flex-shrink-0" />
                    <span>
                      The AI Co-Host gracefully skipped this question to maintain family-friendly radio broadcast guidelines.
                    </span>
                  </div>
                )}
              </div>
            );
          })
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input / 20-Min Cooldown Section */}
      <div className="p-4 sm:p-5 bg-white border-t border-slate-200/90 space-y-3">
        {errorMessage && (
          <div className="p-2.5 text-xs rounded-xl bg-red-50 text-red-600 border border-red-200 flex items-center gap-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {!user ? (
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-center flex flex-col sm:flex-row items-center justify-between gap-3">
            <div className="text-left">
              <p className="text-xs font-semibold text-slate-800">
                Sign in with Google to chat with the live AI Host
              </p>
              <p className="text-[11px] text-slate-500">
                Listeners get real-time on-air voice shoutouts and song replies!
              </p>
            </div>
            <button
              onClick={onSignIn}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-xs font-semibold rounded-xl flex items-center gap-2 shadow-sm transition"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>Sign in with Google</span>
            </button>
          </div>
        ) : (
          <div>
            <div className="mb-2 flex items-center justify-between text-[11px] text-slate-500">
              <span className="flex items-center gap-1.5 text-emerald-600 font-medium">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
                Your channel is open! Say hi or request a song from the AI Host.
              </span>
              {inputText.length > 240 ? (
                <span className={inputText.length >= 280 ? "text-red-500 font-semibold" : "text-slate-400"}>
                  {inputText.length}/280
                </span>
              ) : (
                <span className="text-slate-400">Live Studio Chat</span>
              )}
            </div>

            {/* Input Form */}
            <form onSubmit={handleSubmit} className="flex items-center gap-2">
              <input
                type="text"
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                disabled={isSubmitting}
                placeholder="Type a song request, shoutout, or question for the AI Host..."
                maxLength={280}
                className="flex-1 px-4 py-2.5 text-xs sm:text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-600 disabled:opacity-50 disabled:bg-slate-100 transition text-slate-800"
              />

              <button
                type="submit"
                disabled={!inputText.trim() || isSubmitting}
                className="px-4 py-2.5 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-xs font-semibold rounded-xl shadow-sm shadow-blue-500/20 flex items-center gap-1.5 transition disabled:opacity-50 disabled:pointer-events-none"
              >
                {isSubmitting ? (
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                ) : (
                  <>
                    <Send className="w-3.5 h-3.5" />
                    <span className="hidden sm:inline">Send</span>
                  </>
                )}
              </button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
};
