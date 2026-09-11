/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.client;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import ae2stuff.Tags;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import appeng.api.util.AEColor;

/**
 * Looking at a connector or a hub draws a line to every end it is paired with, in the connector's colour.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID, value = Side.CLIENT)
public final class WirelessOverlay {

    private static final float LINE_WIDTH = 4;

    private WirelessOverlay() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(final RenderWorldLastEvent event) {
        final Minecraft mc = Minecraft.getMinecraft();
        final RayTraceResult hit = mc.objectMouseOver;
        final Entity viewer = mc.getRenderViewEntity();
        if (mc.world == null || viewer == null || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK
                || !(mc.world.getTileEntity(hit.getBlockPos()) instanceof TileWirelessBase tile)
                || tile.getLinkTargets().isEmpty()) {
            return;
        }

        final float partial = event.getPartialTicks();
        GlStateManager.pushMatrix();
        GlStateManager.translate(-(viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partial),
                -(viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partial),
                -(viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partial));
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.glLineWidth(LINE_WIDTH);

        final Tessellator tessellator = Tessellator.getInstance();
        final BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        final BlockPos from = tile.getPos();
        for (final BlockPos to : tile.getLinkTargets()) {
            final int rgb = colorOfLink(mc, tile, to).mediumVariant;
            final int r = rgb >> 16 & 0xFF;
            final int g = rgb >> 8 & 0xFF;
            final int b = rgb & 0xFF;
            buffer.pos(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5).color(r, g, b, 0xFF).endVertex();
            buffer.pos(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5).color(r, g, b, 0xFF).endVertex();
        }
        tessellator.draw();

        GlStateManager.glLineWidth(1);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static AEColor colorOfLink(final Minecraft mc, final TileWirelessBase tile, final BlockPos to) {
        if (tile instanceof TileWirelessHub && mc.world.getTileEntity(to) instanceof TileWirelessConnector connector) {
            return connector.getColor();
        }
        return tile.getColor();
    }
}
