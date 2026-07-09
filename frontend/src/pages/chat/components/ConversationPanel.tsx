import { useEffect, useRef, useState } from "react";

import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/context/AuthContext";
import { useContacts } from "@/hooks/useUsers";
import { useConversations, useMarkConversationAsRead } from "@/hooks/useConversations";
import { useMessages } from "@/hooks/useMessages";
import { useChatStore } from "@/store/chatStore";
import { usePresenceStore } from "@/store/presenceStore";
import { sendChatMessage, sendTyping } from "@/lib/ws";
import { STATUS_LABELS } from "@/types/user";

const TYPING_STOP_DELAY_MS = 2000;

export function ConversationPanel({ contactId }: { contactId: number }) {
  const { user: currentUser } = useAuth();
  const { data: contacts } = useContacts();
  const { data: conversations } = useConversations();
  const statusByUserId = usePresenceStore((state) => state.statusByUserId);
  const isPeerTyping = useChatStore((state) => state.typingByUserId[contactId] ?? false);
  const markAsRead = useMarkConversationAsRead();

  const contact = contacts?.find((c) => c.id === contactId);
  const conversation = conversations?.find((c) => c.participant.id === contactId);
  const { data: messages, isLoading } = useMessages(conversation?.id ?? null);

  const [draft, setDraft] = useState("");
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages, contactId]);

  useEffect(() => {
    if (conversation && conversation.unreadCount > 0) {
      markAsRead.mutate(conversation.id);
    }
    // Re-run when switching conversations, and again whenever more unread
    // messages arrive for the one currently open.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation?.id, conversation?.unreadCount]);

  useEffect(() => {
    setDraft("");
    return () => {
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    };
  }, [contactId]);

  function handleDraftChange(value: string) {
    setDraft(value);
    sendTyping(contactId, true);

    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => {
      sendTyping(contactId, false);
    }, TYPING_STOP_DELAY_MS);
  }

  function handleSend() {
    const content = draft.trim();
    if (!content) return;

    sendChatMessage(contactId, content);
    setDraft("");
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    sendTyping(contactId, false);
  }

  if (!contact) {
    return null;
  }

  const status = statusByUserId[contact.id] ?? contact.status;

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-3 border-b border-slate-200 px-5 py-4">
        <Avatar name={contact.name} avatarUrl={contact.avatarUrl} status={status} />
        <div>
          <p className="text-sm font-semibold text-slate-900">{contact.name}</p>
          <p className="text-xs text-slate-500">{isPeerTyping ? "Typing…" : STATUS_LABELS[status]}</p>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto px-5 py-4">
        {isLoading ? (
          <div className="flex justify-center py-8">
            <Spinner />
          </div>
        ) : messages && messages.length > 0 ? (
          <ul className="flex flex-col gap-2">
            {messages.map((message) => {
              const isOwn = message.senderId === currentUser?.id;
              return (
                <li key={message.id} className={`flex ${isOwn ? "justify-end" : "justify-start"}`}>
                  <div
                    className={`max-w-[70%] rounded-2xl px-4 py-2 text-sm ${
                      isOwn ? "bg-brand-600 text-white" : "bg-slate-100 text-slate-900"
                    }`}
                  >
                    <p className="whitespace-pre-wrap break-words">{message.content}</p>
                    {isOwn && (
                      <span
                        className={`mt-0.5 flex justify-end text-[11px] ${
                          message.readAt ? "text-white" : "text-brand-200"
                        }`}
                        title={message.readAt ? "Read" : "Sent"}
                      >
                        {message.readAt ? "✓✓" : "✓"}
                      </span>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="py-8 text-center text-sm text-slate-500">
            No messages yet — say hi to {contact.name}!
          </p>
        )}
        <div ref={bottomRef} />
      </div>

      <div className="flex items-center gap-2 border-t border-slate-200 px-4 py-3">
        <input
          value={draft}
          onChange={(e) => handleDraftChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          placeholder={`Message ${contact.name}`}
          className="flex-1 rounded-full border border-slate-300 px-4 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
        />
        <Button onClick={handleSend} disabled={!draft.trim()} className="rounded-full">
          Send
        </Button>
      </div>
    </div>
  );
}
