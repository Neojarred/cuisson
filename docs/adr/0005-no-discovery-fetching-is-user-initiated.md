# No recipe discovery, and every fetch is user-initiated from their own device

Hestia does not search, browse or index other people's recipe sites. It fetches a page
only when the user hands it a specific URL, from the user's own device and IP address,
which is what a browser does. An in-app browser was considered and rejected as clutter;
the Android share sheet does the same job with fewer taps and no interface of ours.

## Evidence

A competitor that fetches server-side was observed returning HTTP 403 on a recipe site,
which is what site protection does to a datacentre address. Fetching from the user's own
device, as a browser, does not attract that response. The legal argument and the practical
one point the same way.

## Consequences

This is the decision that keeps the project out of legal trouble, so it is a constraint
rather than a preference. Recipe APIs were also rejected: Edamam returns no cooking
instructions and demands attribution, Spoonacular charges per call, and both would send
every user query off the device. Any future "find me a recipe for X" feature reopens this
and must be argued on legal grounds first, not product grounds.
