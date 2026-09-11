/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

import ae2stuff.AE2Stuff;
import ae2stuff.client.KitKeyHandler;
import ae2stuff.core.AE2StuffConfig;
import ae2stuff.core.ModGuiHandler;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessHub;
import ae2stuff.tile.WirelessLink;
import appeng.api.networking.IGridHost;
import appeng.core.CreativeTab;
import appeng.items.AEBaseItem;

/**
 * Links connectors to connectors and hubs. Simple mode links two clicked ends; queue mode, which the old Advanced
 * Wireless Setup Kit had, queues many and links them one after another; manager mode opens a window over the
 * devices of chosen networks. The kit's key with a right-click in the air switches mode.
 */
public final class ItemWirelessKit extends AEBaseItem {

    /** Simple mode's binding, under the tag the old kit used, so one bound mid-link keeps it. */
    private static final String LOCATION = "loc";
    private static final String MODE = "kitMode";
    private static final String QUEUE = "queue";
    private static final String QUEUE_DIM = "queueDim";
    private static final String LINKING = "linking";

    private static final int TOOLTIP_ENTRIES = 10;

    public enum Mode {
        SIMPLE,
        QUEUE,
        MANAGER;

        private String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public ItemWirelessKit() {
        this.setMaxStackSize(1);
        this.setCreativeTab(CreativeTab.instance);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player, final EnumHand hand) {
        final ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(player.isSneaking() ? EnumActionResult.SUCCESS : EnumActionResult.PASS, stack);
        }

        final NBTTagCompound tag = data(stack);
        final Mode mode = modeOf(tag);
        final boolean key = KitKey.isHeld(player);
        if (key && player.isSneaking()) {
            if (mode == Mode.MANAGER) {
                return new ActionResult<>(EnumActionResult.PASS, stack);
            }
            clear(tag, mode, player);
        } else if (key) {
            switchMode(tag, mode, player);
        } else if (mode == Mode.MANAGER) {
            if (player.isSneaking()) {
                return new ActionResult<>(EnumActionResult.PASS, stack);
            }
            player.openGui(AE2Stuff.instance, ModGuiHandler.WIRELESS_KIT, world, hand.ordinal(), 0, 0);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        } else if (player.isSneaking() && mode == Mode.QUEUE) {
            toggleLinking(tag, player);
        } else if (player.isSneaking() && hasBinding(tag)) {
            clear(tag, mode, player);
        } else {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        save(stack, tag);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    /**
     * Manager mode acts before the block does, so clicking a drive or an interface lists its network instead of
     * opening it.
     */
    @Override
    public EnumActionResult onItemUseFirst(final EntityPlayer player, final World world, final BlockPos pos, final EnumFacing side,
            final float hitX, final float hitY, final float hitZ, final EnumHand hand) {
        final ItemStack stack = player.getHeldItem(hand);
        if (!(world.getTileEntity(pos) instanceof IGridHost) || modeOf(view(stack)) != Mode.MANAGER) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        final NBTTagCompound tag = data(stack);
        if (player.isSneaking()) {
            KitNetworks.remove(tag, world, pos, side, player);
        } else {
            KitNetworks.add(tag, world, pos, side, player);
        }
        save(stack, tag);
        return EnumActionResult.SUCCESS;
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
        final NBTTagCompound tag = data(stack);
        final Mode mode = modeOf(tag);
        final boolean key = KitKey.isHeld(player);

        if (mode == Mode.MANAGER) {
            return EnumActionResult.PASS;
        } else if (key && player.isSneaking()) {
            clear(tag, mode, player);
        } else if (mode == Mode.QUEUE && player.isSneaking()) {
            toggleLinking(tag, player);
        } else if (!tile.canBeChangedBy(player)) {
            message(player, TextFormatting.RED, "security.player");
        } else if (mode == Mode.SIMPLE) {
            useSimple(tile, tag, player, world);
        } else if (tag.getBoolean(LINKING)) {
            linkFromQueue(tile, key && tile instanceof TileWirelessHub, tag, player, world);
        } else {
            addToQueue(tile, key, tag, player, world);
        }

        save(stack, tag);
        return EnumActionResult.SUCCESS;
    }

    private static void useSimple(final TileWirelessBase tile, final NBTTagCompound tag, final EntityPlayer player, final World world) {
        final BlockPos pos = tile.getPos();
        final int dimension = world.provider.getDimension();
        if (!hasBinding(tag)) {
            if (tile instanceof TileWirelessHub hub && !hub.hasRoom()) {
                message(player, TextFormatting.RED, "hub_full");
            } else {
                final NBTTagCompound location = posTag(pos);
                location.setInteger("dim", dimension);
                tag.setTag(LOCATION, location);
                message(player, TextFormatting.GREEN, "bound", pos.getX(), pos.getY(), pos.getZ());
            }
            return;
        }

        final NBTTagCompound location = tag.getCompoundTag(LOCATION);
        tag.removeTag(LOCATION);
        final BlockPos bound = posOf(location);
        final TileWirelessBase source = wirelessAt(world, bound);
        if (location.getInteger("dim") != dimension) {
            message(player, TextFormatting.RED, "dimension");
        } else if (bound.equals(pos)) {
            message(player, TextFormatting.GREEN, "cleared");
        } else if (source == null) {
            message(player, TextFormatting.RED, "missing");
        } else {
            report(player, WirelessLinker.link(source, tile, player), source, tile);
        }
    }

    private static void addToQueue(final TileWirelessBase tile, final boolean allSlots, final NBTTagCompound tag,
            final EntityPlayer player, final World world) {
        final int dimension = world.provider.getDimension();
        final NBTTagList queue = tag.getTagList(QUEUE, Constants.NBT.TAG_COMPOUND);
        if (queue.tagCount() > 0 && tag.getInteger(QUEUE_DIM) != dimension) {
            message(player, TextFormatting.RED, "dimension");
            return;
        }

        final BlockPos pos = tile.getPos();
        final int queued = countIn(queue, pos);
        if (tile instanceof TileWirelessHub hub) {
            final int free = WirelessLink.freeHubSlots(TileWirelessHub.getMaxConnections(), hub.getLinkCount(), queued);
            if (free <= 0) {
                message(player, TextFormatting.RED, "hub_full");
                return;
            }
            final int adding = allSlots ? free : 1;
            for (int i = 0; i < adding; i++) {
                queue.appendTag(posTag(pos));
            }
            message(player, TextFormatting.GREEN, "hub_queued", adding, queue.tagCount());
        } else if (queued > 0) {
            message(player, TextFormatting.RED, "already_queued");
            return;
        } else {
            queue.appendTag(posTag(pos));
            message(player, TextFormatting.GREEN, "queued", pos.getX(), pos.getY(), pos.getZ(), queue.tagCount());
        }

        tag.setTag(QUEUE, queue);
        tag.setInteger(QUEUE_DIM, dimension);
    }

    private static void linkFromQueue(final TileWirelessBase target, final boolean untilFull, final NBTTagCompound tag,
            final EntityPlayer player, final World world) {
        final NBTTagList queue = tag.getTagList(QUEUE, Constants.NBT.TAG_COMPOUND);
        if (queue.tagCount() == 0) {
            message(player, TextFormatting.RED, "queue_empty");
            return;
        }
        if (tag.getInteger(QUEUE_DIM) != world.provider.getDimension()) {
            message(player, TextFormatting.RED, "dimension");
            return;
        }

        int linked = 0;
        TileWirelessBase lastLinked = null;
        while (queue.tagCount() > 0) {
            final BlockPos head = posOf(queue.getCompoundTagAt(0));
            if (head.equals(target.getPos())) {
                queue.removeTag(0);
                continue;
            }

            final TileWirelessBase source = wirelessAt(world, head);
            if (source == null) {
                queue.removeTag(0);
                message(player, TextFormatting.RED, "missing");
            } else {
                final WirelessLinker.Result result = WirelessLinker.link(source, target, player);
                if (result == WirelessLinker.Result.HUB_FULL) {
                    // Waits in the queue for a hub with room
                    report(player, result, source, target);
                    break;
                }

                queue.removeTag(0);
                if (result == WirelessLinker.Result.LINKED) {
                    linked++;
                    lastLinked = source;
                } else {
                    report(player, result, source, target);
                }
            }

            if (!untilFull || !((TileWirelessHub) target).hasRoom()) {
                break;
            }
        }

        if (queue.tagCount() == 0) {
            tag.removeTag(QUEUE);
            tag.removeTag(QUEUE_DIM);
        } else {
            tag.setTag(QUEUE, queue);
        }

        if (untilFull && linked > 0) {
            message(player, TextFormatting.GREEN, "hub_linked", linked, queue.tagCount());
        } else if (lastLinked != null) {
            report(player, WirelessLinker.Result.LINKED, lastLinked, target);
        }
    }

    private static void report(final EntityPlayer player, final WirelessLinker.Result result, final TileWirelessBase source,
            final TileWirelessBase target) {
        if (result == WirelessLinker.Result.LINKED) {
            final BlockPos pos = source.getPos();
            message(player, TextFormatting.GREEN, "connected", pos.getX(), pos.getY(), pos.getZ());
        } else {
            final ITextComponent text = WirelessLinker.describe(result, source, target);
            text.getStyle().setColor(TextFormatting.RED);
            player.sendStatusMessage(text, true);
        }
    }

    private static void switchMode(final NBTTagCompound tag, final Mode mode, final EntityPlayer player) {
        final List<Mode> modes = enabledModes();
        final Mode next = modes.get((modes.indexOf(mode) + 1) % modes.size());
        if (next == Mode.SIMPLE) {
            tag.removeTag(MODE);
        } else {
            tag.setString(MODE, next.id());
        }
        announceMode(tag, player);
    }

    private static void toggleLinking(final NBTTagCompound tag, final EntityPlayer player) {
        if (tag.getBoolean(LINKING)) {
            tag.removeTag(LINKING);
        } else {
            tag.setBoolean(LINKING, true);
        }
        announceMode(tag, player);
    }

    private static void announceMode(final NBTTagCompound tag, final EntityPlayer player) {
        message(player, TextFormatting.GREEN, "mode", new TextComponentTranslation(modeName(tag)));
    }

    private static void clear(final NBTTagCompound tag, final Mode mode, final EntityPlayer player) {
        if (mode == Mode.QUEUE) {
            tag.removeTag(QUEUE);
            tag.removeTag(QUEUE_DIM);
            message(player, TextFormatting.GREEN, "queue_cleared");
        } else {
            tag.removeTag(LOCATION);
            message(player, TextFormatting.GREEN, "cleared");
        }
    }

    private static List<Mode> enabledModes() {
        final AE2StuffConfig config = AE2StuffConfig.instance();
        final List<Mode> modes = new ArrayList<>();
        modes.add(Mode.SIMPLE);
        if (config.isWirelessKitQueueModeEnabled()) {
            modes.add(Mode.QUEUE);
        }
        if (config.isWirelessKitManagerModeEnabled()) {
            modes.add(Mode.MANAGER);
        }
        return modes;
    }

    private static Mode modeOf(final NBTTagCompound tag) {
        final String id = tag.getString(MODE);
        for (final Mode mode : enabledModes()) {
            if (mode.id().equals(id)) {
                return mode;
            }
        }
        return Mode.SIMPLE;
    }

    private static String modeName(final NBTTagCompound tag) {
        return switch (modeOf(tag)) {
            case SIMPLE -> "tooltip.ae2stuff.wireless_kit.mode.simple";
            case QUEUE -> tag.getBoolean(LINKING) ? "tooltip.ae2stuff.wireless_kit.mode.queue_linking"
                    : "tooltip.ae2stuff.wireless_kit.mode.queue_adding";
            case MANAGER -> "tooltip.ae2stuff.wireless_kit.mode.manager";
        };
    }

    private static boolean hasBinding(final NBTTagCompound tag) {
        return tag.getCompoundTag(LOCATION).hasKey("x", Constants.NBT.TAG_INT);
    }

    /**
     * The kit's tag, with an Advanced Wireless Setup Kit's queue moved over to queue mode.
     */
    private static NBTTagCompound data(final ItemStack stack) {
        final NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        migrateLegacy(tag);
        return tag;
    }

    /**
     * A copy to read from, which leaves the stack alone.
     */
    private static NBTTagCompound view(final ItemStack stack) {
        final NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound().copy() : new NBTTagCompound();
        migrateLegacy(tag);
        return tag;
    }

    private static void migrateLegacy(final NBTTagCompound tag) {
        if (!tag.hasKey("mode", Constants.NBT.TAG_INT) && !tag.hasKey(LOCATION, Constants.NBT.TAG_LIST)) {
            return;
        }

        final NBTTagList queue = tag.getTagList(LOCATION, Constants.NBT.TAG_COMPOUND);
        if (queue.tagCount() > 0) {
            tag.setTag(QUEUE, queue);
            tag.setInteger(QUEUE_DIM, tag.getInteger("dim"));
        }
        if (tag.getInteger("mode") == 1) {
            tag.setBoolean(LINKING, true);
        }
        // The old kit left an empty compound under "loc" when its queue was empty
        tag.removeTag(LOCATION);
        tag.removeTag("dim");
        tag.removeTag("mode");
        tag.setString(MODE, Mode.QUEUE.id());
    }

    private static void save(final ItemStack stack, final NBTTagCompound tag) {
        stack.setTagCompound(tag.isEmpty() ? null : tag);
    }

    private static NBTTagCompound posTag(final BlockPos pos) {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", pos.getX());
        tag.setInteger("y", pos.getY());
        tag.setInteger("z", pos.getZ());
        return tag;
    }

    private static BlockPos posOf(final NBTTagCompound tag) {
        return new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
    }

    private static int countIn(final NBTTagList queue, final BlockPos pos) {
        int count = 0;
        for (int i = 0; i < queue.tagCount(); i++) {
            if (posOf(queue.getCompoundTagAt(i)).equals(pos)) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private static TileWirelessBase wirelessAt(final World world, final BlockPos pos) {
        final TileEntity tile = world.isBlockLoaded(pos) ? world.getTileEntity(pos) : null;
        return tile instanceof TileWirelessBase wireless ? wireless : null;
    }

    static void message(final EntityPlayer player, final TextFormatting color, final String key, final Object... args) {
        final ITextComponent text = new TextComponentTranslation("chat.ae2stuff.wireless." + key, args);
        text.getStyle().setColor(color);
        player.sendStatusMessage(text, true);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
            final ITooltipFlag advancedTooltips) {
        final NBTTagCompound tag = view(stack);
        final String key = KitKeyHandler.keyName();

        lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.mode", I18n.format(modeName(tag))));

        switch (modeOf(tag)) {
            case SIMPLE -> {
                if (hasBinding(tag)) {
                    final BlockPos bound = posOf(tag.getCompoundTag(LOCATION));
                    lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.bound", bound.getX(), bound.getY(), bound.getZ()));
                    lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.next"));
                    lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.clear"));
                } else {
                    lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.empty"));
                }
            }
            case QUEUE -> {
                final boolean linking = tag.getBoolean(LINKING);
                final NBTTagList queue = tag.getTagList(QUEUE, Constants.NBT.TAG_COMPOUND);
                if (queue.tagCount() == 0) {
                    lines.add(I18n.format(linking ? "tooltip.ae2stuff.wireless_kit.queue.empty_linking"
                            : "tooltip.ae2stuff.wireless_kit.queue.empty_adding"));
                } else {
                    lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.queue.size", queue.tagCount()));
                    for (int i = 0; i < Math.min(queue.tagCount(), TOOLTIP_ENTRIES); i++) {
                        final BlockPos pos = posOf(queue.getCompoundTagAt(i));
                        lines.add("  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                    }
                    if (queue.tagCount() > TOOLTIP_ENTRIES) {
                        lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.queue.more", queue.tagCount() - TOOLTIP_ENTRIES));
                    }
                }
                lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.queue.toggle"));
                lines.add(I18n.format(linking ? "tooltip.ae2stuff.wireless_kit.queue.hub_linking"
                        : "tooltip.ae2stuff.wireless_kit.queue.hub_adding", key));
                lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.queue.clear", key));
            }
            case MANAGER -> {
                lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.manager.networks", KitNetworks.read(tag).size()));
                lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.manager.add"));
                lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.manager.open"));
            }
        }

        if (enabledModes().size() > 1) {
            lines.add(I18n.format("tooltip.ae2stuff.wireless_kit.switch", key));
        }
    }
}
