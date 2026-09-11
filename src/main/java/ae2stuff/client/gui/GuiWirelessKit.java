/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.input.Mouse;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import ae2stuff.block.BlockWireless;
import ae2stuff.container.ContainerWirelessKit;
import ae2stuff.container.KitManagerData;
import ae2stuff.core.Registration;
import ae2stuff.network.ModNetwork;
import ae2stuff.network.PacketKitAction;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiSmallButton;
import appeng.client.render.BlockPosHighlighter;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;

/**
 * The kit's manager: every connector and hub of the listed networks, in three columns - the list, what to link, and
 * what to link it with. Devices are dragged between the columns.
 */
public final class GuiWirelessKit extends AEBaseGui {

    private static final int MAX_WIDTH = 400;
    private static final int MARGIN = 8;
    private static final int TOOLBAR = 16;
    private static final int HEADER = 12;
    private static final int TOP = MARGIN + TOOLBAR + 4 + HEADER;
    private static final int ROW = 22;
    private static final int GAP = 6;
    private static final int SCROLLBAR = 12;
    private static final int MIN_ROWS = 4;
    private static final int BUTTON_WIDTH = 64;
    /** How far the pointer moves with the button down before a press becomes a drag. */
    private static final int DRAG_THRESHOLD = 3;
    /** As long as AE2UD highlights a machine it located. */
    private static final int HIGHLIGHT_MILLISECONDS = 15_000;

    private static final int LIST = 0;
    private static final int SOURCES = 1;
    private static final int TARGETS = 2;
    private static final String[] HEADERS = { "gui.ae2stuff.wireless_kit.list", "gui.ae2stuff.wireless_kit.sources",
            "gui.ae2stuff.wireless_kit.targets" };

    private static final int TEXT = 0x404040;
    private static final int LIVE = 0x1E7A1E;
    private static final int WAITING = 0x9A7300;
    private static final int UNLINKED = 0x808080;
    private static final int HOVER = 0x30FFFFFF;
    private static final int DRAGGED = 0x60000000;
    private static final int DROP_LINE = 0xFF3A6FD8;
    private static final int FLOATING = 0xE0C6C6C6;

    private final GuiScrollbar[] scrollbars = { new GuiScrollbar(), new GuiScrollbar(), new GuiScrollbar() };
    private final List<List<BlockPos>> columns = new ArrayList<>();
    private final Map<BlockPos, KitManagerData.Device> devices = new HashMap<>();
    private List<BlockPos> networks = Collections.emptyList();
    private boolean received;
    private int rows = MIN_ROWS;
    private int columnWidth;
    @Nullable
    private GuiSmallButton link;
    @Nullable
    private GuiSmallButton unlink;

    @Nullable
    private Hit pressed;
    @Nullable
    private BlockPos pressedPos;
    private int pressX;
    private int pressY;
    private int grabX;
    private int grabY;
    private boolean dragging;

    public GuiWirelessKit(final ContainerWirelessKit container) {
        super(container);
        for (int i = 0; i < HEADERS.length; i++) {
            this.columns.add(new ArrayList<>());
        }
    }

    public static void receive(final KitManagerData data) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiWirelessKit gui) {
            gui.apply(data);
        }
    }

    public static void returned(final List<BlockPos> positions) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiWirelessKit gui) {
            gui.column(SOURCES).removeAll(positions);
            gui.column(TARGETS).removeAll(positions);
            gui.changed();
        }
    }

    private void apply(final KitManagerData data) {
        this.received = true;
        this.devices.clear();
        for (final KitManagerData.Device device : data.devices) {
            this.devices.put(device.pos, device);
        }
        this.networks = data.networks;
        this.column(SOURCES).removeIf(pos -> !this.devices.containsKey(pos));
        this.column(TARGETS).removeIf(pos -> !this.devices.containsKey(pos));
        this.changed();
    }

    /**
     * Rebuilds the list from what is in neither of the other columns, ordered by network, name and position.
     */
    private void changed() {
        final List<BlockPos> list = this.column(LIST);
        list.clear();
        for (final BlockPos pos : this.devices.keySet()) {
            if (!this.column(SOURCES).contains(pos) && !this.column(TARGETS).contains(pos)) {
                list.add(pos);
            }
        }
        list.sort(Comparator.comparingInt((BlockPos pos) -> this.devices.get(pos).network)
                .thenComparing(pos -> this.displayName(this.devices.get(pos)), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.naturalOrder()));

        // A refresh re-sorts the list; the row being dragged is followed by its block, and dropped only if it is gone
        if (this.pressed != null) {
            final int index = this.column(this.pressed.column).indexOf(this.pressedPos);
            if (index < 0) {
                this.pressed = null;
                this.dragging = false;
            } else {
                this.pressed = new Hit(this.pressed.column, index);
            }
        }

        this.updateScrollbars();
        this.updateButtons();
    }

    private List<BlockPos> column(final int column) {
        return this.columns.get(column);
    }

    @Override
    public void initGui() {
        this.xSize = Math.min(MAX_WIDTH, this.width - 2 * MARGIN);
        this.rows = Math.max(MIN_ROWS, (this.height - 3 * MARGIN - TOP) / ROW);
        this.ySize = TOP + this.rows * ROW + MARGIN;
        super.initGui();

        this.columnWidth = (this.xSize - 2 * MARGIN - 2 * GAP - 3 * (SCROLLBAR + 2)) / 3;
        for (int i = 0; i < this.scrollbars.length; i++) {
            this.scrollbars[i].setLeft(this.columnX(i) + this.columnWidth + 2).setTop(TOP).setHeight(this.rows * ROW);
        }

        this.unlink = new GuiSmallButton(0, this.guiLeft + this.xSize - MARGIN - BUTTON_WIDTH, this.guiTop + MARGIN, BUTTON_WIDTH,
                TOOLBAR, I18n.format("gui.ae2stuff.wireless_kit.unlink")).setTooltip(I18n.format("gui.ae2stuff.wireless_kit.unlink.tooltip"));
        this.link = new GuiSmallButton(1, this.unlink.x - 4 - BUTTON_WIDTH, this.guiTop + MARGIN, BUTTON_WIDTH, TOOLBAR,
                I18n.format("gui.ae2stuff.wireless_kit.link")).setTooltip(I18n.format("gui.ae2stuff.wireless_kit.link.tooltip"));
        this.buttonList.add(this.link);
        this.buttonList.add(this.unlink);

        this.pressed = null;
        this.dragging = false;
        this.updateScrollbars();
        this.updateButtons();
    }

    private int columnX(final int column) {
        return MARGIN + column * (this.columnWidth + SCROLLBAR + 2 + GAP);
    }

    private void updateScrollbars() {
        for (int i = 0; i < this.scrollbars.length; i++) {
            this.scrollbars[i].setRange(0, Math.max(0, this.column(i).size() - this.rows), 1);
        }
    }

    private void updateButtons() {
        if (this.link != null && this.unlink != null) {
            this.link.enabled = !this.column(SOURCES).isEmpty() && !this.column(TARGETS).isEmpty();
            this.unlink.enabled = !this.column(SOURCES).isEmpty();
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.drawPanel(offsetX, offsetY, this.xSize, this.ySize);
        for (int i = 0; i < this.columns.size(); i++) {
            final int x = offsetX + this.columnX(i);
            drawWell(x - 1, offsetY + TOP - 1, this.columnWidth + 2, this.rows * ROW + 2);
            drawWell(x + this.columnWidth + 1, offsetY + TOP - 1, SCROLLBAR + 2, this.rows * ROW + 2);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.ae2stuff.wireless_kit.title"), MARGIN, MARGIN + 4, TEXT);

        final int x = mouseX - offsetX;
        final int y = mouseY - offsetY;
        final Hit hovered = this.dragging ? null : this.hitAt(x, y);
        for (int i = 0; i < this.columns.size(); i++) {
            final int left = this.columnX(i);
            final List<BlockPos> column = this.column(i);
            this.fontRenderer.drawString(this.trim(I18n.format(HEADERS[i], column.size()), this.columnWidth + SCROLLBAR), left,
                    TOP - HEADER + 2, TEXT);
            this.scrollbars[i].draw(this);

            final int first = this.scrollbars[i].getCurrentScroll();
            for (int row = 0; row < this.rows && first + row < column.size(); row++) {
                final KitManagerData.Device device = this.devices.get(column.get(first + row));
                if (device == null) {
                    continue;
                }
                final int top = TOP + row * ROW;
                this.drawRow(device, left, top, hovered != null && hovered.column == i && hovered.index == first + row);
                if (this.dragging && this.pressed != null && this.pressed.column == i && this.pressed.index == first + row) {
                    drawRect(left, top, left + this.columnWidth, top + ROW, DRAGGED);
                }
            }
        }

        if (this.dragging) {
            this.drawDropLine(x, y);
        }

        if (this.devices.isEmpty()) {
            final String hint = I18n.format(this.received ? "gui.ae2stuff.wireless_kit.empty" : "gui.ae2stuff.wireless_kit.loading");
            this.fontRenderer.drawSplitString(hint, this.columnX(LIST) + 4, TOP + 4, this.columnWidth - 8, UNLINKED);
        }
    }

    /**
     * Where the dragged device would land. The list keeps its own order, so it only lights up as a whole.
     */
    private void drawDropLine(final int x, final int y) {
        final int column = this.dropColumnAt(x, y);
        if (column < 0) {
            return;
        }

        final int left = this.columnX(column);
        if (column == LIST) {
            final int bottom = TOP + this.rows * ROW;
            drawRect(left, TOP, left + this.columnWidth, TOP + 1, DROP_LINE);
            drawRect(left, bottom - 1, left + this.columnWidth, bottom, DROP_LINE);
            drawRect(left, TOP, left + 1, bottom, DROP_LINE);
            drawRect(left + this.columnWidth - 1, TOP, left + this.columnWidth, bottom, DROP_LINE);
            return;
        }

        final int row = this.insertionIndex(column, y) - this.scrollbars[column].getCurrentScroll();
        if (row >= 0 && row <= this.rows) {
            final int lineY = TOP + row * ROW;
            drawRect(left, lineY - 1, left + this.columnWidth, lineY + 1, DROP_LINE);
        }
    }

    private void drawRow(final KitManagerData.Device device, final int x, final int y, final boolean hovered) {
        if (hovered) {
            drawRect(x, y, x + this.columnWidth, y + ROW, HOVER);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        final ItemStack icon = this.iconOf(device);
        if (!icon.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.renderItemAndEffectIntoGUI(icon, x + 2, y + 3);
            RenderHelper.disableStandardItemLighting();
        }

        final int textX = x + 21;
        final int textWidth = this.columnWidth - 22;
        this.fontRenderer.drawString(this.trim(this.displayName(device), textWidth), textX, y + 2, TEXT);

        final String channels = this.channelsOf(device);
        int stateWidth = textWidth;
        if (!channels.isEmpty()) {
            final int width = this.fontRenderer.getStringWidth(channels);
            this.fontRenderer.drawString(channels, x + this.columnWidth - 2 - width, y + 12, TEXT);
            stateWidth -= width + 4;
        }
        this.fontRenderer.drawString(this.trim(this.stateOf(device), stateWidth), textX, y + 12, this.stateColor(device));
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (this.dragging && this.pressed != null) {
            final KitManagerData.Device device = this.deviceAt(this.pressed);
            if (device != null) {
                final int x = mouseX - this.grabX;
                final int y = mouseY - this.grabY;
                GlStateManager.pushMatrix();
                GlStateManager.translate(0, 0, 300);
                drawRect(x, y, x + this.columnWidth, y + ROW, FLOATING);
                this.drawRow(device, x, y, false);
                GlStateManager.popMatrix();
            }
            return;
        }

        final Hit hit = this.hitAt(mouseX - this.guiLeft, mouseY - this.guiTop);
        final KitManagerData.Device device = hit == null ? null : this.deviceAt(hit);
        if (device != null) {
            this.drawTooltip(mouseX, mouseY, this.tooltipOf(device));
        }
    }

    private List<String> tooltipOf(final KitManagerData.Device device) {
        final List<String> lines = new ArrayList<>();
        lines.add(this.displayName(device));
        lines.add(TextFormatting.GRAY
                + I18n.format("gui.ae2stuff.wireless_kit.tooltip.position", device.pos.getX(), device.pos.getY(), device.pos.getZ()));
        if (device.network >= 0 && device.network < this.networks.size()) {
            final BlockPos anchor = this.networks.get(device.network);
            lines.add(TextFormatting.GRAY
                    + I18n.format("gui.ae2stuff.wireless_kit.tooltip.network", anchor.getX(), anchor.getY(), anchor.getZ()));
        }

        if (device.hub) {
            lines.add(I18n.format("waila.ae2stuff.wireless.links", device.links, device.maxLinks));
        } else if (device.target != null) {
            lines.add(I18n.format(device.live ? "waila.ae2stuff.wireless.linked" : "waila.ae2stuff.wireless.waiting",
                    device.target.getX(), device.target.getY(), device.target.getZ()));
        } else {
            lines.add(I18n.format("waila.ae2stuff.wireless.unlinked"));
        }

        if (device.live && AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS)) {
            lines.add(device.capacity < 0 ? I18n.format("waila.ae2stuff.wireless.channels", device.usedChannels)
                    : I18n.format("waila.appliedenergistics2.Channels", device.usedChannels, device.capacity));
        }
        if (device.live) {
            lines.add(I18n.format("waila.ae2stuff.wireless.power", String.format("%.1f", device.power)));
        }
        if (colorOf(device) != AEColor.TRANSPARENT) {
            lines.add(I18n.format(colorOf(device).unlocalizedName));
        }

        lines.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2stuff.wireless_kit.hint.drag"));
        lines.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2stuff.wireless_kit.hint.highlight"));
        return lines;
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);

        final int x = mouseX - this.guiLeft;
        final int y = mouseY - this.guiTop;
        for (final GuiScrollbar scrollbar : this.scrollbars) {
            scrollbar.click(this, x, y);
        }

        final Hit hit = this.hitAt(x, y);
        if (hit == null) {
            return;
        }

        if (button == 0 && isCtrlKeyDown()) {
            this.highlight(this.column(hit.column).get(hit.index));
        } else if (button == 0) {
            this.pressed = hit;
            this.pressedPos = this.column(hit.column).get(hit.index);
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.grabX = x - this.columnX(hit.column);
            this.grabY = y - TOP - (hit.index - this.scrollbars[hit.column].getCurrentScroll()) * ROW;
            this.dragging = false;
        }
    }

    @Override
    protected void mouseClickMove(final int mouseX, final int mouseY, final int button, final long time) {
        super.mouseClickMove(mouseX, mouseY, button, time);

        if (this.pressed == null) {
            for (final GuiScrollbar scrollbar : this.scrollbars) {
                scrollbar.click(this, mouseX - this.guiLeft, mouseY - this.guiTop);
            }
        } else if (!this.dragging
                && (Math.abs(mouseX - this.pressX) > DRAG_THRESHOLD || Math.abs(mouseY - this.pressY) > DRAG_THRESHOLD)) {
            this.dragging = true;
        }
    }

    @Override
    protected void mouseReleased(final int mouseX, final int mouseY, final int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state != 0) {
            return;
        }

        if (this.pressed != null && this.dragging) {
            this.drop(this.pressed, mouseX - this.guiLeft, mouseY - this.guiTop);
        }
        this.pressed = null;
        this.dragging = false;
    }

    private void drop(final Hit from, final int x, final int y) {
        final int to = this.dropColumnAt(x, y);
        final List<BlockPos> source = this.column(from.column);
        if (to < 0 || from.index >= source.size() || to == LIST && from.column == LIST) {
            return;
        }

        int index = this.insertionIndex(to, y);
        if (to == from.column && index > from.index) {
            index--;
        }

        final BlockPos pos = source.remove(from.index);
        final List<BlockPos> target = this.column(to);
        target.add(Math.min(index, target.size()), pos);
        this.changed();
    }

    /**
     * The gap between rows nearest the pointer, as an index into the column.
     */
    private int insertionIndex(final int column, final int y) {
        final int boundary = Math.max(0, Math.min(this.rows, Math.floorDiv(y - TOP + ROW / 2, ROW)));
        return Math.min(this.scrollbars[column].getCurrentScroll() + boundary, this.column(column).size());
    }

    private void highlight(final BlockPos pos) {
        BlockPosHighlighter.hilightBlock(pos, System.currentTimeMillis() + HIGHLIGHT_MILLISECONDS, this.mc.world.provider.getDimension());
        BlockPosHighlighter.turnPlayerTowards(pos);
        this.mc.player.sendMessage(new TextComponentTranslation("chat.ae2stuff.wireless.manager.highlighted", pos.getX(), pos.getY(), pos.getZ()));
        // Nothing to look at while the window is in the way
        this.mc.player.closeScreen();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        final int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && !isShiftKeyDown()) {
            final int column = this.columnAt(Mouse.getEventX() * this.width / this.mc.displayWidth - this.guiLeft);
            if (column >= 0) {
                this.scrollbars[column].wheel(wheel);
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button == this.link) {
            ModNetwork.CHANNEL.sendToServer(new PacketKitAction(PacketKitAction.Action.LINK, new ArrayList<>(this.column(SOURCES)),
                    new ArrayList<>(this.column(TARGETS))));
        } else if (button == this.unlink) {
            ModNetwork.CHANNEL.sendToServer(new PacketKitAction(PacketKitAction.Action.UNLINK, new ArrayList<>(this.column(SOURCES)),
                    Collections.emptyList()));
        }
    }

    @Nullable
    private Hit hitAt(final int x, final int y) {
        if (y < TOP || y >= TOP + this.rows * ROW) {
            return null;
        }
        for (int i = 0; i < this.columns.size(); i++) {
            final int left = this.columnX(i);
            if (x >= left && x < left + this.columnWidth) {
                final int index = this.scrollbars[i].getCurrentScroll() + (y - TOP) / ROW;
                return index < this.column(i).size() ? new Hit(i, index) : null;
            }
        }
        return null;
    }

    @Nullable
    private KitManagerData.Device deviceAt(final Hit hit) {
        final List<BlockPos> column = this.column(hit.column);
        return hit.index < column.size() ? this.devices.get(column.get(hit.index)) : null;
    }

    private int columnAt(final int x) {
        for (int i = 0; i < this.columns.size(); i++) {
            final int left = this.columnX(i);
            if (x >= left && x < left + this.columnWidth + SCROLLBAR + 2) {
                return i;
            }
        }
        return -1;
    }

    private int dropColumnAt(final int x, final int y) {
        return y >= TOP - HEADER && y < TOP + this.rows * ROW + MARGIN ? this.columnAt(x) : -1;
    }

    private static AEColor colorOf(final KitManagerData.Device device) {
        return device.color >= 0 && device.color < AEColor.values().length ? AEColor.values()[device.color] : AEColor.TRANSPARENT;
    }

    private ItemStack iconOf(final KitManagerData.Device device) {
        final Block block = device.hub ? Registration.wirelessHub : Registration.wirelessConnector;
        return block == null ? ItemStack.EMPTY : new ItemStack(block, 1, BlockWireless.metaOf(colorOf(device)));
    }

    private String displayName(final KitManagerData.Device device) {
        return device.name.isEmpty() ? this.iconOf(device).getDisplayName() : device.name;
    }

    private String channelsOf(final KitManagerData.Device device) {
        if (!device.live || !AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS)) {
            return "";
        }
        return device.capacity < 0 ? String.valueOf(device.usedChannels) : device.usedChannels + "/" + device.capacity;
    }

    private String stateOf(final KitManagerData.Device device) {
        if (device.hub) {
            return I18n.format("gui.ae2stuff.wireless_kit.state.links", device.links, device.maxLinks);
        }
        if (device.target != null) {
            return "→ " + device.target.getX() + ", " + device.target.getY() + ", " + device.target.getZ();
        }
        return I18n.format("gui.ae2stuff.wireless_kit.state.unlinked");
    }

    private int stateColor(final KitManagerData.Device device) {
        if (device.hub) {
            return TEXT;
        }
        if (device.target != null) {
            return device.live ? LIVE : WAITING;
        }
        return UNLINKED;
    }

    private String trim(final String text, final int width) {
        if (width <= 0) {
            return "";
        }
        if (this.fontRenderer.getStringWidth(text) <= width) {
            return text;
        }
        return this.fontRenderer.trimStringToWidth(text, width - this.fontRenderer.getStringWidth("…")) + "…";
    }

    private static final class Hit {

        private final int column;
        private final int index;

        private Hit(final int column, final int index) {
            this.column = column;
            this.index = index;
        }
    }
}
