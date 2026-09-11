/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import ae2stuff.container.ManagerLinking.Outcome;
import ae2stuff.item.ItemWirelessKit;
import ae2stuff.item.KitNetworks;
import ae2stuff.item.WirelessLinker;
import ae2stuff.network.ModNetwork;
import ae2stuff.network.PacketKitDevices;
import ae2stuff.network.PacketKitReturned;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessHub;
import appeng.container.AEBaseContainer;
import appeng.util.Platform;

/**
 * The kit's manager window. It holds nothing; the server sends it the devices of the kit's networks, and it asks the
 * server to link and unlink them.
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
        final KitManagerData data = KitManagerData.collect(KitNetworks.read(this.kit().getTagCompound()), this.player().world);
        if (force || !data.equals(this.lastSent)) {
            this.lastSent = data;
            ModNetwork.CHANNEL.sendTo(new PacketKitDevices(data), (EntityPlayerMP) this.player());
        }
    }

    /**
     * Links the sources to the targets in pairs from the top, a hub taking sources until it is full.
     */
    public void link(final List<BlockPos> sourcePositions, final List<BlockPos> targetPositions) {
        final EntityPlayer player = this.player();
        final List<TileWirelessBase> sources = this.resolve(sourcePositions);
        final List<TileWirelessBase> targets = this.resolve(targetPositions);
        final Set<Integer> takenTargets = new HashSet<>();
        final List<BlockPos> returned = new ArrayList<>();
        final int[] linked = new int[1];
        final int[] failed = new int[1];

        ManagerLinking.run(sources.size(), targets.size(), new ManagerLinking.Attempt() {
            @Override
            public boolean hasRoom(final int target) {
                return targets.get(target) instanceof TileWirelessHub hub ? hub.hasRoom() : !takenTargets.contains(target);
            }

            @Override
            public Outcome link(final int s, final int t) {
                final TileWirelessBase source = sources.get(s);
                final TileWirelessBase target = targets.get(t);
                final WirelessLinker.Result result = WirelessLinker.link(source, target, player);
                if (result == WirelessLinker.Result.LINKED) {
                    linked[0]++;
                    returned.add(source.getPos());
                    returned.add(target.getPos());
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
     * Takes down every link of the given devices, at both ends.
     */
    public void unlink(final List<BlockPos> positions) {
        final EntityPlayer player = this.player();
        final List<BlockPos> returned = new ArrayList<>();
        int failed = 0;
        for (final TileWirelessBase tile : this.resolve(positions)) {
            if (tile.canBeChangedBy(player)) {
                tile.unpairAll();
                returned.add(tile.getPos());
            } else {
                failed++;
                player.sendMessage(failure(tile, new TextComponentTranslation("chat.ae2stuff.wireless.security.player"), null));
            }
        }

        this.finish(returned, "manager.unlinked", returned.size(), failed);
    }

    private void finish(final List<BlockPos> returned, final String summary, final int done, final int failed) {
        final EntityPlayer player = this.player();
        final ITextComponent text = new TextComponentTranslation("chat.ae2stuff.wireless." + summary, done, failed);
        text.getStyle().setColor(failed == 0 ? TextFormatting.GREEN : TextFormatting.GOLD);
        player.sendStatusMessage(text, true);

        KitNetworks.clean(this.kit(), player.world);
        this.send(true);
        ModNetwork.CHANNEL.sendTo(new PacketKitReturned(returned), (EntityPlayerMP) player);
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
     * Only devices the window was shown can be acted on; anything else a client names is ignored.
     */
    private List<TileWirelessBase> resolve(final List<BlockPos> positions) {
        final List<TileWirelessBase> tiles = new ArrayList<>();
        if (this.lastSent == null) {
            return tiles;
        }

        final Set<BlockPos> shown = new HashSet<>();
        for (final KitManagerData.Device device : this.lastSent.devices) {
            shown.add(device.pos);
        }

        final World world = this.player().world;
        for (final BlockPos pos : positions) {
            if (shown.contains(pos) && world.isBlockLoaded(pos)) {
                final TileEntity tile = world.getTileEntity(pos);
                if (tile instanceof TileWirelessBase wireless && !tiles.contains(wireless)) {
                    tiles.add(wireless);
                }
            }
        }
        return tiles;
    }
}
