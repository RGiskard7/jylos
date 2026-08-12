package com.example.jylos.plugin.builtin.dataview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.jylos.plugin.builtin.dataview.DqlLexer.Token;
import com.example.jylos.plugin.builtin.dataview.DqlLexer.Type;

/**
 * Recursive-descent parser for the query language.
 *
 * <p>Grammar (clauses may appear in any order after the projection):</p>
 * <pre>
 * query   := ('TABLE' ['WITHOUT' 'ID'] columns? | 'LIST' ['WITHOUT' 'ID'] expr? | 'TASK') clause*
 * clause  := 'FROM' source | 'WHERE' expr | 'SORT' sortKey (',' sortKey)*
 *          | 'GROUP' 'BY' expr ['AS' name] | 'FLATTEN' expr ['AS' name] | 'LIMIT' number
 * source  := sourceAnd ('or' sourceAnd)*
 * expr    := or | and | comparison | additive | multiplicative | unary | postfix | primary
 * </pre>
 */
final class DqlParser {

    /** Words that end a projection or expression because they open the next clause. */
    private static final Set<String> CLAUSE_KEYWORDS = Set.of(
            "from", "where", "sort", "group", "flatten", "limit");

    private final List<Token> tokens;
    private int index;

    private DqlParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    static Ast.Query parse(String source) {
        DqlParser parser = new DqlParser(DqlLexer.tokenize(source));
        Ast.Query query = parser.parseQuery();
        parser.expectEnd();
        return query;
    }

    /** Parses a standalone expression, used by inline {@code `= expr`} queries. */
    static Ast.Expr parseExpression(String source) {
        DqlParser parser = new DqlParser(DqlLexer.tokenize(source));
        Ast.Expr expression = parser.expression();
        parser.expectEnd();
        return expression;
    }

    // ── Query ────────────────────────────────────────────────────────────────

    private Ast.Query parseQuery() {
        Ast.Kind kind = parseKind();
        boolean withoutId = false;
        if (peek().isKeyword("without")) {
            advance();
            expectKeyword("id");
            withoutId = true;
        }

        List<Ast.Column> columns = new ArrayList<>();
        if (kind == Ast.Kind.TABLE) {
            columns.addAll(parseColumns());
        } else if (kind == Ast.Kind.LIST && !atClauseBoundary()) {
            Ast.Expr expression = expression();
            columns.add(new Ast.Column(expression, aliasOr(expression)));
        }

        Ast.Source source = new Ast.AllPages();
        List<Ast.Expr> filters = new ArrayList<>();
        List<Ast.SortBy> sorts = new ArrayList<>();
        List<Ast.Column> flattens = new ArrayList<>();
        Ast.Expr groupBy = null;
        String groupName = "key";
        Integer limit = null;

        while (peek().type() != Type.EOF) {
            Token token = peek();
            if (token.isKeyword("from")) {
                advance();
                source = parseSource();
            } else if (token.isKeyword("where")) {
                advance();
                filters.add(expression());
            } else if (token.isKeyword("sort")) {
                advance();
                sorts.addAll(parseSorts());
            } else if (token.isKeyword("group")) {
                advance();
                expectKeyword("by");
                groupBy = expression();
                if (peek().isKeyword("as")) {
                    advance();
                    groupName = parseAliasName();
                }
            } else if (token.isKeyword("flatten")) {
                advance();
                Ast.Expr expression = expression();
                String name = aliasOr(expression);
                if (peek().isKeyword("as")) {
                    advance();
                    name = parseAliasName();
                }
                flattens.add(new Ast.Column(expression, name));
            } else if (token.isKeyword("limit")) {
                advance();
                Token number = expect(Type.NUMBER, "a number after LIMIT");
                limit = (int) Double.parseDouble(number.text());
            } else {
                throw new DqlException("Unexpected '" + token.text() + "' — expected a clause "
                        + "(FROM, WHERE, SORT, GROUP BY, FLATTEN, LIMIT)");
            }
        }

        return new Ast.Query(kind, withoutId, columns, source, filters, sorts,
                groupBy, groupName, flattens, limit);
    }

    private Ast.Kind parseKind() {
        Token token = peek();
        if (token.type() != Type.IDENT) {
            throw new DqlException("A query must start with TABLE, LIST or TASK");
        }
        return switch (token.text().toLowerCase(Locale.ROOT)) {
            case "table" -> {
                advance();
                yield Ast.Kind.TABLE;
            }
            case "list" -> {
                advance();
                yield Ast.Kind.LIST;
            }
            case "task" -> {
                advance();
                yield Ast.Kind.TASK;
            }
            default -> throw new DqlException(
                    "Unknown query type '" + token.text() + "' — expected TABLE, LIST or TASK");
        };
    }

    private List<Ast.Column> parseColumns() {
        List<Ast.Column> columns = new ArrayList<>();
        if (atClauseBoundary()) {
            return columns;
        }
        while (true) {
            Ast.Expr expression = expression();
            String header = aliasOr(expression);
            if (peek().isKeyword("as")) {
                advance();
                header = parseAliasName();
            }
            columns.add(new Ast.Column(expression, header));
            if (peek().isOperator(",")) {
                advance();
                continue;
            }
            return columns;
        }
    }

    private List<Ast.SortBy> parseSorts() {
        List<Ast.SortBy> sorts = new ArrayList<>();
        while (true) {
            Ast.Expr expression = expression();
            boolean ascending = true;
            if (peek().isKeyword("asc") || peek().isKeyword("ascending")) {
                advance();
            } else if (peek().isKeyword("desc") || peek().isKeyword("descending")) {
                advance();
                ascending = false;
            }
            sorts.add(new Ast.SortBy(expression, ascending));
            if (peek().isOperator(",")) {
                advance();
                continue;
            }
            return sorts;
        }
    }

    private String parseAliasName() {
        Token token = peek();
        if (token.type() == Type.STRING || token.type() == Type.IDENT) {
            advance();
            return token.text();
        }
        throw new DqlException("Expected a name after AS");
    }

    /** Default column header: the field path, so {@code file.name} reads as "file.name". */
    private static String aliasOr(Ast.Expr expression) {
        return describe(expression);
    }

    private static String describe(Ast.Expr expression) {
        if (expression instanceof Ast.Variable variable) {
            return variable.name();
        }
        if (expression instanceof Ast.FieldAccess field) {
            return describe(field.target()) + "." + field.name();
        }
        if (expression instanceof Ast.Call call) {
            return call.name() + "(…)";
        }
        if (expression instanceof Ast.Literal literal) {
            return DqlValue.toDisplayString(literal.value());
        }
        return "value";
    }

    private boolean atClauseBoundary() {
        Token token = peek();
        return token.type() == Type.EOF
                || (token.type() == Type.IDENT && CLAUSE_KEYWORDS.contains(token.text().toLowerCase(Locale.ROOT)));
    }

    // ── FROM sources ─────────────────────────────────────────────────────────

    private Ast.Source parseSource() {
        Ast.Source left = parseSourceAnd();
        while (peek().isKeyword("or") || peek().isOperator("||")) {
            advance();
            left = new Ast.BinarySource("or", left, parseSourceAnd());
        }
        return left;
    }

    private Ast.Source parseSourceAnd() {
        Ast.Source left = parseSourceUnary();
        while (peek().isKeyword("and") || peek().isOperator("&&")) {
            advance();
            left = new Ast.BinarySource("and", left, parseSourceUnary());
        }
        return left;
    }

    private Ast.Source parseSourceUnary() {
        if (peek().isOperator("-") || peek().isOperator("!") || peek().isKeyword("not")) {
            advance();
            return new Ast.NotSource(parseSourceUnary());
        }
        return parseSourcePrimary();
    }

    private Ast.Source parseSourcePrimary() {
        Token token = peek();
        if (token.type() == Type.TAG) {
            advance();
            return new Ast.TagSource(token.text());
        }
        if (token.type() == Type.STRING) {
            advance();
            return new Ast.FolderSource(token.text());
        }
        if (token.type() == Type.LINK) {
            advance();
            return new Ast.IncomingLinks(bareTarget(token.text()));
        }
        if (token.isKeyword("outgoing") || token.isKeyword("incoming")) {
            boolean outgoing = token.isKeyword("outgoing");
            advance();
            expectOperator("(");
            Token link = expect(Type.LINK, "a [[link]] inside " + (outgoing ? "outgoing()" : "incoming()"));
            expectOperator(")");
            String target = bareTarget(link.text());
            return outgoing ? new Ast.OutgoingLinks(target) : new Ast.IncomingLinks(target);
        }
        if (token.isOperator("(")) {
            advance();
            Ast.Source inner = parseSource();
            expectOperator(")");
            return inner;
        }
        throw new DqlException("Invalid FROM source near '" + token.text()
                + "' — expected #tag, \"folder\", [[link]] or outgoing([[link]])");
    }

    private static String bareTarget(String linkText) {
        return linkText.split("\\|", 2)[0].split("#", 2)[0].trim();
    }

    // ── Expressions ──────────────────────────────────────────────────────────

    private Ast.Expr expression() {
        return or();
    }

    private Ast.Expr or() {
        Ast.Expr left = and();
        while (peek().isKeyword("or") || peek().isOperator("||")) {
            advance();
            left = new Ast.Binary("or", left, and());
        }
        return left;
    }

    private Ast.Expr and() {
        Ast.Expr left = comparison();
        while (peek().isKeyword("and") || peek().isOperator("&&")) {
            advance();
            left = new Ast.Binary("and", left, comparison());
        }
        return left;
    }

    private Ast.Expr comparison() {
        Ast.Expr left = additive();
        while (true) {
            Token token = peek();
            String operator = null;
            if (token.type() == Type.OPERATOR) {
                switch (token.text()) {
                    case "=", "==" -> operator = "=";
                    case "!=", "<>" -> operator = "!=";
                    case ">", ">=", "<", "<=" -> operator = token.text();
                    default -> operator = null;
                }
            }
            if (operator == null) {
                return left;
            }
            advance();
            left = new Ast.Binary(operator, left, additive());
        }
    }

    private Ast.Expr additive() {
        Ast.Expr left = multiplicative();
        while (peek().isOperator("+") || peek().isOperator("-")) {
            String operator = advance().text();
            left = new Ast.Binary(operator, left, multiplicative());
        }
        return left;
    }

    private Ast.Expr multiplicative() {
        Ast.Expr left = unary();
        while (peek().isOperator("*") || peek().isOperator("/") || peek().isOperator("%")) {
            String operator = advance().text();
            left = new Ast.Binary(operator, left, unary());
        }
        return left;
    }

    private Ast.Expr unary() {
        Token token = peek();
        if (token.isOperator("-")) {
            advance();
            return new Ast.Unary("-", unary());
        }
        if (token.isOperator("!") || token.isKeyword("not")) {
            advance();
            return new Ast.Unary("!", unary());
        }
        return postfix();
    }

    private Ast.Expr postfix() {
        Ast.Expr target = primary();
        while (true) {
            Token token = peek();
            if (token.isOperator(".")) {
                advance();
                Token name = peek();
                if (name.type() != Type.IDENT && name.type() != Type.STRING) {
                    throw new DqlException("Expected a field name after '.'");
                }
                advance();
                target = new Ast.FieldAccess(target, name.text());
            } else if (token.isOperator("[")) {
                advance();
                Ast.Expr index = expression();
                expectOperator("]");
                target = new Ast.IndexAccess(target, index);
            } else {
                return target;
            }
        }
    }

    private Ast.Expr primary() {
        Token token = peek();
        switch (token.type()) {
            case NUMBER -> {
                advance();
                return new Ast.Literal(Double.parseDouble(token.text()));
            }
            case STRING -> {
                advance();
                return new Ast.Literal(token.text());
            }
            case LINK -> {
                advance();
                String inner = token.text();
                String[] parts = inner.split("\\|", 2);
                String target = parts[0].split("#", 2)[0].trim();
                return new Ast.Literal(new Link(target, parts.length > 1 ? parts[1].trim() : target));
            }
            case TAG -> {
                advance();
                return new Ast.Literal(token.text());
            }
            case IDENT -> {
                return identifierExpression();
            }
            case OPERATOR -> {
                if (token.isOperator("(")) {
                    advance();
                    Ast.Expr inner = expression();
                    expectOperator(")");
                    return inner;
                }
                if (token.isOperator("[")) {
                    return listLiteral();
                }
                if (token.isOperator("{")) {
                    return objectLiteral();
                }
                throw new DqlException("Unexpected '" + token.text() + "' in expression");
            }
            default -> throw new DqlException("Unexpected end of query");
        }
    }

    private Ast.Expr identifierExpression() {
        Token token = advance();
        String name = token.text();
        String lower = name.toLowerCase(Locale.ROOT);

        if ("true".equals(lower) || "false".equals(lower)) {
            return new Ast.Literal(Boolean.parseBoolean(lower));
        }
        if ("null".equals(lower)) {
            return new Ast.Literal(null);
        }
        if (peek().isOperator("(")) {
            advance();
            List<Ast.Expr> arguments = new ArrayList<>();
            if (!peek().isOperator(")")) {
                while (true) {
                    arguments.add(expression());
                    if (peek().isOperator(",")) {
                        advance();
                        continue;
                    }
                    break;
                }
            }
            expectOperator(")");
            return new Ast.Call(lower, arguments);
        }
        return new Ast.Variable(name);
    }

    private Ast.Expr listLiteral() {
        expectOperator("[");
        List<Ast.Expr> elements = new ArrayList<>();
        if (!peek().isOperator("]")) {
            while (true) {
                elements.add(expression());
                if (peek().isOperator(",")) {
                    advance();
                    continue;
                }
                break;
            }
        }
        expectOperator("]");
        return new Ast.ListLiteral(elements);
    }

    private Ast.Expr objectLiteral() {
        expectOperator("{");
        Map<String, Ast.Expr> entries = new LinkedHashMap<>();
        if (!peek().isOperator("}")) {
            while (true) {
                Token key = peek();
                if (key.type() != Type.IDENT && key.type() != Type.STRING) {
                    throw new DqlException("Expected a key in object literal");
                }
                advance();
                expectOperator(":");
                entries.put(key.text(), expression());
                if (peek().isOperator(",")) {
                    advance();
                    continue;
                }
                break;
            }
        }
        expectOperator("}");
        return new Ast.ObjectLiteral(entries);
    }

    // ── Token helpers ────────────────────────────────────────────────────────

    private Token peek() {
        return tokens.get(Math.min(index, tokens.size() - 1));
    }

    private Token advance() {
        Token token = peek();
        if (index < tokens.size() - 1) {
            index++;
        }
        return token;
    }

    private Token expect(Type type, String description) {
        Token token = peek();
        if (token.type() != type) {
            throw new DqlException("Expected " + description + " but found '" + token.text() + "'");
        }
        return advance();
    }

    private void expectOperator(String operator) {
        if (!peek().isOperator(operator)) {
            throw new DqlException("Expected '" + operator + "' but found '" + peek().text() + "'");
        }
        advance();
    }

    private void expectKeyword(String keyword) {
        if (!peek().isKeyword(keyword)) {
            throw new DqlException("Expected '" + keyword.toUpperCase(Locale.ROOT)
                    + "' but found '" + peek().text() + "'");
        }
        advance();
    }

    private void expectEnd() {
        if (peek().type() != Type.EOF) {
            throw new DqlException("Unexpected trailing input: '" + peek().text() + "'");
        }
    }
}
