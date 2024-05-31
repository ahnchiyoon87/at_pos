package legacymodernizer.parser.service;

import java.util.HashMap;
import java.util.Map;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import legacymodernizer.parser.antlr.CaseChangingCharStream;
import legacymodernizer.parser.antlr.CustomPlSqlListener;
import legacymodernizer.parser.antlr.PlSqlLexer;
import legacymodernizer.parser.antlr.PlSqlParser;

@Service
public class AnalysisProcessingService {

    public ResponseEntity<Map<String, String>> analsisFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String baseFileName = fileName.substring(0, fileName.lastIndexOf('.'));

        try (InputStream in = file.getInputStream()) {
            String originalContent = new String(file.getBytes(), StandardCharsets.UTF_8);

            CharStream charStream = CharStreams.fromStream(in);
            CaseChangingCharStream upperStream = new CaseChangingCharStream(charStream, true);
            PlSqlLexer lexer = new PlSqlLexer(upperStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            PlSqlParser parser = new PlSqlParser(tokens);
            ParserRuleContext tree = parser.sql_script();

            ParseTreeWalker walker = new ParseTreeWalker();
            CustomPlSqlListener listener = new CustomPlSqlListener(tokens);
            walker.walk(listener, tree);

            String jsonOutput = listener.getRoot().toJson();

            // JSON 객체 생성 및 파일 이름과 내용 추가
            Map<String, String> responseMap = new HashMap<>();
            responseMap.put("fileName", baseFileName);
            responseMap.put("Analysis", jsonOutput);
            responseMap.put("fileContent", originalContent);

            return ResponseEntity.ok(responseMap);

        } catch (Exception e) {
            System.err.println("Failed to Analysis Content: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error processing analysis content", e);
        }
    }
}