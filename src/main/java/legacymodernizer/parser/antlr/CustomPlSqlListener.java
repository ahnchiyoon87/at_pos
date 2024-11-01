package legacymodernizer.parser.antlr;

import java.util.Stack;
import legacymodernizer.parser.antlr.plsql.PlSqlParserBaseListener;
import legacymodernizer.parser.antlr.plsql.PlSqlParser;
import org.antlr.v4.runtime.TokenStream;

public class CustomPlSqlListener extends PlSqlParserBaseListener {
    @SuppressWarnings("unused")
    private TokenStream tokens;
    private Stack<Node> nodeStack = new Stack<>();
    private Node root = new Node("ROOT", 0, null); // 루트 노드

    public Node getRoot() {
        return root;
    }

    public CustomPlSqlListener(TokenStream tokens) {
        this.tokens = tokens;
        nodeStack.push(root); // 초기 상태에서 루트 노드를 스택에 푸시
    }

    private void enterStatement(String statementType, int line) {
        Node currentNode = new Node(statementType, line, nodeStack.peek());
        nodeStack.push(currentNode);
        // System.out.println("Enter " + statementType + " Statement Line: " + line);
    }

    private void exitStatement(String statementType, int line) {
        Node node = nodeStack.pop();
        node.endLine = line;
        // System.out.println("Exit " + statementType + " Statement Line: " + line);
    }

    @Override
    public void enterCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
        enterStatement("CREATE_PROCEDURE_BODY", ctx.getStart().getLine());
    }

    @Override
    public void exitCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
        exitStatement("CREATE_PROCEDURE_BODY", ctx.getStop().getLine());
    }

    @Override
    public void enterAssignment_statement(PlSqlParser.Assignment_statementContext ctx) {
        enterStatement("ASSIGNMENT", ctx.getStart().getLine());
    }

    @Override
    public void exitAssignment_statement(PlSqlParser.Assignment_statementContext ctx) {
        exitStatement("ASSIGNMENT", ctx.getStart().getLine());
    }

    @Override
    public void enterIf_statement(PlSqlParser.If_statementContext ctx) {
        enterStatement("IF", ctx.getStart().getLine());
    }

    @Override
    public void exitIf_statement(PlSqlParser.If_statementContext ctx) {
        exitStatement("IF", ctx.getStop().getLine());
    }

    @Override
    public void enterInsert_statement(PlSqlParser.Insert_statementContext ctx) {
        enterStatement("INSERT", ctx.getStart().getLine());
    }

    @Override
    public void exitInsert_statement(PlSqlParser.Insert_statementContext ctx) {
        exitStatement("INSERT", ctx.getStop().getLine());
    }

    @Override
    public void enterSelect_statement(PlSqlParser.Select_statementContext ctx) {
        enterStatement("SELECT", ctx.getStart().getLine());
    }

    @Override
    public void exitSelect_statement(PlSqlParser.Select_statementContext ctx) {
        exitStatement("SELECT", ctx.getStop().getLine());
    }

    @Override
    public void enterUpdate_statement(PlSqlParser.Update_statementContext ctx) {
        enterStatement("UPDATE", ctx.getStart().getLine());
    }

    @Override
    public void exitUpdate_statement(PlSqlParser.Update_statementContext ctx) {
        exitStatement("UPDATE", ctx.getStop().getLine());
    }

    @Override
    public void enterExecute_immediate(PlSqlParser.Execute_immediateContext ctx) {
        enterStatement("EXECUTE_IMMDDIATE", ctx.getStart().getLine());
    }

    @Override
    public void exitExecute_immediate(PlSqlParser.Execute_immediateContext ctx) {
        exitStatement("EXECUTE_IMMDDIATE", ctx.getStop().getLine());
    }

    @Override
    public void enterMerge_statement(PlSqlParser.Merge_statementContext ctx) {
        enterStatement("MERGE", ctx.getStart().getLine());
    }

    @Override
    public void exitMerge_statement(PlSqlParser.Merge_statementContext ctx) {
        exitStatement("MERGE", ctx.getStop().getLine());
    }

    @Override
    public void enterDelete_statement(PlSqlParser.Delete_statementContext ctx) {
        enterStatement("DELETE", ctx.getStart().getLine());
    }

    @Override
    public void exitDelete_statement(PlSqlParser.Delete_statementContext ctx) {
        exitStatement("DELETE", ctx.getStop().getLine());
    }

    @Override
    public void enterLoop_statement(PlSqlParser.Loop_statementContext ctx) {
        enterStatement("WHILE", ctx.getStart().getLine());
    }

    @Override
    public void exitLoop_statement(PlSqlParser.Loop_statementContext ctx) {
        exitStatement("WHILE", ctx.getStop().getLine());
    }

    @Override
    public void enterSeq_of_declare_specs(PlSqlParser.Seq_of_declare_specsContext ctx) {
        enterStatement("DECLARE", ctx.getStart().getLine());
    }

    @Override
    public void exitSeq_of_declare_specs(PlSqlParser.Seq_of_declare_specsContext ctx) {
        exitStatement("DECLARE", ctx.getStop().getLine());
    }

    @Override
    public void enterReturn_statement(PlSqlParser.Return_statementContext ctx) {
        enterStatement("RETURN", ctx.getStart().getLine());
    }

    @Override
    public void exitReturn_statement(PlSqlParser.Return_statementContext ctx) {
        exitStatement("RETURN", ctx.getStop().getLine());
    }

    @Override
    public void enterExceptions_clause(PlSqlParser.Exceptions_clauseContext ctx) {
        enterStatement("EXCEPTION", ctx.getStart().getLine());
    }

    @Override
    public void exitExceptions_clause(PlSqlParser.Exceptions_clauseContext ctx) {
        exitStatement("EXCEPTION", ctx.getStop().getLine());
    }

    @Override
    public void enterCall_statement(PlSqlParser.Call_statementContext ctx) {
        enterStatement("CALL", ctx.getStart().getLine());
    }

    @Override
    public void exitCall_statement(PlSqlParser.Call_statementContext ctx) {
        enterStatement("CALL", ctx.getStart().getLine());
    }

    @Override
    public void enterCreate_package(PlSqlParser.Create_packageContext ctx) {
        enterStatement("PACKAGE_SPEC", ctx.getStart().getLine());
    }

    @Override
    public void exitCreate_package(PlSqlParser.Create_packageContext ctx) {
        exitStatement("PACKAGE_SPEC", ctx.getStop().getLine());
    }

    @Override
    public void enterCreate_package_body(PlSqlParser.Create_package_bodyContext ctx) {
        enterStatement("PACKAGE_BODY", ctx.getStart().getLine());
    }

    @Override
    public void exitCreate_package_body(PlSqlParser.Create_package_bodyContext ctx) {
        exitStatement("PACKAGE_BODY", ctx.getStop().getLine());
    }

    @Override
    public void enterProcedure_spec(PlSqlParser.Procedure_specContext ctx) {
        enterStatement("PROCEDURE_SPEC", ctx.getStart().getLine());
    }

    @Override
    public void exitProcedure_spec(PlSqlParser.Procedure_specContext ctx) {
        exitStatement("PROCEDURE_SPEC", ctx.getStop().getLine());
    }

    @Override
    public void enterFunction_spec(PlSqlParser.Function_specContext ctx) {
        enterStatement("FUNCTION_SPEC", ctx.getStart().getLine());
    }

    @Override
    public void exitFunction_spec(PlSqlParser.Function_specContext ctx) {
        exitStatement("FUNCTION_SPEC", ctx.getStop().getLine());
    }

    @Override
    public void enterFunction_body(PlSqlParser.Function_bodyContext ctx) {
        enterStatement("FUNCTION_BODY", ctx.getStart().getLine());
    }

    @Override
    public void exitFunction_body(PlSqlParser.Function_bodyContext ctx) {
        exitStatement("FUNCTION_BODY", ctx.getStop().getLine());
    }

    @Override
    public void enterVariable_declaration(PlSqlParser.Variable_declarationContext ctx) {
        enterStatement("VARIABLE", ctx.getStart().getLine());
    }

    @Override
    public void exitVariable_declaration(PlSqlParser.Variable_declarationContext ctx) {
        exitStatement("VARIABLE", ctx.getStop().getLine());
    }

    // @Override
    // public void enterStatement(PlSqlParser.StatementContext ctx) {
    // enterStatement("STATEMENT", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitStatement(PlSqlParser.StatementContext ctx) {
    // exitStatement("STATEMENT", ctx.getStop().getLine());
    // }

    // 트리 구조를 출력하는 메서드 (디버깅 목적)
    public void printTree(Node node, String indent) {
        System.out.println(indent + node.type + " (" + node.startLine + ", " + node.endLine + ")");
        for (Node child : node.children) {
            printTree(child, indent + "  ");
        }
    }

    // 트리 구조 출력을 위한 메서드 호출 예시
    public void printStructure() {
        printTree(root, "");
    }
}