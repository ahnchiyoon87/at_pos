package legacymodernizer.parser.antlr.dml_postgresql;

import java.util.Stack;
import org.antlr.v4.runtime.*;

import legacymodernizer.parser.antlr.Node;

/**
 * DML 전용 PostgreSQL Listener (단순화 버전)
 * UPDATE와 SELECT만 추출 (FROM, WHERE 등 세부 노드 제외)
 * SELECT와 SUBQUERY 모두 SELECT로 통일
 */
public class CustomDmlPostgreSQLListener extends PostgreSQLParserBaseListener {
    private Stack<Node> nodeStack = new Stack<>();
    private Node root = new Node("ROOT", 0, null);

    public Node getRoot() {
        return root;
    }

    public CustomDmlPostgreSQLListener(TokenStream tokens) {
        nodeStack.push(root);
    }

    private void enterStatement(String statementType, int line) {
        Node currentNode = new Node(statementType, line, nodeStack.peek());
        nodeStack.push(currentNode);
    }

    private void exitStatement(String statementType, int line) {
        Node node = nodeStack.pop();
        node.endLine = line;
    }

    // ========== DML (Data Manipulation Language) ==========

    // UPDATE
    @Override
    public void enterUpdatestmt(PostgreSQLParser.UpdatestmtContext ctx) {
        enterStatement("UPDATE", ctx.getStart().getLine());
    }

    @Override
    public void exitUpdatestmt(PostgreSQLParser.UpdatestmtContext ctx) {
        exitStatement("UPDATE", ctx.getStop().getLine());
    }

    // INSERT
    @Override
    public void enterInsertstmt(PostgreSQLParser.InsertstmtContext ctx) {
        enterStatement("INSERT", ctx.getStart().getLine());
    }

    @Override
    public void exitInsertstmt(PostgreSQLParser.InsertstmtContext ctx) {
        exitStatement("INSERT", ctx.getStop().getLine());
    }

    // DELETE
    @Override
    public void enterDeletestmt(PostgreSQLParser.DeletestmtContext ctx) {
        enterStatement("DELETE", ctx.getStart().getLine());
    }

    @Override
    public void exitDeletestmt(PostgreSQLParser.DeletestmtContext ctx) {
        exitStatement("DELETE", ctx.getStop().getLine());
    }

    // SELECT - 모든 SELECT 문 (독립적인 SELECT와 서브쿼리 모두 SELECT로 통일)
    @Override
    public void enterSelectstmt(PostgreSQLParser.SelectstmtContext ctx) {
        // selectstmt는 select_no_parens 또는 select_with_parens를 포함
        // 둘 다 SELECT로 처리
        enterStatement("SELECT", ctx.getStart().getLine());
    }

    @Override
    public void exitSelectstmt(PostgreSQLParser.SelectstmtContext ctx) {
        exitStatement("SELECT", ctx.getStop().getLine());
    }

    // SUBQUERY - 괄호로 감싼 서브쿼리도 SELECT로 통일
    @Override
    public void enterSelect_with_parens(PostgreSQLParser.Select_with_parensContext ctx) {
        enterStatement("SELECT", ctx.getStart().getLine());
    }

    @Override
    public void exitSelect_with_parens(PostgreSQLParser.Select_with_parensContext ctx) {
        exitStatement("SELECT", ctx.getStop().getLine());
    }

    // MERGE
    @Override
    public void enterMergestmt(PostgreSQLParser.MergestmtContext ctx) {
        enterStatement("MERGE", ctx.getStart().getLine());
    }

    @Override
    public void exitMergestmt(PostgreSQLParser.MergestmtContext ctx) {
        exitStatement("MERGE", ctx.getStop().getLine());
    }
}
