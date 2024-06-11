package legacymodernizer.parser.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import legacymodernizer.parser.antlr.CaseChangingCharStream;
import legacymodernizer.parser.antlr.CustomPlSqlListener;
import legacymodernizer.parser.antlr.PlSqlLexer;
import legacymodernizer.parser.antlr.PlSqlParser;

@Service
public class PlSqlFileParserService {

    public File parseAndSaveStructure(String fileName) throws IOException {
        System.out.println("\n분석시작\n");

        InputStream in = new FileInputStream("result/plsql/" + fileName);
        CharStream s = CharStreams.fromStream(in);
    
        CaseChangingCharStream upper = new CaseChangingCharStream(s, true);
    
        PlSqlLexer lexer = new PlSqlLexer(upper);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
    
        PlSqlParser parser = new PlSqlParser(tokens);
        ParserRuleContext tree = parser.sql_script();
    
        ParseTreeWalker walker = new ParseTreeWalker();
        CustomPlSqlListener listener = new CustomPlSqlListener(tokens);
        walker.walk(listener, tree);
    
        listener.printStructure();
    
        System.out.println(listener.getRoot().toJson());
    
        String outputDir = "C:\\Users\\roede\\Desktop\\uEngine\\Antlr-Server\\result\\analysis";
        File directory = new File(outputDir);
        if (!directory.exists()) {
            boolean dirsCreated = directory.mkdirs(); 
            if (!dirsCreated) {
                throw new IOException("Failed to create directory structure: " + outputDir);
            }
        }
    
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String outputFilePath = outputDir + "\\" + baseName + ".json";
        File analysisFile = new File(outputFilePath);
        try (FileWriter file = new FileWriter(analysisFile)) {
            file.write(listener.getRoot().toJson());
            file.flush();
        }
    
        return analysisFile;
    }

    public Map<String, String> saveFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        // Windows 환경에서의 절대 경로 설정
        String baseDir = "C:\\Users\\roede\\Desktop\\uEngine\\Antlr-Server\\result\\plsql"; // 절대 경로로 수정
        File directory = new File(baseDir);
        if (!directory.exists()) {
            boolean dirsCreated = directory.mkdirs(); // 디렉토리가 없으면 생성
            if (!dirsCreated) {
                throw new IOException("Failed to create directory structure: " + baseDir);
            }
        }
    
        String path = baseDir + "\\" + fileName; // Windows 경로 구분자
        File outputFile = new File(path);
    
        // 파일 저장
        file.transferTo(outputFile);
    
        String fileContent = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
    
        Map<String, String> fileInfo = new HashMap<>();
        fileInfo.put("fileName", fileName);
        fileInfo.put("fileContent", fileContent);
        return fileInfo;
    }
}