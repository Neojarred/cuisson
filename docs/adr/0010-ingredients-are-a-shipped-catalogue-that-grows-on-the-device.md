# Ingredients are a shipped catalogue of data that grows on the device

Cuisson recognises ingredients against a curated catalogue that ships inside the app, in
which each ingredient points at the Family it is a kind of. A line nothing in the catalogue
matches becomes a Learned Ingredient of its own instead of being merged into a guess.
Lines add up on a Shopping List only when they are the same ingredient; a Family groups
things and never adds their amounts. The catalogue is a list of records rather than a
class per ingredient, because Learned Ingredients have to be created on the phone, and a
phone can add a row but cannot write new code.

## Considered Options

- **Ship nothing and learn from the library.** The first list says onions three times and
  the user does the merging. The user ruled that out: consolidating is the app's job.
- **Open Food Facts.** Licensed ODbL, which is workable, but it describes products with
  barcodes. The granularity is wrong for "onion".
- **Merge an uncertain line into its closest match.** The worst case is a line that
  silently swallows something the cook needed. A separate Learned Ingredient's worst case
  is a duplicate line, which is visible.
- **Add up anything sharing a Family.** Icing sugar and brown sugar share one, and adding
  them sends the cook home with the wrong sugar.

## Consequences

"A kind of" means only that. Tomato purée is made from tomatoes and is not a kind of
tomato, so it has no Family there and sits in its own Aisle with the tins.

The catalogue lives in the app, not the database. An update can improve it with no
migration, and a fresh install needs nothing seeded.

Corrections the user makes, "same as" and "move to", are stored on the device and checked
before the catalogue, so a mistake corrected once stays corrected.
