import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Input } from "@/components/ui/Input";
import { Spinner } from "@/components/ui/Spinner";
import { useAuth } from "@/context/AuthContext";
import { extractErrorMessage } from "@/lib/api";
import { useChangePassword, useProfile, useUpdateAvatar, useUpdateName } from "@/hooks/useProfile";
import { getExistingSubscription, isPushSupported, subscribeToPush, unsubscribeFromPush } from "@/lib/push";

const nameSchema = z.object({
  name: z.string().min(1, "Name is required").max(100, "Name must be at most 100 characters"),
});
type NameFormValues = z.infer<typeof nameSchema>;

const passwordSchema = z.object({
  currentPassword: z.string().min(1, "Current password is required"),
  newPassword: z.string().min(6, "New password must be at least 6 characters"),
});
type PasswordFormValues = z.infer<typeof passwordSchema>;

export function ProfilePage() {
  const { updateUser } = useAuth();
  const { data: profile, isLoading } = useProfile();
  const updateName = useUpdateName();
  const changePassword = useChangePassword();
  const updateAvatar = useUpdateAvatar();

  const [nameError, setNameError] = useState<string | null>(null);
  const [nameSuccess, setNameSuccess] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [pushEnabled, setPushEnabled] = useState(false);
  const [pushLoading, setPushLoading] = useState(false);
  const [pushError, setPushError] = useState<string | null>(null);

  useEffect(() => {
    getExistingSubscription()
      .then((subscription) => setPushEnabled(subscription !== null))
      .catch(() => setPushEnabled(false));
  }, []);

  async function togglePush() {
    setPushError(null);
    setPushLoading(true);
    try {
      if (pushEnabled) {
        await unsubscribeFromPush();
        setPushEnabled(false);
      } else {
        await subscribeToPush();
        setPushEnabled(true);
      }
    } catch (error) {
      setPushError(error instanceof Error ? error.message : "Something went wrong.");
    } finally {
      setPushLoading(false);
    }
  }

  const nameForm = useForm<NameFormValues>({
    resolver: zodResolver(nameSchema),
    values: profile ? { name: profile.name } : undefined,
  });

  const passwordForm = useForm<PasswordFormValues>({ resolver: zodResolver(passwordSchema) });

  async function onSubmitName(values: NameFormValues) {
    setNameError(null);
    setNameSuccess(false);
    try {
      await updateName.mutateAsync(values.name);
      updateUser({ name: values.name });
      setNameSuccess(true);
    } catch (error) {
      setNameError(extractErrorMessage(error));
    }
  }

  async function onSubmitPassword(values: PasswordFormValues) {
    setPasswordError(null);
    setPasswordSuccess(false);
    try {
      await changePassword.mutateAsync(values);
      passwordForm.reset();
      setPasswordSuccess(true);
    } catch (error) {
      setPasswordError(extractErrorMessage(error));
    }
  }

  async function onAvatarSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    setAvatarError(null);
    try {
      await updateAvatar.mutateAsync(file);
    } catch (error) {
      setAvatarError(extractErrorMessage(error));
    }
  }

  if (isLoading || !profile) {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto p-6">
      <h1 className="mb-6 text-xl font-semibold text-slate-900">Profile</h1>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white p-6">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">Avatar</h2>
          <div className="flex items-center gap-4">
            <Avatar name={profile.name} avatarUrl={profile.avatarUrl} size="lg" />
            <div className="flex flex-col gap-2">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif"
                className="hidden"
                onChange={onAvatarSelected}
              />
              <Button
                type="button"
                variant="secondary"
                isLoading={updateAvatar.isPending}
                onClick={() => fileInputRef.current?.click()}
              >
                Change avatar
              </Button>
              <p className="text-xs text-slate-400">PNG, JPEG, WEBP or GIF — up to 5MB.</p>
              <ErrorBanner message={avatarError} />
            </div>
          </div>
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-6">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">Account</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Email</dt>
              <dd className="text-slate-900">{profile.email}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Member since</dt>
              <dd className="text-slate-900">{new Date(profile.createdAt).toLocaleDateString()}</dd>
            </div>
          </dl>
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-6">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">Display name</h2>
          <form onSubmit={nameForm.handleSubmit(onSubmitName)} className="flex flex-col gap-4">
            <ErrorBanner message={nameError} />
            {nameSuccess && <p className="text-sm text-green-600">Name updated.</p>}
            <Input
              label="Name"
              error={nameForm.formState.errors.name?.message}
              {...nameForm.register("name")}
            />
            <Button type="submit" isLoading={nameForm.formState.isSubmitting} className="self-start">
              Save name
            </Button>
          </form>
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-6">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">Change password</h2>
          <form onSubmit={passwordForm.handleSubmit(onSubmitPassword)} className="flex flex-col gap-4">
            <ErrorBanner message={passwordError} />
            {passwordSuccess && <p className="text-sm text-green-600">Password updated.</p>}
            <Input
              label="Current password"
              type="password"
              autoComplete="current-password"
              error={passwordForm.formState.errors.currentPassword?.message}
              {...passwordForm.register("currentPassword")}
            />
            <Input
              label="New password"
              type="password"
              autoComplete="new-password"
              error={passwordForm.formState.errors.newPassword?.message}
              {...passwordForm.register("newPassword")}
            />
            <Button type="submit" isLoading={passwordForm.formState.isSubmitting} className="self-start">
              Update password
            </Button>
          </form>
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-6">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">Push notifications</h2>
          {isPushSupported() ? (
            <div className="flex flex-col gap-3">
              <ErrorBanner message={pushError} />
              <p className="text-sm text-slate-500">
                Get notified about new messages even when PulseHub isn&apos;t open.
              </p>
              <Button
                type="button"
                variant={pushEnabled ? "secondary" : "primary"}
                isLoading={pushLoading}
                onClick={togglePush}
                className="self-start"
              >
                {pushEnabled ? "Disable push notifications" : "Enable push notifications"}
              </Button>
            </div>
          ) : (
            <p className="text-sm text-slate-500">Push notifications aren&apos;t supported in this browser.</p>
          )}
        </section>
      </div>
    </div>
  );
}
