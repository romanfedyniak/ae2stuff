/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import ae2stuff.container.KitEntry;

/**
 * How the manager window was left: its grouping, whether linked devices are hidden, what is pinned and what groups
 * were named. All of it lives on the kit.
 */
public final class KitSettings {

    private static final String GROUPING = "grouping";
    private static final String HIDE_LINKED = "hideLinked";
    private static final String PINS = "pins";
    private static final String NAMES = "names";

    public static final int MAX_NAME_LENGTH = 64;

    public enum Grouping {
        SINGLE,
        COLOR,
        NETWORK
    }

    private KitSettings() {
    }

    public static Grouping grouping(final NBTTagCompound tag) {
        final int ordinal = tag.getByte(GROUPING);
        return ordinal >= 0 && ordinal < Grouping.values().length ? Grouping.values()[ordinal] : Grouping.SINGLE;
    }

    public static void setGrouping(final NBTTagCompound tag, final Grouping grouping) {
        if (grouping == Grouping.SINGLE) {
            tag.removeTag(GROUPING);
        } else {
            tag.setByte(GROUPING, (byte) grouping.ordinal());
        }
    }

    public static boolean hideLinked(final NBTTagCompound tag) {
        return tag.getBoolean(HIDE_LINKED);
    }

    public static void setHideLinked(final NBTTagCompound tag, final boolean hide) {
        if (hide) {
            tag.setBoolean(HIDE_LINKED, true);
        } else {
            tag.removeTag(HIDE_LINKED);
        }
    }

    public static List<KitEntry> pins(final NBTTagCompound tag, final int dimension) {
        final List<KitEntry> pins = new ArrayList<>();
        final NBTTagList list = tag.getTagList(PINS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound pin = list.getCompoundTagAt(i);
            final KitEntry entry = KitEntry.fromNBT(pin);
            if (entry != null && pin.getInteger("dim") == dimension) {
                pins.add(entry);
            }
        }
        return pins;
    }

    public static void setPinned(final NBTTagCompound tag, final KitEntry entry, final int dimension, final boolean pinned) {
        final NBTTagList list = without(tag.getTagList(PINS, Constants.NBT.TAG_COMPOUND), entry, dimension);
        if (pinned) {
            list.appendTag(entry.toNBT(dimension));
        }
        put(tag, PINS, list);
    }

    public static Map<KitEntry, String> names(final NBTTagCompound tag, final int dimension) {
        final Map<KitEntry, String> names = new LinkedHashMap<>();
        final NBTTagList list = tag.getTagList(NAMES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound name = list.getCompoundTagAt(i);
            final KitEntry entry = KitEntry.fromNBT(name);
            if (entry != null && name.getInteger("dim") == dimension) {
                names.put(entry, name.getString("name"));
            }
        }
        return names;
    }

    /**
     * Names a group; an empty name gives it its own name back.
     */
    public static void rename(final NBTTagCompound tag, final KitEntry entry, final int dimension, final String name) {
        final NBTTagList list = without(tag.getTagList(NAMES, Constants.NBT.TAG_COMPOUND), entry, dimension);
        if (!name.isEmpty()) {
            final NBTTagCompound named = entry.toNBT(dimension);
            named.setString("name", name);
            list.appendTag(named);
        }
        put(tag, NAMES, list);
    }

    /**
     * Drops the pins and names of a network, and of its colour groups, once it leaves the kit.
     */
    static void forgetNetwork(final NBTTagCompound tag, final BlockPos anchor, final int dimension) {
        for (final String key : new String[] { PINS, NAMES }) {
            final NBTTagList list = tag.getTagList(key, Constants.NBT.TAG_COMPOUND);
            final NBTTagList kept = new NBTTagList();
            for (int i = 0; i < list.tagCount(); i++) {
                final NBTTagCompound item = list.getCompoundTagAt(i);
                final KitEntry entry = KitEntry.fromNBT(item);
                if (entry == null || !(entry.isGroup() && entry.pos.equals(anchor) && item.getInteger("dim") == dimension)) {
                    kept.appendTag(item);
                }
            }
            put(tag, key, kept);
        }
    }

    private static NBTTagList without(final NBTTagList list, final KitEntry entry, final int dimension) {
        final NBTTagList kept = new NBTTagList();
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound item = list.getCompoundTagAt(i);
            if (!(item.getInteger("dim") == dimension && entry.equals(KitEntry.fromNBT(item)))) {
                kept.appendTag(item);
            }
        }
        return kept;
    }

    private static void put(final NBTTagCompound tag, final String key, final NBTTagList list) {
        if (list.tagCount() == 0) {
            tag.removeTag(key);
        } else {
            tag.setTag(key, list);
        }
    }
}
