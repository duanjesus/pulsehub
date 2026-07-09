import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import { useConversations, useCreateDirectConversation } from "@/hooks/useConversations";
import { ConversationList } from "@/pages/chat/components/ConversationList";
import { ConversationPanel } from "@/pages/chat/components/ConversationPanel";
import { NewGroupModal } from "@/pages/chat/components/NewGroupModal";

export function ChatPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { data: conversations } = useConversations();
  const createDirect = useCreateDirectConversation();
  const [showNewGroup, setShowNewGroup] = useState(false);

  const conversationParam = searchParams.get("conversation");
  const withParam = searchParams.get("with");
  const activeConversationId = conversationParam ? Number(conversationParam) : null;

  // "?with=<contactId>" is a shortcut used by the Dashboard's online-users list,
  // which only knows a user id, not an existing conversation — resolve or
  // create the direct conversation, then settle on "?conversation=<id>".
  useEffect(() => {
    if (!withParam || !conversations) return;

    const contactId = Number(withParam);
    const existing = conversations.find(
      (c) => c.type === "DIRECT" && c.participants.some((p) => p.userId === contactId),
    );

    if (existing) {
      setSearchParams({ conversation: String(existing.id) }, { replace: true });
    } else {
      createDirect.mutate(contactId, {
        onSuccess: (conversation) => setSearchParams({ conversation: String(conversation.id) }, { replace: true }),
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [withParam, conversations]);

  const activeConversation = conversations?.find((c) => c.id === activeConversationId) ?? null;

  function selectConversation(conversationId: number) {
    setSearchParams({ conversation: String(conversationId) });
  }

  function startDirect(contactId: number) {
    setSearchParams({ with: String(contactId) });
  }

  return (
    <div className="flex h-full">
      <div className="flex w-80 shrink-0 flex-col border-r border-slate-200 bg-white">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h1 className="text-base font-semibold text-slate-900">Messages</h1>
          <Button variant="secondary" onClick={() => setShowNewGroup(true)}>
            New Group
          </Button>
        </div>
        <ConversationList
          activeConversationId={activeConversationId}
          onSelectConversation={selectConversation}
          onStartDirect={startDirect}
        />
      </div>

      <div className="flex-1 bg-white">
        {activeConversation ? (
          <ConversationPanel key={activeConversation.id} conversation={activeConversation} />
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-slate-500">
            Select a conversation to start chatting
          </div>
        )}
      </div>

      {showNewGroup && (
        <NewGroupModal
          onClose={() => setShowNewGroup(false)}
          onCreated={(conversationId) => {
            setShowNewGroup(false);
            selectConversation(conversationId);
          }}
        />
      )}
    </div>
  );
}
