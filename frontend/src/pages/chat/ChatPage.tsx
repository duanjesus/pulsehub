import { useSearchParams } from "react-router-dom";

import { ContactList } from "@/pages/chat/components/ContactList";
import { ConversationPanel } from "@/pages/chat/components/ConversationPanel";

export function ChatPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeContactId = searchParams.get("with") ? Number(searchParams.get("with")) : null;

  function selectContact(contactId: number) {
    setSearchParams({ with: String(contactId) });
  }

  return (
    <div className="flex h-full">
      <div className="flex w-80 shrink-0 flex-col border-r border-slate-200 bg-white">
        <div className="border-b border-slate-200 px-5 py-4">
          <h1 className="text-base font-semibold text-slate-900">Messages</h1>
        </div>
        <ContactList activeContactId={activeContactId} onSelect={selectContact} />
      </div>

      <div className="flex-1 bg-white">
        {activeContactId ? (
          <ConversationPanel key={activeContactId} contactId={activeContactId} />
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-slate-500">
            Select a contact to start chatting
          </div>
        )}
      </div>
    </div>
  );
}
