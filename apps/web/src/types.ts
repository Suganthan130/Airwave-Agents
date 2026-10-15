export type UserType = "broadcaster" | "listener";

export interface UserProfile {
  uid: string;
  displayName: string;
  email: string | null;
  photoURL: string | null;
  country: string;
  userType: UserType;
  stationId?: string;
  lastChatAt?: number;
  createdAt?: number;
}

export type MessageStatus = "queued" | "processing" | "replied" | "skipped";

export interface ChatMessage {
  id: string;
  stationId?: string;
  userId: string;
  userName: string;
  userCountry: string;
  userType?: UserType;
  userAvatar?: string | null;
  text: string;
  status: MessageStatus;
  createdAt: number;
  replyText?: string;
  replyAudioBase64?: string;
  replyCreatedAt?: number;
}

export interface BroadcasterStation {
  stationId: string;
  broadcasterId: string;
  uniqueId?: string;
  broadcasterName: string;
  broadcasterEmail?: string;
  stationTitle: string;
  host?: string;
  port?: string;
  serverType?: string;
  mount?: string;
  publicListeningUrl?: string;
  publicPageUrl?: string;
  streamUrl: string;
  isLive: boolean;
  nowPlayingTrack?: string;
  nowPlayingArtist?: string;
  listenersCount?: number;
  rjVoice?: number;
  voice?: string;
  rjSpeed?: number;
  speakingRate?: number;
  aiPersona?: string;
  updatedAt?: number;
}


export interface RadioStatus {
  isLive: boolean;
  nowPlayingTrack: string;
  nowPlayingArtist: string;
  listenersCount: number;
  streamUrl: string;
}
