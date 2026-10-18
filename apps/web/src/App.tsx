import React, { useState, useEffect } from "react";
import {
  auth,
  db,
  googleProvider,
  signInWithPopup,
  fbSignOut,
  onAuthStateChanged,
  doc,
  getDoc,
  setDoc,
  collection,
  query,
  orderBy,
  limit,
  onSnapshot,
  serverTimestamp,
  type FirebaseUser,
} from "./firebase.ts";
import type { UserProfile, ChatMessage, RadioStatus, BroadcasterStation } from "./types.ts";
import { Header } from "./components/Header.tsx";
import { RadioPlayer } from "./components/RadioPlayer.tsx";
import { ChatBox } from "./components/ChatBox.tsx";
import { ProfileModal } from "./components/ProfileModal.tsx";
import { RjAnnouncementBanner } from "./components/RjAnnouncementBanner.tsx";
import { StationDirectoryModal } from "./components/StationDirectoryModal.tsx";
import { AuthModal } from "./components/AuthModal.tsx";
import { Sparkles, Headphones, ShieldCheck, Crown, Radio as RadioIcon, Share2, Check, WifiOff, AlertCircle, X } from "lucide-react";
import { copyText } from "./lib/clipboard.ts";

// Default station loaded when the site is opened without a ?station= param,
// so https://airwave-agents.web.app/ tunes straight into the demo station.
const DEMO_STATION_ID = "BtA8T6KYxuZf8bxFNKleilheS9v1";

// Validates that a stream URL is a public listener endpoint, rejecting transmitter source mounts
const isValidPublicStream = (url?: string | null): boolean => {
  if (!url || typeof url !== "string") return false;
  const lower = url.toLowerCase().trim();
  if (lower.includes("/source") || lower.includes("-broadcast.") || lower.includes("broadcastmyself")) {
    return false;
  }
  return lower.startsWith("http://") || lower.startsWith("https://");
};

export const App: React.FC = () => {
  const [currentUser, setCurrentUser] = useState<UserProfile | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [canDismissModal, setCanDismissModal] = useState(false);
  const [showStationModal, setShowStationModal] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);
  const [toast, setToast] = useState<{ kind: "error" | "success"; text: string } | null>(null);
  // Start optimistic. navigator.onLine is unreliable at page load (false positives
  // behind proxies/VPNs), so we only trust actual online/offline event transitions.
  const [isOffline, setIsOffline] = useState<boolean>(false);

  // Auto-dismiss transient toasts
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 5000);
    return () => clearTimeout(t);
  }, [toast]);

  // Connectivity indicator — distinguishes a listener-side network drop from a
  // station-side stream failure.
  useEffect(() => {
    const goOnline = () => setIsOffline(false);
    const goOffline = () => setIsOffline(true);
    window.addEventListener("online", goOnline);
    window.addEventListener("offline", goOffline);
    return () => {
      window.removeEventListener("online", goOnline);
      window.removeEventListener("offline", goOffline);
    };
  }, []);

  // Active station ID from URL query ?station=UID or auto-detected
  const [activeStationId, setActiveStationId] = useState<string | null>(() => {
    const params = new URLSearchParams(window.location.search);
    return params.get("station") || DEMO_STATION_ID;
  });

  const [stations, setStations] = useState<BroadcasterStation[]>([]);
  const [currentStation, setCurrentStation] = useState<BroadcasterStation | null>(null);

  const [streamUrl, setStreamUrl] = useState<string>(() => {
    const saved = localStorage.getItem("airwave_stream_url") || "";
    if (!isValidPublicStream(saved)) {
      localStorage.removeItem("airwave_stream_url");
      return "";
    }
    return saved;
  });

  const [radioStatus, setRadioStatus] = useState<RadioStatus>({
    isLive: false,
    nowPlayingTrack: "",
    nowPlayingArtist: "",
    listenersCount: 0,
    streamUrl: "",
  });

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [activeRjAudioUrl, setActiveRjAudioUrl] = useState<string | null>(null);
  const [activeRjText, setActiveRjText] = useState<string | null>(null);
  const [bannerNotice, setBannerNotice] = useState<{ text: string; audioBase64?: string } | null>(null);

  // 1. Auth Listener & User Profile Fetch (Distinguishes Broadcaster vs Listener)
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (fbUser: FirebaseUser | null) => {
      if (!fbUser) {
        setCurrentUser(null);
        setLoadingAuth(false);
        return;
      }

      try {
        const userRef = doc(db, "users", fbUser.uid);
        const snapshot = await getDoc(userRef);

        if (snapshot.exists()) {
          const data = snapshot.data();
          const profile: UserProfile = {
            uid: fbUser.uid,
            displayName: data.displayName || data.name || fbUser.displayName || "Listener",
            email: fbUser.email,
            photoURL: fbUser.photoURL,
            country: data.country || "",
            userType: (data.userType as "broadcaster" | "listener") || "listener",
            stationId: data.stationId || undefined,
            lastChatAt: data.lastChatAt?.toMillis ? data.lastChatAt.toMillis() : data.lastChatAt || 0,
            createdAt: data.createdAt?.toMillis ? data.createdAt.toMillis() : Date.now(),
          };
          setCurrentUser(profile);

          if (!profile.country || !profile.displayName) {
            setCanDismissModal(false);
            setShowProfileModal(true);
          }
        } else {
          // New Listener Onboarding (Default role: listener)
          const newProfile: UserProfile = {
            uid: fbUser.uid,
            displayName: fbUser.displayName || "Listener",
            email: fbUser.email,
            photoURL: fbUser.photoURL,
            country: "",
            userType: "listener",
          };
          setCurrentUser(newProfile);
          setCanDismissModal(false);
          setShowProfileModal(true);
        }
      } catch (err) {
        console.error("Error reading user profile:", err);
      } finally {
        setLoadingAuth(false);
      }
    });

    return () => unsubscribe();
  }, []);

  // 2. Realtime Listener for Broadcaster Stations Directory
  useEffect(() => {
    const q = query(
      collection(db, "stations"),
      orderBy("updatedAt", "desc"),
      limit(25)
    );

    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        const list: BroadcasterStation[] = [];
        snapshot.forEach((d) => {
          const data = d.data();
          const publicStream = (isValidPublicStream(data.streamUrl) ? data.streamUrl : "") ||
            (isValidPublicStream(data.publicListeningUrl) ? data.publicListeningUrl : "");
          list.push({
            stationId: d.id,
            stationTitle: data.stationTitle || (data.broadcasterName ? `${data.broadcasterName}'s Station` : "Live Station"),
            broadcasterName: data.broadcasterName || "",
            broadcasterEmail: data.broadcasterEmail,
            host: data.host,
            port: data.port,
            serverType: data.serverType,
            mount: data.mount,
            publicPageUrl: data.publicPageUrl,
            streamUrl: publicStream,
            isLive: data.isLive ?? false,
            nowPlayingTrack: data.nowPlayingTrack || "",
            nowPlayingArtist: data.nowPlayingArtist || "",
            listenersCount: Number(data.listenersCount) || 0,
            voice: data.voice,
            speakingRate: data.speakingRate,
            aiPersona: data.aiPersona,
            playlist: data.playlist,
            updatedAt: data.updatedAt || Date.now(),
            broadcasterId: data.stationId || data.broadcasterId || d.id,
            uniqueId: data.stationId || data.uniqueId || d.id,
          });
        });
        setStations(list);

        // If no active station is chosen, pick the live one or first available station
        if (!activeStationId && list.length > 0) {
          const liveOne = list.find((s) => s.isLive) || list[0];
          setActiveStationId(liveOne.stationId);
        }
      },
      (err) => console.warn("Stations snapshot warning:", err)
    );

    return () => unsubscribe();
  }, [activeStationId]);

  // 3. Realtime Listener for Current Selected Station
  useEffect(() => {
    if (!activeStationId) {
      setCurrentStation(null);
      return;
    }

    const stationRef = doc(db, "stations", activeStationId);
    const unsubscribe = onSnapshot(stationRef, (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();
        const publicStream = (isValidPublicStream(data.streamUrl) ? data.streamUrl : "") ||
          (isValidPublicStream(data.publicListeningUrl) ? data.publicListeningUrl : "");
        const st: BroadcasterStation = {
          stationId: docSnap.id,
          stationTitle: data.stationTitle || (data.broadcasterName ? `${data.broadcasterName}'s Station` : "Live Station"),
          broadcasterName: data.broadcasterName || "",
          broadcasterEmail: data.broadcasterEmail,
          host: data.host,
          port: data.port,
          serverType: data.serverType,
          mount: data.mount,
          publicPageUrl: data.publicPageUrl,
          streamUrl: publicStream,
          isLive: data.isLive ?? false,
          nowPlayingTrack: data.nowPlayingTrack || "",
          nowPlayingArtist: data.nowPlayingArtist || "",
          listenersCount: Number(data.listenersCount) || 0,
          voice: data.voice,
          speakingRate: data.speakingRate,
          aiPersona: data.aiPersona,
          playlist: data.playlist,
          updatedAt: data.updatedAt || Date.now(),
          broadcasterId: data.stationId || data.broadcasterId || docSnap.id,
          uniqueId: data.stationId || data.uniqueId || docSnap.id,
        };
        setCurrentStation(st);
        setRadioStatus({
          isLive: st.isLive,
          nowPlayingTrack: st.nowPlayingTrack || "",
          nowPlayingArtist: st.nowPlayingArtist || "",
          listenersCount: st.listenersCount || 0,
          streamUrl: publicStream,
        });
        if (publicStream) {
          setStreamUrl(publicStream);
          localStorage.setItem("airwave_stream_url", publicStream);
        } else {
          setStreamUrl("");
          localStorage.removeItem("airwave_stream_url");
        }
      } else {
        setCurrentStation(null);
      }
    });

    return () => unsubscribe();
  }, [activeStationId]);

  // 4. Realtime Chat Messages Listener (Scoped to Current Station)
  useEffect(() => {
    const q = query(
      collection(db, "messages"),
      orderBy("createdAt", "desc"),
      limit(60)
    );

    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        const list: ChatMessage[] = [];
        snapshot.forEach((d) => {
          const val = d.data();
          list.push({
            id: d.id,
            stationId: val.stationId,
            userId: val.userId,
            userName: val.userName || "Listener",
            userCountry: val.userCountry || "Global",
            userType: val.userType || "listener",
            userAvatar: val.userAvatar || null,
            text: val.text || "",
            status: val.status || "queued",
            createdAt: val.createdAt?.toMillis ? val.createdAt.toMillis() : Number(val.createdAt) || Date.now(),
            replyText: val.replyText || undefined,
            replyAudioBase64: val.replyAudioBase64 || undefined,
            replyCreatedAt: val.replyCreatedAt?.toMillis ? val.replyCreatedAt.toMillis() : undefined,
          });
        });

        // Always sort chronologically ascending (oldest to newest) for chat display
        list.sort((a, b) => a.createdAt - b.createdAt);

        // Filter messages to active station, or show all if stationId is unassigned/global
        const scopedMessages = activeStationId
          ? list.filter((m) => !m.stationId || m.stationId === "global" || m.stationId === activeStationId)
          : list;

        setMessages(scopedMessages);

        // Check if latest message is a fresh reply to announce
        if (scopedMessages.length > 0) {
          const latest = scopedMessages[scopedMessages.length - 1];
          if (latest.status === "replied" && latest.replyText && latest.replyAudioBase64) {
            const age = Date.now() - (latest.replyCreatedAt || latest.createdAt);
            if (age < 30000) {
              setBannerNotice((prev) => {
                if (prev && prev.text === latest.replyText) return prev;
                return {
                  text: latest.replyText!,
                  audioBase64: latest.replyAudioBase64!,
                };
              });
            }
          }
        }
      },
      (error) => {
        console.warn("Messages snapshot error:", error);
      }
    );

    return () => unsubscribe();
  }, [activeStationId]);

  const handleSelectStation = (newStationId: string) => {
    setActiveStationId(newStationId);
    const newUrl = new URL(window.location.href);
    newUrl.searchParams.set("station", newStationId);
    window.history.pushState({}, "", newUrl.toString());
  };

  const handleGoogleSignIn = async () => {
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (err: any) {
      // Closing the Google popup is a benign cancellation, not an error.
      const code = err?.code || "";
      if (code === "auth/popup-closed-by-user" || code === "auth/cancelled-popup-request") {
        return;
      }
      console.error("Sign-in failed:", err);
      const message =
        code === "auth/popup-blocked"
          ? "Your browser blocked the sign-in popup. Allow popups for this site and try again."
          : code === "auth/network-request-failed"
          ? "Network error during sign-in. Check your connection and try again."
          : "Couldn't sign you in. Please try again.";
      setToast({ kind: "error", text: message });
    }
  };

  const handleSignOut = async () => {
    try {
      await fbSignOut(auth);
      setCurrentUser(null);
    } catch (err) {
      console.error("Sign-out failed:", err);
      setToast({ kind: "error", text: "Couldn't sign out. Please try again." });
    }
  };

  const handleSendMessage = async (text: string) => {
    if (!currentUser) return;

    const stationId = activeStationId || "global";
    const userRole = currentUser.userType || "listener";
    const now = Date.now();
    const messageDocRef = doc(collection(db, "messages"));
    const messageId = messageDocRef.id;

    try {
      await setDoc(messageDocRef, {
        stationId: stationId,
        userId: currentUser.uid,
        userName: currentUser.displayName,
        userCountry: currentUser.country || "Global",
        userType: userRole,
        userAvatar: currentUser.photoURL || null,
        text: text,
        status: "queued",
        createdAt: serverTimestamp(),
      });

      const userRef = doc(db, "users", currentUser.uid);
      await setDoc(userRef, { lastChatAt: now }, { merge: true });

      setCurrentUser((prev) => (prev ? { ...prev, lastChatAt: now } : null));

      // Get Firebase ID token for authenticated Cloud Run API call
      let token: string | undefined;
      try {
        token = await auth.currentUser?.getIdToken();
      } catch (tokenErr) {
        console.warn("Auth token retrieval failed (calling with optional auth):", tokenErr);
      }

      const payload = JSON.stringify({
        messageId: messageId,
        nowTrack: radioStatus.nowPlayingTrack,
        nowArtist: radioStatus.nowPlayingArtist,
        voice: currentStation?.voice,
        speakingRate: currentStation?.speakingRate,
        aiPersona: currentStation?.aiPersona,
      });

      const headers: Record<string, string> = {
        "Content-Type": "application/json",
      };
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      // Trigger Cloud Run AI co-host with fallback to direct service URL
      (async () => {
        try {
          const res = await fetch("/api/v1/process-message", {
            method: "POST",
            headers,
            body: payload,
          });
          if (res.ok) {
            console.log("Cloud Run process-message succeeded via /api");
            return;
          }
          console.warn("Relative /api trigger returned status:", res.status, "- trying direct Cloud Run URL");
        } catch (netErr) {
          console.warn("Relative /api trigger network error:", netErr, "- trying direct Cloud Run URL");
        }

        try {
          const directRes = await fetch("https://agent-api-191983702527.us-central1.run.app/api/v1/process-message", {
            method: "POST",
            headers,
            body: payload,
          });
          if (directRes.ok) {
            console.log("Cloud Run process-message succeeded via direct Cloud Run URL");
          } else {
            console.error("Direct Cloud Run trigger returned status:", directRes.status);
          }
        } catch (directErr) {
          console.error("Direct Cloud Run trigger failed:", directErr);
        }
      })().catch((e) => console.error("Cloud Run process-message unhandled error:", e));
    } catch (err: any) {
      console.error("Failed to send chat message:", err);
      throw err;
    }
  };

  const handlePlayRjAudio = (audioBase64: string, text: string) => {
    if (activeRjAudioUrl && activeRjText === text) {
      setActiveRjAudioUrl(null);
      setActiveRjText(null);
    } else {
      const audioSrc = `data:audio/mp3;base64,${audioBase64}`;
      setActiveRjAudioUrl(audioSrc);
      setActiveRjText(text);
    }
  };

  const isBroadcasterOwner =
    currentUser && currentStation && (currentUser.uid === currentStation.stationId || currentUser.uid === currentStation.broadcasterId);

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col text-slate-900 selection:bg-blue-100 selection:text-blue-900">
      {/* Top Header */}
      <Header
        user={currentUser}
        loadingAuth={loadingAuth}
        onSignIn={() => setShowAuthModal(true)}
        onSignOut={handleSignOut}
        onEditProfile={() => {
          setCanDismissModal(true);
          setShowProfileModal(true);
        }}
        isStreaming={radioStatus.isLive}
        listenerCount={radioStatus.listenersCount}
        currentStation={currentStation}
        onOpenStationDirectory={() => setShowStationModal(true)}
      />

      {/* On-Air Shoutout Banner */}
      {bannerNotice && (
        <RjAnnouncementBanner
          text={bannerNotice.text}
          isPlaying={activeRjText === bannerNotice.text}
          onPlay={() => {
            if (bannerNotice.audioBase64) {
              handlePlayRjAudio(bannerNotice.audioBase64, bannerNotice.text);
            }
          }}
          onDismiss={() => setBannerNotice(null)}
        />
      )}

      {/* Offline Notice */}
      {isOffline && (
        <div className="bg-amber-500 text-slate-900 px-4 py-2 text-xs font-semibold flex items-center justify-center gap-2" role="status">
          <WifiOff className="w-4 h-4" />
          You're offline — chat and live audio will reconnect automatically when your connection returns.
        </div>
      )}

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8 space-y-6">
        {/* Broadcaster Station Banner or No Stations Notice */}
        {currentStation ? (
          <div className="bg-blue-700 rounded-2xl p-4 sm:p-5 text-white shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-xl bg-white/10 backdrop-blur-md flex items-center justify-center border border-white/20">
                <RadioIcon className="w-6 h-6 text-white" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-base sm:text-lg font-bold">{currentStation.stationTitle}</h2>
                  {currentStation.isLive ? (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-red-500 text-white animate-pulse">
                      ON AIR
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-white/20 text-white/80">
                      OFFLINE
                    </span>
                  )}
                </div>
                {currentStation.broadcasterName && (
                  <p className="text-xs text-blue-100 mt-0.5">
                    Broadcaster: <strong className="text-white">{currentStation.broadcasterName}</strong>
                    {isBroadcasterOwner && (
                      <span className="ml-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-amber-400 text-slate-900 font-bold text-[10px]">
                        <Crown className="w-3 h-3" />
                        You Own This Station
                      </span>
                    )}
                  </p>
                )}
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-end gap-2 self-stretch sm:self-auto">
              <button
                onClick={async () => {
                  const ok = await copyText(window.location.href);
                  if (ok) {
                    setCopiedLink(true);
                    setTimeout(() => setCopiedLink(false), 2000);
                  } else {
                    setToast({ kind: "error", text: "Couldn't copy the link. Copy it from the address bar." });
                  }
                }}
                className="px-3.5 py-1.5 rounded-xl bg-white/10 hover:bg-white/20 text-white text-xs font-semibold flex items-center gap-1.5 border border-white/20 transition"
              >
                {copiedLink ? <Check className="w-3.5 h-3.5 text-emerald-300" /> : <Share2 className="w-3.5 h-3.5" />}
                <span>{copiedLink ? "Link Copied!" : "Share Station"}</span>
              </button>

              {stations.length > 1 && (
                <button
                  onClick={() => setShowStationModal(true)}
                  className="px-3.5 py-1.5 rounded-xl bg-white text-blue-700 hover:bg-blue-50 text-xs font-semibold shadow-xs transition"
                >
                  Browse All Stations ({stations.length})
                </button>
              )}
            </div>
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs flex flex-col sm:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center flex-shrink-0">
                <RadioIcon className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-slate-900">
                  {activeStationId ? "Station Not Found" : "No Broadcaster Connected Yet"}
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  {activeStationId
                    ? `Station "${activeStationId}" does not exist. Check the URL or start a station from the app.`
                    : "Broadcasters register their station automatically when signing in to the Airwave Transmitter Android app."}
                </p>
              </div>
            </div>
            {stations.length > 0 && (
              <button
                onClick={() => setShowStationModal(true)}
                className="px-3.5 py-1.5 rounded-xl bg-blue-600 text-white text-xs font-semibold hover:bg-blue-700 transition"
              >
                View Available Stations ({stations.length})
              </button>
            )}
          </div>
        )}

        {/* Top Radio Stream Player */}
        <RadioPlayer
          radioStatus={radioStatus}
          currentStation={currentStation}
          activeRjAudioUrl={activeRjAudioUrl}
          activeRjText={activeRjText}
          onRjAudioFinished={() => {
            setActiveRjAudioUrl(null);
            setActiveRjText(null);
          }}
          streamUrl={streamUrl}
        />

        {/* Studio Grid: Chat + Station Highlights */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left 2 Cols: Live Chat & AI Requests */}
          <div className="lg:col-span-2">
            <ChatBox
              messages={messages}
              user={currentUser}
              currentStation={currentStation}
              onSendMessage={handleSendMessage}
              onSignIn={() => setShowAuthModal(true)}
              onPromptProfile={() => {
                setCanDismissModal(false);
                setShowProfileModal(true);
              }}
              onPlayRjAudio={handlePlayRjAudio}
              activePlayingAudioText={activeRjText}
            />
          </div>

          {/* Right Col: Station Info & AI Host Specs */}
          <div className="space-y-4">
            {/* AI Co-Host Card */}
            <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs">
              <div className="flex items-center gap-3 mb-3">
                <div className="w-10 h-10 rounded-xl bg-purple-600 text-white flex items-center justify-center shadow-sm">
                  <Sparkles className="w-5 h-5" />
                </div>
                <div>
                  <h4 className="font-bold text-sm text-slate-900">AI Radio Jockey</h4>
                  <p className="text-[11px] text-slate-500">Gemini 2.5 Flash + Neural2 TTS</p>
                </div>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Our AI RJ listens to chat comments, filters out sensitive/18+ questions, and crafts polite, smooth on-air voice shoutouts between songs.
              </p>

              <div className="mt-4 pt-4 border-t border-slate-100 space-y-2 text-xs text-slate-600">
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Station Role</span>
                  <span className="font-medium text-purple-700">
                    {currentUser?.userType === "broadcaster" ? "Broadcaster (Host)" : "Listener"}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Host Cadence</span>
                  <span className="font-medium text-slate-800">Warm, Polite & Charismatic</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Live Chat</span>
                  <span className="font-medium text-emerald-600">Open & Real-Time</span>
                </div>
              </div>
            </div>

            {/* About this station */}
            <div className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs space-y-3">
              <h4 className="font-bold text-xs text-slate-900">About this station</h4>
              <div className="flex items-start gap-2.5 text-[11px] text-slate-500 leading-relaxed">
                <Crown className="w-4 h-4 text-blue-600 flex-shrink-0 mt-0.5" />
                <span>Each broadcaster runs one public listener page. Tune in from any browser and chat with the AI host.</span>
              </div>
              <div className="flex items-start gap-2.5 text-[11px] text-slate-500 leading-relaxed">
                <ShieldCheck className="w-4 h-4 text-emerald-600 flex-shrink-0 mt-0.5" />
                <span>Explicit or hateful messages are filtered automatically to keep the broadcast family-friendly.</span>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white py-6 mt-12 text-center text-xs text-slate-400">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p className="flex items-center gap-1">
            <span>Airwave Agents</span> · <span>AI Builder Cup 2026</span>
          </p>
          <p className="flex items-center gap-1 text-[11px]">
            Powered by Gemini 2.5 Flash, Cloud TTS & Firebase
          </p>
        </div>
      </footer>

      {/* Profile Onboarding Modal */}
      {currentUser && (
        <ProfileModal
          isOpen={showProfileModal}
          user={currentUser}
          canDismiss={canDismissModal}
          onSave={(updated) => {
            setCurrentUser(updated);
            setShowProfileModal(false);
          }}
          onClose={() => setShowProfileModal(false)}
        />
      )}

      {/* Broadcaster Station Directory Modal */}
      <StationDirectoryModal
        isOpen={showStationModal}
        onClose={() => setShowStationModal(false)}
        stations={stations}
        activeStationId={activeStationId}
        onSelectStation={handleSelectStation}
      />

      {/* Email/Password & Google Sign In Modal */}
      <AuthModal
        isOpen={showAuthModal}
        onClose={() => setShowAuthModal(false)}
        onSuccess={() => setShowAuthModal(false)}
      />

      {/* Transient toast notifications */}
      {toast && (
        <div className="fixed bottom-4 inset-x-4 sm:inset-x-auto sm:right-6 sm:bottom-6 z-[60] flex justify-center sm:justify-end pointer-events-none">
          <div
            role="alert"
            className={`pointer-events-auto flex items-center gap-2.5 max-w-sm w-full sm:w-auto px-4 py-3 rounded-xl shadow-lg border text-xs font-medium ${
              toast.kind === "error"
                ? "bg-red-50 text-red-700 border-red-200"
                : "bg-emerald-50 text-emerald-700 border-emerald-200"
            }`}
          >
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span className="flex-1">{toast.text}</span>
            <button
              onClick={() => setToast(null)}
              aria-label="Dismiss"
              className="flex-shrink-0 p-1 -m-1 rounded hover:bg-black/5 transition"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default App;
