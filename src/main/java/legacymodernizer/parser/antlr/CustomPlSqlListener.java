package legacymodernizer.parser.antlr;

import java.util.Stack;

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

    // // 각 구문에 대한 enter와 exit 메서드 구현
    // @Override
    // public void enterIf_statement(PlSqlParser.If_statementContext ctx) {
    // enterStatement("IF", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitIf_statement(PlSqlParser.If_statementContext ctx) {
    // exitStatement("IF", ctx.getStop().getLine());
    // }

    // @Override
    // public void enterProcedure_body(PlSqlParser.Procedure_bodyContext ctx) {
    // enterStatement("PROCEDURE_BODY", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitProcedure_body(PlSqlParser.Procedure_bodyContext ctx) {
    // exitStatement("PROCEDURE_BODY", ctx.getStop().getLine());
    // }

    // @Override
    // public void enterFor_clause(PlSqlParser.For_clauseContext ctx) {
    // enterStatement("FOR", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitFor_clause(PlSqlParser.For_clauseContext ctx) {
    // exitStatement("FOR", ctx.getStop().getLine());
    // }

    // @Override
    // public void enterSelect_statement(PlSqlParser.Select_statementContext ctx) {
    // enterStatement("SELECT", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitSelect_statement(PlSqlParser.Select_statementContext ctx) {
    // exitStatement("SELECT", ctx.getStop().getLine());
    // }

    // // 예시: Update 구문에 대한 진입과 빠져나옴 메서드 추가
    // @Override
    // public void enterUpdate_statement(PlSqlParser.Update_statementContext ctx) {
    // enterStatement("UPDATE", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitUpdate_statement(PlSqlParser.Update_statementContext ctx) {
    // exitStatement("UPDATE", ctx.getStop().getLine());
    // }

    
    // @Override
    // public void enterBegin_or_end(PlSqlParser.Begin_or_endContext ctx) {
        // enterStatement("BEGIN_OR_END", ctx.getStart().getLine());
    // }
    
    // @Override
    // public void exitBegin_or_end(PlSqlParser.Begin_or_endContext ctx) {
        // exitStatement("BEGIN_OR_END", ctx.getStop().getLine());
    // }

    // @Override
    // public void enterDeclare_spec(PlSqlParser.Declare_specContext ctx) {
    // enterStatement("DECLARE", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitDeclare_spec(PlSqlParser.Declare_specContext ctx) {
    // exitStatement("DECLARE", ctx.getStop().getLine());
    // }

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

	@Override public void enterReturn_statement(PlSqlParser.Return_statementContext ctx) { 
        enterStatement("RETURN", ctx.getStart().getLine());
    }
    
    @Override public void exitReturn_statement(PlSqlParser.Return_statementContext ctx) { 
        exitStatement("RETURN", ctx.getStop().getLine());
    }
    
	@Override public void enterExceptions_clause(PlSqlParser.Exceptions_clauseContext ctx) { 
        enterStatement("EXCEPTION", ctx.getStart().getLine());
    }
    
    @Override public void exitExceptions_clause(PlSqlParser.Exceptions_clauseContext ctx) { 
        exitStatement("EXCEPTION", ctx.getStop().getLine());
    }

    // @Override
    // public void enterStatement(PlSqlParser.StatementContext ctx) {
    //     enterStatement("STATEMENT", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitStatement(PlSqlParser.StatementContext ctx) {
    //     exitStatement("STATEMENT", ctx.getStop().getLine());
    // }

    // 나머지 구문(Select, Update 등)에 대한 enter와 exit 메서드도 비슷한 방식으로 구현합니다.

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