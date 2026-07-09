import { useMemo } from "react";

import { Avatar } from "@/components/ui/Avatar";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useContacts } from "@/hooks/useUsers";
import { useConversations } from "@/hooks/useConversations";
import { usePresenceStore } from "@/store/presenceStore";
import type { UserStatus } from "@/types/user";

interface ContactRow {
  id: number;
  name: string;
  avatarUrl: string | null;
  status: UserStatus;
  preview: string | null;
  unreadCount: number;
}

export function ContactList({
  activeContactId,
  onSelect,
}: {
  activeContactId: number | null;
  onSelect: (contactId: number) => void;
}) {
  const { data: contacts, isLoading } = useContacts();
  const { data: conversations } = useConversations();
  const statusByUserId = usePresenceStore((state) => state.statusByUserId);

  const rows = useMemo<ContactRow[]>(() => {
    if (!contacts) return [];

    const conversationByContactId = new Map(
      (conversations ?? []).map((conversation) => [conversation.participant.id, conversation]),
    );

    return contacts
      .map((contact) => {
        const conversation = conversationByContactId.get(contact.id);
        return {
          id: contact.id,
          name: contact.name,
          avatarUrl: contact.avatarUrl,
          status: statusByUserId[contact.id] ?? contact.status,
          preview: conversation?.lastMessage?.content ?? null,
          unreadCount: conversation?.unreadCount ?? 0,
        };
      })
      .sort((a, b) => {
        if (a.status !== b.status) {
          const order: Record<UserStatus, number> = { ONLINE: 0, AWAY: 1, OFFLINE: 2 };
          return order[a.status] - order[b.status];
        }
        return a.name.localeCompare(b.name);
      });
  }, [contacts, conversations, statusByUserId]);

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <Spinner />
      </div>
    );
  }

  if (rows.length === 0) {
    return <EmptyState message="No other users have signed up yet." />;
  }

  return (
    <ul className="flex-1 divide-y divide-slate-100 overflow-y-auto">
      {rows.map((row) => (
        <li key={row.id}>
          <button
            onClick={() => onSelect(row.id)}
            className={`flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-slate-50 ${
              activeContactId === row.id ? "bg-brand-50" : ""
            }`}
          >
            <Avatar name={row.name} avatarUrl={row.avatarUrl} status={row.status} size="sm" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-slate-900">{row.name}</p>
              <p className="truncate text-xs text-slate-500">{row.preview ?? "No messages yet"}</p>
            </div>
            {row.unreadCount > 0 && (
              <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-600 px-1.5 text-xs font-semibold text-white">
                {row.unreadCount}
              </span>
            )}
          </button>
        </li>
      ))}
    </ul>
  );
}
