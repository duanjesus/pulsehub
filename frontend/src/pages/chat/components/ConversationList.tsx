import { useMemo } from "react";

import { Avatar } from "@/components/ui/Avatar";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/context/AuthContext";
import { useConversations } from "@/hooks/useConversations";
import { useContacts } from "@/hooks/useUsers";
import { usePresenceStore } from "@/store/presenceStore";
import type { Conversation } from "@/types/chat";

export function GroupIcon() {
  return (
    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-200 text-slate-500">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
        <path d="M4.5 6.375a4.125 4.125 0 1 1 8.25 0 4.125 4.125 0 0 1-8.25 0ZM14.25 8.625a3.375 3.375 0 1 1 6.75 0 3.375 3.375 0 0 1-6.75 0ZM1.5 19.125a7.125 7.125 0 0 1 14.25 0v.003l-.001.119a.75.75 0 0 1-.363.63 13.067 13.067 0 0 1-6.761 1.873c-2.472 0-4.786-.684-6.76-1.873a.75.75 0 0 1-.364-.63l-.001-.122ZM17.25 19.128l-.001.144a2.25 2.25 0 0 1-.233.96 10.088 10.088 0 0 0 5.06-1.01.75.75 0 0 0 .42-.643 4.875 4.875 0 0 0-6.957-4.611 8.586 8.586 0 0 1 1.71 5.157v.003Z" />
      </svg>
    </span>
  );
}

export function ConversationList({
  activeConversationId,
  onSelectConversation,
  onStartDirect,
}: {
  activeConversationId: number | null;
  onSelectConversation: (conversationId: number) => void;
  onStartDirect: (contactId: number) => void;
}) {
  const { user: currentUser } = useAuth();
  const { data: conversations, isLoading: loadingConversations } = useConversations();
  const { data: contacts, isLoading: loadingContacts } = useContacts();
  const statusByUserId = usePresenceStore((state) => state.statusByUserId);

  const contactsWithoutConversation = useMemo(() => {
    if (!contacts) return [];
    const directContactIds = new Set(
      (conversations ?? [])
        .filter((c) => c.type === "DIRECT")
        .flatMap((c) => c.participants.map((p) => p.userId))
        .filter((id) => id !== currentUser?.id),
    );
    return contacts.filter((c) => !directContactIds.has(c.id));
  }, [contacts, conversations, currentUser?.id]);

  function conversationStatus(conversation: Conversation) {
    if (conversation.type !== "DIRECT") return undefined;
    const other = conversation.participants.find((p) => p.userId !== currentUser?.id);
    return other ? (statusByUserId[other.userId] ?? other.status) : undefined;
  }

  if (loadingConversations || loadingContacts) {
    return (
      <div className="flex justify-center py-8">
        <Spinner />
      </div>
    );
  }

  const hasAnything = (conversations?.length ?? 0) > 0 || contactsWithoutConversation.length > 0;
  if (!hasAnything) {
    return <EmptyState message="No other users have signed up yet." />;
  }

  return (
    <div className="flex-1 overflow-y-auto">
      {conversations && conversations.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {conversations.map((conversation) => (
            <li key={conversation.id}>
              <button
                onClick={() => onSelectConversation(conversation.id)}
                className={`flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-50 ${
                  activeConversationId === conversation.id ? "bg-brand-50" : ""
                }`}
              >
                {conversation.type === "GROUP" ? (
                  <GroupIcon />
                ) : (
                  <Avatar name={conversation.name} avatarUrl={conversation.avatarUrl} status={conversationStatus(conversation)} size="sm" />
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-900">{conversation.name}</p>
                  <p className="truncate text-xs text-slate-500">
                    {conversation.lastMessage?.content ?? "No messages yet"}
                  </p>
                </div>
                {conversation.unreadCount > 0 && (
                  <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-600 px-1.5 text-xs font-semibold text-white">
                    {conversation.unreadCount}
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}

      {contactsWithoutConversation.length > 0 && (
        <>
          <p className="px-4 pb-1 pt-4 text-xs font-semibold uppercase tracking-wide text-slate-400">
            Start a conversation
          </p>
          <ul className="divide-y divide-slate-100">
            {contactsWithoutConversation.map((contact) => (
              <li key={contact.id}>
                <button
                  onClick={() => onStartDirect(contact.id)}
                  className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-50"
                >
                  <Avatar name={contact.name} avatarUrl={contact.avatarUrl} status={statusByUserId[contact.id] ?? contact.status} size="sm" />
                  <span className="truncate text-sm text-slate-800">{contact.name}</span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
