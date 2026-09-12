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

### Wireless Connector and Wireless Hub

- **A link comes back by itself.** Linking two ends pairs them until one of them is broken or linked elsewhere. When
  either end's chunk unloads the link drops, and the connector makes it again as soon as both ends are loaded. It
  checks every second that the link is still up. A link that failed to come back used to stay down for good.
- **Channels are AE2UD channel tiers**, `ae2stuff:wireless_connector` and `ae2stuff:wireless_hub`, 32 each as
  before, in AE2UD's `[ChannelTiers]` config. The hub's number can be raised. Both blocks' tooltips say how many
  channels they carry, and the hub's how many connectors it links.
- **The colour is a real network colour.** A coloured connector or hub joins only cables of the same colour or fluix
  ones next to it, as a cable does. It is painted with AE2UD's Color Applicator, by right-clicking it with a dye, or
  by crafting eight of them around a dye; one crafted with a water bucket turns fluix again. Colours stack
  separately.
- Looking at a connector or a hub draws a line to every end it is paired with, in the connector's colour, however
  many links a hub has. AE2UD's Network Visualiser draws wireless links in the connector's colour too.
- Waila and The One Probe show what a connector is linked to or waiting for, how many links a hub has, the channels
  in use, the power drawn, the name it was given and the colour.
- A link draws power at both ends while it is up, by the old formula.
- Links stay within one dimension and load no chunks.
- `config/ae2stuff.cfg` has a `wireless` section: whether connectors and the Wireless Setup Kit exist, their power
  formula, and `maxRange`, the farthest apart two ends can be linked, with no limit by default. `wireless.hub`
  switches the hub off on its own and sets how many connectors it links and its power formula. Switching `wireless`
  off takes the hub with it.
- Connectors and hubs placed with the old mod keep their links, colour, name and owner.

### Wireless Setup Kit

- **One kit with modes.** Holding the kit's key, Left Alt by default and set in Controls, and right-clicking the air
  switches mode, and the tooltip says so. A mode switched off in the config's `wireless.kit` section is skipped.
- **Simple mode** links as before: click one end, then the other, and sneak-right-click the air to clear it.
- **Queue mode** is what the Advanced Wireless Setup Kit did. Sneak-right-clicking the air switches between adding
  and linking.
  - Adding queues the clicked connector, or one slot of the clicked hub, or with the key held all of the hub's free
    slots.
  - Linking links the next queued end to the clicked one, or with the key held on a hub, queued ends one after another
    until the hub is full.
  - The key with a sneak-right-click clears the queue. The tooltip lists the first ten queued ends.
- **Manager mode** lists the connectors and hubs of chosen networks in a window.
  - Right-clicking any block of a network lists that network, and sneak-right-clicking one removes it. A network
    whose block is gone, or which has joined another listed network, drops off the list.
  - Right-clicking the air opens the window, with every device of the listed networks in this dimension: its state
    and channels, and on hover its position, network, power and colour.
  - Devices are dragged from the list into "What to link" and "Link with", back again, or up and down a column to
    change the order. "Link" links the two columns in pairs from the top, a hub taking devices until it is full, and
    "Unlink" breaks every link of "What to link".
  - Ctrl-left-clicking a device closes the window, outlines the device in red in the world as AE2UD does for a
    machine it locates, and turns the player to face it. The result shows above the hotbar and each failure goes to chat
    with its reason; devices that were done return to the list, and the rest stay.
  - **Rows can be devices or groups.** A button cycles the grouping between none, by colour, by network, and by
    whether a device is linked. A group row stands for a whole network, or for one colour or one link state on it,
    and moves between the columns as one. Two boxes on a group row say whether Link and Unlink take its connectors
    and its hubs, and a group gives Link only the connectors that are not linked and the hubs with room left.
  - **Linked devices can be hidden** with the second button: connectors with a live link and full hubs drop out of
    the window, and so do groups with nothing left to show.
  - **Rows can be kept at the top** with the square on the right of a row, and **renamed** by double-clicking the
    name. A device takes the name onto the block itself, as the quartz knife does, while a network's or a colour's
    name is remembered by the kit. An empty name gives the row its own name back.
  - **A strip of colours** under the toolbar paints everything in "What to link". It costs no dye, and devices on
    networks the player may not build on are skipped.
  - A network row also has an ✕ that takes the network off the kit.
  - The window fits the screen and shows as many rows as there is room for, and HEI shows nothing beside it. It
    refreshes every second while it is open.
- **A hub can no longer be queued past its free slots.** Its slots already in the queue count against it, which they
  did not.
- Both modes refuse networks the player may not build on, two hubs, a full hub and ends too far apart. A queued end
  that is gone or fails to link leaves the queue with a message; one waiting for a full hub stays.
- **The Advanced Wireless Setup Kit is removed.** One already in a world or an inventory becomes a Wireless Setup Kit
  in queue mode, keeping its queue.
