# Cuisson

An Android recipe app that keeps your recipes on your own phone.

Cuisson is a placeholder name.

## What it is

Most recipe sites already publish their recipes as machine-readable data, because search
engines require it. Cuisson reads that directly, so importing a recipe from a URL is
parsing rather than guesswork: exact, instant, offline afterwards, and incapable of
inventing an ingredient that was not on the page. A language model is involved only for
things with no structure to read, such as a photograph of a cookbook page, and never for a
URL.

Nothing is sent anywhere. There is no server, no account and no analytics. Pages are
fetched by your phone, from your address, the way a browser does. If you turn on syncing
it writes to storage you already own.

## Status

Early. The repository currently holds the domain model, the database schema and a screen
that renders one recipe. Nothing is importable yet.

## Principles

These are decisions rather than habits, and each is written up in `docs/adr/`:

- No server, ever
- Deterministic extraction first; a URL never reaches a language model
- The text a recipe arrived as is never discarded, so a better parser can be run over your
  whole library later
- Cuisson does not search or browse other people's recipe sites
- Your data leaves in an open format whenever you want it

`CONTEXT.md` holds the vocabulary the code and the interface both use.

## Building

Requires JDK 17 and the Android SDK.

```
./gradlew :androidApp:assembleDebug
```

## Licence

GPLv3. See `LICENSE`.
