import { useState } from "react";

import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Input } from "@/components/ui/Input";
import { extractErrorMessage } from "@/lib/api";
import { useContacts } from "@/hooks/useUsers";
import { useCreateGroup } from "@/hooks/useConversations";

export function NewGroupModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (conversationId: number) => void;
}) {
  const { data: contacts } = useContacts();
  const createGroup = useCreateGroup();

  const [name, setName] = useState("");
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [error, setError] = useState<string | null>(null);

  function toggleContact(contactId: number) {
    setSelectedIds((current) =>
      current.includes(contactId) ? current.filter((id) => id !== contactId) : [...current, contactId],
    );
  }

  async function handleSubmit() {
    setError(null);
    if (!name.trim()) {
      setError("Group name is required");
      return;
    }
    if (selectedIds.length === 0) {
      setError("Select at least one member");
      return;
    }

    try {
      const conversation = await createGroup.mutateAsync({ name: name.trim(), memberIds: selectedIds });
      onCreated(conversation.id);
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-black/30 px-4">
      <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-lg">
        <h2 className="mb-4 text-base font-semibold text-slate-900">New group</h2>

        <div className="flex flex-col gap-4">
          <ErrorBanner message={error} />
          <Input label="Group name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Project team" />

          <div>
            <p className="mb-2 text-sm font-medium text-slate-700">Members</p>
            <ul className="max-h-48 space-y-1 overflow-y-auto">
              {(contacts ?? []).map((contact) => (
                <li key={contact.id}>
                  <label className="flex items-center gap-3 rounded-md px-2 py-1.5 hover:bg-slate-50">
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(contact.id)}
                      onChange={() => toggleContact(contact.id)}
                      className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                    />
                    <Avatar name={contact.name} avatarUrl={contact.avatarUrl} size="sm" />
                    <span className="text-sm text-slate-800">{contact.name}</span>
                  </label>
                </li>
              ))}
            </ul>
          </div>

          <div className="mt-2 flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="button" isLoading={createGroup.isPending} onClick={handleSubmit}>
              Create group
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
