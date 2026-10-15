import React, { useState, useEffect } from "react";
import { COUNTRIES } from "../data/countries.ts";
import { doc, setDoc, db, serverTimestamp } from "../firebase.ts";
import type { UserProfile } from "../types.ts";
import { Globe, User, Check, Radio, ChevronDown } from "lucide-react";

interface ProfileModalProps {
  isOpen: boolean;
  user: UserProfile;
  onSave: (updated: UserProfile) => void;
  onClose?: () => void;
  canDismiss?: boolean;
}

export const ProfileModal: React.FC<ProfileModalProps> = ({
  isOpen,
  user,
  onSave,
  onClose,
  canDismiss = false,
}) => {
  // Normalise any stored value (code OR name) to a country NAME, which is what
  // the <option value> list uses — otherwise the select and the preview disagree.
  const resolveCountryName = (val?: string) =>
    COUNTRIES.find((c) => c.code === val || c.name === val)?.name || "Sri Lanka";

  const [name, setName] = useState(user.displayName || "");
  const [country, setCountry] = useState(resolveCountryName(user.country));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setName(user.displayName || "");
    setCountry(resolveCountryName(user.country));
  }, [user]);

  // Allow Escape to dismiss, but only when the modal is dismissible (not during
  // required onboarding).
  useEffect(() => {
    if (!isOpen || !canDismiss) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && onClose) onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [isOpen, canDismiss, onClose]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Please enter your listener name.");
      return;
    }
    if (!country) {
      setError("Please select your country.");
      return;
    }

    try {
      setSaving(true);
      setError(null);
      const userRef = doc(db, "users", user.uid);
      const updatedData = {
        uid: user.uid,
        displayName: trimmed,
        country: country,
        userType: user.userType || "listener",
        photoURL: user.photoURL || null,
        email: user.email || null,
        updatedAt: serverTimestamp(),
      };
      await setDoc(userRef, updatedData, { merge: true });

      onSave({
        ...user,
        displayName: trimmed,
        country: country,
        userType: user.userType || "listener",
      });
    } catch (err: any) {
      console.error("Failed to save profile:", err);
      setError(err?.message || "Failed to update profile. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  const selectedCountryObj = COUNTRIES.find((c) => c.code === country || c.name === country);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
      onClick={canDismiss && onClose ? onClose : undefined}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Listener profile"
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md max-h-[92vh] overflow-y-auto bg-white rounded-2xl shadow-2xl border border-slate-100 animate-in fade-in zoom-in-95 duration-200"
      >
        {/* Header Banner */}
        <div className="bg-blue-600 p-6 text-white text-center relative">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-white/10 backdrop-blur-md mb-3 border border-white/20">
            <Radio className="w-6 h-6 text-white" />
          </div>
          <h2 className="text-xl font-bold tracking-tight">Welcome to Airwave Agents</h2>
          <p className="text-blue-100 text-xs mt-1">
            Set up your on-air listener identity so the AI Host can address you by name!
          </p>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 text-xs rounded-lg bg-red-50 text-red-600 border border-red-200">
              {error}
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1.5 flex items-center gap-1.5">
              <User className="w-3.5 h-3.5 text-blue-600" />
              Listener Name / Nickname
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Jason or Sarah"
              maxLength={32}
              className="w-full px-3.5 py-2.5 text-sm border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-600 transition"
              autoFocus
            />
            <p className="text-[11px] text-slate-400 mt-1">
              The AI Co-Host will announce your name during live shoutouts.
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1.5 flex items-center gap-1.5">
              <Globe className="w-3.5 h-3.5 text-blue-600" />
              Your Country / Region
            </label>
            <div className="relative">
              <select
                value={country}
                onChange={(e) => setCountry(e.target.value)}
                className="w-full appearance-none px-3.5 py-2.5 text-sm bg-white border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-600 transition text-slate-800"
              >
                {COUNTRIES.map((c) => (
                  <option key={c.code} value={c.name}>
                    {c.flag} {c.name}
                  </option>
                ))}
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-3 text-slate-400">
                <ChevronDown className="w-4 h-4" />
              </div>
            </div>
            {selectedCountryObj && (
              <p className="text-[11px] text-slate-500 mt-1 flex items-center gap-1">
                Selected: <span className="text-base">{selectedCountryObj.flag}</span>{" "}
                <span className="font-medium text-slate-700">{selectedCountryObj.name}</span>
              </p>
            )}
          </div>

          <div className="pt-2 flex items-center gap-2">
            {canDismiss && onClose && (
              <button
                type="button"
                onClick={onClose}
                disabled={saving}
                className="flex-1 py-2.5 px-4 text-xs font-semibold text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-xl transition disabled:opacity-50 disabled:pointer-events-none"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={saving}
              className="flex-1 py-2.5 px-4 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-xs font-semibold rounded-xl shadow-md shadow-blue-500/20 flex items-center justify-center gap-2 transition disabled:opacity-50"
            >
              {saving ? (
                <span>Saving...</span>
              ) : (
                <>
                  <Check className="w-4 h-4" />
                  <span>Join On-Air Chat</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
