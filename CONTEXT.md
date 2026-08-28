# Hestia

An Android recipe app that keeps a person's recipes on their own device. It imports
recipes from the structured data that recipe sites already publish, helps decide what to
cook, and turns that into a shopping list. No server, no account, sold once.

This file is the shared vocabulary. It is a glossary and nothing else. Architecture lives
in `05-architecture.md`, decisions in `docs/adr/`, working agreements in `CLAUDE.md`.

## Recipes

**Recipe**:
A dish as Hestia stores it, with its ingredients, steps and provenance. Always belongs to
the person who imported it.
_Avoid_: entry, item, dish, card

**Raw Text**:
The exact text a recipe arrived as, kept forever and never edited by Hestia. Everything
parsed is an overlay on top of it.
_Avoid_: original, source text, raw data

**Ingredient Line**:
One line of a recipe's ingredient list, holding both its raw text and whatever the parser
managed to pull out of it.
_Avoid_: ingredient (ambiguous with Canonical Ingredient)

**Canonical Ingredient**:
The single thing that many written forms refer to. "Spring onion" and "scallion" resolve
to one Canonical Ingredient.
_Avoid_: ingredient, product, food, item

**Alias**:
A written form that resolves to a Canonical Ingredient, either shipped with the app or
learned from a correction the user made.
_Avoid_: synonym, mapping

**Step**:
One instruction in a recipe's method, optionally carrying a duration that becomes a timer.
_Avoid_: instruction, direction

**Cookbook**:
A named book of recipes the user put together, divided into Chapters. A Recipe lives in
exactly one Cookbook.
_Avoid_: folder, collection, album

**Chapter**:
A named division inside one Cookbook, holding recipes. The middle level of
Cookbook, Chapter, Recipe.
_Avoid_: section, category, subfolder

**Tag**:
A label attached to a Recipe for finding it. Cuts across Cookbooks, unlike a Chapter.
_Avoid_: keyword, label, category

**Unfiled**:
Where a Recipe lives until the user files it, which may be forever. Behaves as a Cookbook
so nothing is ever homeless, and never asks to be emptied.
_Avoid_: inbox, uncategorised, default cookbook

**Recipe Card**:
A generated image of a recipe, made for posting somewhere outside Hestia. Uses a User
Image or no image at all, never a Source Image.
_Avoid_: share image, export image

**Timeline**:
The single ordered record of everything that has happened to a Recipe: when it was
imported, each time it was changed, and each time it was cooked. Changes can be walked
back.
_Avoid_: history, audit log, versions, changelog

**Cook Entry**:
One event on the Timeline recording that the user cooked the recipe, with an optional
rating and note.
_Avoid_: cook log, history entry

## Import

**Import**:
The act of turning something outside Hestia into a Recipe. Always ends at the Review.
_Avoid_: scrape, clip, capture, save

**Structured Extraction**:
Reading the machine-readable recipe data a site already publishes. The main path, and the
only one that cannot invent an ingredient.
_Avoid_: tier 1, scraping, parsing (too general)

**Site Rule**:
A stored set of selectors for a site that publishes no structured data. Data, not code.
_Avoid_: tier 2, scraper, adapter

**Model Extraction**:
Using a language model to structure something with no structure to read, meaning captions,
photographs and pasted prose. Never used on a URL.
_Avoid_: tier 3, AI import, magic import

**Extractor**:
Whatever performs Model Extraction on this device. One of: none, the platform's own model,
a downloaded model, or the user's own API key.
_Avoid_: AI, engine, provider

**Draft Recipe**:
The result of an extraction, before the user has accepted it. Not yet a Recipe.
_Avoid_: candidate, preview, staged recipe

**Review**:
The screen where a Draft Recipe becomes a Recipe. Always shown, dismissible in one tap
when the extraction was clean.
_Avoid_: confirmation, edit screen

**Needs Review**:
A flag on a Recipe whose extraction is not trustworthy enough to present silently.
_Avoid_: unverified, low confidence

**Source Image**:
A photograph that came from the page a recipe was imported from. Stays on the device and
travels in the user's own Export, never to another person.
_Avoid_: recipe photo, thumbnail

**User Image**:
A photograph the user took of their own cooking. Theirs outright, with none of the
restrictions on a Source Image.
_Avoid_: my photo, custom image

**Cover Image**:
The one image shown for a Recipe in lists. Defaults to the user's own photo as soon as
there is one.
_Avoid_: thumbnail, hero, main photo

## Shopping and cooking

**Shopping List**:
A named list of things to buy, built from recipes the user added to it and editable by
hand. The user creates them and has as many as they want. Independent of each other: the
same recipe on two lists is two unrelated entries.
_Avoid_: grocery list, basket, cart

**Archived List**:
A Shopping List the user has finished with. The same list in a resting state, not a copy,
so bringing it back makes it active again with everything unticked. Kept rather than
deleted because a past shop is the best starting point for the next one.
_Avoid_: completed list, past list, history, template

**Shopping Item**:
One line on a Shopping List, which may have been contributed by several recipes.
_Avoid_: entry, product

**Serving Scale**:
How far up or down a recipe is being multiplied. Never stored on the Recipe, which always
shows the servings it was written for. A Shopping List remembers the scale each recipe was
added at, and that can be changed from the list.
_Avoid_: scaling factor, portions, multiplier

**Consolidation**:
Merging the same Canonical Ingredient across several recipes into one Shopping Item,
always able to show which recipes contributed what.
_Avoid_: merging, deduplication, aggregation

**Cook Mode**:
The full-screen, screen-awake view used while actually cooking.
_Avoid_: cooking view, kitchen mode, presentation mode

## Ownership and licensing

**Trial**:
The period during which an unpaid installation behaves exactly like a paid one.
_Avoid_: free tier, freemium, demo

**Read-Only Mode**:
What Hestia becomes when the Trial ends without a purchase. Everything already saved stays
visible and exportable. Nothing new can be written.
_Avoid_: locked, expired, limited mode

**Unlock**:
The single in-app purchase that ends Read-Only Mode permanently for that Google account.
_Avoid_: subscription, licence, premium, pro

**Sync Target**:
Where a user has chosen to put their data. A Drive application data folder, a WebDAV
folder, or nothing at all.
_Avoid_: cloud, server, backend, account

**Export File**:
A single versioned `.hestia` archive holding every recipe including Raw Text, plus images.
The promise that the user's data is theirs.
_Avoid_: dump, backup file, archive

## Words we do not use

**Pantry**, **stock**, **inventory**: Hestia does not track what you have. Decided in Q29.

**Meal Plan**: deferred indefinitely. The Shopping List is expected to cover it, per Q37.

**Account**, **sign-up**, **login**: Hestia has no accounts. A Google account may be used
as a Sync Target and to hold the Unlock, which is not the same thing.

**Discovery**, **recipe search**: Hestia does not search other people's sites. Decided
in D2.
