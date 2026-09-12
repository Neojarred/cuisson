# Source Notes are captured, kept local, and never shared

A publisher's recipe notes are captured into the Recipe, attributed to them, stored on the
device, and excluded from a Recipe Card or anything else sent to another person.

This is a deliberate exception to the rule that the publisher's prose does not enter
Cuisson at all. That rule exists because the prose around a recipe is the one genuinely
copyrighted part of the page, and it is also the waffle nobody wants. Recipe notes are
different in kind: "use 6 chillies for a mild curry" is functional cooking guidance, and a
recipe that says "see Note 3" without it is quietly incomplete in a way the cook only
discovers halfway through.

## Considered options

Not capturing them was the previous position. It leaves every Note Reference dangling, and
RecipeTin Eats' beef rendang points at nine of them.

Capturing them and treating them like any other field was rejected. The reasoning of
ADR-0007 applies to text as much as to photographs: a private copy for the reader who
asked for the page is one thing, and putting that writing in front of someone else is
another.

## Consequences

Source Notes never appear in a Recipe Card, in app-to-app sharing, or in anything else
that leaves the device for another person. They do travel in the user's own Export,
because that is still their own copy.

They are shown attributed rather than mixed into the recipe, so a reader can see that the
advice is the author's and not Cuisson's.
