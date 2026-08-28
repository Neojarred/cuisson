# Raw Text is never discarded; parsing is an overlay

Every imported recipe keeps the exact text it arrived as, and parsed fields sit on top of
it rather than replacing it. Parsing an ingredient line into quantity, unit and item is
lossy and sometimes wrong, and a destructive parse makes both correction and reprocessing
impossible.

## Consequences

Improving the parser in a later release lets every recipe already in the library be
re-parsed, which an app that parses destructively can never do. The cost is storage, which
is text and therefore negligible. Any change that drops Raw Text to save space or tidy the
schema is reversing this decision.
