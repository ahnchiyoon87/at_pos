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

    // * 패키지 관련 리스너 모음 *
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
    
    // * 프로시저/함수 관련 리스너 모음 *
    @Override
    public void enterCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
        enterStatement("CREATE_PROCEDURE_BODY", ctx.getStart().getLine());

        enterStatement("SPEC", ctx.getStart().getLine());
        
        if (ctx.IS() != null) {
            exitStatement("SPEC", ctx.IS().getSymbol().getLine() - 1);
        } else if (ctx.AS() != null) {
            exitStatement("SPEC", ctx.AS().getSymbol().getLine() - 1);
        }
    }

    @Override
    public void exitCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
        exitStatement("CREATE_PROCEDURE_BODY", ctx.getStop().getLine());
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
    public void enterAssignment_statement(PlSqlParser.Assignment_statementContext ctx) {
        enterStatement("ASSIGNMENT", ctx.getStart().getLine());
    }

    @Override
    public void enterReturn_statement(PlSqlParser.Return_statementContext ctx) {
        enterStatement("RETURN", ctx.getStart().getLine());
    }

    @Override
    public void exitReturn_statement(PlSqlParser.Return_statementContext ctx) {
        exitStatement("RETURN", ctx.getStop().getLine());
    }

    // * DML 관련 리스너 모음 *
    @Override
    public void enterInsert_statement(PlSqlParser.Insert_statementContext ctx) {
        enterStatement("INSERT", ctx.getStart().getLine());
    }

    @Override
    public void exitInsert_statement(PlSqlParser.Insert_statementContext ctx) {
        exitStatement("INSERT", ctx.getStop().getLine());
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
    public void enterDelete_statement(PlSqlParser.Delete_statementContext ctx) {
        enterStatement("DELETE", ctx.getStart().getLine());
    }

    @Override
    public void exitDelete_statement(PlSqlParser.Delete_statementContext ctx) {
        exitStatement("DELETE", ctx.getStop().getLine());
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
    public void enterSelect_statement(PlSqlParser.Select_statementContext ctx) {
        enterStatement("SELECT", ctx.getStart().getLine());
    }

    @Override
    public void exitSelect_statement(PlSqlParser.Select_statementContext ctx) {
        exitStatement("SELECT", ctx.getStop().getLine());
    }
    // * 제어문 관련 리스너 모음 *
    @Override
    public void enterIf_statement(PlSqlParser.If_statementContext ctx) {
        enterStatement("IF", ctx.getStart().getLine());
    }

    @Override
    public void exitIf_statement(PlSqlParser.If_statementContext ctx) {
        exitStatement("IF", ctx.getStop().getLine());
    }

    @Override
    public void enterLoop_statement(PlSqlParser.Loop_statementContext ctx) {
        enterStatement("WHILE", ctx.getStart().getLine());
    }

    @Override
    public void exitLoop_statement(PlSqlParser.Loop_statementContext ctx) {
        exitStatement("WHILE", ctx.getStop().getLine());
    }

    // * 예외처리 관련 리스너 모음 *
    @Override
    public void enterException_handler(PlSqlParser.Exception_handlerContext ctx) {
        enterStatement("EXCEPTION", ctx.getStart().getLine());
    }
    
    @Override
    public void exitException_handler(PlSqlParser.Exception_handlerContext ctx) {
        exitStatement("EXCEPTION", ctx.getStop().getLine());
    }

    // * 기타 리스너 모음 *
    @Override
    public void exitAssignment_statement(PlSqlParser.Assignment_statementContext ctx) {
        exitStatement("ASSIGNMENT", ctx.getStop().getLine());
    }

    @Override
    public void enterExecute_immediate(PlSqlParser.Execute_immediateContext ctx) {
        enterStatement("EXECUTE_IMMDDIATE", ctx.getStart().getLine());
    }

    @Override
    public void exitExecute_immediate(PlSqlParser.Execute_immediateContext ctx) {
        exitStatement("EXECUTE_IMMDDIATE", ctx.getStop().getLine());
    }

    // * 커스텀된 리스너 메서드 모음 *
    @Override
    public void enterSeq_of_statements(PlSqlParser.Seq_of_statementsContext ctx) {
        String text = ctx.getText();
        
        // // 프로시저나 함수의 본문인 경우 (BodyContext의 자식이면서 Create_procedure_bodyContext나 Function_bodyContext의 손자)
        // if (ctx.getParent() instanceof PlSqlParser.BodyContext && 
        //     (ctx.getParent().getParent() instanceof PlSqlParser.Create_procedure_bodyContext ||
        //      ctx.getParent().getParent() instanceof PlSqlParser.Function_bodyContext ||
        //      ctx.getParent().getParent() instanceof PlSqlParser.Procedure_bodyContext)) {
        //     enterStatement("BODY", ctx.getStart().getLine());
        // }
        
        // // BEGIN-END 블록의 내용인 경우
        // else if (!text.contains("BEGIN") && 
        //          ctx.getParent() instanceof PlSqlParser.BodyContext && 
        //          ctx.getParent().getParent() instanceof PlSqlParser.StatementContext) {
        //     enterStatement("TRY", ctx.getStart().getLine());
        // }
        
        // BEGIN-END 블록의 내용인 경우
        if (!text.contains("BEGIN") && 
            ctx.getParent() instanceof PlSqlParser.BodyContext && 
            ctx.getParent().getParent() instanceof PlSqlParser.StatementContext) {
            enterStatement("TRY", ctx.getStart().getLine());
        }
    }
    

    @Override
    public void exitSeq_of_statements(PlSqlParser.Seq_of_statementsContext ctx) {
        String text = ctx.getText();
        
        // // 프로시저나 함수의 본문인 경우
        // if (ctx.getParent() instanceof PlSqlParser.BodyContext && 
        //     (ctx.getParent().getParent() instanceof PlSqlParser.Create_procedure_bodyContext ||
        //      ctx.getParent().getParent() instanceof PlSqlParser.Function_bodyContext ||
        //      ctx.getParent().getParent() instanceof PlSqlParser.Procedure_bodyContext)) {
        //     exitStatement("BODY", ctx.getStop().getLine());
        // }
        
        // // BEGIN-END 블록의 내용인 경우
        // else if (!text.contains("BEGIN") && 
        //          ctx.getParent() instanceof PlSqlParser.BodyContext && 
        //          ctx.getParent().getParent() instanceof PlSqlParser.StatementContext) {
        //     exitStatement("TRY", ctx.getStop().getLine());
        // }

        // BEGIN-END 블록의 내용인 경우
        if (!text.contains("BEGIN") && 
            ctx.getParent() instanceof PlSqlParser.BodyContext && 
            ctx.getParent().getParent() instanceof PlSqlParser.StatementContext) {
            exitStatement("TRY", ctx.getStop().getLine());
        }
    }
    
    @Override
    public void enterCall_statement(PlSqlParser.Call_statementContext ctx) {
        String statementType = "CALL";
        if (!ctx.routine_name().isEmpty()) {
            PlSqlParser.Routine_nameContext routineName = ctx.routine_name(0); 
            if (routineName.getText().toUpperCase().contains("RAISE")) {
                // statementType = "RAISE";
                return;
            }
        }
        enterStatement(statementType, ctx.getStart().getLine());
    }
    
    @Override
    public void exitCall_statement(PlSqlParser.Call_statementContext ctx) {
        String statementType = "CALL";
        if (!ctx.routine_name().isEmpty()) {
            PlSqlParser.Routine_nameContext routineName = ctx.routine_name(0);
            if (routineName.getText().toUpperCase().contains("RAISE")) {
                // statementType = "RAISE";
                return;
            }
        }
        exitStatement(statementType, ctx.getStop().getLine());
    }

    @Override
    public void enterPackage_obj_spec(PlSqlParser.Package_obj_specContext ctx) {
        String text = ctx.getText().toUpperCase().trim();
        String specType;
        
        if (text.startsWith("FUNCTION")) {
            specType = "FUNCTION_SPEC";
        } else if (text.startsWith("PROCEDURE")) {
            specType = "PROCEDURE_SPEC";
        } else {
            specType = "PACKAGE_VARIABLE";
        }
        enterStatement(specType, ctx.getStart().getLine());
    }
    
    @Override
    public void exitPackage_obj_spec(PlSqlParser.Package_obj_specContext ctx) {
        String text = ctx.getText().toUpperCase().trim();
        String specType;
        
        if (text.startsWith("FUNCTION")) {
            specType = "FUNCTION_SPEC";
        } else if (text.startsWith("PROCEDURE")) {
            specType = "PROCEDURE_SPEC";
        } else {
            specType = "PACKAGE_VARIABLE";
        }
        exitStatement(specType, ctx.getStop().getLine());
    }

    @Override
    public void enterPackage_obj_body(PlSqlParser.Package_obj_bodyContext ctx) {
        // FUNCTION 또는 PROCEDURE 타입 결정
        // function_body가 null이 아니면 "FUNCTION", null이면 "PROCEDURE"
        String memberType = ctx.function_body() != null ? "FUNCTION" : "PROCEDURE";
        
        // 1. 전체 함수/프로시저의 부모 노드 생성
        // nodeStack: [ROOT] -> [ROOT, FUNCTION/PROCEDURE]
        // 이때 FUNCTION/PROCEDURE의 parent는 ROOT가 됨
        enterStatement(memberType, ctx.getStart().getLine());
        
        // 2. IS/AS 이전까지의 선언부를 SPEC 노드로 생성
        // nodeStack: [ROOT, FUNCTION/PROCEDURE] -> [ROOT, FUNCTION/PROCEDURE, SPEC]
        enterStatement("SPEC", ctx.getStart().getLine());
    
        // IS나 AS를 만나면 SPEC 노드를 종료 (선언부 끝)
        // nodeStack: [ROOT, FUNCTION/PROCEDURE, SPEC] -> [ROOT, FUNCTION/PROCEDURE]
        Object bodyCtx = ctx.function_body() != null ? ctx.function_body() : ctx.procedure_body();
        if (bodyCtx instanceof PlSqlParser.Function_bodyContext) {
            PlSqlParser.Function_bodyContext funcCtx = (PlSqlParser.Function_bodyContext) bodyCtx;
            exitStatement("SPEC", (funcCtx.IS() != null ? funcCtx.IS() : funcCtx.AS()).getSymbol().getLine() - 1);
        } else {
            PlSqlParser.Procedure_bodyContext procCtx = (PlSqlParser.Procedure_bodyContext) bodyCtx;
            exitStatement("SPEC", (procCtx.IS() != null ? procCtx.IS() : procCtx.AS()).getSymbol().getLine() - 1);
        }
    }

    @Override
    public void exitPackage_obj_body(PlSqlParser.Package_obj_bodyContext ctx) {
        String memberType = ctx.function_body() != null ? "FUNCTION" : "PROCEDURE";
        exitStatement(memberType, ctx.getStop().getLine());
    }

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