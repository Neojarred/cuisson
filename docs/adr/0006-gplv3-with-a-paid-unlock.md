# GPLv3, public from the first commit, with a paid Unlock

The source is public under GPLv3 from the start, and the Play build sells a single in-app
purchase. Copyleft was chosen over MIT so that a fork cannot be taken closed and resold
silently, which is the outcome that would actually be objectionable.

## Consequences

Anyone who compiles from source can delete the Unlock check in a minute. No licence or
obfuscation prevents this, and the alternatives all require the phone-home behaviour this
project rejects. This is accepted deliberately: people willing to build an Android app
from source were never going to pay 5 euros. The repository is public from the first
commit rather than opened later, because opening a private repository afterwards means
auditing every commit for things that were never meant to be published.
