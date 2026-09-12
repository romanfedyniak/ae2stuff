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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;
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
import ae2stuff.container.KitEntry;
import ae2stuff.container.KitManagerData;
import ae2stuff.container.KitSelection;
import ae2stuff.core.Registration;
import ae2stuff.item.KitSettings;
import ae2stuff.network.ModNetwork;
import ae2stuff.network.PacketKitAction;
import ae2stuff.network.PacketKitEdit;
import appeng.api.AEApi;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiSmallButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.render.BlockPosHighlighter;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;

/**
 * The kit's manager: the connectors and hubs of the listed networks, one by one or in groups, in three columns - the
 * list, what to link, and what to link it with. Rows are dragged between the columns.
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
    private static final int GROUPING_WIDTH = 104;
    private static final int HIDE_WIDTH = 92;
    private static final int BOX = 8;
    /** How far the pointer moves with the button down before a press becomes a drag. */
    private static final int DRAG_THRESHOLD = 3;
    private static final long DOUBLE_CLICK_MILLIS = 300;
    /** As long as AE2UD highlights a machine it located. */
    private static final int HIGHLIGHT_MILLISECONDS = 15_000;

    private static final int LIST = 0;
    private static final int SOURCES = 1;
    private static final int TARGETS = 2;
    private static final String[] HEADERS = { "gui.ae2stuff.wireless_kit.list", "gui.ae2stuff.wireless_kit.sources",
            "gui.ae2stuff.wireless_kit.targets" };
    private static final String[] GROUPINGS = { "gui.ae2stuff.wireless_kit.grouping.single", "gui.ae2stuff.wireless_kit.grouping.color",
            "gui.ae2stuff.wireless_kit.grouping.network" };

    private static final int TEXT = 0x404040;
    private static final int LIVE = 0x1E7A1E;
    private static final int WAITING = 0x9A7300;
    private static final int UNLINKED = 0x808080;
    private static final int HOVER = 0x30FFFFFF;
    private static final int DRAGGED = 0x60000000;
    private static final int DROP_LINE = 0xFF3A6FD8;
    private static final int FLOATING = 0xE0C6C6C6;
    private static final int PINNED = 0xFFE0A020;
    private static final int BOX_BORDER = 0xFF555555;
    private static final int BOX_FILL = 0xFFDDDDDD;
    private static final int CHECKED = 0xFF3A6FD8;
    private static final int FORGET = 0xFFB02020;

    private enum Control {
        NONE,
        PIN,
        FORGET,
        CONNECTORS,
        HUBS
    }

    private final GuiScrollbar[] scrollbars = { new GuiScrollbar(), new GuiScrollbar(), new GuiScrollbar() };
    private final List<List<KitEntry>> columns = new ArrayList<>();
    private final Map<BlockPos, KitManagerData.Device> devices = new LinkedHashMap<>();
    private final Set<KitEntry> pins = new HashSet<>();
    private final Map<KitEntry, String> names = new HashMap<>();
    /** Whether a group row takes its connectors and its hubs along; both to begin with. */
    private final Map<KitEntry, boolean[]> includes = new HashMap<>();
    private List<BlockPos> networks = Collections.emptyList();
    @Nullable
    private KitSettings.Grouping grouping;
    private boolean hideLinked;
    private boolean received;
    private int rows = MIN_ROWS;
    private int columnWidth;
    @Nullable
    private GuiSmallButton link;
    @Nullable
    private GuiSmallButton unlink;
    @Nullable
    private GuiSmallButton groupingButton;
    @Nullable
    private GuiSmallButton hideButton;

    @Nullable
    private Hit pressed;
    @Nullable
    private KitEntry pressedEntry;
    private int pressX;
    private int pressY;
    private int grabX;
    private int grabY;
    private boolean dragging;

    @Nullable
    private KitEntry lastClicked;
    private long lastClickTime;

    @Nullable
    private MEGuiTextField renameField;
    @Nullable
    private KitEntry renaming;
    private int renameColumn;
    private int renameIndex;
    private int renameScroll;

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

    public static void returned(final List<KitEntry> entries) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiWirelessKit gui) {
            gui.column(SOURCES).removeAll(entries);
            gui.column(TARGETS).removeAll(entries);
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
        this.hideLinked = data.hideLinked;
        this.pins.clear();
        this.pins.addAll(data.pins);
        this.names.clear();
        this.names.putAll(data.names);

        if (this.grouping != data.grouping) {
            if (this.grouping != null) {
                this.column(SOURCES).clear();
                this.column(TARGETS).clear();
            }
            this.grouping = data.grouping;
        }

        this.updateToolbar();
        this.changed();
    }

    /**
     * Rebuilds the list for the grouping in force, pinned rows first and then by network, name and position.
     */
    private void changed() {
        this.column(SOURCES).removeIf(entry -> !this.exists(entry));
        this.column(TARGETS).removeIf(entry -> !this.exists(entry));

        final List<KitEntry> list = this.column(LIST);
        list.clear();
        for (final KitEntry entry : this.entriesForGrouping()) {
            if (!this.column(SOURCES).contains(entry) && !this.column(TARGETS).contains(entry)) {
                list.add(entry);
            }
        }
        list.sort(Comparator.comparing((KitEntry entry) -> !this.pins.contains(entry))
                .thenComparingInt(this::networkIndexOf)
                .thenComparing(this::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.pos)
                .thenComparingInt(entry -> entry.color));

        // A refresh re-sorts the list; the row being dragged is followed by its entry, and dropped only if it is gone
        if (this.pressed != null && this.pressedEntry != null) {
            final int index = this.column(this.pressed.column).indexOf(this.pressedEntry);
            if (index < 0) {
                this.pressed = null;
                this.dragging = false;
            } else {
                this.pressed = new Hit(this.pressed.column, index);
            }
        }
        if (this.renaming != null && !this.exists(this.renaming)) {
            this.cancelRename();
        }

        this.updateScrollbars();
        this.updateButtons();
    }

    private List<KitEntry> entriesForGrouping() {
        final List<KitEntry> entries = new ArrayList<>();
        switch (this.grouping == null ? KitSettings.Grouping.SINGLE : this.grouping) {
            case SINGLE -> {
                for (final KitManagerData.Device device : this.devices.values()) {
                    if (!this.hidden(device)) {
                        entries.add(KitEntry.device(device.pos));
                    }
                }
            }
            case COLOR -> {
                final Set<KitEntry> groups = new LinkedHashSet<>();
                for (final KitManagerData.Device device : this.devices.values()) {
                    if (device.network >= 0 && device.network < this.networks.size()) {
                        groups.add(KitEntry.color(this.networks.get(device.network), device.color));
                    }
                }
                for (final KitEntry group : groups) {
                    if (!this.allHidden(group)) {
                        entries.add(group);
                    }
                }
            }
            case NETWORK -> {
                for (final BlockPos anchor : this.networks) {
                    final KitEntry group = KitEntry.network(anchor);
                    if (!this.allHidden(group)) {
                        entries.add(group);
                    }
                }
            }
        }
        return entries;
    }

    private boolean hidden(final KitManagerData.Device device) {
        return this.hideLinked && (device.hub ? device.links >= device.maxLinks : device.live);
    }

    private boolean allHidden(final KitEntry group) {
        final List<KitManagerData.Device> members = this.membersOf(group);
        if (members.isEmpty()) {
            return false;
        }
        for (final KitManagerData.Device device : members) {
            if (!this.hidden(device)) {
                return false;
            }
        }
        return true;
    }

    private List<KitManagerData.Device> membersOf(final KitEntry entry) {
        if (!entry.isGroup()) {
            final KitManagerData.Device device = this.devices.get(entry.pos);
            return device == null ? Collections.emptyList() : Collections.singletonList(device);
        }

        final int network = this.networks.indexOf(entry.pos);
        if (network < 0) {
            return Collections.emptyList();
        }

        final List<KitManagerData.Device> members = new ArrayList<>();
        for (final KitManagerData.Device device : this.devices.values()) {
            if (device.network == network && (entry.kind != KitEntry.Kind.COLOR || device.color == entry.color)) {
                members.add(device);
            }
        }
        members.sort(Comparator.comparing((KitManagerData.Device device) -> this.displayName(device), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(device -> device.pos));
        return members;
    }

    private boolean exists(final KitEntry entry) {
        return switch (entry.kind) {
            case DEVICE -> this.devices.containsKey(entry.pos);
            case NETWORK -> this.networks.contains(entry.pos);
            case COLOR -> !this.membersOf(entry).isEmpty();
        };
    }

    private int networkIndexOf(final KitEntry entry) {
        if (entry.isGroup()) {
            return this.networks.indexOf(entry.pos);
        }
        final KitManagerData.Device device = this.devices.get(entry.pos);
        return device == null ? Integer.MAX_VALUE : device.network;
    }

    private List<KitEntry> column(final int column) {
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

        this.groupingButton = new GuiSmallButton(2, this.guiLeft + MARGIN, this.guiTop + MARGIN, GROUPING_WIDTH, TOOLBAR, "");
        this.hideButton = new GuiSmallButton(3, this.groupingButton.x + GROUPING_WIDTH + 4, this.guiTop + MARGIN, HIDE_WIDTH, TOOLBAR, "");
        this.unlink = new GuiSmallButton(0, this.guiLeft + this.xSize - MARGIN - BUTTON_WIDTH, this.guiTop + MARGIN, BUTTON_WIDTH,
                TOOLBAR, I18n.format("gui.ae2stuff.wireless_kit.unlink")).setTooltip(I18n.format("gui.ae2stuff.wireless_kit.unlink.tooltip"));
        this.link = new GuiSmallButton(1, this.unlink.x - 4 - BUTTON_WIDTH, this.guiTop + MARGIN, BUTTON_WIDTH, TOOLBAR,
                I18n.format("gui.ae2stuff.wireless_kit.link")).setTooltip(I18n.format("gui.ae2stuff.wireless_kit.link.tooltip"));
        this.buttonList.add(this.groupingButton);
        this.buttonList.add(this.hideButton);
        this.buttonList.add(this.link);
        this.buttonList.add(this.unlink);

        this.cancelRename();
        this.pressed = null;
        this.dragging = false;
        this.updateToolbar();
        this.updateScrollbars();
        this.updateButtons();
    }

    private int columnX(final int column) {
        return MARGIN + column * (this.columnWidth + SCROLLBAR + 2 + GAP);
    }

    private void updateToolbar() {
        if (this.groupingButton == null || this.hideButton == null) {
            return;
        }
        final KitSettings.Grouping mode = this.grouping == null ? KitSettings.Grouping.SINGLE : this.grouping;
        this.groupingButton.displayString = I18n.format("gui.ae2stuff.wireless_kit.grouping", I18n.format(GROUPINGS[mode.ordinal()]));
        this.hideButton.displayString = I18n
                .format(this.hideLinked ? "gui.ae2stuff.wireless_kit.linked.hidden" : "gui.ae2stuff.wireless_kit.linked.shown");
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
        final int x = mouseX - offsetX;
        final int y = mouseY - offsetY;
        final Hit hovered = this.dragging ? null : this.hitAt(x, y);

        for (int i = 0; i < this.columns.size(); i++) {
            final int left = this.columnX(i);
            final List<KitEntry> column = this.column(i);
            this.fontRenderer.drawString(this.trim(I18n.format(HEADERS[i], column.size()), this.columnWidth + SCROLLBAR), left,
                    TOP - HEADER + 2, TEXT);
            this.scrollbars[i].draw(this);

            final int first = this.scrollbars[i].getCurrentScroll();
            for (int row = 0; row < this.rows && first + row < column.size(); row++) {
                final KitEntry entry = column.get(first + row);
                final int top = TOP + row * ROW;
                this.drawRow(entry, left, top, hovered != null && hovered.column == i && hovered.index == first + row);
                if (this.dragging && this.pressed != null && this.pressed.column == i && this.pressed.index == first + row) {
                    drawRect(left, top, left + this.columnWidth, top + ROW, DRAGGED);
                }
            }
        }

        if (this.dragging) {
            this.drawDropLine(x, y);
        }

        if (this.column(LIST).isEmpty() && this.column(SOURCES).isEmpty() && this.column(TARGETS).isEmpty()) {
            final String hint = I18n.format(this.received ? "gui.ae2stuff.wireless_kit.empty" : "gui.ae2stuff.wireless_kit.loading");
            this.fontRenderer.drawSplitString(hint, this.columnX(LIST) + 4, TOP + 4, this.columnWidth - 8, UNLINKED);
        }
    }

    /**
     * Where the dragged row would land. The list keeps its own order, so it only lights up as a whole.
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

    private void drawRow(final KitEntry entry, final int x, final int y, final boolean hovered) {
        if (hovered) {
            drawRect(x, y, x + this.columnWidth, y + ROW, HOVER);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        final ItemStack icon = this.iconOf(entry);
        if (!icon.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.renderItemAndEffectIntoGUI(icon, x + 2, y + 3);
            RenderHelper.disableStandardItemLighting();
        }

        final int textX = x + 21;
        final int nameWidth = this.columnWidth - 22 - (entry.kind == KitEntry.Kind.NETWORK ? 22 : 12);
        if (!entry.equals(this.renaming)) {
            this.fontRenderer.drawString(this.trim(this.displayName(entry), nameWidth), textX, y + 2, TEXT);
        }

        this.drawBox(x + this.columnWidth - 10, y + 2, this.pins.contains(entry) ? PINNED : 0, hovered || this.pins.contains(entry));
        if (entry.kind == KitEntry.Kind.NETWORK) {
            this.fontRenderer.drawString("x", x + this.columnWidth - 18, y + 2, hovered ? FORGET : UNLINKED);
        }

        if (entry.isGroup()) {
            this.drawGroupLine(entry, x, y);
        } else {
            this.drawDeviceLine(this.devices.get(entry.pos), x, y);
        }
    }

    private void drawDeviceLine(@Nullable final KitManagerData.Device device, final int x, final int y) {
        if (device == null) {
            return;
        }

        final int textX = x + 21;
        final int textWidth = this.columnWidth - 22;
        final String channels = this.channelsOf(device);
        int stateWidth = textWidth;
        if (!channels.isEmpty()) {
            final int width = this.fontRenderer.getStringWidth(channels);
            this.fontRenderer.drawString(channels, x + this.columnWidth - 2 - width, y + 12, TEXT);
            stateWidth -= width + 4;
        }
        this.fontRenderer.drawString(this.trim(this.stateOf(device), stateWidth), textX, y + 12, this.stateColor(device));
    }

    private void drawGroupLine(final KitEntry entry, final int x, final int y) {
        final boolean[] include = this.includeOf(entry);
        final int[] boxes = this.checkboxes(x);
        final String connectors = I18n.format("gui.ae2stuff.wireless_kit.group.connectors_short");
        final String hubs = I18n.format("gui.ae2stuff.wireless_kit.group.hubs_short");

        this.fontRenderer.drawString(connectors, boxes[0] - this.fontRenderer.getStringWidth(connectors) - 2, y + 12, TEXT);
        this.drawBox(boxes[0], y + 12, include[0] ? CHECKED : 0, true);
        this.fontRenderer.drawString(hubs, boxes[1] - this.fontRenderer.getStringWidth(hubs) - 2, y + 12, TEXT);
        this.drawBox(boxes[1], y + 12, include[1] ? CHECKED : 0, true);

        final String count = I18n.format("gui.ae2stuff.wireless_kit.group.count", this.membersOf(entry).size());
        this.fontRenderer.drawString(this.trim(count, boxes[0] - this.fontRenderer.getStringWidth(connectors) - 4 - (x + 21)), x + 21,
                y + 12, TEXT);
    }

    /** The left edges of the connectors and hubs boxes on a group row. */
    private int[] checkboxes(final int x) {
        final int hubs = x + this.columnWidth - 2 - BOX;
        final int connectors = hubs - BOX - 4 - this.fontRenderer.getStringWidth(I18n.format("gui.ae2stuff.wireless_kit.group.hubs_short"))
                - 2;
        return new int[] { connectors, hubs };
    }

    private void drawBox(final int x, final int y, final int fill, final boolean visible) {
        if (!visible) {
            return;
        }
        drawRect(x, y, x + BOX, y + BOX, BOX_BORDER);
        drawRect(x + 1, y + 1, x + BOX - 1, y + BOX - 1, fill == 0 ? BOX_FILL : fill);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (this.renameField != null) {
            if (this.renaming == null || this.column(this.renameColumn).indexOf(this.renaming) != this.renameIndex
                    || this.scrollbars[this.renameColumn].getCurrentScroll() != this.renameScroll) {
                this.cancelRename();
            } else {
                this.renameField.drawTextBox();
            }
        }

        if (this.dragging && this.pressed != null && this.pressedEntry != null) {
            final int x = mouseX - this.grabX;
            final int y = mouseY - this.grabY;
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 300);
            drawRect(x, y, x + this.columnWidth, y + ROW, FLOATING);
            this.drawRow(this.pressedEntry, x, y, false);
            GlStateManager.popMatrix();
            return;
        }

        final Hit hit = this.hitAt(mouseX - this.guiLeft, mouseY - this.guiTop);
        if (hit == null) {
            return;
        }

        final KitEntry entry = this.column(hit.column).get(hit.index);
        final Control control = this.controlAt(hit, mouseX - this.guiLeft, mouseY - this.guiTop);
        if (control != Control.NONE) {
            this.drawTooltip(mouseX, mouseY, I18n.format(this.controlTooltip(entry, control)));
        } else {
            this.drawTooltip(mouseX, mouseY, this.tooltipOf(entry));
        }
    }

    private String controlTooltip(final KitEntry entry, final Control control) {
        return switch (control) {
            case PIN -> this.pins.contains(entry) ? "gui.ae2stuff.wireless_kit.control.unpin" : "gui.ae2stuff.wireless_kit.control.pin";
            case FORGET -> "gui.ae2stuff.wireless_kit.control.forget";
            case CONNECTORS -> "gui.ae2stuff.wireless_kit.control.connectors";
            case HUBS -> "gui.ae2stuff.wireless_kit.control.hubs";
            default -> "";
        };
    }

    private List<String> tooltipOf(final KitEntry entry) {
        final List<String> lines = new ArrayList<>();
        lines.add(this.displayName(entry));

        if (entry.isGroup()) {
            this.groupTooltip(entry, lines);
        } else {
            this.deviceTooltip(this.devices.get(entry.pos), lines);
        }

        lines.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2stuff.wireless_kit.hint.drag"));
        lines.add(TextFormatting.DARK_GRAY + I18n.format(entry.isGroup() ? "gui.ae2stuff.wireless_kit.hint.highlight_group"
                : "gui.ae2stuff.wireless_kit.hint.highlight"));
        lines.add(TextFormatting.DARK_GRAY + I18n.format("gui.ae2stuff.wireless_kit.hint.rename"));
        return lines;
    }

    private void groupTooltip(final KitEntry entry, final List<String> lines) {
        lines.add(TextFormatting.GRAY
                + I18n.format("gui.ae2stuff.wireless_kit.tooltip.network", entry.pos.getX(), entry.pos.getY(), entry.pos.getZ()));

        int connectors = 0;
        int linked = 0;
        int hubs = 0;
        for (final KitManagerData.Device device : this.membersOf(entry)) {
            if (device.hub) {
                hubs++;
            } else {
                connectors++;
                if (device.live) {
                    linked++;
                }
            }
        }
        lines.add(I18n.format("gui.ae2stuff.wireless_kit.group.connectors", connectors, linked));
        lines.add(I18n.format("gui.ae2stuff.wireless_kit.group.hubs", hubs));
    }

    private void deviceTooltip(@Nullable final KitManagerData.Device device, final List<String> lines) {
        if (device == null) {
            return;
        }

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
        if (colorOf(device.color) != AEColor.TRANSPARENT) {
            lines.add(I18n.format(colorOf(device.color).unlocalizedName));
        }
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);

        final int x = mouseX - this.guiLeft;
        final int y = mouseY - this.guiTop;
        if (this.renameField != null) {
            if (this.renameField.isMouseIn(mouseX, mouseY)) {
                this.renameField.mouseClicked(mouseX, mouseY, button);
                return;
            }
            this.cancelRename();
        }

        for (final GuiScrollbar scrollbar : this.scrollbars) {
            scrollbar.click(this, x, y);
        }

        final Hit hit = this.hitAt(x, y);
        if (hit == null || button != 0) {
            return;
        }

        final KitEntry entry = this.column(hit.column).get(hit.index);
        switch (this.controlAt(hit, x, y)) {
            case PIN -> this.togglePin(entry);
            case FORGET -> this.send(new PacketKitEdit(PacketKitEdit.Action.FORGET, entry, 0, ""));
            case CONNECTORS -> this.includeOf(entry)[0] ^= true;
            case HUBS -> this.includeOf(entry)[1] ^= true;
            default -> this.pressRow(hit, entry, mouseX, mouseY, x, y);
        }
    }

    private void pressRow(final Hit hit, final KitEntry entry, final int mouseX, final int mouseY, final int x, final int y) {
        if (isCtrlKeyDown()) {
            this.highlight(entry);
            return;
        }

        final long now = Minecraft.getSystemTime();
        if (entry.equals(this.lastClicked) && now - this.lastClickTime < DOUBLE_CLICK_MILLIS && this.onName(hit, x, y)) {
            this.lastClicked = null;
            this.startRename(entry, hit);
            return;
        }

        this.lastClicked = entry;
        this.lastClickTime = now;
        this.pressed = hit;
        this.pressedEntry = entry;
        this.pressX = mouseX;
        this.pressY = mouseY;
        this.grabX = x - this.columnX(hit.column);
        this.grabY = y - TOP - (hit.index - this.scrollbars[hit.column].getCurrentScroll()) * ROW;
        this.dragging = false;
    }

    private void togglePin(final KitEntry entry) {
        final boolean pinned = this.pins.contains(entry);
        if (pinned) {
            this.pins.remove(entry);
        } else {
            this.pins.add(entry);
        }
        this.send(new PacketKitEdit(pinned ? PacketKitEdit.Action.UNPIN : PacketKitEdit.Action.PIN, entry, 0, ""));
        this.changed();
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
        this.pressedEntry = null;
        this.dragging = false;
    }

    private void drop(final Hit from, final int x, final int y) {
        final int to = this.dropColumnAt(x, y);
        final List<KitEntry> source = this.column(from.column);
        if (to < 0 || from.index >= source.size() || to == LIST && from.column == LIST) {
            return;
        }

        int index = this.insertionIndex(to, y);
        if (to == from.column && index > from.index) {
            index--;
        }

        final KitEntry entry = source.remove(from.index);
        final List<KitEntry> target = this.column(to);
        target.add(Math.min(index, target.size()), entry);
        this.changed();
    }

    /**
     * The gap between rows nearest the pointer, as an index into the column.
     */
    private int insertionIndex(final int column, final int y) {
        final int boundary = Math.max(0, Math.min(this.rows, Math.floorDiv(y - TOP + ROW / 2, ROW)));
        return Math.min(this.scrollbars[column].getCurrentScroll() + boundary, this.column(column).size());
    }

    private void startRename(final KitEntry entry, final Hit hit) {
        final int rowX = this.columnX(hit.column);
        final int rowY = TOP + (hit.index - this.scrollbars[hit.column].getCurrentScroll()) * ROW;
        final int width = this.columnWidth - 20 - (entry.kind == KitEntry.Kind.NETWORK ? 22 : 12);

        this.renameField = new MEGuiTextField(this.fontRenderer, this.guiLeft + rowX + 19, this.guiTop + rowY + 1, width, 12);
        this.renameField.setMaxStringLength(KitSettings.MAX_NAME_LENGTH);
        this.renameField.setText(this.currentName(entry), true);
        this.renameField.setFocused(true);
        this.renameField.selectAll();
        this.renaming = entry;
        this.renameColumn = hit.column;
        this.renameIndex = hit.index;
        this.renameScroll = this.scrollbars[hit.column].getCurrentScroll();
    }

    private void commitRename() {
        if (this.renameField == null || this.renaming == null) {
            return;
        }

        final KitEntry entry = this.renaming;
        final String text = this.renameField.getText().trim();
        this.cancelRename();

        if (text.equals(this.currentName(entry))) {
            return;
        }
        if (entry.isGroup()) {
            if (text.isEmpty() || text.equals(this.defaultName(entry))) {
                this.names.remove(entry);
                this.send(new PacketKitEdit(PacketKitEdit.Action.RENAME, entry, 0, ""));
            } else {
                this.names.put(entry, text);
                this.send(new PacketKitEdit(PacketKitEdit.Action.RENAME, entry, 0, text));
            }
            this.changed();
        } else {
            this.send(new PacketKitEdit(PacketKitEdit.Action.RENAME, entry, 0, text.equals(this.defaultName(entry)) ? "" : text));
        }
    }

    private void cancelRename() {
        this.renameField = null;
        this.renaming = null;
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.renameField != null) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.commitRename();
            } else if (key == Keyboard.KEY_ESCAPE) {
                this.cancelRename();
            } else {
                this.renameField.textboxKeyTyped(character, key);
            }
            return;
        }

        super.keyTyped(character, key);
    }

    @Override
    public boolean isTextFieldFocused() {
        return this.renameField != null;
    }

    private void highlight(final KitEntry entry) {
        final List<BlockPos> blocks = new ArrayList<>();
        for (final KitManagerData.Device device : this.membersOf(entry)) {
            blocks.add(device.pos);
        }
        if (blocks.isEmpty()) {
            return;
        }

        final int dimension = this.mc.world.provider.getDimension();
        BlockPosHighlighter.hilightBlocks(blocks, System.currentTimeMillis() + HIGHLIGHT_MILLISECONDS, dimension);
        BlockPosHighlighter.turnPlayerTowards(blocks);
        if (blocks.size() == 1) {
            final BlockPos pos = blocks.get(0);
            this.mc.player.sendMessage(
                    new TextComponentTranslation("chat.ae2stuff.wireless.manager.highlighted", pos.getX(), pos.getY(), pos.getZ()));
        } else {
            this.mc.player.sendMessage(new TextComponentTranslation("chat.ae2stuff.wireless.manager.highlighted_group", blocks.size()));
        }
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
                this.cancelRename();
                this.scrollbars[column].wheel(wheel);
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton button) throws IOException {
        super.actionPerformed(button);

        if (button == this.groupingButton) {
            final KitSettings.Grouping[] modes = KitSettings.Grouping.values();
            final KitSettings.Grouping next = modes[((this.grouping == null ? 0 : this.grouping.ordinal()) + 1) % modes.length];
            this.grouping = next;
            this.column(SOURCES).clear();
            this.column(TARGETS).clear();
            this.cancelRename();
            this.updateToolbar();
            this.changed();
            this.send(new PacketKitEdit(PacketKitEdit.Action.GROUPING, null, next.ordinal(), ""));
        } else if (button == this.hideButton) {
            this.hideLinked = !this.hideLinked;
            this.updateToolbar();
            this.changed();
            this.send(new PacketKitEdit(PacketKitEdit.Action.HIDE_LINKED, null, this.hideLinked ? 1 : 0, ""));
        } else if (button == this.link) {
            ModNetwork.CHANNEL.sendToServer(
                    new PacketKitAction(PacketKitAction.Action.LINK, this.selections(SOURCES), this.selections(TARGETS)));
        } else if (button == this.unlink) {
            ModNetwork.CHANNEL.sendToServer(
                    new PacketKitAction(PacketKitAction.Action.UNLINK, this.selections(SOURCES), Collections.emptyList()));
        }
    }

    private void send(final PacketKitEdit edit) {
        ModNetwork.CHANNEL.sendToServer(edit);
    }

    private List<KitSelection> selections(final int column) {
        final List<KitSelection> selections = new ArrayList<>();
        for (final KitEntry entry : this.column(column)) {
            final boolean[] include = this.includeOf(entry);
            selections.add(new KitSelection(entry, include[0], include[1]));
        }
        return selections;
    }

    private boolean[] includeOf(final KitEntry entry) {
        return this.includes.computeIfAbsent(entry, key -> new boolean[] { true, true });
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

    private Control controlAt(final Hit hit, final int x, final int y) {
        final KitEntry entry = this.column(hit.column).get(hit.index);
        final int rowX = this.columnX(hit.column);
        final int rowY = TOP + (hit.index - this.scrollbars[hit.column].getCurrentScroll()) * ROW;

        if (within(x, y, rowX + this.columnWidth - 10, rowY + 2)) {
            return Control.PIN;
        }
        if (entry.kind == KitEntry.Kind.NETWORK && within(x, y, rowX + this.columnWidth - 19, rowY + 2)) {
            return Control.FORGET;
        }
        if (entry.isGroup()) {
            final int[] boxes = this.checkboxes(rowX);
            if (within(x, y, boxes[0], rowY + 12)) {
                return Control.CONNECTORS;
            }
            if (within(x, y, boxes[1], rowY + 12)) {
                return Control.HUBS;
            }
        }
        return Control.NONE;
    }

    private static boolean within(final int x, final int y, final int left, final int top) {
        return x >= left && x < left + BOX && y >= top && y < top + BOX;
    }

    private boolean onName(final Hit hit, final int x, final int y) {
        final int rowY = TOP + (hit.index - this.scrollbars[hit.column].getCurrentScroll()) * ROW;
        return x >= this.columnX(hit.column) + 19 && y >= rowY && y < rowY + 12;
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

    private static AEColor colorOf(final int ordinal) {
        return ordinal >= 0 && ordinal < AEColor.values().length ? AEColor.values()[ordinal] : AEColor.TRANSPARENT;
    }

    private ItemStack iconOf(final KitEntry entry) {
        if (entry.kind == KitEntry.Kind.NETWORK) {
            return AEApi.instance().definitions().blocks().controller().maybeStack(1).orElse(ItemStack.EMPTY);
        }

        final KitManagerData.Device device = entry.isGroup() ? null : this.devices.get(entry.pos);
        final Block block = device != null && device.hub ? Registration.wirelessHub : Registration.wirelessConnector;
        final AEColor color = colorOf(device == null ? entry.color : device.color);
        return block == null ? ItemStack.EMPTY : new ItemStack(block, 1, BlockWireless.metaOf(color));
    }

    private String displayName(final KitEntry entry) {
        final String named = this.names.get(entry);
        return named == null || named.isEmpty() ? this.defaultName(entry) : named;
    }

    private String defaultName(final KitEntry entry) {
        return switch (entry.kind) {
            case DEVICE -> {
                final KitManagerData.Device device = this.devices.get(entry.pos);
                yield device == null ? "" : this.displayName(device);
            }
            case NETWORK -> I18n.format("gui.ae2stuff.wireless_kit.network", entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());
            case COLOR -> I18n.format(colorOf(entry.color).unlocalizedName);
        };
    }

    /** What the rename field starts from: a name already given, or what the row is called by default. */
    private String currentName(final KitEntry entry) {
        if (entry.isGroup()) {
            final String named = this.names.get(entry);
            return named == null ? this.defaultName(entry) : named;
        }
        final KitManagerData.Device device = this.devices.get(entry.pos);
        return device == null || device.name.isEmpty() ? this.defaultName(entry) : device.name;
    }

    private String displayName(final KitManagerData.Device device) {
        if (!device.name.isEmpty()) {
            return device.name;
        }
        final Block block = device.hub ? Registration.wirelessHub : Registration.wirelessConnector;
        return block == null ? "" : new ItemStack(block, 1, BlockWireless.metaOf(colorOf(device.color))).getDisplayName();
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
