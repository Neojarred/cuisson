# Extraction is deterministic first, and a model is the last resort

A URL is never given to a language model. Recipe sites publish machine-readable recipe
data because Google requires it for rich results, so Structured Extraction reads it
exactly, instantly, at no cost, and without the ability to invent an ingredient that was
not there. Site Rules handle sites that publish nothing. Model Extraction exists only for
inputs with no structure to read: captions, photographs and pasted prose.

## Consequences

The default install contains no model at all. In 2026 a reader will assume an AI-first
import pipeline and wonder why this one is not, which is the reason this record exists.
Extraction accuracy is therefore a parsing problem, not a prompting problem, and should be
improved by fixing parsers rather than by reaching for a model.
