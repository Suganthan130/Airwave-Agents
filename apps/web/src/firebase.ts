import { initializeApp, getApps } from "firebase/app";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  signOut as fbSignOut,
  onAuthStateChanged,
  type User as FirebaseUser,
} from "firebase/auth";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  collection,
  query,
  orderBy,
  limit,
  onSnapshot,
  serverTimestamp,
} from "firebase/firestore";

const firebaseConfig = {
  projectId: "airwave-agents",
  appId: "1:191983702527:web:b0887153e1bec12e84d7ca",
  storageBucket: "airwave-agents.firebasestorage.app",
  apiKey: "AIzaSyAjX2vrKwsO1-8qiR52vYHxzCUX4B3oHoI",
  authDomain: "airwave-agents.firebaseapp.com",
  messagingSenderId: "191983702527",
};

const app = !getApps().length ? initializeApp(firebaseConfig) : getApps()[0];

export const auth = getAuth(app);
export const db = getFirestore(app);

export const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: "select_account" });

export {
  signInWithPopup,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
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
};
