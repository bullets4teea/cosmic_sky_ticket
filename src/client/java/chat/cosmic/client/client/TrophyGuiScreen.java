package chat.cosmic.client.client;

import chat.cosmic.client.client.TrophyTrackerMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrophyGuiScreen extends Screen {

    private static final int BACKGROUND_COLOR = 0x88000000;
    private static final int PANEL_COLOR = 0xAA111111;
    private static final int BORDER_COLOR = 0xFF444444;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HIGHLIGHT_COLOR = 0xFFFFFF00;

    private int panelX, panelY, panelWidth, panelHeight;
    private List<Map.Entry<String, Integer>> sortedTrophies;
    private int scrollOffset = 0;
    private final int maxVisibleEntries = 10;
    private boolean canScroll = false;

    public TrophyGuiScreen() {
        super(Text.literal("Trophy Points"));
    }

    @Override
    protected void init() {
        super.init();

        panelWidth = Math.min(400, this.width - 40);
        panelHeight = Math.min(300, this.height - 40);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        Map<String, Integer> trophies = TrophyTrackerMod.getTrophies();
        sortedTrophies = new ArrayList<>(trophies.entrySet());
        sortedTrophies.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Check if we need scrolling
        canScroll = sortedTrophies.size() > maxVisibleEntries;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> this.close())
                .dimensions(panelX + panelWidth - 60, panelY + panelHeight - 30, 50, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset All").formatted(Formatting.RED), button -> {
                    TrophyTrackerMod.resetAllTrophies();
                    this.close();
                }).dimensions(panelX + 10, panelY + panelHeight - 30, 70, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, BACKGROUND_COLOR);

        renderRoundedPanel(context);

        Text title = Text.literal("🏆 Trophy Points").formatted(Formatting.GOLD, Formatting.BOLD);
        int titleWidth = this.textRenderer.getWidth(title);
        context.drawText(this.textRenderer, title, panelX + (panelWidth - titleWidth) / 2, panelY + 10, TEXT_COLOR, true);

        int totalPoints = TrophyTrackerMod.getTotalPoints();
        Text totalText = Text.literal("Total: " + totalPoints + " points").formatted(Formatting.GREEN);
        int totalWidth = this.textRenderer.getWidth(totalText);
        context.drawText(this.textRenderer, totalText, panelX + (panelWidth - totalWidth) / 2, panelY + 25, TEXT_COLOR, true);

        if (sortedTrophies.isEmpty()) {
            Text noTrophies = Text.literal("No trophy points yet!").formatted(Formatting.GRAY);
            int noTrophiesWidth = this.textRenderer.getWidth(noTrophies);
            context.drawText(this.textRenderer, noTrophies,
                    panelX + (panelWidth - noTrophiesWidth) / 2, panelY + 60, TEXT_COLOR, true);
        } else {
            renderTrophiesList(context);
        }

        if (canScroll) {
            renderScrollIndicator(context);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderRoundedPanel(DrawContext context) {
        int cornerRadius = 5;

        context.fill(panelX + cornerRadius, panelY, panelX + panelWidth - cornerRadius, panelY + panelHeight, PANEL_COLOR);
        context.fill(panelX, panelY + cornerRadius, panelX + panelWidth, panelY + panelHeight - cornerRadius, PANEL_COLOR);

        context.fill(panelX, panelY, panelX + cornerRadius, panelY + cornerRadius, PANEL_COLOR);
        context.fill(panelX + panelWidth - cornerRadius, panelY, panelX + panelWidth, panelY + cornerRadius, PANEL_COLOR);
        context.fill(panelX, panelY + panelHeight - cornerRadius, panelX + cornerRadius, panelY + panelHeight, PANEL_COLOR);
        context.fill(panelX + panelWidth - cornerRadius, panelY + panelHeight - cornerRadius, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);

        context.drawBorder(panelX, panelY, panelWidth, panelHeight, BORDER_COLOR);
    }

    private void renderTrophiesList(DrawContext context) {
        int startY = panelY + 50;
        int entryHeight = 20;

        // Calculate how many entries we can actually show
        int maxEntries = Math.min(maxVisibleEntries, sortedTrophies.size() - scrollOffset);

        // Ensure we don't try to show more entries than available
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > sortedTrophies.size() - maxVisibleEntries) {
            scrollOffset = Math.max(0, sortedTrophies.size() - maxVisibleEntries);
        }

        // Draw a semi-transparent background for the list area
        context.fill(panelX + 5, startY - 2, panelX + panelWidth - 5, startY + maxEntries * entryHeight, 0x22000000);

        for (int i = 0; i < maxEntries; i++) {
            int index = i + scrollOffset;
            if (index >= sortedTrophies.size()) break;

            Map.Entry<String, Integer> entry = sortedTrophies.get(index);
            String cropName = entry.getKey();
            int points = entry.getValue();

            int y = startY + i * entryHeight;

            // Highlight every other row for better readability
            if (i % 2 == 0) {
                context.fill(panelX + 5, y - 1, panelX + panelWidth - 5, y + entryHeight - 1, 0x11FFFFFF);
            }

            // Render crop icon/emoji
            String icon = getCropIcon(cropName);
            context.drawText(this.textRenderer, Text.literal(icon), panelX + 10, y, TEXT_COLOR, true);

            // Render crop name with better contrast
            context.drawText(this.textRenderer, Text.literal(cropName).formatted(Formatting.AQUA),
                    panelX + 30, y, TEXT_COLOR, true);

            // Render points (right-aligned) with better contrast
            Text pointsText = Text.literal(points + " pts").formatted(Formatting.YELLOW);
            int pointsWidth = this.textRenderer.getWidth(pointsText);
            context.drawText(this.textRenderer, pointsText,
                    panelX + panelWidth - pointsWidth - 15, y, TEXT_COLOR, true);
        }
    }

    private void renderScrollIndicator(DrawContext context) {
        int scrollBarX = panelX + panelWidth - 8;
        int scrollBarY = panelY + 50;
        int scrollBarHeight = maxVisibleEntries * 20;

        // Scroll bar background
        context.fill(scrollBarX, scrollBarY, scrollBarX + 6, scrollBarY + scrollBarHeight, 0xFF333333);

        // Calculate scroll thumb position
        float scrollPercent = (float) scrollOffset / (sortedTrophies.size() - maxVisibleEntries);
        int thumbY = scrollBarY + (int) (scrollPercent * (scrollBarHeight - 20));

        // Ensure thumb doesn't go outside scrollbar bounds
        thumbY = Math.max(scrollBarY, Math.min(scrollBarY + scrollBarHeight - 20, thumbY));

        // Scroll thumb
        context.fill(scrollBarX + 1, thumbY, scrollBarX + 5, thumbY + 20, 0xFF888888);
    }

    private String getCropIcon(String cropName) {
        return switch (cropName.toLowerCase()) {
            case "wheat" -> "🌾";
            case "carrot", "carrots" -> "🥕";
            case "potato", "potatoes" -> "🥔";
            case "melon", "melons" -> "🍉";
            case "pumpkin", "pumpkins" -> "🎃";
            case "sugar cane" -> "🎋";
            case "cocoa beans", "cocoa" -> "🫘";
            case "nether wart" -> "🍄";
            case "beetroot" -> "🟥";
            default -> "🌱";
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (canScroll) {
            // Calculate new scroll offset
            int newScrollOffset = scrollOffset - (int) verticalAmount;

            // Ensure scroll offset stays within valid bounds
            newScrollOffset = Math.max(0, Math.min(sortedTrophies.size() - maxVisibleEntries, newScrollOffset));

            // Only update if changed
            if (newScrollOffset != scrollOffset) {
                scrollOffset = newScrollOffset;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle scroll bar clicking for manual scrolling
        if (canScroll && button == 0) {
            int scrollBarX = panelX + panelWidth - 8;
            int scrollBarY = panelY + 50;
            int scrollBarHeight = maxVisibleEntries * 20;

            // Check if click is on the scroll bar
            if (mouseX >= scrollBarX && mouseX <= scrollBarX + 6 &&
                    mouseY >= scrollBarY && mouseY <= scrollBarY + scrollBarHeight) {

                // Calculate new scroll position based on click
                float clickPercent = (float) (mouseY - scrollBarY) / scrollBarHeight;
                scrollOffset = (int) (clickPercent * (sortedTrophies.size() - maxVisibleEntries));

                // Ensure scroll offset stays within valid bounds
                scrollOffset = Math.max(0, Math.min(sortedTrophies.size() - maxVisibleEntries, scrollOffset));

                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}