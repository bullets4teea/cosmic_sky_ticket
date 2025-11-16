package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ChatHistorySaver implements ClientModInitializer {
    private static final Path CHAT_HISTORY_FILE = Paths.get("config", "cosmic_chat_history.txt");
    private static final int MAX_MESSAGES = 1000;
    private static final List<ChatMessage> chatHistory = new ArrayList<>();
    private static boolean initialized = false;

    private static class ChatMessage {
        final Text text;
        final MessageSignatureData signature;
        final MessageIndicator indicator;

        ChatMessage(Text text, MessageSignatureData signature, MessageIndicator indicator) {
            this.text = text;
            this.signature = signature;
            this.indicator = indicator;
        }
    }

    @Override
    public void onInitializeClient() {
        loadChatHistory();
        
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            saveChatHistory();
        });
        
        initialized = true;
    }

    public static void addChatMessage(Text message, MessageSignatureData signature, MessageIndicator indicator) {
        if (!initialized) return;
        

        chatHistory.add(new ChatMessage(message, signature, indicator));
        

        if (chatHistory.size() > MAX_MESSAGES) {
            chatHistory.remove(0);
        }
    }

    public static void onChatClearAttempt() {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null || client.inGameHud.getChatHud() == null) {
            return;
        }


        List<ChatMessage> messagesToRestore = new ArrayList<>(chatHistory);
        

        for (ChatMessage msg : messagesToRestore) {
            try {
                client.inGameHud.getChatHud().addMessage(msg.text, msg.signature, msg.indicator);
            } catch (Exception e) {

                client.inGameHud.getChatHud().addMessage(msg.text);
            }
        }
    }

    private static void loadChatHistory() {
        chatHistory.clear();
        
        if (!Files.exists(CHAT_HISTORY_FILE)) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(CHAT_HISTORY_FILE.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {

                chatHistory.add(new ChatMessage(Text.literal(line), null, null));
            }
        } catch (IOException e) {
            System.err.println("Failed to load chat history: " + e.getMessage());
        }
    }

    private static void saveChatHistory() {
        try {
            Files.createDirectories(CHAT_HISTORY_FILE.getParent());
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(CHAT_HISTORY_FILE.toFile()))) {
                for (ChatMessage msg : chatHistory) {
                    writer.write(msg.text.getString());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save chat history: " + e.getMessage());
        }
    }
}
