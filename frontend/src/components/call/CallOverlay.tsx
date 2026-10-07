import { useEffect, useRef, useState } from "react";

import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { acceptCall, declineCall, dismissCallNotice, hangUp, toggleCamera, toggleMic } from "@/lib/call";
import { useCallStore } from "@/store/callStore";

const NOTICE_VISIBLE_MS = 5000;

function formatElapsed(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function StreamVideo({
  stream,
  muted = false,
  label,
  className,
}: {
  stream: MediaStream;
  muted?: boolean;
  label: string;
  className: string;
}) {
  const ref = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    if (ref.current) ref.current.srcObject = stream;
  }, [stream]);

  return <video ref={ref} autoPlay playsInline muted={muted} aria-label={label} className={className} />;
}

function ControlButton({ onClick, pressed, children }: { onClick: () => void; pressed: boolean; children: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={pressed}
      className={`rounded-full px-5 py-2.5 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white ${
        pressed ? "bg-white text-slate-900 hover:bg-slate-200" : "bg-slate-700 text-white hover:bg-slate-600"
      }`}
    >
      {children}
    </button>
  );
}

/**
 * The whole call UI: incoming-call prompt, the in-call screen, and the one-line
 * outcome shown after a call ends. Mounted once in AppLayout so a call survives
 * navigating between pages.
 */
export function CallOverlay() {
  const phase = useCallStore((state) => state.phase);
  const peer = useCallStore((state) => state.peer);
  const localStream = useCallStore((state) => state.localStream);
  const remoteStream = useCallStore((state) => state.remoteStream);
  const micEnabled = useCallStore((state) => state.micEnabled);
  const cameraEnabled = useCallStore((state) => state.cameraEnabled);
  const activeSince = useCallStore((state) => state.activeSince);
  const notice = useCallStore((state) => state.notice);

  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    if (activeSince === null) return;
    const tick = () => setElapsedSeconds(Math.floor((Date.now() - activeSince) / 1000));
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [activeSince]);

  useEffect(() => {
    if (!notice) return;
    const timeout = setTimeout(dismissCallNotice, NOTICE_VISIBLE_MS);
    return () => clearTimeout(timeout);
  }, [notice]);

  useEffect(() => {
    // Closing the tab mid-call: tell the peer now rather than leaving them to an ICE timeout.
    window.addEventListener("pagehide", hangUp);
    return () => window.removeEventListener("pagehide", hangUp);
  }, []);

  if (phase === "idle" || !peer) {
    return notice ? (
      <div
        role="status"
        className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-full bg-slate-900 px-4 py-2 text-sm text-white shadow-lg"
      >
        {notice}
      </div>
    ) : null;
  }

  if (phase === "incoming") {
    return (
      <div
        role="dialog"
        aria-modal="true"
        aria-label={`Incoming call from ${peer.name}`}
        className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60"
      >
        <div className="w-80 rounded-2xl bg-white p-6 text-center shadow-xl">
          <div className="flex justify-center">
            <Avatar name={peer.name} avatarUrl={peer.avatarUrl} size="lg" />
          </div>
          <p className="mt-4 text-base font-semibold text-slate-900">{peer.name}</p>
          <p className="text-sm text-slate-500">Incoming video call…</p>
          <div className="mt-6 flex justify-center gap-3">
            <Button variant="danger" onClick={declineCall}>
              Decline
            </Button>
            <Button onClick={() => void acceptCall()}>Accept</Button>
          </div>
        </div>
      </div>
    );
  }

  const remoteHasVideo = (remoteStream?.getVideoTracks().length ?? 0) > 0;
  const localHasVideo = (localStream?.getVideoTracks().length ?? 0) > 0;
  const status =
    phase === "outgoing" ? "Calling…" : phase === "connecting" ? "Connecting…" : formatElapsed(elapsedSeconds);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`Call with ${peer.name}`}
      className="fixed inset-0 z-50 flex flex-col bg-slate-900 text-white"
    >
      <div className="px-6 py-4">
        <p className="text-sm font-semibold">{peer.name}</p>
        <p className="text-xs tabular-nums text-slate-300">{status}</p>
      </div>

      <div className="relative min-h-0 flex-1">
        {remoteStream && (
          // Stays mounted without video too: this element is also what plays the remote audio.
          <StreamVideo
            stream={remoteStream}
            label={`${peer.name}'s video`}
            className={remoteHasVideo ? "h-full w-full object-contain" : "hidden"}
          />
        )}
        {!remoteHasVideo && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-4">
            <Avatar name={peer.name} avatarUrl={peer.avatarUrl} size="lg" />
            <p className="text-sm text-slate-300">{phase === "active" ? "Voice only" : status}</p>
          </div>
        )}
        {localStream && localHasVideo && (
          <StreamVideo
            stream={localStream}
            muted
            label="Your camera"
            className="absolute bottom-4 right-4 h-32 w-44 -scale-x-100 rounded-lg border border-slate-700 bg-black object-cover shadow-lg"
          />
        )}
      </div>

      <div className="flex items-center justify-center gap-3 px-6 py-5">
        <ControlButton onClick={toggleMic} pressed={!micEnabled}>
          {micEnabled ? "Mute" : "Unmute"}
        </ControlButton>
        {localHasVideo && (
          <ControlButton onClick={toggleCamera} pressed={!cameraEnabled}>
            {cameraEnabled ? "Turn camera off" : "Turn camera on"}
          </ControlButton>
        )}
        <button
          type="button"
          onClick={hangUp}
          className="rounded-full bg-red-600 px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
        >
          Hang up
        </button>
      </div>
    </div>
  );
}
