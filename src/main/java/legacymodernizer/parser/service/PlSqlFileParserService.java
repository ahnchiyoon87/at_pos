package legacymodernizer.parser.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import legacymodernizer.parser.antlr.CaseChangingCharStream;
import legacymodernizer.parser.antlr.CustomPlSqlListener;
import legacymodernizer.parser.antlr.plsql.PlSqlLexer;
import legacymodernizer.parser.antlr.plsql.PlSqlParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PlSqlFileParserService {
        
    // SQL 객체(패키지, 프로시저, 함수 등)의 이름을 추출하기 위한 정규식 패턴
    private static final Pattern SQL_OBJECT_PATTERN = Pattern.compile(
        "CREATE\\s+OR\\s+REPLACE\\s+(?:PACKAGE|PACKAGE BODY|PROCEDURE|FUNCTION)\\s+([\\w$]+)", 
        Pattern.CASE_INSENSITIVE
    );


    /**
     * MultipartFile을 받아 파일 시스템에 저장하고 관련 정보를 반환
     * @param file 업로드된 MultipartFile 객체
     * @return 파일명, 파일내용, 객체명을 포함한 Map
     * @throws IOException 파일 처리 중 발생하는 예외
     */
    public Map<String, String> saveFile(MultipartFile file, String plsqlDir) throws IOException {
        String fileName = file.getOriginalFilename();
        File directory = new File(plsqlDir);

        // 저장 디렉토리가 없으면 생성
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("디렉토리 생성 실패: " + plsqlDir);
        }

        // 파일 저장 및 내용 분석
        File outputFile = new File(directory, fileName);

        // 파일이 이미 존재하지 않는 경우에만 저장
        if (!outputFile.exists()) {
            file.transferTo(outputFile);
            System.out.println("새 파일 저장됨: " + fileName);
        } else {
            System.out.println("파일이 이미 존재하여 저장하지 않음: " + fileName);
        }

        String fileContent = readFileContent(outputFile);                  
        String objectName = extractSqlObjectName(fileContent); 

        System.out.println("추출된 SQL 객체 이름: " + objectName);
        return Map.of("fileName", fileName, "fileContent", fileContent, "objectName", objectName);
    }

    
    /**
     * 파일의 내용을 다양한 인코딩으로 시도하여 읽어옴
     * @param file 읽을 파일 객체
     * @return 파일의 내용 문자열
     * @throws IOException 파일 읽기 실패시 발생
     */
    public String readFileContent(File file) throws IOException {
        try {
            // UTF-8로 먼저 시도
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            try {
                // UTF-8 실패시 EUC-KR 시도
                return Files.readString(file.toPath(), Charset.forName("EUC-KR"));
            } catch (Exception e2) {
                // EUC-KR 실패시 MS949 시도
                return Files.readString(file.toPath(), Charset.forName("MS949"));
            }
        }
    }

    /**
     * PL/SQL 파일을 파싱하고 구조를 JSON 형식으로 저장
     * @param fileName 분석할 파일명
     * @param outputPath 결과를 저장할 경로
     * @throws IOException 파일 처리 중 발생하는 예외
     */
    public void parseAndSaveStructure(String fileName, String outputPath, String plsqlDir) throws IOException {
        try (InputStream in = new FileInputStream(new File(plsqlDir, fileName))) {
            // ANTLR 파서 설정
            CharStream s = CharStreams.fromStream(in);
            CaseChangingCharStream upper = new CaseChangingCharStream(s, true);
            PlSqlLexer lexer = new PlSqlLexer(upper);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            
            // 구문 분석
            PlSqlParser parser = new PlSqlParser(tokens);
            ParserRuleContext tree = parser.sql_script();
            
            // 리스너를 통한 구문 트리 순회
            CustomPlSqlListener listener = new CustomPlSqlListener(tokens);
            new ParseTreeWalker().walk(listener, tree);
            
            // 분석 결과를 JSON 파일로 저장
            File analysisFile = new File(outputPath);
            try (FileWriter file = new FileWriter(analysisFile)) {
                file.write(listener.getRoot().toJson());
            }
        }
    }

    /**
     * SQL 내용에서 객체 이름을 추출
     * @param sqlContent SQL 파일 내용
     * @return 추출된 객체 이름, 매칭되지 않으면 null
     */
    public String extractSqlObjectName(String sqlContent) {
        Matcher matcher = SQL_OBJECT_PATTERN.matcher(sqlContent);
        if (matcher.find()) {
            return matcher.group(1);  // 첫 번째 매칭된 객체 이름만 반환
        }
        return null;
    }
}