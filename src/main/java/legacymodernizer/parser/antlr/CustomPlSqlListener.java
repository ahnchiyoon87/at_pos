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

    /* ------------------------------------------------------------ */
    /* Procedure/Function/Trigger/Package 관련 리스너 모음 */
    /* ------------------------------------------------------------ */
    @Override
    public void enterCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
        enterStatement("PROCEDURE", ctx.getStart().getLine());
        enterStatement("SPEC", ctx.getStart().getLine());
        
        if (ctx.IS() != null) {
            exitStatement("SPEC", ctx.IS().getSymbol().getLine() - 1);
        } else if (ctx.AS() != null) {
            exitStatement("SPEC", ctx.AS().getSymbol().getLine() - 1);
        }
    }

    @Override
    public void exitCreate_procedure_body(PlSqlParser.Create_procedure_bodyContext ctx) {
        exitStatement("PROCEDURE", ctx.getStop().getLine());
    }

    @Override
    public void enterCreate_function_body(PlSqlParser.Create_function_bodyContext ctx) {
        enterStatement("FUNCTION", ctx.getStart().getLine());
        enterStatement("SPEC", ctx.getStart().getLine());
        if (ctx.IS() != null) {
            exitStatement("SPEC", ctx.IS().getSymbol().getLine() - 1);
        } else if (ctx.AS() != null) {
            exitStatement("SPEC", ctx.AS().getSymbol().getLine() - 1);
        }
    }

    @Override
    public void exitCreate_function_body(PlSqlParser.Create_function_bodyContext ctx) {
        exitStatement("FUNCTION", ctx.getStop().getLine());
    }

    @Override
    public void enterPackage_obj_spec(PlSqlParser.Package_obj_specContext ctx) {
        String text = ctx.getText().toUpperCase().trim();
        
        // FUNCTION이나 PROCEDURE로 시작하면 무시하고 리턴
        if (text.startsWith("FUNCTION") || text.startsWith("PROCEDURE")) {
            return;
        }
        
        // 그 외의 경우만 PACKAGE_VARIABLE로 처리
        enterStatement("PACKAGE_VARIABLE", ctx.getStart().getLine());
    }
    
    @Override
    public void exitPackage_obj_spec(PlSqlParser.Package_obj_specContext ctx) {
        String text = ctx.getText().toUpperCase().trim();
        
        // FUNCTION이나 PROCEDURE로 시작하면 무시하고 리턴
        if (text.startsWith("FUNCTION") || text.startsWith("PROCEDURE")) {
            return;
        }
        
        // 그 외의 경우만 PACKAGE_VARIABLE로 처리
        exitStatement("PACKAGE_VARIABLE", ctx.getStop().getLine());
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
            exitStatement("SPEC", (funcCtx.IS() != null ? funcCtx.IS() : funcCtx.AS()).getSymbol().getLine());
        } else {
            PlSqlParser.Procedure_bodyContext procCtx = (PlSqlParser.Procedure_bodyContext) bodyCtx;
            exitStatement("SPEC", (procCtx.IS() != null ? procCtx.IS() : procCtx.AS()).getSymbol().getLine());
        }
    }

    @Override
    public void exitPackage_obj_body(PlSqlParser.Package_obj_bodyContext ctx) {
        String memberType = ctx.function_body() != null ? "FUNCTION" : "PROCEDURE";
        exitStatement(memberType, ctx.getStop().getLine());
    }

    @Override
    public void enterCreate_trigger(PlSqlParser.Create_triggerContext ctx) {
        enterStatement("TRIGGER", ctx.getStart().getLine());
        enterStatement("HEADER", ctx.getStart().getLine());
    }
    @Override
    public void exitCreate_trigger(PlSqlParser.Create_triggerContext ctx) {
        exitStatement("TRIGGER", ctx.getStop().getLine());
    }
    @Override
    public void enterTrigger_block(PlSqlParser.Trigger_blockContext ctx) {
        exitStatement("HEADER", ctx.getStart().getLine() - 1);
        enterStatement("TRIGGER_BLOCK", ctx.getStart().getLine());
    }
    @Override
    public void exitTrigger_block(PlSqlParser.Trigger_blockContext ctx) {
        exitStatement("TRIGGER_BLOCK", ctx.getStop().getLine());
    }

    // @Override
    // public void enterProcedure_spec(PlSqlParser.Procedure_specContext ctx) {
    //     enterStatement("PROCEDURE_SPEC", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitProcedure_spec(PlSqlParser.Procedure_specContext ctx) {
    //     exitStatement("PROCEDURE_SPEC", ctx.getStop().getLine());
    // }

    // @Override
    // public void enterFunction_spec(PlSqlParser.Function_specContext ctx) {
    //     enterStatement("FUNCTION_SPEC", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitFunction_spec(PlSqlParser.Function_specContext ctx) {
    //     exitStatement("FUNCTION_SPEC", ctx.getStop().getLine());
    // }

    /* ------------------------------------------------------------ */
    /* DECLARE/ASSIGNMENT/RETURN 변수 관련 리스너 모음 */
    /* ------------------------------------------------------------ */
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
    public void exitAssignment_statement(PlSqlParser.Assignment_statementContext ctx) {
        exitStatement("ASSIGNMENT", ctx.getStop().getLine());
    }

    @Override
    public void enterReturn_statement(PlSqlParser.Return_statementContext ctx) {
        enterStatement("RETURN", ctx.getStart().getLine());
    }

    @Override
    public void exitReturn_statement(PlSqlParser.Return_statementContext ctx) {
        exitStatement("RETURN", ctx.getStop().getLine());
    }

    /* ------------------------------------------------------------ */
    /* INSERT/UPDATE/DELETE/MERGE/SELECT 관련 리스너 모음 */
    /* ------------------------------------------------------------ */

    @Override
    public void enterQuery_block(PlSqlParser.Query_blockContext ctx) {
        enterStatement("SELECT", ctx.getStart().getLine());
    }
    @Override
    public void exitQuery_block(PlSqlParser.Query_blockContext ctx) {
        exitStatement("SELECT", ctx.getStop().getLine());
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

    // @Override
    // public void enterSelect_statement(PlSqlParser.Select_statementContext ctx) {
    //     enterStatement("SELECT", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitSelect_statement(PlSqlParser.Select_statementContext ctx) {
    //     exitStatement("SELECT", ctx.getStop().getLine());
    // }

    /* ------------------------------------------------------------ */
    /* IF/ELSIF/ELSE/LOOP 관련 리스너 모음 */
    /* ------------------------------------------------------------ */
    @Override
    public void enterIf_statement(PlSqlParser.If_statementContext ctx) {
        enterStatement("IF", ctx.getStart().getLine());
    }

    @Override
    public void exitIf_statement(PlSqlParser.If_statementContext ctx) {
        exitStatement("IF", ctx.getStop().getLine());
    }

    @Override
    public void enterElsif_part(PlSqlParser.Elsif_partContext ctx) {
        enterStatement("ELSIF", ctx.getStart().getLine());
    }

    @Override
    public void exitElsif_part(PlSqlParser.Elsif_partContext ctx) {
        exitStatement("ELSIF", ctx.getStop().getLine());
    }
    
    @Override
    public void enterElse_part(PlSqlParser.Else_partContext ctx) {
        enterStatement("ELSE", ctx.getStart().getLine());
    }

    @Override
    public void exitElse_part(PlSqlParser.Else_partContext ctx) {
        exitStatement("ELSE", ctx.getStop().getLine());
    }
    
    @Override
    public void enterLoop_statement(PlSqlParser.Loop_statementContext ctx) {
        enterStatement("LOOP", ctx.getStart().getLine());
    }

    @Override
    public void exitLoop_statement(PlSqlParser.Loop_statementContext ctx) {
        exitStatement("LOOP", ctx.getStop().getLine());
    }

    @Override
    public void enterSingle_column_for_loop(PlSqlParser.Single_column_for_loopContext ctx) {
        enterStatement("LOOP", ctx.getStart().getLine());
    }
    @Override
    public void exitSingle_column_for_loop(PlSqlParser.Single_column_for_loopContext ctx) {
        exitStatement("LOOP", ctx.getStop().getLine());
    }
    
    @Override
    public void enterMulti_column_for_loop(PlSqlParser.Multi_column_for_loopContext ctx) {
        enterStatement("LOOP", ctx.getStart().getLine());
    }
    @Override
    public void exitMulti_column_for_loop(PlSqlParser.Multi_column_for_loopContext ctx) {
        exitStatement("LOOP", ctx.getStop().getLine());
    }

    /* ------------------------------------------------------------ */
    /* EXCEPTION 관련 리스너 모음 */
    /* ------------------------------------------------------------ */
    @Override
    public void enterException_handler(PlSqlParser.Exception_handlerContext ctx) {
        if (!"EXCEPTION".equals(nodeStack.peek().type)) { // 첫 핸들러라면
            enterStatement("EXCEPTION", ctx.getStart().getLine());
        }
    }
    
    @Override
    public void exitException_handler(PlSqlParser.Exception_handlerContext ctx) {
        // no-op: 핸들러마다 닫지 않음
    }
    
    @Override
    public void exitBody(PlSqlParser.BodyContext ctx) {
        if (!nodeStack.isEmpty() && "EXCEPTION".equals(nodeStack.peek().type)) {
            exitStatement("EXCEPTION", ctx.getStop().getLine());
        }
    }

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
            enterStatement("TRY", ctx.getStart().getLine() - 1); // TRY 노드의 시작 라인을 -1 적용
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

    /* ------------------------------------------------------------ */
    /* CURSOR 관련 리스너 모음 */
    /* ------------------------------------------------------------ */
    @Override
    public void enterOpen_statement(PlSqlParser.Open_statementContext ctx) {
        enterStatement("OPEN_CURSOR", ctx.getStart().getLine());
    }
    @Override
    public void exitOpen_statement(PlSqlParser.Open_statementContext ctx) {
        exitStatement("OPEN_CURSOR", ctx.getStop().getLine());
    }
    
    @Override
    public void enterFetch_statement(PlSqlParser.Fetch_statementContext ctx) {
        enterStatement("FETCH", ctx.getStart().getLine());
    }
    @Override
    public void exitFetch_statement(PlSqlParser.Fetch_statementContext ctx) {
        exitStatement("FETCH", ctx.getStop().getLine());
    }
    
    @Override
    public void enterClose_statement(PlSqlParser.Close_statementContext ctx) {
        enterStatement("CLOSE_CURSOR", ctx.getStart().getLine());
    }
    @Override
    public void exitClose_statement(PlSqlParser.Close_statementContext ctx) {
        exitStatement("CLOSE_CURSOR", ctx.getStop().getLine());
    }
    
    @Override
    public void enterOpen_for_statement(PlSqlParser.Open_for_statementContext ctx) {
        enterStatement("OPEN_FOR", ctx.getStart().getLine());
    }
    @Override
    public void exitOpen_for_statement(PlSqlParser.Open_for_statementContext ctx) {
        exitStatement("OPEN_FOR", ctx.getStop().getLine());
    }
    
    @Override
    public void enterExit_statement(PlSqlParser.Exit_statementContext ctx) {
        enterStatement("EXIT", ctx.getStart().getLine());
    }
    @Override
    public void exitExit_statement(PlSqlParser.Exit_statementContext ctx) {
        exitStatement("EXIT", ctx.getStop().getLine());
    }
    

    /* ------------------------------------------------------------ */
    /* WITH/CTE/JOIN/EXECUTE_IMMEDIATE/COMMIT 관련 리스너 모음 */
    /* ------------------------------------------------------------ */

    @Override
    public void enterSubquery_factoring_clause(PlSqlParser.Subquery_factoring_clauseContext ctx) {
        enterStatement("CTE", ctx.getStart().getLine());
    }
    @Override
    public void exitSubquery_factoring_clause(PlSqlParser.Subquery_factoring_clauseContext ctx) {
        exitStatement("CTE", ctx.getStop().getLine());
    }
    
    @Override
    public void enterJoin_clause(PlSqlParser.Join_clauseContext ctx) {
        enterStatement("JOIN", ctx.getStart().getLine());
    }
    @Override
    public void exitJoin_clause(PlSqlParser.Join_clauseContext ctx) {
        exitStatement("JOIN", ctx.getStop().getLine());
    }

    @Override
    public void enterSubquery_operation_part(PlSqlParser.Subquery_operation_partContext ctx) {
        String t = ctx.getText().toUpperCase();
        String type = t.contains("UNIONALL") ? "UNION_ALL"
                : t.contains("UNION")     ? "UNION"
                : t.contains("INTERSECT") ? "INTERSECT"
                : t.contains("MINUS")     ? "MINUS"
                : "SET_OPERATION";
        enterStatement(type, ctx.getStart().getLine());
    }

    @Override
    public void exitSubquery_operation_part(PlSqlParser.Subquery_operation_partContext ctx) {
        String t = ctx.getText().toUpperCase();
        String type = t.contains("UNION ALL") ? "UNION_ALL"
                : t.contains("UNION")     ? "UNION"
                : t.contains("INTERSECT") ? "INTERSECT"
                : t.contains("MINUS")     ? "MINUS"
                : "SET_OPERATION";
        exitStatement(type, ctx.getStop().getLine());
    }

    @Override
    public void enterExecute_immediate(PlSqlParser.Execute_immediateContext ctx) {
        enterStatement("EXECUTE_IMMEDIATE", ctx.getStart().getLine());
    }
    @Override
    public void exitExecute_immediate(PlSqlParser.Execute_immediateContext ctx) {
        exitStatement("EXECUTE_IMMEDIATE", ctx.getStop().getLine());
    }

    @Override
    public void enterCommit_statement(PlSqlParser.Commit_statementContext ctx) {
        enterStatement("COMMIT", ctx.getStart().getLine());
    }
    
    @Override
    public void exitCommit_statement(PlSqlParser.Commit_statementContext ctx) {
        exitStatement("COMMIT", ctx.getStop().getLine());
    }

    // @Override
    // public void enterCreate_table(PlSqlParser.Create_tableContext ctx) {
    //     enterStatement("CREATE_TABLE", ctx.getStart().getLine());
    // }
    
    // @Override
    // public void exitCreate_table(PlSqlParser.Create_tableContext ctx) {
    //     exitStatement("CREATE_TABLE", ctx.getStop().getLine());
    // }

    // @Override
    // public void enterCreate_sequence(PlSqlParser.Create_sequenceContext ctx) {
    //     enterStatement("CREATE_SEQUENCE", ctx.getStart().getLine());
    // }

    // @Override
    // public void exitCreate_sequence(PlSqlParser.Create_sequenceContext ctx) {
    //     exitStatement("CREATE_SEQUENCE", ctx.getStop().getLine());
    // }


    /* ------------------------------------------------------------ */
    /* 필요시 기타 관련 리스너 모음 */
    /* ------------------------------------------------------------ */
    // @Override
    // public void enterWindowing_clause(PlSqlParser.Windowing_clauseContext ctx) {
    //     enterStatement("WINDOWING", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitWindowing_clause(PlSqlParser.Windowing_clauseContext ctx) {
    //     exitStatement("WINDOWING", ctx.getStop().getLine());
    // }


    // @Override
    // public void enterForall_statement(PlSqlParser.Forall_statementContext ctx) {
    //     enterStatement("FORALL", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitForall_statement(PlSqlParser.Forall_statementContext ctx) {
    //     exitStatement("FORALL", ctx.getStop().getLine());
    // }

        // @Override
    // public void enterOrder_by_clause(PlSqlParser.Order_by_clauseContext ctx) {
    //     enterStatement("ORDER_BY", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitOrder_by_clause(PlSqlParser.Order_by_clauseContext ctx) {
    //     exitStatement("ORDER_BY", ctx.getStop().getLine());
    // }
    
    // @Override
    // public void enterOver_clause(PlSqlParser.Over_clauseContext ctx) {
    //     enterStatement("OVER", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitOver_clause(PlSqlParser.Over_clauseContext ctx) {
    //     exitStatement("OVER", ctx.getStop().getLine());
    // }

        // @Override
    // public void enterJoin_on_part(PlSqlParser.Join_on_partContext ctx) {
    //     enterStatement("JOIN_ON", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitJoin_on_part(PlSqlParser.Join_on_partContext ctx) {
    //     exitStatement("JOIN_ON", ctx.getStop().getLine());
    // }
    // @Override
    // public void enterJoin_using_part(PlSqlParser.Join_using_partContext ctx) {
    //     enterStatement("JOIN_USING", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitJoin_using_part(PlSqlParser.Join_using_partContext ctx) {
    //     exitStatement("JOIN_USING", ctx.getStop().getLine());
    // }
    
    // @Override
    // public void enterWhere_clause(PlSqlParser.Where_clauseContext ctx) {
    //     enterStatement("WHERE", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitWhere_clause(PlSqlParser.Where_clauseContext ctx) {
    //     exitStatement("WHERE", ctx.getStop().getLine());
    // }
    
    // @Override
    // public void enterGroup_by_clause(PlSqlParser.Group_by_clauseContext ctx) {
    //     enterStatement("GROUP_BY", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitGroup_by_clause(PlSqlParser.Group_by_clauseContext ctx) {
    //     exitStatement("GROUP_BY", ctx.getStop().getLine());
    // }
    
    // @Override
    // public void enterHierarchical_query_clause(PlSqlParser.Hierarchical_query_clauseContext ctx) {
    //     enterStatement("HIERARCHICAL_QUERY", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitHierarchical_query_clause(PlSqlParser.Hierarchical_query_clauseContext ctx) {
    //     exitStatement("HIERARCHICAL_QUERY", ctx.getStop().getLine());
    // }

    // @Override
    // public void enterWith_clause(PlSqlParser.With_clauseContext ctx) {
    //     enterStatement("WITH", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitWith_clause(PlSqlParser.With_clauseContext ctx) {
    //     exitStatement("WITH", ctx.getStop().getLine());
    // }

        // @Override
    // public void enterSubquery(PlSqlParser.SubqueryContext ctx) {
    //     enterStatement("SUBQUERY", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitSubquery(PlSqlParser.SubqueryContext ctx) {
    //     exitStatement("SUBQUERY", ctx.getStop().getLine());
    // }
    
    // @Override
    // public void enterFrom_clause(PlSqlParser.From_clauseContext ctx) {
    //     enterStatement("FROM", ctx.getStart().getLine());
    // }
    // @Override
    // public void exitFrom_clause(PlSqlParser.From_clauseContext ctx) {
    //     exitStatement("FROM", ctx.getStop().getLine());
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