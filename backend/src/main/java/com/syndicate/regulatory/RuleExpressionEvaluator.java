package com.syndicate.regulatory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic JSON-logic evaluator for regulatory rules. There is no model call anywhere in
 * this class: the same expression and the same facts always produce the same verdict, which is
 * what makes a rule outcome defensible to a regulator.
 *
 * <p>Supported forms:
 * <ul>
 *   <li>literals - numbers, strings, booleans</li>
 *   <li>{@code {"var": "name"}} - a value from the evaluation context</li>
 *   <li>{@code {"factValue": "<label>"}} - numeric value of the matching verified fact</li>
 *   <li>{@code {"factCount": "<label>"}} - how many verified facts match</li>
 *   <li>{@code {"countFactsAbove": ["<label>", threshold]}} - matching facts above a number</li>
 *   <li>{@code {"minValidTo": "<label>"}} - earliest business-validity end among matching facts,
 *       where an open-ended fact counts as never expiring</li>
 *   <li>{@code <, <=, >, >=, ==, !=, and, or, not, +, -, *, /}</li>
 * </ul>
 */
@Component
public class RuleExpressionEvaluator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Raised when the expression needs a value the transaction has not recorded yet. This is a
     * different outcome from a rule that genuinely fails, so the caller reports MISSING_EVIDENCE.
     */
    public static class MissingOperandException extends RuntimeException {
        public MissingOperandException(String message) {
            super(message);
        }
    }

    public boolean evaluate(String expressionJson, RuleFactContext context) {
        JsonNode expression;
        try {
            expression = objectMapper.readTree(expressionJson);
        } catch (Exception e) {
            throw new IllegalStateException("Rule expression is not valid JSON: " + e.getMessage(), e);
        }
        Object result = eval(expression, context);
        if (!(result instanceof Boolean bool)) {
            throw new IllegalStateException("Rule expression must evaluate to a boolean, got: " + result);
        }
        return bool;
    }

    private Object eval(JsonNode node, RuleFactContext context) {
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNull()) {
            return null;
        }
        if (!node.isObject() || node.size() != 1) {
            throw new IllegalStateException("Expected a single-operator object, got: " + node);
        }

        Map.Entry<String, JsonNode> entry = node.fields().next();
        String operator = entry.getKey();
        JsonNode operand = entry.getValue();

        return switch (operator) {
            case "var" -> context.variable(operand.asText());
            case "factValue" -> context.factValue(operand.asText());
            case "factCount" -> (double) context.factCount(operand.asText());
            case "countFactsAbove" -> {
                List<JsonNode> args = args(operand, 2, operator);
                yield (double) context.countFactsAbove(args.get(0).asText(), asNumber(eval(args.get(1), context), operator));
            }
            case "minValidTo" -> context.minValidToEpochMillis(operand.asText());
            case "not" -> !asBoolean(eval(operand, context), operator);
            case "and", "or" -> {
                boolean and = operator.equals("and");
                for (JsonNode child : args(operand, -1, operator)) {
                    boolean value = asBoolean(eval(child, context), operator);
                    if (and && !value) {
                        yield false;
                    }
                    if (!and && value) {
                        yield true;
                    }
                }
                yield and;
            }
            case "==", "!=" -> {
                List<JsonNode> args = args(operand, 2, operator);
                Object left = eval(args.get(0), context);
                Object right = eval(args.get(1), context);
                requirePresent(left, operator);
                requirePresent(right, operator);
                boolean equal = left.equals(right);
                yield operator.equals("==") == equal;
            }
            case "<", "<=", ">", ">=" -> {
                List<JsonNode> args = args(operand, 2, operator);
                double left = asNumber(eval(args.get(0), context), operator);
                double right = asNumber(eval(args.get(1), context), operator);
                yield switch (operator) {
                    case "<" -> left < right;
                    case "<=" -> left <= right;
                    case ">" -> left > right;
                    default -> left >= right;
                };
            }
            case "+", "-", "*", "/" -> {
                List<JsonNode> args = args(operand, 2, operator);
                double left = asNumber(eval(args.get(0), context), operator);
                double right = asNumber(eval(args.get(1), context), operator);
                if (operator.equals("/") && right == 0) {
                    throw new IllegalStateException("Rule expression divides by zero");
                }
                yield switch (operator) {
                    case "+" -> left + right;
                    case "-" -> left - right;
                    case "*" -> left * right;
                    default -> left / right;
                };
            }
            default -> throw new IllegalStateException("Unsupported rule operator: " + operator);
        };
    }

    private List<JsonNode> args(JsonNode operand, int expected, String operator) {
        if (!operand.isArray()) {
            throw new IllegalStateException("Operator '" + operator + "' expects an array of arguments");
        }
        List<JsonNode> args = new ArrayList<>();
        for (Iterator<JsonNode> it = operand.elements(); it.hasNext(); ) {
            args.add(it.next());
        }
        if (expected >= 0 && args.size() != expected) {
            throw new IllegalStateException(
                    "Operator '" + operator + "' expects " + expected + " arguments, got " + args.size());
        }
        return args;
    }

    private void requirePresent(Object value, String operator) {
        if (value == null) {
            throw new MissingOperandException("Operator '" + operator + "' is missing a required value");
        }
    }

    private double asNumber(Object value, String operator) {
        requirePresent(value, operator);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalStateException("Operator '" + operator + "' expects a number, got: " + value);
    }

    private boolean asBoolean(Object value, String operator) {
        requirePresent(value, operator);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalStateException("Operator '" + operator + "' expects a boolean, got: " + value);
    }
}
