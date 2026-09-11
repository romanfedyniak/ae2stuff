# Changelog

All notable AE2 Stuff Unofficial Deconstructed changes are grouped by the version in which they first appeared.

## Important compatibility notice

- AE2 Stuff Unofficial Deconstructed runs only with AE2 Unofficial Deconstructed, not with AE2 Unofficial Extended
  Life or any other AE2 build.
- Back up the world before installing or updating the mod.

## Unreleased

- **Rewritten from scratch in Java.** The Scala code is gone, and so is BdLib, the library it was built on, so neither
  has to be installed any more.
- **The Network Visualisation Tool is removed**, because AE2UD has a Network Visualiser of its own. One already in a
  world or an inventory becomes AE2UD's.
- Only the English and Ukrainian translations ship. The partial translations into other languages are removed.

### Crystal Growth Chamber

- **What goes in and what comes out are kept apart.** The chamber had twenty-seven slots that held seeds and
  grown crystals alike. It has twenty-seven for what goes in, on top, and twenty-seven for what comes out, below.
  A crystal that has finished growing moves down by itself, and waits in its slot while there is no room.
  Automation can only put things into the top slots and only take them from the bottom ones.
- **Auto-Export**, the same button AE2UD's Inscriber has, hands what the chamber made to the inventories against
  it, a face at a time.
- It takes a **Redstone Card** as well as Acceleration Cards, in four upgrade slots, so a signal can switch it on
  and off. Acceleration counts upgrade points rather than cards, so a card worth several points speeds it up by
  that many.
- **Power comes from the network when the chamber's own store runs dry**, instead of the chamber stopping until
  the store has refilled.
- Every number is in `config/ae2stuff.cfg`: whether the chamber exists at all, how much power it stores and draws
  idle, what a growth cycle costs and how long it takes, and how many Acceleration Cards it takes and how many of
  their points count. A chamber switched off is never registered, and neither is its recipe.
- Breaking the chamber drops what is in it and its cards, and the block itself comes back as a plain item that
  stacks.
- A chamber placed with the old mod keeps what it held: its twenty-seven slots are sorted into the new input and
  output, and its cards and stored power carry over.
- HEI lists the chamber beside AE2UD's recipes for growing crystal seeds, and shows fluix made in it as a recipe of
  its own, which can be switched off in the config's `jei` section.
- Waila and The One Probe say how many stacks are growing.

### Advanced Inscriber

- **It presses twice as fast as AE2UD's Inscriber**: an item takes 50 ticks rather than 100 with no Acceleration
  Cards, for the same 1000 AE, so it draws twice the power while it works.
- **It works a batch at a time.** Every point of capacity from its Capacity Cards presses one more item in the
  same cycle, as long as the input, the plates the recipe uses up and the room in the output allow for them.
  Acceleration Cards still shorten the cycle, and power is charged for every item in the batch.
- It has AE2UD's Inscriber's three buttons: **Automation Access Mode**, which decides whether each face reaches
  one part of the machine or all of it, **Auto-Export**, and **Input Slot Stack Size**. With separate faces, the
  top and bottom faces reach the plates. Access mode takes the place of the two lock toggles it had, since plates
  can only be taken out through their own faces anyway.
- Its five upgrade slots take up to five Acceleration Cards and three Capacity Cards, and they compete for the
  slots.
- It renames with a Name Press, as AE2UD's Inscriber does.
- **Power comes from the network when its own store runs dry**, instead of the inscriber stopping until the store
  has refilled.
- Every number is in `config/ae2stuff.cfg`: whether it exists at all, its power store and idle draw, what pressing
  one item costs and how long it takes, and how many of each card it takes and how many of their points count.
- Breaking it drops what is in it and its cards, and the block comes back as a plain item that stacks.
- An inscriber placed with the old mod keeps its plates, input, output, cards and stored power. What it was in the
  middle of pressing is handed to the output rather than lost.
- HEI lists it beside AE2UD's Inscriber recipes.
