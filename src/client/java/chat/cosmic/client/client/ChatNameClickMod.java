package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.text.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatNameClickMod implements ClientModInitializer {
    
    // Pattern to match player names in chat (without color codes)
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile(">\\s*([A-Za-z0-9_]+)(?:\\s*\\[|:)");
    
    @Override
    public void onInitializeClient() {

    }
    
    public static Text processMessage(Text originalMessage) {
        String messageString = originalMessage.getString();
        

        String cleanMessage = messageString.replaceAll("§.", "");
        

        Matcher matcher = PLAYER_NAME_PATTERN.matcher(cleanMessage);
        
        if (!matcher.find()) {
            return originalMessage;
        }
        

        String playerName = matcher.group(1);
        
        if (playerName == null || playerName.isEmpty()) {
            return originalMessage;
        }
        

        return addClickEventToMessage(originalMessage, playerName);
    }
    
    private static Text addClickEventToMessage(Text original, String playerName) {

        MutableText result = MutableText.of(original.getContent());
        
        boolean foundArrow = false;
        

        for (Text sibling : original.getSiblings()) {
            String siblingText = sibling.getString();
            

            if (siblingText.contains("»")) {
                foundArrow = true;
                

                int arrowIndex = siblingText.indexOf("»");
                String beforeArrow = siblingText.substring(0, arrowIndex + 1);
                String afterArrow = siblingText.substring(arrowIndex + 1);
                

                Style clickableStyle = sibling.getStyle().withClickEvent(
                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + playerName + " ")
                );
                
                MutableText clickablePart = Text.literal(beforeArrow).setStyle(clickableStyle);
                MutableText nonClickablePart = Text.literal(afterArrow).setStyle(sibling.getStyle());
                
                result.append(clickablePart).append(nonClickablePart);
                

                for (Text subSibling : sibling.getSiblings()) {
                    result.append(subSibling);
                }
            } else if (!foundArrow) {

                String cleanSiblingText = siblingText.replaceAll("§.", "");
                
                if (cleanSiblingText.contains(playerName)) {
                    Style newStyle = sibling.getStyle().withClickEvent(
                        new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + playerName + " ")
                    );
                    
                    MutableText clickableSibling = MutableText.of(sibling.getContent()).setStyle(newStyle);
                    
                    for (Text subSibling : sibling.getSiblings()) {
                        clickableSibling.append(subSibling);
                    }
                    
                    result.append(clickableSibling);
                } else {
                    result.append(sibling);
                }
            } else {

                result.append(sibling);
            }
        }
        

        String mainText = original.getContent().toString();
        String cleanMainText = mainText.replaceAll("§.", "");
        
        if (!foundArrow && cleanMainText.contains(playerName)) {
            Style newStyle = original.getStyle().withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + playerName + " ")
            );
            result.setStyle(newStyle);
        } else {
            result.setStyle(original.getStyle());
        }
        
        return result;
    }
}
