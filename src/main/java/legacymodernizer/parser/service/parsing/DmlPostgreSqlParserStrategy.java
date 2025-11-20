package legacymodernizer.parser.service.parsing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import legacymodernizer.parser.antlr.dml_postgresql.CustomDmlPostgreSQLListener;
import legacymodernizer.parser.antlr.dml_postgresql.PostgreSQLLexer;
import legacymodernizer.parser.antlr.dml_postgresql.PostgreSQLParser;
import legacymodernizer.parser.service.PlSqlFileParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DML/DDL 전용 PostgreSQL 파싱 전략 구현체
 * 복잡한 DML 서브쿼리, JOIN 등을 상세히 파싱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmlPostgreSqlParserStrategy implements DbmsParserStrategy {
    
    private final PlSqlFileParserService plSqlFileParserService;
    
    @Override
    public Map<String, Object> processUploadByMetadata(String sessionUUID,
                                                      String projectName,
                                                      Object systemsObj,
                                                      Object ddlObj,
                                                      Object seqObj,
                                                      Map<String, MultipartFile> nameToFile) {
        // 파일 업로드 로직은 DBMS에 관계없이 동일하므로 재사용
        return plSqlFileParserService.processUploadByMetadata(
            sessionUUID, projectName, systemsObj, ddlObj, seqObj, nameToFile
        );
    }
    
    @Override
    public Map<String, Object> processParsingBySystems(String sessionUUID,
                                                      String projectName,
                                                      List<?> systems) {
        // DML/DDL 전용 PostgreSQL 파싱 전략을 사용하여 처리
        return plSqlFileParserService.processParsingBySystemsWithStrategy(
            sessionUUID, 
            projectName, 
            systems, 
            this::parseFile
        );
    }
    
    @Override
    public void parseFile(File file, String outputPath) throws Exception {
        log.debug("      [ANTLR DML/DDL PostgreSQL 파싱 시작]");
        try (InputStream in = new FileInputStream(file)) {
            CharStream charStream = CharStreams.fromStream(in);
            PostgreSQLLexer lexer = new PostgreSQLLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            PostgreSQLParser parser = new PostgreSQLParser(tokens);
            
            // PostgreSQL 파서의 root 규칙 실행
            PostgreSQLParser.RootContext tree = parser.root();
            
            // CustomDmlPostgreSQLListener로 파싱 (DML/DDL 전용)
            CustomDmlPostgreSQLListener listener = new CustomDmlPostgreSQLListener(tokens);
            new ParseTreeWalker().walk(listener, tree);
            
            // 분석 결과 저장
            File analysisFile = new File(outputPath);
            try (FileWriter writer = new FileWriter(analysisFile)) {
                writer.write(listener.getRoot().toJson());
            }
            log.debug("      → 분석 결과 저장: {}", analysisFile.getName());
        }
    }
    
    @Override
    public String getSupportedDbmsType() {
        return "dml_postgresql";
    }
}


