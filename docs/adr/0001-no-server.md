# Hestia has no server

Hestia stores everything on the device and talks to no backend of ours. Sync, when a user
wants it, writes to a Sync Target the user owns: a Google Drive application data folder or
a WebDAV folder. This keeps us outside the GDPR data controller role entirely, keeps
running costs at zero, and is the property that distinguishes Hestia from every
subscription competitor.

## Consequences

Features that need a server are not available to us and should not be proposed without
revisiting this: shared live shopping lists, public recipe links, account-based sync,
server-side extraction, and any analytics. Each of those was considered and declined.
