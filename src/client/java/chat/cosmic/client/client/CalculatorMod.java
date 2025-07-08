package chat.cosmic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CalculatorMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("calculator")
                            .then(argument("expression", StringArgumentType.greedyString())
                                    .executes(new CalculatorCommand())
                            ));

            dispatcher.register(
                    literal("calc")
                            .then(argument("expression", StringArgumentType.greedyString())
                                    .executes(new CalculatorCommand()))
            );
        });
    }

    private static class CalculatorCommand implements Command<FabricClientCommandSource> {
        @Override
        public int run(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
            String input = StringArgumentType.getString(context, "expression")
                    .replaceAll("\\s", "")
                    .toLowerCase();

            if (input.isEmpty()) {
                sendError(context, "Expression cannot be empty! Example: /calc 1.5k*2");
                return 0;
            }

            try {
                List<Object> tokens = tokenize(input);
                List<Object> processedTokens = evaluateMultDiv(tokens);
                double result = evaluateAddSub(processedTokens);
                sendFormattedResult(context, input, result);
            } catch (Exception e) {
                sendError(context, "Error: " + e.getMessage());
            }
            return 1;
        }

        private List<Object> tokenize(String expr) throws Exception {
            List<Object> tokens = new ArrayList<>();
            int index = 0;
            while (index < expr.length()) {
                char c = expr.charAt(index);

                if (Character.isDigit(c) || c == '.' || (c == '-' && (tokens.isEmpty() || isOperator(tokens.get(tokens.size() - 1))))) {
                    NumberToken token = parseNumber(expr, index);
                    tokens.add(token.value);
                    index += token.length;
                }
                else if (c == '+' || c == '-' || c == '*' || c == '/' || c == 'x') {
                    tokens.add(c);
                    index++;
                }
                else {
                    throw new Exception("Invalid character: '" + c + "'");
                }
            }
            return tokens;
        }

        private boolean isOperator(Object token) {
            return token instanceof Character && "+-*/x".indexOf((Character) token) >= 0;
        }

        private NumberToken parseNumber(String expr, int start) throws Exception {
            int originalStart = start;
            boolean isNegative = false;

            if (expr.charAt(start) == '-') {
                isNegative = true;
                start++;
            }

            int end = start;
            while (end < expr.length() && (Character.isDigit(expr.charAt(end)) || expr.charAt(end) == '.')) {
                end++;
            }

            String numberPart = expr.substring(start, end);
            if (numberPart.isEmpty() || numberPart.equals(".")) {
                throw new Exception("Invalid number format");
            }

            double value;
            try {
                value = Double.parseDouble(numberPart);
            } catch (NumberFormatException e) {
                throw new Exception("Invalid number: " + numberPart);
            }

            if (isNegative) {
                value = -value;
            }

            int suffixLength = 0;
            if (end < expr.length()) {
                char suffix = expr.charAt(end);
                switch (suffix) {
                    case 'k': value *= 1000; suffixLength = 1; break;
                    case 'm': value *= 1_000_000; suffixLength = 1; break;
                    case 'b': value *= 1_000_000_000; suffixLength = 1; break;
                }
            }

            int totalLength = (end - start) + suffixLength + (isNegative ? 1 : 0);
            return new NumberToken(value, totalLength);
        }

        private List<Object> evaluateMultDiv(List<Object> tokens) throws Exception {
            List<Object> result = new ArrayList<>();
            int i = 0;

            while (i < tokens.size()) {
                if (tokens.get(i) instanceof Character) {
                    char operator = (Character) tokens.get(i);

                    if (operator == '*' || operator == 'x' || operator == '/') {
                        if (result.isEmpty() || i + 1 >= tokens.size() || !(tokens.get(i + 1) instanceof Double)) {
                            throw new Exception("Missing operand for operator '" + operator + "'");
                        }

                        double left = (Double) result.remove(result.size() - 1);
                        double right = (Double) tokens.get(i + 1);

                        double calculated;
                        if (operator == '*' || operator == 'x') {
                            calculated = left * right;
                        } else {
                            if (right == 0) throw new Exception("Division by zero");
                            calculated = left / right;
                        }

                        result.add(calculated);
                        i += 2;
                    } else {
                        result.add(operator);
                        i++;
                    }
                } else {
                    result.add(tokens.get(i));
                    i++;
                }
            }

            return result;
        }

        private double evaluateAddSub(List<Object> tokens) throws Exception {
            if (tokens.isEmpty()) throw new Exception("Empty expression");
            if (!(tokens.get(0) instanceof Double)) throw new Exception("Expression must start with a number");

            double result = (Double) tokens.get(0);

            for (int i = 1; i < tokens.size(); i += 2) {
                if (i + 1 >= tokens.size()) throw new Exception("Missing operand");

                char operator = (Character) tokens.get(i);
                double operand = (Double) tokens.get(i + 1);

                if (operator == '+') {
                    result += operand;
                } else if (operator == '-') {
                    result -= operand;
                } else {
                    throw new Exception("Unexpected operator: " + operator);
                }
            }

            return result;
        }

        private void sendFormattedResult(CommandContext<FabricClientCommandSource> context, String input, double result) {
            String formatted;
            if (result == (long) result) {
                formatted = String.format("%,d", (long) result);
            } else {
                BigDecimal bd = BigDecimal.valueOf(result).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
                formatted = bd.toPlainString();
            }

            context.getSource().sendFeedback(Text.literal("§6" + input + " §r= §a" + formatted));
        }

        private void sendError(CommandContext<FabricClientCommandSource> context, String message) {
            context.getSource().sendError(Text.literal("§c" + message));
        }
    }

    private static class NumberToken {
        public final double value;
        public final int length;

        public NumberToken(double value, int length) {
            this.value = value;
            this.length = length;
        }
    }
}