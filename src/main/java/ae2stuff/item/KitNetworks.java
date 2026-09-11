/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.item;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.AEPartLocation;

/**
 * The networks the kit's manager lists, each remembered by a block of it: a network has no position of its own.
 */
public final class KitNetworks {

    static final String NETWORKS = "networks";

    private KitNetworks() {
    }

    public static final class Anchor {

        public final BlockPos pos;
        public final int dimension;
        public final EnumFacing side;

        Anchor(final BlockPos pos, final int dimension, final EnumFacing side) {
            this.pos = pos;
            this.dimension = dimension;
            this.side = side;
        }

        /**
         * @return the network, or null while the block is in another dimension, unloaded, or not on one
         */
        @Nullable
        public IGrid grid(final World world) {
            if (world.provider.getDimension() != this.dimension || !world.isBlockLoaded(this.pos)) {
                return null;
            }
            return gridAt(world, this.pos, this.side);
        }

        private NBTTagCompound write() {
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", this.pos.getX());
            tag.setInteger("y", this.pos.getY());
            tag.setInteger("z", this.pos.getZ());
            tag.setInteger("dim", this.dimension);
            tag.setByte("side", (byte) this.side.getIndex());
            return tag;
        }

        private static Anchor read(final NBTTagCompound tag) {
            return new Anchor(new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")), tag.getInteger("dim"),
                    EnumFacing.byIndex(tag.getByte("side")));
        }
    }

    public static List<Anchor> read(@Nullable final NBTTagCompound tag) {
        final List<Anchor> anchors = new ArrayList<>();
        if (tag != null) {
            final NBTTagList list = tag.getTagList(NETWORKS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                anchors.add(Anchor.read(list.getCompoundTagAt(i)));
            }
        }
        return anchors;
    }

    private static void write(final NBTTagCompound tag, final List<Anchor> anchors) {
        if (anchors.isEmpty()) {
            tag.removeTag(NETWORKS);
            return;
        }
        final NBTTagList list = new NBTTagList();
        for (final Anchor anchor : anchors) {
            list.appendTag(anchor.write());
        }
        tag.setTag(NETWORKS, list);
    }

    /**
     * The network of the block, through its centre node or, for a cable bus with no cable, the part on the clicked face.
     */
    @Nullable
    public static IGrid gridAt(final World world, final BlockPos pos, final EnumFacing side) {
        final TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof IGridHost host)) {
            return null;
        }
        IGridNode node = host.getGridNode(AEPartLocation.INTERNAL);
        if (node == null) {
            node = host.getGridNode(AEPartLocation.fromFacing(side));
        }
        return node == null ? null : node.getGrid();
    }

    static void add(final NBTTagCompound tag, final World world, final BlockPos pos, final EnumFacing side, final EntityPlayer player) {
        final IGrid grid = gridAt(world, pos, side);
        if (grid == null) {
            ItemWirelessKit.message(player, TextFormatting.RED, "manager.no_network");
            return;
        }

        final List<Anchor> anchors = read(tag);
        for (final Anchor anchor : anchors) {
            if (anchor.grid(world) == grid) {
                ItemWirelessKit.message(player, TextFormatting.RED, "manager.already_listed");
                return;
            }
        }

        anchors.add(new Anchor(pos, world.provider.getDimension(), side));
        write(tag, anchors);
        ItemWirelessKit.message(player, TextFormatting.GREEN, "manager.added", anchors.size());
    }

    static void remove(final NBTTagCompound tag, final World world, final BlockPos pos, final EnumFacing side, final EntityPlayer player) {
        final IGrid grid = gridAt(world, pos, side);
        final List<Anchor> anchors = read(tag);
        final int before = anchors.size();
        anchors.removeIf(anchor -> anchor.dimension == world.provider.getDimension()
                && (anchor.pos.equals(pos) || grid != null && anchor.grid(world) == grid));

        if (anchors.size() == before) {
            ItemWirelessKit.message(player, TextFormatting.RED, "manager.not_listed");
            return;
        }
        write(tag, anchors);
        ItemWirelessKit.message(player, TextFormatting.GREEN, "manager.removed", anchors.size());
    }

    /**
     * Drops networks whose block is gone and the second of two that have become one. A network that is only
     * unloaded or in another dimension stays.
     */
    public static void clean(final ItemStack kit, final World world) {
        final NBTTagCompound tag = kit.getTagCompound();
        if (tag == null) {
            return;
        }

        final List<Anchor> anchors = read(tag);
        final List<IGrid> seen = new ArrayList<>();
        final boolean changed = anchors.removeIf(anchor -> {
            if (anchor.dimension != world.provider.getDimension() || !world.isBlockLoaded(anchor.pos)) {
                return false;
            }
            final IGrid grid = gridAt(world, anchor.pos, anchor.side);
            if (grid == null || seen.contains(grid)) {
                return true;
            }
            seen.add(grid);
            return false;
        });

        if (changed) {
            write(tag, anchors);
            kit.setTagCompound(tag.isEmpty() ? null : tag);
        }
    }
}
