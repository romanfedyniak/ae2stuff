/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import ae2stuff.core.AE2StuffConfig;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import ae2stuff.tile.WirelessLink;
import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.core.CreativeTab;
import appeng.items.AEBaseItem;

/**
 * Links a connector to another connector or to a hub: click one end, then the other.
 */
public final class ItemWirelessKit extends AEBaseItem {

    /** The same tag the old kit used, so one bound mid-link keeps its binding. */
    private static final String LOCATION = "loc";

    public ItemWirelessKit() {
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTab.instance);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player, final EnumHand hand) {
        final ItemStack stack = player.getHeldItem(hand);
        if (!player.isSneaking() || location(stack) == null) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        if (!world.isRemote) {
            clearLocation(stack);
            message(player, TextFormatting.GREEN, "cleared");
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public EnumActionResult onItemUse(final EntityPlayer player, final World world, final BlockPos pos, final EnumHand hand,
            final EnumFacing facing, final float hitX, final float hitY, final float hitZ) {
        if (!(world.getTileEntity(pos) instanceof TileWirelessBase tile)) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        final ItemStack stack = player.getHeldItem(hand);
        if (!tile.canBeChangedBy(player)) {
            message(player, TextFormatting.RED, "security.player");
            return EnumActionResult.SUCCESS;
        }

        final NBTTagCompound location = location(stack);
        if (location == null) {
            if (tile instanceof TileWirelessHub hub && !hub.hasRoom()) {
                message(player, TextFormatting.RED, "hub_full");
            } else {
                setLocation(stack, pos, world.provider.getDimension());
                message(player, TextFormatting.GREEN, "bound", pos.getX(), pos.getY(), pos.getZ());
            }
            return EnumActionResult.SUCCESS;
        }

        clearLocation(stack);
        final BlockPos bound = new BlockPos(location.getInteger("x"), location.getInteger("y"), location.getInteger("z"));
        if (location.getInteger("dim") != world.provider.getDimension()) {
            message(player, TextFormatting.RED, "dimension");
            return EnumActionResult.SUCCESS;
        }
        if (bound.equals(pos)) {
            message(player, TextFormatting.GREEN, "cleared");
            return EnumActionResult.SUCCESS;
        }

        final TileEntity boundTile = world.isBlockLoaded(bound) ? world.getTileEntity(bound) : null;
        if (!(boundTile instanceof TileWirelessBase other)) {
            message(player, TextFormatting.RED, "missing");
        } else if (!other.canBeChangedBy(player)) {
            message(player, TextFormatting.RED, "security.player");
        } else if (tile instanceof TileWirelessHub && other instanceof TileWirelessHub) {
            message(player, TextFormatting.RED, "two_hubs");
        } else {
            link(tile, other, player);
        }
        return EnumActionResult.SUCCESS;
    }

    private static void link(final TileWirelessBase clicked, final TileWirelessBase bound, final EntityPlayer player) {
        final TileWirelessConnector connector = clicked instanceof TileWirelessConnector c ? c : (TileWirelessConnector) bound;
        final TileWirelessBase other = connector == clicked ? bound : clicked;

        if (!other.hasRoomFor(connector.getPos())) {
            message(player, TextFormatting.RED, "hub_full");
            return;
        }

        final BlockPos from = connector.getPos();
        final BlockPos to = other.getPos();
        final double distance = WirelessLink.distance(from.getX() - to.getX(), from.getY() - to.getY(), from.getZ() - to.getZ());
        final int maxRange = AE2StuffConfig.instance().getWirelessMaxRange();
        if (!WirelessLink.inRange(maxRange, distance)) {
            message(player, TextFormatting.RED, "too_far", (int) Math.ceil(distance), maxRange);
            return;
        }

        // The player who links both ends owns them, as with the old kit
        final int playerId = AEApi.instance().registries().players().getID(player);
        for (final TileWirelessBase end : new TileWirelessBase[] { connector, other }) {
            final IGridNode node = end.getProxy().getNode();
            if (node != null) {
                node.setPlayerID(playerId);
            }
        }

        connector.pair(to);
        if (other instanceof TileWirelessConnector peer) {
            peer.pair(from);
        } else {
            ((TileWirelessHub) other).addConnector(from);
        }
        shareName(connector, other);

        switch (connector.connectNow()) {
            case LINKED -> message(player, TextFormatting.GREEN, "connected", to.getX(), to.getY(), to.getZ());
            case SECURITY -> {
                connector.unpairAll();
                message(player, TextFormatting.RED, "security.network");
            }
            case FAILED -> {
                connector.unpairAll();
                message(player, TextFormatting.RED, "failed");
            }
        }
    }

    private static void shareName(final TileWirelessBase a, final TileWirelessBase b) {
        if (a.hasCustomInventoryName() && !b.hasCustomInventoryName()) {
            b.setName(a.getCustomInventoryName());
            b.saveChanges();
        } else if (b.hasCustomInventoryName() && !a.hasCustomInventoryName()) {
            a.setName(b.getCustomInventoryName());
            a.saveChanges();
        }
    }

    private static void message(final EntityPlayer player, final TextFormatting color, final String key, final Object... args) {
        final ITextComponent text = new TextComponentTranslation("chat.ae2stuff.wireless." + key, args);
        text.getStyle().setColor(color);
        player.sendStatusMessage(text, true);
    }

    @Nullable
    private static NBTTagCompound location(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(LOCATION, Constants.NBT.TAG_COMPOUND) ? tag.getCompoundTag(LOCATION) : null;
    }

    private static void setLocation(final ItemStack stack, final BlockPos pos, final int dimension) {
        final NBTTagCompound location = new NBTTagCompound();
        location.setInteger("x", pos.getX());
        location.setInteger("y", pos.getY());
        location.setInteger("z", pos.getZ());
        location.setInteger("dim", dimension);

        final NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setTag(LOCATION, location);
        stack.setTagCompound(tag);
    }

    private static void clearLocation(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.removeTag(LOCATION);
            if (tag.isEmpty()) {
                stack.setTagCompound(null);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
            final ITooltipFlag advancedTooltips) {
        final NBTTagCompound location = location(stack);
        if (location == null) {
            lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.empty"));
        } else {
            lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.bound", location.getInteger("x"), location.getInteger("y"),
                    location.getInteger("z")));
            lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.next"));
            lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.clear"));
        }
    }
}
