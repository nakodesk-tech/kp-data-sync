package com.example.excel.engine

import kotlin.math.pow
import kotlin.math.round

/**
 * Small deterministic formula evaluator for the engine prototype.
 * Unsupported functions return an explicit #NAME? error instead of a fake value.
 */
class FormulaEngine(private val sheetProvider: (String) -> SpreadsheetSheet?) {
    fun evaluate(sheetName: String, expression: String): CellValue =
        EvaluationContext().evaluate(sheetName, expression)

    private inner class EvaluationContext {
        private val stack = mutableSetOf<Pair<String, CellAddress>>()

        fun evaluate(sheetName: String, expression: String): CellValue = try {
            CellValue.Number(Parser(expression.removePrefix("="), this).parseExpression())
        } catch (e: FormulaException) {
            CellValue.Error(e.code)
        } catch (_: ArithmeticException) {
            CellValue.Error("#DIV/0!")
        } catch (_: Exception) {
            CellValue.Error("#VALUE!")
        }

        fun number(sheetName: String, address: CellAddress): Double {
            val key = sheetName to address
            if (!stack.add(key)) throw FormulaException("#CIRC!")
            return try {
                when (val value = sheetProvider(sheetName)?.valueAt(address) ?: CellValue.Empty) {
                    CellValue.Empty -> 0.0
                    is CellValue.Number -> value.value
                    is CellValue.BooleanValue -> if (value.value) 1.0 else 0.0
                    is CellValue.Text -> value.value.toDoubleOrNull() ?: throw FormulaException("#VALUE!")
                    is CellValue.Formula -> evaluate(sheetName, value.expression).asNumber()
                    is CellValue.Error -> throw FormulaException(value.code)
                }
            } finally {
                stack.remove(key)
            }
        }

        fun range(sheetName: String, range: CellRange): List<Double> =
            range.addresses().map { number(sheetName, it) }.toList()
    }

    private fun CellValue.asNumber(): Double = when (this) {
        is CellValue.Number -> value
        is CellValue.Error -> throw FormulaException(code)
        else -> throw FormulaException("#VALUE!")
    }

    private class FormulaException(val code: String) : RuntimeException()

    private inner class Parser(
        private val input: String,
        private val context: EvaluationContext
    ) {
        private var pos = 0

        fun parseExpression(): Double {
            val result = parseComparison()
            skipSpaces()
            if (pos != input.length) throw FormulaException("#VALUE!")
            return result
        }

        private fun parseComparison(): Double {
            var left = parseAdditive()
            while (true) {
                skipSpaces()
                val op = when {
                    input.startsWith(">=", pos) -> ">="
                    input.startsWith("<=", pos) -> "<="
                    input.startsWith("<>", pos) -> "<>"
                    input.startsWith("=", pos) -> "="
                    input.startsWith(">", pos) -> ">"
                    input.startsWith("<", pos) -> "<"
                    else -> return left
                }
                pos += op.length
                val right = parseAdditive()
                left = when (op) {
                    "=" -> if (left == right) 1.0 else 0.0
                    "<>" -> if (left != right) 1.0 else 0.0
                    ">" -> if (left > right) 1.0 else 0.0
                    "<" -> if (left < right) 1.0 else 0.0
                    ">=" -> if (left >= right) 1.0 else 0.0
                    else -> if (left <= right) 1.0 else 0.0
                }
            }
        }

        private fun parseAdditive(): Double {
            var value = parseMultiplicative()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value + parseMultiplicative()
                    consume('-') -> value - parseMultiplicative()
                    else -> return value
                }
            }
        }

        private fun parseMultiplicative(): Double {
            var value = parsePower()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value * parsePower()
                    consume('/') -> value / parsePower()
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            val value = parseUnary()
            skipSpaces()
            return if (consume('^')) value.pow(parsePower()) else value
        }

        private fun parseUnary(): Double {
            skipSpaces()
            return when {
                consume('+') -> parseUnary()
                consume('-') -> -parseUnary()
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (consume('(')) {
                val value = parseComparison()
                requireConsume(')')
                return value
            }
            if (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) return parseNumber()
            val token = parseIdentifier()
            if (token.isEmpty()) throw FormulaException("#VALUE!")
            skipSpaces()
            if (consume('(')) return parseFunction(token)
            val ref = parseReference(token)
            return context.number(ref.first, ref.second)
        }

        private fun parseFunction(name: String): Double {
            val args = mutableListOf<Double>()
            val ranges = mutableListOf<List<Double>>()
            skipSpaces()
            if (!consume(')')) {
                while (true) {
                    val start = pos
                    val identifier = parseIdentifier()
                    skipSpaces()
                    if (identifier.isNotEmpty() && peek(':')) {
                        val ref = parseReference(identifier)
                        consume(':')
                        val end = parseReference(parseIdentifier())
                        if (ref.first != end.first) throw FormulaException("#REF!")
                        ranges += context.range(ref.first, CellRange(ref.second, end.second))
                    } else {
                        pos = start
                        args += parseComparison()
                    }
                    skipSpaces()
                    if (consume(')')) break
                    requireConsume(',')
                }
            }
            val all = args + ranges.flatten()
            return when (name.uppercase()) {
                "SUM" -> all.sum()
                "AVERAGE" -> all.averageOrError()
                "MIN" -> all.minOrError()
                "MAX" -> all.maxOrError()
                "COUNT" -> all.size.toDouble()
                "ROUND" -> {
                    val value = args.getOrNull(0) ?: throw FormulaException("#VALUE!")
                    val digits = args.getOrNull(1)?.toInt() ?: 0
                    val factor = 10.0.pow(digits)
                    round(value * factor) / factor
                }
                "IF" -> if ((args.getOrNull(0) ?: 0.0) != 0.0) args.getOrNull(1) ?: 0.0 else args.getOrNull(2) ?: 0.0
                "AND" -> if (args.all { it != 0.0 }) 1.0 else 0.0
                "OR" -> if (args.any { it != 0.0 }) 1.0 else 0.0
                else -> throw FormulaException("#NAME?")
            }
        }

        private fun List<Double>.averageOrError(): Double = if (isEmpty()) throw FormulaException("#DIV/0!") else average()
        private fun List<Double>.minOrError(): Double = minOrNull() ?: throw FormulaException("#VALUE!")
        private fun List<Double>.maxOrError(): Double = maxOrNull() ?: throw FormulaException("#VALUE!")

        private fun parseNumber(): Double {
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            return input.substring(start, pos).toDoubleOrNull() ?: throw FormulaException("#VALUE!")
        }

        private fun parseIdentifier(): String {
            skipSpaces()
            val start = pos
            while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_' || input[pos] == '!' || input[pos] == '$' || input[pos] == '.' || input[pos] == '\'' || input[pos] == '"')) pos++
            return input.substring(start, pos).replace("$", "")
        }

        private fun parseReference(token: String): Pair<String, CellAddress> {
            val parts = token.split('!', limit = 2)
            return if (parts.size == 2) parts[0].trim('"', '\'') to CellAddress.parse(parts[1])
            else "Sheet1" to CellAddress.parse(token)
        }

        private fun skipSpaces() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        private fun peek(c: Char): Boolean { skipSpaces(); return pos < input.length && input[pos] == c }
        private fun consume(c: Char): Boolean { skipSpaces(); return if (pos < input.length && input[pos] == c) { pos++; true } else false }
        private fun requireConsume(c: Char) { if (!consume(c)) throw FormulaException("#VALUE!") }
    }
}
