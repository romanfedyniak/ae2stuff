/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import ae2stuff.container.ManagerLinking.Outcome;
import ae2stuff.item.ItemWirelessKit;
import ae2stuff.item.KitNetworks;
import ae2stuff.item.KitSettings;
import ae2stuff.item.WirelessLinker;
import ae2stuff.network.ModNetwork;
import ae2stuff.network.PacketKitDevices;
import ae2stuff.network.PacketKitEdit;
import ae2stuff.network.PacketKitReturned;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import appeng.api.util.AEColor;
import appeng.container.AEBaseContainer;
import appeng.util.Platform;

/**
 * The kit's manager window. It holds nothing; the server sends it the devices of the kit's networks, and it asks the
 * server to link, unlink, rename and rearrange them.
 */
public final class ContainerWirelessKit extends AEBaseContainer {

    private static final int REFRESH_TICKS = 20;

    private final EnumHand hand;
    private int ticksToRefresh;
    @Nullable
    private KitManagerData lastSent;

    public ContainerWirelessKit(final InventoryPlayer ip, final EnumHand hand) {
        super(ip, null, null);
        this.hand = hand;
        if (Platform.isServer()) {
            KitNetworks.clean(this.kit(), ip.player.world);
        }
    }

    private ItemStack kit() {
        return this.getPlayerInv().player.getHeldItem(this.hand);
    }

    private EntityPlayer player() {
        return this.getPlayerInv().player;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!Platform.isServer()) {
            return;
        }

        if (!(this.kit().getItem() instanceof ItemWirelessKit)) {
            this.setValidContainer(false);
            return;
        }
        if (--this.ticksToRefresh <= 0) {
            this.ticksToRefresh = REFRESH_TICKS;
            this.send(false);
        }
    }

    private void send(final boolean force) {
        final KitManagerData data = KitManagerData.collect(this.kit().getTagCompound(), this.player().world);
        if (force || !data.equals(this.lastSent)) {
            this.lastSent = data;
            ModNetwork.CHANNEL.sendTo(new PacketKitDevices(data), (EntityPlayerMP) this.player());
        }
    }

    /**
     * Links the sources to the targets in pairs from the top, a hub taking sources until it is full. A group row
     * stands for its devices that are free to link.
     */
    public void link(final List<KitSelection> sourceRows, final List<KitSelection> targetRows) {
        final EntityPlayer player = this.player();
        final List<Member> sources = this.expand(sourceRows, true);
        final List<Member> targets = this.expand(targetRows, true);
        final Set<Integer> takenTargets = new HashSet<>();
        final Set<KitEntry> returned = new LinkedHashSet<>();
        final int[] linked = new int[1];
        final int[] failed = new int[1];

        ManagerLinking.run(sources.size(), targets.size(), new ManagerLinking.Attempt() {
            @Override
            public boolean hasRoom(final int target) {
                return targets.get(target).tile instanceof TileWirelessHub hub ? hub.hasRoom() : !takenTargets.contains(target);
            }

            @Override
            public Outcome link(final int s, final int t) {
                final TileWirelessBase source = sources.get(s).tile;
                final TileWirelessBase target = targets.get(t).tile;
                final WirelessLinker.Result result = WirelessLinker.link(source, target, player);
                if (result == WirelessLinker.Result.LINKED) {
                    linked[0]++;
                    returned.add(sources.get(s).row);
                    returned.add(targets.get(t).row);
                    if (!(target instanceof TileWirelessHub)) {
                        takenTargets.add(t);
                    }
                    return Outcome.LINKED;
                }

                failed[0]++;
                player.sendMessage(failure(source, WirelessLinker.describe(result, source, target), target));
                return switch (result) {
                    case HUB_FULL -> target instanceof TileWirelessHub ? Outcome.SKIP_TARGET : Outcome.SKIP_SOURCE;
                    case NOT_ALLOWED -> source.canBeChangedBy(player) ? Outcome.SKIP_TARGET : Outcome.SKIP_SOURCE;
                    default -> Outcome.SKIP_SOURCE;
                };
            }
        });

        this.finish(returned, "manager.linked", linked[0], failed[0]);
    }

    /**
     * Takes down every link of the given rows' devices, at both ends.
     */
    public void unlink(final List<KitSelection> rows) {
        final EntityPlayer player = this.player();
        final Set<KitEntry> returned = new LinkedHashSet<>();
        int unlinked = 0;
        int failed = 0;
        for (final Member member : this.expand(rows, false)) {
            if (member.tile.canBeChangedBy(player)) {
                member.tile.unpairAll();
                returned.add(member.row);
                unlinked++;
            } else {
                failed++;
                player.sendMessage(failure(member.tile, new TextComponentTranslation("chat.ae2stuff.wireless.security.player"), null));
            }
        }

        this.finish(returned, "manager.unlinked", unlinked, failed);
    }

    /**
     * Paints the rows' devices.
     */
    public void recolor(final List<KitSelection> rows, final int color) {
        final EntityPlayer player = this.player();
        int painted = 0;
        int failed = 0;
        for (final Member member : this.expand(rows, false)) {
            if (!member.tile.canBeChangedBy(player)) {
                failed++;
                player.sendMessage(failure(member.tile, new TextComponentTranslation("chat.ae2stuff.wireless.security.player"), null));
                continue;
            }

            final AEColor paint = AEColor.values()[Math.max(0, Math.min(color, AEColor.values().length - 1))];
            if (member.tile.recolourBlock(EnumFacing.UP, paint, player)) {
                painted++;
            }
        }

        final ITextComponent text = new TextComponentTranslation("chat.ae2stuff.wireless.manager.recoloured", painted, failed);
        text.getStyle().setColor(failed == 0 ? TextFormatting.GREEN : TextFormatting.GOLD);
        player.sendStatusMessage(text, true);
        this.send(true);
    }

    public void edit(final PacketKitEdit.Action action, @Nullable final KitEntry entry, final int value, final String text) {
        final ItemStack kit = this.kit();
        final NBTTagCompound tag = kit.hasTagCompound() ? kit.getTagCompound() : new NBTTagCompound();
        final int dimension = this.player().world.provider.getDimension();

        switch (action) {
            case GROUPING -> KitSettings.setGrouping(tag,
                    value >= 0 && value < KitSettings.Grouping.values().length ? KitSettings.Grouping.values()[value] : KitSettings.Grouping.SINGLE);
            case HIDE_LINKED -> KitSettings.setHideLinked(tag, value != 0);
            case PIN, UNPIN -> {
                if (entry != null) {
                    KitSettings.setPinned(tag, entry, dimension, action == PacketKitEdit.Action.PIN);
                }
            }
            case RENAME -> {
                if (entry != null && entry.isGroup()) {
                    KitSettings.rename(tag, entry, dimension, text.trim());
                } else if (entry != null) {
                    this.renameDevice(entry.pos, text.trim());
                }
            }
            case FORGET -> {
                if (entry != null && entry.kind == KitEntry.Kind.NETWORK) {
                    KitNetworks.forget(tag, entry.pos, dimension);
                }
            }
        }

        kit.setTagCompound(tag.isEmpty() ? null : tag);
        this.send(true);
    }

    private void renameDevice(final BlockPos pos, final String name) {
        final EntityPlayer player = this.player();
        final TileWirelessBase tile = this.resolve(pos);
        if (tile == null) {
            return;
        }
        if (!tile.canBeChangedBy(player)) {
            final ITextComponent text = new TextComponentTranslation("chat.ae2stuff.wireless.security.player");
            text.getStyle().setColor(TextFormatting.RED);
            player.sendStatusMessage(text, true);
            return;
        }
        tile.setCustomName(name.isEmpty() ? null : name);
        tile.saveChanges();
    }

    private void finish(final Set<KitEntry> returned, final String summary, final int done, final int failed) {
        final EntityPlayer player = this.player();
        final ITextComponent text = new TextComponentTranslation("chat.ae2stuff.wireless." + summary, done, failed);
        text.getStyle().setColor(failed == 0 ? TextFormatting.GREEN : TextFormatting.GOLD);
        player.sendStatusMessage(text, true);

        KitNetworks.clean(this.kit(), player.world);
        this.send(true);
        ModNetwork.CHANNEL.sendTo(new PacketKitReturned(new ArrayList<>(returned)), (EntityPlayerMP) player);
    }

    private static ITextComponent failure(final TileWirelessBase source, final ITextComponent reason, @Nullable final TileWirelessBase target) {
        final BlockPos from = source.getPos();
        final ITextComponent text = target == null
                ? new TextComponentTranslation("chat.ae2stuff.wireless.manager.unlink_failure", from.getX(), from.getY(), from.getZ(), reason)
                : new TextComponentTranslation("chat.ae2stuff.wireless.manager.link_failure", from.getX(), from.getY(), from.getZ(),
                        target.getPos().getX(), target.getPos().getY(), target.getPos().getZ(), reason);
        text.getStyle().setColor(TextFormatting.RED);
        return text;
    }

    /**
     * A device standing in for the row it was put forward by.
     */
    private static final class Member {

        private final TileWirelessBase tile;
        private final KitEntry row;

        private Member(final TileWirelessBase tile, final KitEntry row) {
            this.tile = tile;
            this.row = row;
        }
    }

    /**
     * The devices behind the rows, each once. For Link a group gives only its connectors that are not paired and its
     * hubs with room; a device put forward by itself is taken as it is.
     */
    private List<Member> expand(final List<KitSelection> rows, final boolean forLink) {
        final List<Member> members = new ArrayList<>();
        if (this.lastSent == null) {
            return members;
        }

        final Map<BlockPos, KitManagerData.Device> shown = new HashMap<>();
        for (final KitManagerData.Device device : this.lastSent.devices) {
            shown.put(device.pos, device);
        }

        final Set<BlockPos> taken = new HashSet<>();
        for (final KitSelection row : rows) {
            if (!row.entry.isGroup()) {
                final TileWirelessBase tile = shown.containsKey(row.entry.pos) ? this.resolve(row.entry.pos) : null;
                if (tile != null && taken.add(tile.getPos())) {
                    members.add(new Member(tile, row.entry));
                }
                continue;
            }

            final int network = this.lastSent.networks.indexOf(row.entry.pos);
            final List<KitManagerData.Device> devices = new ArrayList<>();
            for (final KitManagerData.Device device : this.lastSent.devices) {
                final boolean inGroup = switch (row.entry.kind) {
                    case COLOR -> device.color == row.entry.color;
                    case STATE -> device.live == (row.entry.color == 1);
                    default -> true;
                };
                if (device.network == network && inGroup && (device.hub ? row.hubs : row.connectors)) {
                    devices.add(device);
                }
            }
            devices.sort(Comparator.comparing((KitManagerData.Device device) -> device.name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(device -> device.pos));

            for (final KitManagerData.Device device : devices) {
                final TileWirelessBase tile = this.resolve(device.pos);
                if (tile == null || forLink && (tile instanceof TileWirelessConnector connector && connector.getTarget() != null
                        || tile instanceof TileWirelessHub hub && !hub.hasRoom())) {
                    continue;
                }
                if (taken.add(tile.getPos())) {
                    members.add(new Member(tile, row.entry));
                }
            }
        }
        return members;
    }

    @Nullable
    private TileWirelessBase resolve(final BlockPos pos) {
        final World world = this.player().world;
        if (!world.isBlockLoaded(pos)) {
            return null;
        }
        final TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileWirelessBase wireless ? wireless : null;
    }
}
