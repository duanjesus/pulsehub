self.addEventListener("push", (event) => {
  if (!event.data) return;

  let payload;
  try {
    payload = event.data.json();
  } catch {
    payload = { title: "PulseHub", body: event.data.text() };
  }

  const title = payload.title || "PulseHub";
  const options = {
    body: payload.body || "",
    icon: "/favicon.svg",
    badge: "/favicon.svg",
    data: { relatedConversationId: payload.relatedConversationId ?? null },
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();

  const conversationId = event.notification.data?.relatedConversationId;
  const targetUrl = conversationId ? `/chat?conversation=${conversationId}` : "/";

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clients) => {
      for (const client of clients) {
        if ("focus" in client) {
          client.navigate(targetUrl);
          return client.focus();
        }
      }
      return self.clients.openWindow(targetUrl);
    }),
  );
});
