import { useEffect, useMemo, useRef, useState } from "react";

import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/context/AuthContext";
import { useContacts } from "@/hooks/useUsers";
import {
  useAddMember,
  useLeaveGroup,
  useMarkConversationAsRead,
  useRemoveMember,
} from "@/hooks/useConversations";
import { useMessages, useSendVoiceMessage } from "@/hooks/useMessages";
import { useCallStore } from "@/store/callStore";
import { useChatStore } from "@/store/chatStore";
import { usePresenceStore } from "@/store/presenceStore";
import { startCall } from "@/lib/call";
import { sendChatMessage, sendTyping } from "@/lib/ws";
import { STATUS_LABELS } from "@/types/user";
import type { Conversation, Message } from "@/types/chat";

const TYPING_STOP_DELAY_MS = 2000;

function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = Math.floor(totalSeconds % 60);
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function VoiceMessageBubble({ message, isOwn }: { message: Message; isOwn: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      <audio controls src={message.attachmentUrl ?? undefined} className="h-9 max-w-[220px]" />
      <span className={`text-[11px] ${isOwn ? "text-brand-100" : "text-slate-400"}`}>
        🎤 {message.attachmentDurationSeconds != null ? formatDuration(message.attachmentDurationSeconds) : ""}
      </span>
    </div>
  );
}

export function ConversationPanel({ conversation }: { conversation: Conversation }) {
  const { user: currentUser } = useAuth();
  const { data: contacts } = useContacts();
  const statusByUserId = usePresenceStore((state) => state.statusByUserId);
  const typingRecord = useChatStore((state) => state.typingByConversation[conversation.id]);
  const typingUserIds = useMemo(() => Object.keys(typingRecord ?? {}).map(Number), [typingRecord]);
  const callPhase = useCallStore((state) => state.phase);
  const markAsRead = useMarkConversationAsRead();
  const addMember = useAddMember(conversation.id);
  const removeMember = useRemoveMember(conversation.id);
  const leaveGroup = useLeaveGroup();
  const sendVoiceMessage = useSendVoiceMessage(conversation.id);

  const { data: messages, isLoading } = useMessages(conversation.id);

  const [draft, setDraft] = useState("");
  const [showMembers, setShowMembers] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState(0);
  const [voiceError, setVoiceError] = useState<string | null>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const recordingStartRef = useRef<number>(0);
  const recordingIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const isGroup = conversation.type === "GROUP";
  const otherParticipants = conversation.participants.filter((p) => p.userId !== currentUser?.id);
  const me = conversation.participants.find((p) => p.userId === currentUser?.id);
  const isOwner = me?.role === "OWNER";

  const typingNames = typingUserIds
    .map((id) => otherParticipants.find((p) => p.userId === id)?.name)
    .filter((name): name is string => !!name);

  const headerStatus = !isGroup ? statusByUserId[otherParticipants[0]?.userId] ?? otherParticipants[0]?.status : undefined;

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages, conversation.id]);

  useEffect(() => {
    if (conversation.unreadCount > 0) {
      markAsRead.mutate(conversation.id);
    }
    // Re-run when switching conversations, and again whenever more unread messages arrive.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.id, conversation.unreadCount]);

  useEffect(() => {
    setDraft("");
    setShowMembers(false);
    return () => {
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
      if (recordingIntervalRef.current) clearInterval(recordingIntervalRef.current);
    };
  }, [conversation.id]);

  function handleDraftChange(value: string) {
    setDraft(value);
    sendTyping(conversation.id, true);

    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => {
      sendTyping(conversation.id, false);
    }, TYPING_STOP_DELAY_MS);
  }

  function handleSend() {
    const content = draft.trim();
    if (!content) return;

    sendChatMessage(conversation.id, content);
    setDraft("");
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    sendTyping(conversation.id, false);
  }

  async function startRecording() {
    setVoiceError(null);

    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      setVoiceError("Voice messages aren't supported in this browser.");
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      audioChunksRef.current = [];

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) audioChunksRef.current.push(event.data);
      };

      recorder.onstop = () => {
        stream.getTracks().forEach((track) => track.stop());
        const durationSeconds = Math.max(1, Math.round((Date.now() - recordingStartRef.current) / 1000));
        const blob = new Blob(audioChunksRef.current, { type: recorder.mimeType || "audio/webm" });
        sendVoiceMessage.mutate(
          { blob, durationSeconds },
          { onError: () => setVoiceError("Could not send the voice message. Please try again.") },
        );
      };

      mediaRecorderRef.current = recorder;
      recordingStartRef.current = Date.now();
      recorder.start();
      setIsRecording(true);
      setRecordingSeconds(0);
      recordingIntervalRef.current = setInterval(() => {
        setRecordingSeconds(Math.round((Date.now() - recordingStartRef.current) / 1000));
      }, 250);
    } catch {
      setVoiceError("Microphone access was denied or is unavailable.");
    }
  }

  function stopRecording() {
    mediaRecorderRef.current?.stop();
    mediaRecorderRef.current = null;
    setIsRecording(false);
    if (recordingIntervalRef.current) {
      clearInterval(recordingIntervalRef.current);
      recordingIntervalRef.current = null;
    }
  }

  const availableToAdd = (contacts ?? []).filter(
    (c) => !conversation.participants.some((p) => p.userId === c.id),
  );

  const subtitle = typingNames.length > 0
    ? `${typingNames.join(" and ")} ${typingNames.length > 1 ? "are" : "is"} typing…`
    : isGroup
      ? `${conversation.participants.length} members`
      : headerStatus
        ? STATUS_LABELS[headerStatus]
        : "";

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-3">
          <Avatar name={conversation.name} avatarUrl={conversation.avatarUrl} status={isGroup ? undefined : headerStatus} />
          <div>
            <p className="text-sm font-semibold text-slate-900">{conversation.name}</p>
            <p className="text-xs text-slate-500">{subtitle}</p>
          </div>
        </div>
        {isGroup && (
          <Button variant="ghost" onClick={() => setShowMembers((v) => !v)}>
            Members
          </Button>
        )}
        {!isGroup && otherParticipants[0] && (
          <button
            type="button"
            onClick={() =>
              void startCall(conversation.id, {
                id: otherParticipants[0].userId,
                name: conversation.name,
                avatarUrl: conversation.avatarUrl,
              })
            }
            disabled={callPhase !== "idle"}
            title="Start a video call"
            aria-label="Start a video call"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
              <path d="M4.5 4.5a3 3 0 0 0-3 3v9a3 3 0 0 0 3 3h8.25a3 3 0 0 0 3-3v-9a3 3 0 0 0-3-3H4.5ZM19.94 18.75l-2.69-2.69V7.94l2.69-2.69c.944-.945 2.56-.276 2.56 1.06v11.38c0 1.336-1.616 2.005-2.56 1.06Z" />
            </svg>
          </button>
        )}
      </header>

      {isGroup && showMembers && (
        <div className="border-b border-slate-200 bg-slate-50 px-5 py-4">
          <ul className="space-y-2">
            {conversation.participants.map((participant) => (
              <li key={participant.userId} className="flex items-center gap-3">
                <Avatar name={participant.name} avatarUrl={participant.avatarUrl} status={statusByUserId[participant.userId] ?? participant.status} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-slate-800">
                    {participant.name} {participant.userId === currentUser?.id && "(You)"}
                  </p>
                  <p className="text-xs text-slate-400">{participant.role === "OWNER" ? "Owner" : "Member"}</p>
                </div>
                {isOwner && participant.userId !== currentUser?.id && (
                  <button
                    onClick={() => removeMember.mutate(participant.userId)}
                    className="text-xs font-medium text-red-600 hover:text-red-700"
                  >
                    Remove
                  </button>
                )}
              </li>
            ))}
          </ul>

          {isOwner && availableToAdd.length > 0 && (
            <div className="mt-3 border-t border-slate-200 pt-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Add member</p>
              <div className="flex flex-wrap gap-2">
                {availableToAdd.map((contact) => (
                  <button
                    key={contact.id}
                    onClick={() => addMember.mutate(contact.id)}
                    className="rounded-full border border-slate-300 px-3 py-1 text-xs text-slate-700 hover:bg-white"
                  >
                    + {contact.name}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="mt-3 border-t border-slate-200 pt-3">
            <button
              onClick={() => leaveGroup.mutate(conversation.id)}
              className="text-xs font-medium text-red-600 hover:text-red-700"
            >
              Leave group
            </button>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto px-5 py-4">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner />
          </div>
        ) : messages && messages.length > 0 ? (
          <ul className="flex flex-col gap-2">
            {messages.map((message) => {
              const isOwn = message.senderId === currentUser?.id;
              const otherCount = otherParticipants.length;
              const readCount = message.readBy.length;
              return (
                <li key={message.id} className={`flex ${isOwn ? "justify-end" : "justify-start"}`}>
                  <div
                    className={`max-w-[70%] rounded-2xl px-4 py-2 text-sm ${
                      isOwn ? "bg-brand-600 text-white" : "bg-slate-100 text-slate-900"
                    }`}
                  >
                    {message.type === "VOICE" ? (
                      <VoiceMessageBubble message={message} isOwn={isOwn} />
                    ) : (
                      <p className="whitespace-pre-wrap break-words">{message.content}</p>
                    )}
                    {isOwn && (
                      <span
                        className={`mt-0.5 flex justify-end text-[11px] ${
                          readCount >= otherCount && otherCount > 0 ? "text-white" : "text-brand-200"
                        }`}
                        title={isGroup ? `Read by ${readCount} of ${otherCount}` : readCount > 0 ? "Read" : "Sent"}
                      >
                        {isGroup
                          ? readCount > 0
                            ? `Read ${readCount}/${otherCount}`
                            : "✓"
                          : readCount > 0
                            ? "✓✓"
                            : "✓"}
                      </span>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="py-8 text-center text-sm text-slate-500">
            No messages yet — say hi to {conversation.name}!
          </p>
        )}
        <div ref={bottomRef} />
      </div>

      <div className="border-t border-slate-200 px-4 py-3">
        <ErrorBanner message={voiceError} />
        <div className="flex items-center gap-2">
          {isRecording ? (
            <>
              <span className="flex flex-1 items-center gap-2 rounded-full border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
                <span className="h-2 w-2 animate-pulse rounded-full bg-red-500" />
                Recording… {formatDuration(recordingSeconds)}
              </span>
              <Button variant="danger" onClick={stopRecording} className="rounded-full">
                Stop
              </Button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={startRecording}
                title="Record a voice message"
                aria-label="Record a voice message"
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-700"
              >
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                  <path d="M12 15.75a3.75 3.75 0 0 0 3.75-3.75V6a3.75 3.75 0 1 0-7.5 0v6a3.75 3.75 0 0 0 3.75 3.75Z" />
                  <path d="M6 11.25a.75.75 0 0 1 .75.75 5.25 5.25 0 1 0 10.5 0 .75.75 0 0 1 1.5 0 6.751 6.751 0 0 1-6 6.709V21h3.75a.75.75 0 0 1 0 1.5H8.25a.75.75 0 0 1 0-1.5H12v-2.291a6.751 6.751 0 0 1-6-6.709.75.75 0 0 1 .75-.75Z" />
                </svg>
              </button>
              <input
                value={draft}
                onChange={(e) => handleDraftChange(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                placeholder={`Message ${conversation.name}`}
                className="flex-1 rounded-full border border-slate-300 px-4 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
              <Button onClick={handleSend} disabled={!draft.trim()} className="rounded-full">
                Send
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
