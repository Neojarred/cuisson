# The Export File format

An Export File, with the extension `.cuisson`, is a whole Cuisson library in one file: every recipe and its picture,
cookbooks and chapters, the record of what was cooked, shopping lists, and the corrections
made to how ingredients are recognised. It is an ordinary zip file with nothing
compressed, so any unzip tool opens it, and the text inside is JSON.

This page is the promise behind that. It says what is in the file, what each field means,
and what Cuisson does with files made by older and newer versions of itself.

## Layout

```
manifest.json    what the file is and when it was made
recipes.json     the library
media/<sha256>   one file per picture, named by the SHA-256 of its bytes
```

Pictures are stored exactly as they were saved, usually JPEG or WebP, with no extension.
Two recipes with the same picture share one file.

Times are milliseconds since the start of 1970, UTC. Durations are minutes.

## manifest.json

| Field | Meaning |
|---|---|
| `format` | Always `"cuisson"`. |
| `formatVersion` | `1` for everything on this page. |
| `appVersion` | The version of Cuisson that made the file. |
| `exportedAt` | When the file was made. |
| `recipes`, `images`, `cookbooks`, `shoppingLists` | How many of each the file holds. |

## recipes.json

The top level holds `recipes`, `cookbooks`, `cookEntries`, `shoppingLists`, `corrections`
and `aisleCorrections`.

### recipes

| Field | Meaning |
|---|---|
| `id` | Stable across Export Files of the same library. |
| `title` | The name shown in Cuisson, which may have been tidied or edited. |
| `titleAsPublished` | The name as the source gave it. |
| `sourceKind` | `WEB`, `MANUAL`, `PASTED_TEXT`, `PHOTO`, `SOCIAL` or `APP_IMPORT`. |
| `sourceUrl`, `sourceName` | Where it came from, when it came from somewhere. |
| `servings`, `servingsUnit` | What the recipe was written for, such as `6` and `personnes`. |
| `prepMinutes`, `cookMinutes`, `totalMinutes` | As published. |
| `note` | The user's own note on the recipe. |
| `image` | The entry under `media/` holding its picture. |
| `chapterId` | The chapter it is filed in. |
| `language` | As the source declared it. Often wrong: many French sites declare English. |
| `extractionTier`, `extractionConfidence`, `needsReview` | How the recipe was read, and whether it should be checked. |
| `createdAt`, `updatedAt` | When it was saved, and when it last changed. |
| `ingredients` | Lines, in order. |
| `steps` | Steps, in order. |
| `sourceNotes` | The publisher's own notes, with the label they gave each one. |

An ingredient line has `rawText`, which is the line exactly as its publisher wrote it,
and `amendment`, which is present only when the user rewrote the line. Both are always
kept, so an Export File can still show a recipe as it was published. `group` is the heading the
line sits under, such as `For the sauce`.

A step has `text`, as published, an optional `amendment`, and `references`: notes it
points at that were not captured, such as `Note 3`.

### cookbooks

Each has an `id`, `name`, `position`, `createdAt` and its `chapters`. Every cookbook has
one chapter with an empty name, which holds whatever is in the cookbook but in no named
chapter. The cookbook with the id `unfiled` is where recipes live until they are filed.

### cookEntries

One per time a recipe was cooked: `recipeId`, `cookedAt`, and an optional `rating` and
`note`.

### shoppingLists

Each has an `id`, `name`, `createdAt`, `lastUsedAt` and `archivedAt`, which is present only
for an archived list. Then:

- `recipes`: the recipes on the list, each with the `servings` it was added at.
- `ownLines`: lines the user typed onto the list themselves.
- `typedAmounts`: amounts the user typed over the computed one. `basis` records what the
  recipes added up to at the time, so Cuisson can tell when the item has since changed.

The items on a list are not stored. They are worked out from the recipes and the user's
own lines whenever the list is read.

### corrections and aisleCorrections

A correction says that a written form is a particular ingredient: `writtenAs` is the
folded form of the name, and `ingredient` is the ingredient's id. An aisle correction
moves an ingredient to a different aisle.

## What is not in the file

Parsed ingredient amounts, step timers and the search index. Cuisson works these out
again when it restores an Export File, so a better ingredient reader in a later version improves
an old Export File too. Ticks on shopping lists are not kept either: a restored list starts
unshopped.

## Restoring

Restoring adds to what is already on the phone. It never deletes anything.

- A recipe the phone does not have is added.
- A recipe on both, unchanged since, is left alone.
- A recipe on both, with different changes, keeps the more recently changed version in
  its place. The other version is kept beside it, with "(copy)" after its title.
- Cookbooks, chapters, cooking records, shopping lists and corrections are added where
  they are missing. Anything the phone already has stays as it is.

A file that is damaged, or is not a Cuisson Export File, is refused, and nothing in it is
restored.

## Versions

A newer version of this format adds fields before it changes any. Cuisson ignores fields
it does not recognise, so a file from a slightly newer app still opens. A change older
apps cannot read raises `formatVersion`, and an older Cuisson refuses that file with a
message to update, rather than restoring part of it.
